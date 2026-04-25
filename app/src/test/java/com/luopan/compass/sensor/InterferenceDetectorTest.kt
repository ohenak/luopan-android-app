package com.luopan.compass.sensor

import com.luopan.compass.model.InterferenceState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class InterferenceDetectorTest {

    private lateinit var detector: InterferenceDetector

    @Before fun setUp() { detector = InterferenceDetector() }

    private fun metrics(field: Float = 0f, incl: Float = 0f) = InterferenceMetrics(
        fieldMagnitude_uT = 0f,
        expectedField_uT = 0f,
        fieldDeviation = field,
        inclination_deg = 0f,
        expectedInclination_deg = 0f,
        inclinationDeviation_deg = incl
    )

    @Test fun `initial state is CLEAR`() {
        assertEquals(InterferenceState.CLEAR, detector.getState())
    }

    @Test fun `field 24_9 percent from CLEAR goes to MODERATE`() {
        detector.updateState(metrics(field = 0.249f), 0L)
        assertEquals(InterferenceState.MODERATE, detector.getState())
    }

    @Test fun `field 25 percent goes to WARNING`() {
        detector.updateState(metrics(field = 0.25f), 0L)
        assertEquals(InterferenceState.WARNING, detector.getState())
    }

    @Test fun `incl 8 deg goes to WARNING`() {
        detector.updateState(metrics(incl = 8f), 0L)
        assertEquals(InterferenceState.WARNING, detector.getState())
    }

    @Test fun `incl 3 deg goes to MODERATE`() {
        detector.updateState(metrics(incl = 3f), 0L)
        assertEquals(InterferenceState.MODERATE, detector.getState())
    }

    @Test fun `incl 2_99 deg stays CLEAR after 3s`() {
        detector.updateState(metrics(incl = 2.99f), 0L)
        detector.updateState(metrics(incl = 2.99f), 3_000_000_000L)
        assertEquals(InterferenceState.CLEAR, detector.getState())
    }

    @Test fun `hysteresis - MODERATE does not clear before 3s`() {
        detector.updateState(metrics(field = 0.20f), 0L)
        assertEquals(InterferenceState.MODERATE, detector.getState())
        detector.updateState(metrics(field = 0.05f), 1_000_000_000L) // below threshold, 1s
        assertEquals(InterferenceState.MODERATE, detector.getState()) // still MODERATE
    }

    @Test fun `hysteresis - clears after 3s below threshold`() {
        detector.updateState(metrics(field = 0.05f), 0L) // start clearance timer
        detector.updateState(metrics(field = 0.05f), 3_000_000_000L) // exactly 3s
        assertEquals(InterferenceState.CLEAR, detector.getState())
    }

    @Test fun `MODERATE resets clearance timer`() {
        detector.updateState(metrics(field = 0.05f), 0L)       // start timer at t=0
        detector.updateState(metrics(field = 0.20f), 1_000_000_000L) // MODERATE at t=1s, timer reset
        detector.updateState(metrics(field = 0.05f), 2_000_000_000L) // below again at t=2s
        detector.updateState(metrics(field = 0.05f), 4_500_000_000L) // 2.5s after restart, not enough
        assertEquals(InterferenceState.MODERATE, detector.getState())
    }

    @Test fun `PROP-DETECT-04 clearance after exactly 3s`() {
        detector.updateState(metrics(field = 0.10f), 0L)
        detector.updateState(metrics(field = 0.10f), 3_000_000_000L)
        assertEquals(InterferenceState.CLEAR, detector.getState())
    }

    @Test fun `PROP-DETECT-05 field 15 percent goes to MODERATE`() {
        detector.updateState(metrics(field = 0.15f), 0L)
        assertEquals(InterferenceState.MODERATE, detector.getState())
    }

    @Test fun `reset returns to CLEAR`() {
        detector.updateState(metrics(field = 0.30f), 0L)
        detector.reset()
        assertEquals(InterferenceState.CLEAR, detector.getState())
    }

    // -----------------------------------------------------------------------
    // P4.2 — WMM-baseline test cases
    //
    // These cases exercise InterferenceDetector with metrics where expectedField_uT
    // and expectedInclination_deg are populated from a WMM model (not EMA).
    // The deviation values are pre-computed by the caller (CompassViewModel) before
    // being passed to updateState — the detector itself does not change.
    // -----------------------------------------------------------------------

    /**
     * WMM baseline: when sensor field matches WMM expected field, deviation is low → CLEAR.
     *
     * Sensor reads 52300 nT (same as WMM expected value), so fieldDeviation = 0% → CLEAR.
     */
    @Test fun `WMM-baseline CLEAR when sensor field matches WMM expected field`() {
        val wmmExpectedField = 52300f
        val sensorField = 52300f
        val deviation = kotlin.math.abs(sensorField - wmmExpectedField) / wmmExpectedField
        val m = InterferenceMetrics(
            fieldMagnitude_uT = sensorField,
            expectedField_uT = wmmExpectedField,
            fieldDeviation = deviation,
            inclination_deg = 66.0f,
            expectedInclination_deg = 66.0f,
            inclinationDeviation_deg = 0.0f
        )
        detector.updateState(m, 0L)
        detector.updateState(m, 3_000_000_000L) // past hysteresis window
        assertEquals(InterferenceState.CLEAR, detector.getState())
    }

    /**
     * WMM baseline: sensor field 20% above WMM expected → MODERATE interference.
     *
     * WMM expected = 52300 nT; sensor reads 52300 * 1.20 = 62760 nT → 20% deviation → MODERATE.
     */
    @Test fun `WMM-baseline MODERATE when sensor field is 20 percent above WMM expected`() {
        val wmmExpectedField = 52300f
        val sensorField = wmmExpectedField * 1.20f
        val deviation = kotlin.math.abs(sensorField - wmmExpectedField) / wmmExpectedField
        val m = InterferenceMetrics(
            fieldMagnitude_uT = sensorField,
            expectedField_uT = wmmExpectedField,
            fieldDeviation = deviation,
            inclination_deg = 66.0f,
            expectedInclination_deg = 66.0f,
            inclinationDeviation_deg = 0.0f
        )
        detector.updateState(m, 0L)
        assertEquals(InterferenceState.MODERATE, detector.getState())
    }

    /**
     * WMM baseline: sensor field 30% above WMM expected → WARNING interference.
     *
     * WMM expected = 52300 nT; sensor reads 52300 * 1.30 = 67990 nT → 30% deviation → WARNING.
     */
    @Test fun `WMM-baseline WARNING when sensor field is 30 percent above WMM expected`() {
        val wmmExpectedField = 52300f
        val sensorField = wmmExpectedField * 1.30f
        val deviation = kotlin.math.abs(sensorField - wmmExpectedField) / wmmExpectedField
        val m = InterferenceMetrics(
            fieldMagnitude_uT = sensorField,
            expectedField_uT = wmmExpectedField,
            fieldDeviation = deviation,
            inclination_deg = 66.0f,
            expectedInclination_deg = 66.0f,
            inclinationDeviation_deg = 0.0f
        )
        detector.updateState(m, 0L)
        assertEquals(InterferenceState.WARNING, detector.getState())
    }

    /**
     * WMM baseline inclination: sensor inclination differs from WMM expected by 5° → MODERATE.
     *
     * WMM expected inclination = 66.0°; sensor pitch = 69.0° → inclinationDeviation = 3.0° → MODERATE.
     */
    @Test fun `WMM-baseline MODERATE when inclination deviation is 3 degrees from WMM expected`() {
        val wmmExpectedInclination = 66.0f
        val sensorInclination = 69.0f
        val inclDev = kotlin.math.abs(sensorInclination - wmmExpectedInclination)
        val m = InterferenceMetrics(
            fieldMagnitude_uT = 52300f,
            expectedField_uT = 52300f,
            fieldDeviation = 0f,
            inclination_deg = sensorInclination,
            expectedInclination_deg = wmmExpectedInclination,
            inclinationDeviation_deg = inclDev
        )
        detector.updateState(m, 0L)
        assertEquals(InterferenceState.MODERATE, detector.getState())
    }

    /**
     * WMM baseline inclination: sensor inclination differs from WMM expected by 10° → WARNING.
     *
     * WMM expected inclination = 66.0°; sensor pitch = 74.0° → inclinationDeviation = 8.0° → WARNING.
     */
    @Test fun `WMM-baseline WARNING when inclination deviation is 8 degrees from WMM expected`() {
        val wmmExpectedInclination = 66.0f
        val sensorInclination = 74.0f
        val inclDev = kotlin.math.abs(sensorInclination - wmmExpectedInclination)
        val m = InterferenceMetrics(
            fieldMagnitude_uT = 52300f,
            expectedField_uT = 52300f,
            fieldDeviation = 0f,
            inclination_deg = sensorInclination,
            expectedInclination_deg = wmmExpectedInclination,
            inclinationDeviation_deg = inclDev
        )
        detector.updateState(m, 0L)
        assertEquals(InterferenceState.WARNING, detector.getState())
    }
}
