package com.luopan.compass.sensor

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SensorRateMonitorTest {

    private lateinit var monitor: SensorRateMonitor

    @Before fun setUp() { monitor = SensorRateMonitor(100) }

    @Test fun `rate is 0 with fewer than 2 samples`() {
        monitor.onSample(0L)
        assertEquals(0.0, monitor.getActualRateHz(), 1e-9)
    }

    @Test fun `rate 50Hz for 20ms intervals`() {
        repeat(100) { i -> monitor.onSample(i * 20_000_000L) }
        assertEquals(50.0, monitor.getActualRateHz(), 0.5)
    }

    @Test fun `rate 100Hz for 10ms intervals`() {
        repeat(100) { i -> monitor.onSample(i * 10_000_000L) }
        assertEquals(100.0, monitor.getActualRateHz(), 1.0)
    }

    @Test fun `reset clears samples`() {
        repeat(50) { monitor.onSample(it * 20_000_000L) }
        monitor.reset()
        assertEquals(0.0, monitor.getActualRateHz(), 1e-9)
    }

    @Test fun `window respects size limit`() {
        repeat(200) { i -> monitor.onSample(i * 20_000_000L) }
        assertEquals(50.0, monitor.getActualRateHz(), 0.5)
    }
}
