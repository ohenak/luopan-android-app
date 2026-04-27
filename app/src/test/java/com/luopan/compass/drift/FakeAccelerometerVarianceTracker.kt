package com.luopan.compass.drift

import com.luopan.compass.util.FakeClock

/**
 * Fake AccelerometerVarianceTracker that returns a configured variance value.
 *
 * Uses a real [FakeClock] internally but always returns [variance] from [update].
 */
class FakeAccelerometerVarianceTracker(
    private val variance: Float = 0.001f
) : AccelerometerVarianceTracker(FakeClock(0L)) {

    override fun update(ax: Float, ay: Float, az: Float): Float = variance
}
