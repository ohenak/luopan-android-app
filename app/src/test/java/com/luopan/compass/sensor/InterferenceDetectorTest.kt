package com.luopan.compass.sensor

import com.luopan.compass.model.InterferenceState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class InterferenceDetectorTest {

    private lateinit var detector: InterferenceDetector

    @Before fun setUp() { detector = InterferenceDetector() }

    private fun metrics(field: Float = 0f, incl: Float = 0f) = InterferenceMetrics(field, incl)

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
}
