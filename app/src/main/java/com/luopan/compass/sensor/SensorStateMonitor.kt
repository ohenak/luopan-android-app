package com.luopan.compass.sensor

import com.luopan.compass.model.SensorState

class SensorStateMonitor(
    private val stuckThresholdNs: Long = 2_000_000_000L,
    private val stabilizingThresholdNs: Long = 1_000_000_000L
) {
    private var lastTimestampNs: Long = -1L
    private var startupNs: Long = -1L
    private var state: SensorState = SensorState.NORMAL

    fun update(nowNs: Long): SensorState {
        if (startupNs < 0L) {
            startupNs = nowNs
            lastTimestampNs = nowNs
            state = SensorState.STABILIZING
            return state
        }

        if (nowNs - startupNs < stabilizingThresholdNs) {
            state = SensorState.STABILIZING
            lastTimestampNs = nowNs
            return state
        }

        val gap = nowNs - lastTimestampNs
        state = if (gap > stuckThresholdNs) SensorState.STUCK else SensorState.NORMAL
        lastTimestampNs = nowNs
        return state
    }

    fun reset() {
        lastTimestampNs = -1L
        startupNs = -1L
        state = SensorState.NORMAL
    }

    fun getState(): SensorState = state
}
