package com.luopan.compass.drift

import com.luopan.compass.util.FakeClock

/**
 * Test double for [AccelerometerVarianceTracker].
 *
 * Returns the configured [variance] from every [update] call, ignoring accel inputs.
 * Tracks [updateCallCount] and [resetCallCount] for assertion in tests.
 *
 * Phase 4 — PLAN B-4; used by CompassViewModelDriftTest (Phase D)
 */
class FakeAccelerometerVarianceTracker(
    /** The variance value to return from every update() call. */
    private val variance: Float,
    clock: FakeClock = FakeClock(0L)
) : AccelerometerVarianceTracker(clock) {

    /** Number of times [update] has been called. */
    var updateCallCount: Int = 0
        private set

    /** Number of times [reset] has been called. */
    var resetCallCount: Int = 0
        private set

    override fun update(ax: Float, ay: Float, az: Float): Float {
        updateCallCount++
        return variance
    }

    override fun reset() {
        resetCallCount++
    }
}
