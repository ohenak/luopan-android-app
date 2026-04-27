package com.luopan.compass.drift

import com.luopan.compass.model.InterferenceState

/**
 * Interface for drift detection, extracted from [DriftDetector] to allow
 * [FakeDriftDetector] injection in unit tests (TE TSPEC-v1 F-01).
 */
interface IDriftDetector {
    /**
     * Called once per sensor frame from [CompassViewModel.startSensorCollection()].
     *
     * @param accVariance      Variance of combined 3-axis accel magnitude over rolling 5-s window.
     * @param measuredMagnitudeUt Current magnetometer field magnitude in µT.
     * @param interferenceState Current InterferenceState from InterferenceDetector.
     * @param expectedFieldUt  CalibrationRecord.expected_field_ut (0.0 if no calibration).
     * @return [DriftEvent.TRIGGERED] when drift condition is met; [DriftEvent.RESET] on precondition
     *         violation from COUNTING state; null otherwise.
     */
    fun onFrame(
        accVariance: Float,
        measuredMagnitudeUt: Float,
        interferenceState: InterferenceState,
        expectedFieldUt: Float
    ): DriftEvent?

    /** Resets the detector to IDLE with a cleared timer. */
    fun reset()
}
