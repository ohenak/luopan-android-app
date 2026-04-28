package com.luopan.compass.drift

import com.luopan.compass.model.InterferenceState

/**
 * Test double for [IDriftDetector].
 *
 * Constructor-configured [nextEvent] is returned from every [onFrame] call.
 * Tracks [onFrameCallCount] and [resetCallCount] for assertion in tests.
 *
 * Phase 4 — PLAN B-4; used by CompassViewModelDriftTest (Phase D)
 */
class FakeDriftDetector(
    /** The event to return from every onFrame() call. Null = no event. */
    private val nextEvent: DriftEvent?
) : IDriftDetector {

    /** Number of times [onFrame] has been called. */
    var onFrameCallCount: Int = 0
        private set

    /** Number of times [reset] has been called. */
    var resetCallCount: Int = 0
        private set

    override fun onFrame(
        accVariance: Float,
        measuredMagnitudeUt: Float,
        interferenceState: InterferenceState,
        expectedFieldUt: Float
    ): DriftEvent? {
        onFrameCallCount++
        return nextEvent
    }

    override fun reset() {
        resetCallCount++
    }
}
