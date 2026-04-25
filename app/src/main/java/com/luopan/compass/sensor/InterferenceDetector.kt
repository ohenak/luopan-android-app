package com.luopan.compass.sensor

import com.luopan.compass.model.InterferenceState

class InterferenceDetector {

    companion object {
        const val CLEARANCE_DURATION_NS = 3_000_000_000L
        private const val WARNING_FIELD_THRESHOLD = 0.25f
        private const val MODERATE_FIELD_THRESHOLD = 0.15f
        private const val WARNING_INCL_THRESHOLD = 8f
        private const val MODERATE_INCL_THRESHOLD = 3f
    }

    private var state: InterferenceState = InterferenceState.CLEAR
    private var clearanceStartNs: Long = -1L

    fun getState(): InterferenceState = state

    fun updateState(metrics: InterferenceMetrics, nowNs: Long) {
        val fieldWarning = metrics.fieldDeviation >= WARNING_FIELD_THRESHOLD
        val fieldModerate = metrics.fieldDeviation >= MODERATE_FIELD_THRESHOLD
        val inclWarning = metrics.inclinationDeviation_deg >= WARNING_INCL_THRESHOLD
        val inclModerate = metrics.inclinationDeviation_deg >= MODERATE_INCL_THRESHOLD

        val isBelow = metrics.fieldDeviation < MODERATE_FIELD_THRESHOLD && metrics.inclinationDeviation_deg < MODERATE_INCL_THRESHOLD

        when {
            fieldWarning || inclWarning -> {
                state = InterferenceState.WARNING
                clearanceStartNs = -1L
            }
            fieldModerate || inclModerate -> {
                if (state != InterferenceState.WARNING) state = InterferenceState.MODERATE
                clearanceStartNs = -1L
            }
            isBelow -> {
                if (clearanceStartNs < 0L) clearanceStartNs = nowNs
                if (nowNs - clearanceStartNs >= CLEARANCE_DURATION_NS) {
                    state = InterferenceState.CLEAR
                }
                // else remain in current state (hysteresis)
            }
        }
    }

    fun reset() {
        state = InterferenceState.CLEAR
        clearanceStartNs = -1L
    }
}
