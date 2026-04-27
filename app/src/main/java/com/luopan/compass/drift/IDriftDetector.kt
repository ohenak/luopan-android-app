package com.luopan.compass.drift

import com.luopan.compass.model.InterferenceState

/**
 * Contract for drift detection implementations.
 *
 * Extracted as an interface to allow [FakeDriftDetector] injection in unit tests
 * (AT-VM-DRIFT-01 in Phase D). [DriftDetector] is the production implementation.
 *
 * Thread-safety: implementations are not required to be thread-safe.
 * Must be called from a single thread (Dispatchers.Default in CompassViewModel).
 *
 * Phase 4 — TSPEC §5.3; TE TSPEC-v1 F-01; SE PROPERTIES-v2 F-01
 */
interface IDriftDetector {

    /**
     * Called once per sensor frame from [CompassViewModel.startSensorCollection()].
     *
     * @param accVariance      Variance of combined 3-axis accel magnitude over rolling 5-s window.
     * @param measuredMagnitudeUt Current magnetometer field magnitude in µT.
     * @param interferenceState Current InterferenceState from InterferenceDetector.
     * @param expectedFieldUt  CalibrationRecord.expected_field_ut (0.0 if no calibration).
     * @return [DriftEvent.TRIGGERED] when the drift condition is met; [DriftEvent.RESET] on
     *         precondition violation transition; null otherwise.
     */
    fun onFrame(
        accVariance: Float,
        measuredMagnitudeUt: Float,
        interferenceState: InterferenceState,
        expectedFieldUt: Float
    ): DriftEvent?

    /**
     * Resets the detector to IDLE with a cleared timer.
     * Called by CompassViewModel on RESULT_OK from CalibrationWizardActivity.
     */
    fun reset()
}
