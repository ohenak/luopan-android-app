package com.luopan.compass.drift

import com.luopan.compass.model.InterferenceState
import com.luopan.compass.util.Clock
import kotlin.math.abs

/**
 * State machine that tracks whether the measured magnetic field has drifted significantly
 * from the calibration-time expected field for a sustained period.
 *
 * [onFrame] is called from the CompassViewModel sensor loop on Dispatchers.Default.
 * Thread-safety: not thread-safe; must always be called from the same thread.
 *
 * @param clock Injectable Clock for deterministic testing. Production: WallClock.
 */
class DriftDetector(private val clock: Clock) : IDriftDetector {

    private enum class State { IDLE, COUNTING }

    private var state: State = State.IDLE

    /** Wall-clock ms when all preconditions first held in the current COUNTING run. */
    private var countingStartMs: Long? = null

    companion object {
        private const val DRIFT_WINDOW_MS = 60_000L       // 60 seconds
        private const val DRIFT_THRESHOLD = 0.10f         // 10%
        private const val ACCEL_VARIANCE_THRESHOLD = 0.01f // (m/s²)²
    }

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
                null
            }
            State.COUNTING -> {
                if (!allPreconditionsMet) {
                    state = State.IDLE
                    countingStartMs = null
                    return DriftEvent.RESET
                }
                val elapsedMs = clock.nowMs() - (countingStartMs ?: clock.nowMs())
                if (elapsedMs > DRIFT_WINDOW_MS) {
                    val deviation = abs(measuredMagnitudeUt - expectedFieldUt) / expectedFieldUt
                    if (deviation > DRIFT_THRESHOLD) {
                        state = State.IDLE
                        countingStartMs = null
                        return DriftEvent.TRIGGERED
                    }
                    // Timer elapsed but deviation <= 10%: stay COUNTING from current start
                    // (re-evaluate on next frame — "COUNTING past 60s" rule from FSPEC-CAL-03)
                }
                null
            }
        }
    }

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
