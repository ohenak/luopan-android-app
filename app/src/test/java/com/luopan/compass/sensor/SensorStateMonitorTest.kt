package com.luopan.compass.sensor

import com.luopan.compass.model.SensorState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SensorStateMonitorTest {

    private lateinit var monitor: SensorStateMonitor

    @Before fun setUp() {
        monitor = SensorStateMonitor(stuckThresholdNs = 2_000_000_000L, stabilizingThresholdNs = 1_000_000_000L)
    }

    @Test fun `initial update returns STABILIZING`() {
        assertEquals(SensorState.STABILIZING, monitor.update(0L))
    }

    @Test fun `within stabilizing window returns STABILIZING`() {
        monitor.update(0L)
        assertEquals(SensorState.STABILIZING, monitor.update(500_000_000L))
    }

    @Test fun `after stabilizing window returns NORMAL`() {
        monitor.update(0L)
        assertEquals(SensorState.NORMAL, monitor.update(1_000_000_001L))
    }

    @Test fun `stuck after 2s gap`() {
        monitor.update(0L)
        monitor.update(1_500_000_000L) // past stabilizing
        assertEquals(SensorState.STUCK, monitor.update(4_000_000_000L)) // 2.5s gap
    }

    @Test fun `normal when gap within threshold`() {
        monitor.update(0L)
        monitor.update(1_500_000_000L)
        assertEquals(SensorState.NORMAL, monitor.update(2_000_000_000L)) // 0.5s gap
    }

    @Test fun `reset allows fresh start`() {
        monitor.update(0L)
        monitor.update(5_000_000_000L)
        monitor.reset()
        assertEquals(SensorState.STABILIZING, monitor.update(0L))
    }
}
