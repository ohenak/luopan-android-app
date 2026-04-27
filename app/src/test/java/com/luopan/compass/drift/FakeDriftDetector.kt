package com.luopan.compass.drift

import com.luopan.compass.model.InterferenceState

/**
 * Recording fake implementation of [IDriftDetector] for unit testing [CompassViewModel].
 *
 * Constructor-configured [nextEvent] is returned on the first [onFrame] call.
 * Records [onFrameCallCount] and [resetCallCount] for assertion.
 */
class FakeDriftDetector(
    private val nextEvent: DriftEvent? = null
) : IDriftDetector {

    var onFrameCallCount: Int = 0
        private set

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
