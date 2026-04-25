package com.luopan.compass.model

import com.luopan.compass.confidence.ConfidenceScore
import org.junit.Test

/**
 * Exhaustiveness tests: each `when` expression has no `else` branch,
 * so adding a new enum case without updating this file will cause a compile error.
 */
class EnumExhaustivenessTest {

    @Test
    fun `OverallConfidence all cases covered`() {
        val values = OverallConfidence.values()
        for (v in values) {
            val label = when (v) {
                OverallConfidence.HIGH -> "HIGH"
                OverallConfidence.MODERATE -> "MODERATE"
                OverallConfidence.POOR -> "POOR"
                OverallConfidence.STABILIZING -> "STABILIZING"
                OverallConfidence.SENSOR_ERROR -> "SENSOR_ERROR"
            }
            assert(label == v.name)
        }
    }

    @Test
    fun `InterferenceState all cases covered`() {
        val values = InterferenceState.values()
        for (v in values) {
            val label = when (v) {
                InterferenceState.CLEAR -> "CLEAR"
                InterferenceState.MODERATE -> "MODERATE"
                InterferenceState.WARNING -> "WARNING"
            }
            assert(label == v.name)
        }
    }

    @Test
    fun `CalibrationQuality all cases covered`() {
        val values = CalibrationQuality.values()
        for (v in values) {
            val label = when (v) {
                CalibrationQuality.GOOD -> "GOOD"
                CalibrationQuality.FAIR -> "FAIR"
                CalibrationQuality.POOR -> "POOR"
            }
            assert(label == v.name)
        }
    }

    @Test
    fun `SensorAdvisory all cases covered`() {
        val values = SensorAdvisory.values()
        for (v in values) {
            val label = when (v) {
                SensorAdvisory.NONE -> "NONE"
                SensorAdvisory.NO_GYROSCOPE -> "NO_GYROSCOPE"
                SensorAdvisory.POWER_SAVING -> "POWER_SAVING"
                SensorAdvisory.STABILIZING -> "STABILIZING"
            }
            assert(label == v.name)
        }
    }

    @Test
    fun `SensorState all cases covered`() {
        val values = SensorState.values()
        for (v in values) {
            val label = when (v) {
                SensorState.NORMAL -> "NORMAL"
                SensorState.STABILIZING -> "STABILIZING"
                SensorState.STUCK -> "STUCK"
            }
            assert(label == v.name)
        }
    }

    @Test
    fun `CalDotColor all cases covered`() {
        val values = CalDotColor.values()
        for (v in values) {
            val label = when (v) {
                CalDotColor.GREEN -> "GREEN"
                CalDotColor.AMBER -> "AMBER"
                CalDotColor.RED -> "RED"
            }
            assert(label == v.name)
        }
    }

    @Test
    fun `ConfidenceScore all cases covered`() {
        val values = ConfidenceScore.values()
        for (v in values) {
            val numeric = when (v) {
                ConfidenceScore.GOOD -> 3
                ConfidenceScore.MODERATE -> 2
                ConfidenceScore.POOR -> 1
            }
            assert(numeric == v.numericValue)
        }
    }
}
