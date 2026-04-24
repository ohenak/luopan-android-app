package com.luopan.compass.sensor

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NoiseSpikeFilterTest {

    private lateinit var filter: NoiseSpikeFilter

    @Before fun setUp() { filter = NoiseSpikeFilter(windowSize = 5, spikeThreshold = 50f) }

    @Test fun `first sample always accepted`() {
        assertNotNull(filter.filter(30f, 0f, -30f))
    }

    @Test fun `normal sample accepted`() {
        filter.filter(30f, 0f, -30f)
        assertNotNull(filter.filter(30.1f, 0.1f, -29.9f))
    }

    @Test fun `spike rejected`() {
        filter.filter(30f, 0f, -30f)
        assertNull(filter.filter(30f + 100f, 0f, -30f))
    }

    @Test fun `sample at threshold accepted`() {
        filter.filter(30f, 0f, -30f)
        // deviation exactly 50 = not > 50, so accepted
        assertNotNull(filter.filter(30f + 50f, 0f, -30f))
    }

    @Test fun `sample just above threshold rejected`() {
        filter.filter(30f, 0f, -30f)
        assertNull(filter.filter(30f + 50.001f, 0f, -30f))
    }

    @Test fun `rejected spike does not update window`() {
        filter.filter(30f, 0f, -30f)
        filter.filter(30f + 100f, 0f, -30f) // spike, rejected
        val result = filter.filter(30.1f, 0.1f, -29.9f) // should pass
        assertNotNull(result)
    }

    @Test fun `reset clears window`() {
        repeat(5) { filter.filter(it.toFloat(), 0f, 0f) }
        filter.reset()
        assertNotNull(filter.filter(1000f, 0f, 0f)) // first after reset always accepted
    }
}
