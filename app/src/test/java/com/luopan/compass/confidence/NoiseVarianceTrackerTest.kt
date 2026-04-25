package com.luopan.compass.confidence

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NoiseVarianceTrackerTest {

    private lateinit var tracker: NoiseVarianceTracker

    @Before fun setUp() { tracker = NoiseVarianceTracker(50) }

    @Test fun `variance is 0 with fewer than 2 samples`() {
        tracker.addSample(30.0)
        assertEquals(0.0, tracker.getVariance(), 1e-9)
    }

    @Test fun `variance is 0 for constant signal`() {
        repeat(50) { tracker.addSample(30.0) }
        assertEquals(0.0, tracker.getVariance(), 1e-6)
    }

    @Test fun `variance increases with noise`() {
        repeat(50) { i -> tracker.addSample(30.0 + if (i % 2 == 0) 1.0 else -1.0) }
        assertTrue(tracker.getVariance() > 0.0)
    }

    @Test fun `sample count is correct`() {
        repeat(10) { tracker.addSample(it.toDouble()) }
        assertEquals(10, tracker.getSampleCount())
    }

    @Test fun `respects window size`() {
        repeat(100) { tracker.addSample(it.toDouble()) }
        assertEquals(50, tracker.getSampleCount())
    }
}
