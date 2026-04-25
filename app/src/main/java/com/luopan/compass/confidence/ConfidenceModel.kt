package com.luopan.compass.confidence

import com.luopan.compass.model.CalibrationQuality
import com.luopan.compass.model.InterferenceState
import com.luopan.compass.model.OverallConfidence
import com.luopan.compass.model.SensorState

class ConfidenceModel {

    companion object {
        private const val TILT_MODERATE_THRESHOLD = 5.0
        private const val TILT_POOR_THRESHOLD = 20.0
        private const val NOISE_MODERATE_THRESHOLD = 0.1
        private const val NOISE_POOR_THRESHOLD = 0.5
        private const val CAL_AGE_MODERATE_DAYS = 7L
        private const val CAL_AGE_POOR_DAYS = 30L
    }

    fun compute(
        interferencState: InterferenceState,
        tilt_deg: Double,
        noiseVariance: Double,
        calibrationAgeDays: Long,
        hasGyroscope: Boolean,
        sensorState: SensorState = SensorState.NORMAL,
        calibrationQuality: CalibrationQuality = CalibrationQuality.GOOD
    ): OverallConfidence {
        return when (sensorState) {
            SensorState.STUCK -> OverallConfidence.SENSOR_ERROR
            SensorState.STABILIZING -> OverallConfidence.STABILIZING
            SensorState.NORMAL -> normalCompute(interferencState, tilt_deg, noiseVariance, calibrationAgeDays, hasGyroscope, calibrationQuality)
        }
    }

    private fun normalCompute(
        interferencState: InterferenceState,
        tilt_deg: Double,
        noiseVariance: Double,
        calibrationAgeDays: Long,
        hasGyroscope: Boolean,
        calibrationQuality: CalibrationQuality
    ): OverallConfidence {
        val scores = listOf(
            scoreInterference(interferencState),
            scoreTilt(tilt_deg),
            scoreNoiseVariance(noiseVariance),
            scoreCalibrationAge(calibrationAgeDays),
            scoreCalibrationQuality(calibrationAgeDays, calibrationQuality)
        )

        val minScore = scores.minOf { it.numericValue }
        var result = when (minScore) {
            ConfidenceScore.GOOD.numericValue -> OverallConfidence.HIGH
            ConfidenceScore.MODERATE.numericValue -> OverallConfidence.MODERATE
            else -> OverallConfidence.POOR
        }

        if (!hasGyroscope && result == OverallConfidence.HIGH) {
            result = OverallConfidence.MODERATE
        }

        return result
    }


    internal fun scoreInterference(state: InterferenceState): ConfidenceScore = when (state) {
        InterferenceState.CLEAR -> ConfidenceScore.GOOD
        InterferenceState.MODERATE -> ConfidenceScore.MODERATE
        InterferenceState.WARNING -> ConfidenceScore.POOR
    }

    internal fun scoreTilt(tilt_deg: Double): ConfidenceScore = when {
        tilt_deg > TILT_POOR_THRESHOLD -> ConfidenceScore.POOR
        tilt_deg > TILT_MODERATE_THRESHOLD -> ConfidenceScore.MODERATE
        else -> ConfidenceScore.GOOD
    }

    internal fun scoreNoiseVariance(variance: Double): ConfidenceScore = when {
        variance > NOISE_POOR_THRESHOLD -> ConfidenceScore.POOR
        variance > NOISE_MODERATE_THRESHOLD -> ConfidenceScore.MODERATE
        else -> ConfidenceScore.GOOD
    }

    internal fun scoreCalibrationAge(ageDays: Long): ConfidenceScore = when {
        ageDays < 0 -> ConfidenceScore.POOR
        ageDays > CAL_AGE_POOR_DAYS -> ConfidenceScore.POOR
        ageDays > CAL_AGE_MODERATE_DAYS -> ConfidenceScore.MODERATE
        else -> ConfidenceScore.GOOD
    }

    internal fun scoreCalibrationQuality(ageDays: Long, quality: CalibrationQuality = CalibrationQuality.GOOD): ConfidenceScore {
        if (ageDays < 0) return ConfidenceScore.POOR
        return when (quality) {
            CalibrationQuality.GOOD -> ConfidenceScore.GOOD
            CalibrationQuality.FAIR -> ConfidenceScore.MODERATE
            CalibrationQuality.POOR -> ConfidenceScore.POOR
        }
    }
}
