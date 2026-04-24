package com.luopan.compass.ui

import com.luopan.compass.model.CalDotColor
import com.luopan.compass.model.InterferenceState
import com.luopan.compass.model.OverallConfidence
import com.luopan.compass.model.SensorState
import com.luopan.compass.sensor.InterferenceMetrics

data class CompassUiState(
    val heading_deg: Double,
    val heading_formatted: String,
    val north_label: String,
    val confidence: OverallConfidence,
    val interference_state: InterferenceState,
    val interference_metrics: InterferenceMetrics?,
    val tilt_deg: Double,
    val tilt_text: String?,
    val calibration_age_days: Long,
    val calibration_age_label: String,
    val cal_dot_color: CalDotColor,
    val power_saving_advisory: Boolean,
    val no_gyroscope_advisory: Boolean,
    val fallback_mag_advisory: Boolean,
    val location_fallback_advisory: Boolean,
    val sensor_state: SensorState,
    val is_stabilizing: Boolean,
    val last_valid_heading_deg: Double?,
    val show_calibration_cta: Boolean
) {
    companion object {
        val INITIAL = CompassUiState(
            heading_deg = 0.0,
            heading_formatted = "---",
            north_label = "Magnetic N",
            confidence = OverallConfidence.POOR,
            interference_state = InterferenceState.CLEAR,
            interference_metrics = null,
            tilt_deg = 0.0,
            tilt_text = null,
            calibration_age_days = -1L,
            calibration_age_label = "Uncalibrated",
            cal_dot_color = CalDotColor.RED,
            power_saving_advisory = false,
            no_gyroscope_advisory = false,
            fallback_mag_advisory = false,
            location_fallback_advisory = false,
            sensor_state = SensorState.NORMAL,
            is_stabilizing = false,
            last_valid_heading_deg = null,
            show_calibration_cta = true
        )
    }
}
