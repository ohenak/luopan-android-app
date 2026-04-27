package com.luopan.compass.drift

import com.luopan.compass.model.InterferenceState
import com.luopan.compass.util.Clock
import kotlin.math.abs

/**
 * State machine that tracks whether the measured magnetic field has drifted significantly
 * from the calibration-time expected field for a sustained period.
 *
 * States: IDLE → COUNTING → (TRIGGERED or back to IDLE)
 *
 * [onFrame] is called from the CompassViewModel sensor loop on Dispatchers.Default.
 * Thread-safety: not thread-safe; must always be called from the same thread.
 *
 * Phase 4 — TSPEC §5.3
 */
class DriftDetector(private val clock: Clock) : IDriftDetector {

    private enum class State { IDLE, COUNTING }

    private var state: State = State.IDLE

    /** Wall-clock ms when all preconditions first held in the current COUNTING run. Null when IDLE. */
    private var countingStartMs: Long? = null

    companion object {
        private const val DRIFT_WINDOW_MS = 60_000L       // 60 seconds
        private const val DRIFT_THRESHOLD = 0.10f         // 10% (strict: deviation MUST BE > 10%)
        private const val ACCEL_VARIANCE_THRESHOLD = 0.01f // (m/s²)²
    }

    /**
     * Called once per sensor frame from [CompassViewModel.startSensorCollection()].
     *
     * Preconditions for COUNTING:
     *   1. accVariance < 0.01 (device is stationary)
     *   2. interferenceState ≠ WARNING
     *   3. expectedFieldUt > 0.0 (calibration data exists)
     *
     * Timer behavior after 60 s:
     *   - deviation > 10%: emit TRIGGERED, reset to IDLE
     *   - deviation ≤ 10%: stay COUNTING (re-evaluate-each-frame rule — FSPEC-CAL-03 backport)
     *
     * @return [DriftEvent.TRIGGERED] when drift detected; [DriftEvent.RESET] on precondition
     *         violation from COUNTING; null otherwise.
     */
    override fun onFrame(
        accVariance: Float,
        measuredMagnitudeUt: Float,
        interferenceState: InterferenceState,
        expectedFieldUt: Float
    ): DriftEvent? {
        val allPreconditionsMet =
            accVariance < ACCEL_VARIANCE_THRESHOLD &&
            interferenceState != InterferenceState.WARNING &&
            expectedFieldUt > 0.0f

        return when (state) {
            State.IDLE -> {
                if (allPreconditionsMet) {
                    state = State.COUNTING
                    countingStartMs = clock.nowMs()
                }
                null  // IDLE→COUNTING or IDLE→IDLE: no event
            }
            State.COUNTING -> {
                if (!allPreconditionsMet) {
                    // Precondition violated: transition back to IDLE
                    state = State.IDLE
                    countingStartMs = null
                    return DriftEvent.RESET
                }
                val elapsedMs = clock.nowMs() - (countingStartMs ?: clock.nowMs())
                if (elapsedMs > DRIFT_WINDOW_MS) {
                    val deviation = abs(measuredMagnitudeUt - expectedFieldUt) / expectedFieldUt
                    if (deviation > DRIFT_THRESHOLD) {
                        // Drift confirmed: trigger and reset to IDLE
                        state = State.IDLE
                        countingStartMs = null
                        return DriftEvent.TRIGGERED
                    }
                    // Timer elapsed but deviation ≤ 10%: stay COUNTING from current start.
                    // Re-evaluate deviation on next frame — do NOT reset timer.
                    // (FSPEC-CAL-03 re-evaluate-each-frame rule: PM TSPEC-v1 F-01)
                }
                null  // Still counting; no event
            }
        }
    }

    /**
     * Resets the detector to IDLE with a cleared timer.
     * Called by CompassViewModel on RESULT_OK from CalibrationWizardActivity.
     */
    override fun reset() {
        state = State.IDLE
        countingStartMs = null
    }

    /**
     * Exposed for testing: current elapsed counting time in ms, or null if IDLE.
     * @VisibleForTesting
     */
    internal fun elapsedCountingMs(): Long? {
        val start = countingStartMs ?: return null
        return clock.nowMs() - start
    }
}
