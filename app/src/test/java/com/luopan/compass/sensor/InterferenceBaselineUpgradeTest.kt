package com.luopan.compass.sensor

import com.luopan.compass.magnetic.FakeMagneticFieldModel
import com.luopan.compass.magnetic.WmmResult
import com.luopan.compass.model.InterferenceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the WMM interference baseline upgrade (AT-F).
 *
 * TSPEC §10.1 — InterferenceBaselineUpgradeTest:
 *   Inject two [FakeMagneticFieldModel] configurations (WMM vs. GeomagneticField-equivalent
 *   values); assert [InterferenceState] differs between configurations for the same sensor input.
 *
 * This verifies that the interference baseline source (WMM2025 vs. GeomagneticField) materially
 * affects the interference classification as required by AT-F-01 (FSPEC §2.6).
 *
 * PLAN §4 P8.2 success criteria — InterferenceBaselineUpgradeTest (AT-F).
 */
class InterferenceBaselineUpgradeTest {

    private lateinit var detector: InterferenceDetector

    @Before
    fun setUp() {
        detector = InterferenceDetector()
    }

    // ── AT-F: WMM vs GeomagneticField produce different InterferenceState ─────

    /**
     * AT-F primary assertion: two FakeMagneticFieldModel configurations (WMM values vs.
     * GeomagneticField-equivalent values) produce different InterferenceStates for the same
     * sensor input.
     *
     * Sensor scenario:
     *   - sensorFieldMagnitude = 55_000 µT  (sensor reading)
     *   - sensorInclination    = 70°         (sensor pitch)
     *
     * WMM configuration (accurate baseline):
     *   - expectedField = 52_300 µT  (NOAA-pinned vector for lat=40°N, lon=-105°W)
     *   - expectedInclination = 66°
     *   - fieldDeviation = |55000 - 52300| / 52300 ≈ 5.2% → below MODERATE threshold (15%) → CLEAR
     *   - inclinationDeviation = |70 - 66| = 4° → above MODERATE threshold (3°) → MODERATE
     *
     * GeomagneticField-equivalent configuration (less accurate baseline):
     *   - expectedField = 49_000 µT  (a value that differs by >5% from WMM)
     *   - expectedInclination = 60°
     *   - fieldDeviation = |55000 - 49000| / 49000 ≈ 12.2% → below WARNING (25%) but at MODERATE (15%)
     *   - inclinationDeviation = |70 - 60| = 10° → above WARNING threshold (8°) → WARNING
     *
     * The two configurations must produce different InterferenceStates.
     */
    @Test
    fun `WMM and GeomagneticField configurations produce different InterferenceStates for same sensor input`() {
        val sensorField = 55_000f
        val sensorInclination = 70f

        // WMM configuration (accurate, NOAA-pinned values)
        val wmmModel = FakeMagneticFieldModel(
            fieldMagnitude = 52_300f,
            inclination = 66f,
            declination = 8.93f,
            modelId = "WMM2025"
        )
        val wmmResult = WmmResult(
            declination = wmmModel.getDeclination(40.0, -105.0, 0.0, 2025.5),
            inclination = wmmModel.getExpectedInclination(40.0, -105.0, 0.0, 2025.5),
            totalField  = wmmModel.getExpectedFieldMagnitude(40.0, -105.0, 0.0, 2025.5)
        )

        // GeomagneticField-equivalent configuration (less accurate, divergent values)
        val geoModel = FakeMagneticFieldModel(
            fieldMagnitude = 49_000f,
            inclination = 60f,
            declination = 7.5f,
            modelId = "AndroidGeoField"
        )
        val geoResult = WmmResult(
            declination = geoModel.getDeclination(40.0, -105.0, 0.0, 2025.5),
            inclination = geoModel.getExpectedInclination(40.0, -105.0, 0.0, 2025.5),
            totalField  = geoModel.getExpectedFieldMagnitude(40.0, -105.0, 0.0, 2025.5)
        )

        // Build interference metrics with the WMM baseline
        val wmmMetrics = InterferenceMetricsFactory.build(
            sensorFieldMagnitude = sensorField,
            sensorInclination    = sensorInclination,
            wmmResult            = wmmResult,
            emaBaselineFallback  = 0f
        )

        // Build interference metrics with the GeomagneticField baseline
        val geoMetrics = InterferenceMetricsFactory.build(
            sensorFieldMagnitude = sensorField,
            sensorInclination    = sensorInclination,
            wmmResult            = geoResult,
            emaBaselineFallback  = 0f
        )

        // Run the detector for WMM — reset between runs for clean state
        detector.reset()
        detector.updateState(wmmMetrics, nowNs = 0L)
        val wmmState = detector.getState()

        // Run the detector for GeoField
        detector.reset()
        detector.updateState(geoMetrics, nowNs = 0L)
        val geoState = detector.getState()

        // AT-F-01: the interference classification must differ between the two model configs
        assertNotEquals(
            "InterferenceState must differ between WMM (${wmmState}) and GeoField (${geoState}) " +
            "for the same sensor input — baseline source must materially affect classification",
            wmmState,
            geoState
        )
    }

    /**
     * WMM baseline with accurate expected field → MODERATE from inclination deviation only.
     *
     * sensorField = 55_000, expectedField = 52_300
     *   fieldDeviation = |55000 - 52300| / 52300 ≈ 5.2% → below 15% MODERATE threshold
     * sensorInclination = 70, expectedInclination = 66
     *   inclinationDeviation = 4° → above 3° MODERATE threshold
     * Expected state: MODERATE
     */
    @Test
    fun `WMM accurate baseline produces MODERATE from inclination deviation alone`() {
        val metrics = InterferenceMetricsFactory.build(
            sensorFieldMagnitude = 55_000f,
            sensorInclination    = 70f,
            wmmResult            = WmmResult(declination = 8.93f, inclination = 66f, totalField = 52_300f),
            emaBaselineFallback  = 0f
        )

        detector.updateState(metrics, nowNs = 0L)

        assertEquals(InterferenceState.MODERATE, detector.getState())
    }

    /**
     * GeomagneticField-equivalent baseline with larger inclination gap → WARNING.
     *
     * sensorField = 55_000, expectedField = 49_000
     *   fieldDeviation = |55000 - 49000| / 49000 ≈ 12.2% → below 25% WARNING threshold
     * sensorInclination = 70, expectedInclination = 60
     *   inclinationDeviation = 10° → above 8° WARNING threshold
     * Expected state: WARNING
     */
    @Test
    fun `GeomagneticField-equivalent baseline produces WARNING from large inclination gap`() {
        val metrics = InterferenceMetricsFactory.build(
            sensorFieldMagnitude = 55_000f,
            sensorInclination    = 70f,
            wmmResult            = WmmResult(declination = 7.5f, inclination = 60f, totalField = 49_000f),
            emaBaselineFallback  = 0f
        )

        detector.updateState(metrics, nowNs = 0L)

        assertEquals(InterferenceState.WARNING, detector.getState())
    }

    /**
     * EMA fallback path (wmmResult = null): same sensor values with EMA baseline close to
     * sensor produce CLEAR after 3s hysteresis.
     *
     * sensorField = 55_000, emaBaseline = 55_000 → fieldDeviation = 0%
     * sensorInclination = 70 → inclinationDeviation from 0° = 70° → WARNING!
     *
     * This contrast shows that the EMA fallback treats inclination from 0° reference,
     * whereas the WMM path uses the model's expected inclination.
     */
    @Test
    fun `EMA fallback with zero expected inclination produces WARNING from large raw inclination`() {
        val metrics = InterferenceMetricsFactory.build(
            sensorFieldMagnitude = 55_000f,
            sensorInclination    = 70f,
            wmmResult            = null,                // EMA fallback
            emaBaselineFallback  = 55_000f
        )

        detector.updateState(metrics, nowNs = 0L)

        // EMA path: inclinationDeviation = abs(sensorInclination - 0°) = 70° → WARNING
        assertEquals(InterferenceState.WARNING, detector.getState())
    }

    /**
     * Same sensor input with WMM providing an accurate expected inclination (66°) produces
     * MODERATE (not WARNING), showing the WMM path is more precise than the EMA fallback.
     */
    @Test
    fun `WMM baseline is more precise than EMA fallback for same inclination sensor value`() {
        val sensorField = 55_000f
        val sensorInclination = 70f

        // EMA fallback: inclinationDeviation = 70° → WARNING
        val emaMetrics = InterferenceMetricsFactory.build(
            sensorFieldMagnitude = sensorField,
            sensorInclination    = sensorInclination,
            wmmResult            = null,
            emaBaselineFallback  = sensorField  // perfect match on field
        )
        detector.reset()
        detector.updateState(emaMetrics, nowNs = 0L)
        val emaState = detector.getState()

        // WMM path: inclinationDeviation = |70 - 66| = 4° → MODERATE
        val wmmMetrics = InterferenceMetricsFactory.build(
            sensorFieldMagnitude = sensorField,
            sensorInclination    = sensorInclination,
            wmmResult            = WmmResult(declination = 8.93f, inclination = 66f, totalField = 52_300f),
            emaBaselineFallback  = 0f
        )
        detector.reset()
        detector.updateState(wmmMetrics, nowNs = 0L)
        val wmmState = detector.getState()

        assertEquals("EMA fallback should be WARNING for 70° raw inclination", InterferenceState.WARNING, emaState)
        assertEquals("WMM baseline should be MODERATE for 4° deviation", InterferenceState.MODERATE, wmmState)
        assertNotEquals("WMM and EMA must differ for this sensor scenario", emaState, wmmState)
    }

    // ── InterferenceMetricsFactory field population (AT-F supporting tests) ──

    /**
     * When wmmResult is provided, expectedField_uT and expectedInclination_deg are sourced
     * from the WmmResult, not from the EMA baseline.
     */
    @Test
    fun `InterferenceMetricsFactory uses WmmResult values when provided`() {
        val wmmResult = WmmResult(declination = 8.93f, inclination = 66f, totalField = 52_300f)

        val metrics = InterferenceMetricsFactory.build(
            sensorFieldMagnitude = 55_000f,
            sensorInclination    = 70f,
            wmmResult            = wmmResult,
            emaBaselineFallback  = 999f    // should be ignored
        )

        assertEquals(52_300f, metrics.expectedField_uT, 0.01f)
        assertEquals(66f, metrics.expectedInclination_deg, 0.01f)
    }

    /**
     * When wmmResult is null, expectedField_uT comes from emaBaselineFallback.
     */
    @Test
    fun `InterferenceMetricsFactory uses EMA baseline when wmmResult is null`() {
        val metrics = InterferenceMetricsFactory.build(
            sensorFieldMagnitude = 55_000f,
            sensorInclination    = 70f,
            wmmResult            = null,
            emaBaselineFallback  = 50_000f
        )

        assertEquals(50_000f, metrics.expectedField_uT, 0.01f)
        assertEquals(0f, metrics.expectedInclination_deg, 0.01f)  // EMA path: always 0
    }
}
