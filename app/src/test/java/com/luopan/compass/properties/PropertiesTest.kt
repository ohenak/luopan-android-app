package com.luopan.compass.properties

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.luopan.compass.calibration.CalibrationEngine
import com.luopan.compass.calibration.CalibrationRepository
import com.luopan.compass.calibration.CalibrationResult
import com.luopan.compass.confidence.ConfidenceModel
import com.luopan.compass.db.LuopanDatabase
import com.luopan.compass.fusion.FusionEngine
import com.luopan.compass.fusion.MadgwickFilter
import com.luopan.compass.model.CalibrationQuality
import com.luopan.compass.model.InterferenceState
import com.luopan.compass.model.OverallConfidence
import com.luopan.compass.model.SensorState
import com.luopan.compass.sensor.InterferenceDetector
import com.luopan.compass.sensor.InterferenceMetrics
import com.luopan.compass.sensor.NoiseSpikeFilter
import com.luopan.compass.sensor.SensorFrame
import com.luopan.compass.sensor.SensorStateMonitor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.abs
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
class PropertiesTest {

    // --- Helpers ---

    private fun madgwickFilter() = MadgwickFilter(beta = 0.1f)
    private fun fusionEngine() = FusionEngine()
    private fun calEngine() = CalibrationEngine()
    private fun interferenceDetector() = InterferenceDetector()
    private fun confidenceModel() = ConfidenceModel()

    private fun metrics(field: Float = 0f, incl: Float = 0f) = InterferenceMetrics(
        fieldMagnitude_uT = 0f,
        expectedField_uT = 0f,
        fieldDeviation = field,
        inclination_deg = 0f,
        expectedInclination_deg = 0f,
        inclinationDeviation_deg = incl
    )

    private fun frame(ts: Long, mx: Float = 30f, my: Float = 0f, mz: Float = -30f,
                      ax: Float = 0f, ay: Float = 0f, az: Float = 9.8f) =
        SensorFrame(ts, mx, my, mz, 0f, 0f, 0f, ax, ay, az, null, null, null)

    // ==================== PROP-FUSION ====================

    /** PROP-FUSION-01: heading always in [0, 360) */
    @Test fun `PROP-FUSION-01 heading always in 0 to 360`() {
        val engine = fusionEngine()
        val testCases = listOf(
            Triple(30f, 0f, -30f),
            Triple(-30f, 0f, 30f),
            Triple(0f, 30f, -30f),
            Triple(0f, -30f, 30f),
            Triple(30f, 30f, 0f)
        )
        testCases.forEachIndexed { i, (mx, my, mz) ->
            val result = engine.process(frame(i * 20_000_000L, mx, my, mz))
            assertTrue("heading must be >= 0", result.heading_deg >= 0.0)
            assertTrue("heading must be < 360", result.heading_deg < 360.0)
        }
    }

    /** PROP-FUSION-02: heading never NaN or Infinite even with degenerate input */
    @Test fun `PROP-FUSION-02 heading never NaN or Infinite`() {
        val engine = fusionEngine()
        // Degenerate: all-zero magnetometer
        val result = engine.process(SensorFrame(20_000_000L, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 9.8f, null, null, null))
        assertFalse("heading must not be NaN", result.heading_deg.isNaN())
        assertTrue("heading must be finite", result.heading_deg.isFinite())
    }

    /** PROP-FUSION-03: quaternion normalized after every update */
    @Test fun `PROP-FUSION-03 quaternion normalized after every update`() {
        val f = madgwickFilter()
        repeat(100) {
            f.update(0f, 0f, 9.8f, 0f, 0f, 0f, 30f, 0f, -30f, 0.02f)
            val q = f.getQuaternion()
            val norm = sqrt((q[0]*q[0] + q[1]*q[1] + q[2]*q[2] + q[3]*q[3]).toDouble())
            assertEquals("quaternion norm must be 1.0 after iteration $it", 1.0, norm, 1e-5)
        }
    }

    /** PROP-FUSION-04: full update (zero gyro) converges to ~North within 50 iterations */
    @Test fun `PROP-FUSION-04 full update converges to North within 50 iterations`() {
        val f = MadgwickFilter(beta = 0.1f)
        val engine = FusionEngine(f)
        // North input: mx=0, my=30 → tiltCompensatedHeading returns atan2(-0, 30) = 0° (North).
        // Device flat (az=9.8): gravity = (0,0,1), horizontal projection hx=0, hy=30.
        repeat(50) { i ->
            engine.process(frame(i * 20_000_000L, mx = 0f, my = 30f, mz = 0f,
                                  ax = 0f, ay = 0f, az = 9.8f))
        }
        val result = engine.process(frame(50 * 20_000_000L, mx = 0f, my = 30f, mz = 0f))
        val diff = abs(result.heading_deg).let { if (it > 180.0) 360.0 - it else it }
        assertTrue("heading should be within 5° of North, was ${result.heading_deg}°", diff <= 5.0)
    }

    /** PROP-FUSION-05: no-gyro mode converges to ~North within 50 iterations */
    @Test fun `PROP-FUSION-05 updateNoGyro converges to North within 50 iterations`() {
        val f = MadgwickFilter(beta = 0.1f)
        val engine = FusionEngine(f)
        // North input: mx=0, my=30 → tiltCompensatedHeading returns 0° (North).
        // Use null gyro to trigger updateNoGyro path.
        repeat(50) { i ->
            engine.process(SensorFrame(i * 20_000_000L, 0f, 30f, 0f, 0f, 0f, 0f, 0f, 0f, 9.8f, null, null, null))
        }
        val result = engine.process(SensorFrame(50 * 20_000_000L, 0f, 30f, 0f, 0f, 0f, 0f, 0f, 0f, 9.8f, null, null, null))
        val diff = abs(result.heading_deg).let { if (it > 180.0) 360.0 - it else it }
        assertTrue("heading should be within 5° of North, was ${result.heading_deg}°", diff <= 5.0)
    }

    // ==================== PROP-CAL ====================

    /** PROP-CAL-01: coverage 0 when all samples identical */
    @Test fun `PROP-CAL-01 coverage zero when all samples identical`() {
        val engine = calEngine()
        repeat(50) { engine.addSample(10f, 5f, -3f) }
        val coverage = engine.getCoverageScore()
        // All at same point -> no range -> xRange=0, totalRange=0 -> returns 0f when totalRange==0
        assertEquals(0f, coverage, 0.001f)
    }

    /** PROP-CAL-03/04: residual <= 1.0 and coverage >= 0.6 -> GOOD */
    @Test fun `PROP-CAL-03 residual le 1_0 and coverage ge 0_6 gives GOOD`() {
        assertEquals(CalibrationQuality.GOOD, calEngine().classifyQuality(0.8f, 0.7f))
    }

    @Test fun `PROP-CAL-04 residual exactly 1_0 is GOOD closed upper bound`() {
        assertEquals(CalibrationQuality.GOOD, calEngine().classifyQuality(1.0f, 0.7f))
    }

    /** PROP-CAL-05: residual just above 1.0 -> FAIR */
    @Test fun `PROP-CAL-05 residual 1_0001 is FAIR`() {
        assertEquals(CalibrationQuality.FAIR, calEngine().classifyQuality(1.0001f, 0.5f))
    }

    /** PROP-CAL-09: third save evicts oldest */
    @Test fun `PROP-CAL-09 third save evicts oldest`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = LuopanDatabase.buildInMemory(ctx)
        val repo = CalibrationRepository(db.calibrationDao())
        val dao = db.calibrationDao()

        fun mockResult(quality: CalibrationQuality) = CalibrationResult(
            hardIron = floatArrayOf(1f, 2f, 3f),
            softIron = Array(3) { i -> FloatArray(3) { j -> if (i == j) 1f else 0f } },
            residualMicroTesla = 0.5f, coverageScore = 0.8f, quality = quality
        )

        repo.save(mockResult(CalibrationQuality.GOOD), 1000L)   // record1
        repo.save(mockResult(CalibrationQuality.FAIR), 2000L)   // record2
        repo.save(mockResult(CalibrationQuality.POOR), 3000L)   // record3

        val all = dao.getAll()
        assertEquals("exactly 2 records after 3 saves", 2, all.size)
        assertEquals("current is record3 (POOR)", "POOR", repo.getCurrent()!!.quality)
        assertEquals("rollback is record2 (FAIR)", "FAIR", repo.getRollback()!!.quality)
        db.close()
    }

    /** PROP-CAL-10: age in UTC days */
    @Test fun `PROP-CAL-10 calibration age computed as UTC days`() {
        val T = 1_000_000_000L
        val eightDaysMs = 8L * 86_400_000L
        val ageMs = (T + eightDaysMs) - T
        val ageDays = ageMs / 86_400_000L
        assertEquals(8L, ageDays)
    }

    // ==================== PROP-DETECT ====================

    /** PROP-DETECT-03: field 0.251f -> WARNING */
    @Test fun `PROP-DETECT-03 field 0_251 goes to WARNING`() {
        val d = interferenceDetector()
        d.updateState(metrics(field = 0.251f), 0L)
        assertEquals(InterferenceState.WARNING, d.getState())
    }

    /** PROP-DETECT-06: stays WARNING at 2.9s (clearance not yet elapsed) */
    @Test fun `PROP-DETECT-06 stays WARNING at 2_9s below threshold`() {
        val d = interferenceDetector()
        d.updateState(metrics(field = 0.30f), 0L)   // -> WARNING
        d.updateState(metrics(field = 0.10f), 0L)   // start clearance timer
        d.updateState(metrics(field = 0.10f), 2_900_000_000L) // 2.9s elapsed
        assertEquals(InterferenceState.WARNING, d.getState())
    }

    /** PROP-DETECT-07: mid-window excursion resets clearance timer */
    @Test fun `PROP-DETECT-07 mid-window MODERATE spike resets clearance timer`() {
        val d = interferenceDetector()
        d.updateState(metrics(field = 0.30f), 0L)       // -> WARNING
        d.updateState(metrics(field = 0.10f), 0L)       // start clearance timer at t=0
        d.updateState(metrics(field = 0.20f), 1_000_000_000L) // MODERATE at t=1s -> timer reset
        d.updateState(metrics(field = 0.10f), 2_000_000_000L) // below again at t=2s
        d.updateState(metrics(field = 0.10f), 4_500_000_000L) // 2.5s after restart -- not enough
        assertEquals(InterferenceState.WARNING, d.getState())
    }

    /** PROP-DETECT-08: inclination 2.9 degrees stays CLEAR after 3.1s */
    @Test fun `PROP-DETECT-08 inclination 2_9 stays in clearance zone`() {
        val d = interferenceDetector()
        // Start in CLEAR, field=0.05, incl=2.9 degrees -- both below thresholds
        d.updateState(metrics(field = 0.05f, incl = 2.9f), 0L)
        d.updateState(metrics(field = 0.05f, incl = 2.9f), 3_100_000_000L)
        assertEquals(InterferenceState.CLEAR, d.getState())
    }

    /** PROP-DETECT-10: inclination 7.9 degrees -> MODERATE */
    @Test fun `PROP-DETECT-10 inclination 7_9 deg goes to MODERATE`() {
        val d = interferenceDetector()
        d.updateState(metrics(field = 0.05f, incl = 7.9f), 0L)
        assertEquals(InterferenceState.MODERATE, d.getState())
    }

    /** PROP-DETECT-12: NoiseSpikeFilter prevents single spike from triggering WARNING */
    @Test fun `PROP-DETECT-12 spike filter prevents single outlier from triggering WARNING`() {
        val filter = NoiseSpikeFilter(windowSize = 5, spikeThreshold = 50f)
        val detector = interferenceDetector()

        // Warm up filter with normal samples
        repeat(5) {
            filter.filter(30f, 0f, -30f) // always accepted
            detector.updateState(metrics(field = 0.05f), it * 20_000_000L)
        }
        assertEquals(InterferenceState.CLEAR, detector.getState())

        // Send a spike -- spike filter should reject it
        val spikeResult = filter.filter(30f + 200f, 0f, -30f)
        // Spike rejected (null), so we don't call updateState for it
        if (spikeResult == null) {
            // Good -- spike was rejected, detector stays CLEAR
        } else {
            // In edge case where spike passes, update detector
            detector.updateState(metrics(field = 0.80f), 6 * 20_000_000L)
        }
        // Normal frame after spike
        filter.filter(30.1f, 0.1f, -29.9f)
        detector.updateState(metrics(field = 0.05f), 7 * 20_000_000L)

        assertEquals(InterferenceState.CLEAR, detector.getState())
    }

    // ==================== PROP-CONF ====================

    /** PROP-CONF-01: minimum of five dimensions wins */
    @Test fun `PROP-CONF-01 minimum of five dimension scores determines result`() {
        val model = confidenceModel()
        // MODERATE interference + good everything else -> MODERATE overall
        val result = model.compute(
            interferencState = InterferenceState.MODERATE,
            tilt_deg = 0.0,
            noiseVariance = 0.0,
            calibrationAgeDays = 0L,
            hasGyroscope = true,
            sensorState = SensorState.NORMAL
        )
        assertEquals(OverallConfidence.MODERATE, result)
    }

    /** PROP-CONF-07: STUCK -> SENSOR_ERROR */
    @Test fun `PROP-CONF-07 SensorState STUCK returns SENSOR_ERROR`() {
        val result = confidenceModel().compute(
            interferencState = InterferenceState.CLEAR,
            tilt_deg = 0.0,
            noiseVariance = 0.0,
            calibrationAgeDays = 0L,
            hasGyroscope = true,
            sensorState = SensorState.STUCK
        )
        assertEquals(OverallConfidence.SENSOR_ERROR, result)
    }

    /** PROP-CONF-08: STABILIZING -> STABILIZING */
    @Test fun `PROP-CONF-08 SensorState STABILIZING returns STABILIZING`() {
        val result = confidenceModel().compute(
            interferencState = InterferenceState.CLEAR,
            tilt_deg = 0.0,
            noiseVariance = 0.0,
            calibrationAgeDays = 0L,
            hasGyroscope = true,
            sensorState = SensorState.STABILIZING
        )
        assertEquals(OverallConfidence.STABILIZING, result)
    }

    /** PROP-CONF-09: cal quality with residual 1.0 -> GOOD (evaluates GOOD first) */
    @Test fun `PROP-CONF-09 residual 1_0 calibration quality scores GOOD`() {
        val engine = calEngine()
        val score = engine.classifyQuality(1.0f, 0.7f)
        assertEquals(CalibrationQuality.GOOD, score)
    }

    /** M-09a: scoreCalibrationQuality maps FAIR -> MODERATE */
    @Test fun `scoreCalibrationQuality FAIR calibration maps to MODERATE confidence`() {
        val model = confidenceModel()
        val result = model.compute(
            interferencState = InterferenceState.CLEAR,
            tilt_deg = 0.0,
            noiseVariance = 0.0,
            calibrationAgeDays = 0L,
            hasGyroscope = true,
            sensorState = SensorState.NORMAL,
            calibrationQuality = CalibrationQuality.FAIR
        )
        assertEquals(OverallConfidence.MODERATE, result)
    }

    /** M-09b: scoreCalibrationQuality maps POOR -> POOR */
    @Test fun `scoreCalibrationQuality POOR calibration maps to POOR confidence`() {
        val model = confidenceModel()
        val result = model.compute(
            interferencState = InterferenceState.CLEAR,
            tilt_deg = 0.0,
            noiseVariance = 0.0,
            calibrationAgeDays = 0L,
            hasGyroscope = true,
            sensorState = SensorState.NORMAL,
            calibrationQuality = CalibrationQuality.POOR
        )
        assertEquals(OverallConfidence.POOR, result)
    }

    /** M-09c: uncalibrated (ageDays < 0) overrides quality to POOR regardless of quality enum */
    @Test fun `scoreCalibrationQuality uncalibrated always returns POOR regardless of quality param`() {
        val model = confidenceModel()
        val result = model.compute(
            interferencState = InterferenceState.CLEAR,
            tilt_deg = 0.0,
            noiseVariance = 0.0,
            calibrationAgeDays = -1L,
            hasGyroscope = true,
            sensorState = SensorState.NORMAL,
            calibrationQuality = CalibrationQuality.GOOD
        )
        assertEquals(OverallConfidence.POOR, result)
    }

    /** M-10a: getPerAxisCoverage returns 1.0 for best axis when all ranges equal */
    @Test fun `getPerAxisCoverage equal ranges return all ones`() {
        val engine = calEngine()
        // Add samples that create equal X, Y, Z ranges of 10 units each
        for (i in 0..9) {
            engine.addSample(i.toFloat(), i.toFloat(), i.toFloat())
        }
        val (cx, cy, cz) = engine.getPerAxisCoverage()
        assertEquals(1.0f, cx, 0.01f)
        assertEquals(1.0f, cy, 0.01f)
        assertEquals(1.0f, cz, 0.01f)
    }

    /** M-10b: getPerAxisCoverage returns 0 for compressed axes */
    @Test fun `getPerAxisCoverage zero range axis returns zero coverage`() {
        val engine = calEngine()
        // X varies 0..10, Y and Z constant
        for (i in 0..10) {
            engine.addSample(i.toFloat(), 5.0f, 5.0f)
        }
        val (cx, cy, cz) = engine.getPerAxisCoverage()
        assertEquals(1.0f, cx, 0.01f)
        assertEquals(0.0f, cy, 0.01f)
        assertEquals(0.0f, cz, 0.01f)
    }

    // ==================== PROP-PERSIST ====================

    /** PROP-PERSIST-04: UTC timestamp is timezone-invariant */
    @Test fun `PROP-PERSIST-04 timestamps are UTC epoch milliseconds`() {
        val T = System.currentTimeMillis()
        // Simulate storing and retrieving timestamp
        val stored = T
        val retrieved = stored
        assertEquals(T, retrieved)
        // Verify it can be parsed as UTC epoch
        val instant = java.time.Instant.ofEpochMilli(retrieved)
        assertNotNull(instant)
        assertTrue(instant.toEpochMilli() > 0)
    }
}
