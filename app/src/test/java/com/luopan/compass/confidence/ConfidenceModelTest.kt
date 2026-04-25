package com.luopan.compass.confidence

import com.luopan.compass.model.InterferenceState
import com.luopan.compass.model.OverallConfidence
import com.luopan.compass.model.SensorState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ConfidenceModelTest {

    private lateinit var model: ConfidenceModel

    @Before fun setUp() { model = ConfidenceModel() }

    @Test fun `all GOOD dimensions with gyro → HIGH`() {
        val result = model.compute(InterferenceState.CLEAR, 0.0, 0.0, 0L, true, SensorState.NORMAL)
        assertEquals(OverallConfidence.HIGH, result)
    }

    @Test fun `WARNING interference → POOR`() {
        val result = model.compute(InterferenceState.WARNING, 0.0, 0.0, 0L, true, SensorState.NORMAL)
        assertEquals(OverallConfidence.POOR, result)
    }

    @Test fun `MODERATE interference → at most MODERATE`() {
        val result = model.compute(InterferenceState.MODERATE, 0.0, 0.0, 0L, true, SensorState.NORMAL)
        assertTrue(result == OverallConfidence.MODERATE || result == OverallConfidence.POOR)
    }

    @Test fun `tilt 5_0 exact is GOOD threshold is exclusive`() {
        assertEquals(ConfidenceScore.GOOD, model.scoreTilt(5.0))
    }

    @Test fun `tilt just above 5_0 → MODERATE`() {
        assertEquals(ConfidenceScore.MODERATE, model.scoreTilt(5.001))
    }

    @Test fun `tilt 20_0 exact is MODERATE threshold is exclusive`() {
        assertEquals(ConfidenceScore.MODERATE, model.scoreTilt(20.0))
    }

    @Test fun `tilt just above 20_0 → POOR`() {
        assertEquals(ConfidenceScore.POOR, model.scoreTilt(20.001))
    }

    @Test fun `no gyroscope caps HIGH to MODERATE`() {
        val result = model.compute(InterferenceState.CLEAR, 0.0, 0.0, 0L, false, SensorState.NORMAL)
        assertEquals(OverallConfidence.MODERATE, result)
    }

    @Test fun `no gyroscope does not upgrade POOR`() {
        val result = model.compute(InterferenceState.WARNING, 0.0, 0.0, 0L, false, SensorState.NORMAL)
        assertEquals(OverallConfidence.POOR, result)
    }

    @Test fun `isStabilizing returns STABILIZING`() {
        val result = model.compute(InterferenceState.CLEAR, 0.0, 0.0, 0L, true, SensorState.STABILIZING)
        assertEquals(OverallConfidence.STABILIZING, result)
    }

    @Test fun `uncalibrated (-1 days) → POOR`() {
        val result = model.compute(InterferenceState.CLEAR, 0.0, 0.0, -1L, true, SensorState.NORMAL)
        assertEquals(OverallConfidence.POOR, result)
    }

    @Test fun `cal age 7 days → GOOD`() {
        assertEquals(ConfidenceScore.GOOD, model.scoreCalibrationAge(7L))
    }

    @Test fun `cal age 7 days + 1 → MODERATE`() {
        assertEquals(ConfidenceScore.MODERATE, model.scoreCalibrationAge(8L))
    }

    @Test fun `cal age 30 days → MODERATE`() {
        assertEquals(ConfidenceScore.MODERATE, model.scoreCalibrationAge(30L))
    }

    @Test fun `cal age 31 days → POOR`() {
        assertEquals(ConfidenceScore.POOR, model.scoreCalibrationAge(31L))
    }

    @Test fun `noise variance 0_1 → GOOD`() {
        assertEquals(ConfidenceScore.GOOD, model.scoreNoiseVariance(0.1))
    }

    @Test fun `noise variance 0_101 → MODERATE`() {
        assertEquals(ConfidenceScore.MODERATE, model.scoreNoiseVariance(0.101))
    }

    @Test fun `noise variance 0_5 → MODERATE`() {
        assertEquals(ConfidenceScore.MODERATE, model.scoreNoiseVariance(0.5))
    }

    @Test fun `noise variance 0_501 → POOR`() {
        assertEquals(ConfidenceScore.POOR, model.scoreNoiseVariance(0.501))
    }

    @Test fun `minimum score wins`() {
        // CLEAR (GOOD) + big tilt (POOR) → POOR
        val result = model.compute(InterferenceState.CLEAR, 25.0, 0.0, 0L, true, SensorState.NORMAL)
        assertEquals(OverallConfidence.POOR, result)
    }
}
