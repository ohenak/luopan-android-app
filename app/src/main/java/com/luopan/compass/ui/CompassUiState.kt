package com.luopan.compass.ui

import com.luopan.compass.model.CalDotColor
import com.luopan.compass.model.InterferenceState
import com.luopan.compass.model.NorthType
import com.luopan.compass.model.OverallConfidence
import com.luopan.compass.model.SensorState
import com.luopan.compass.sensor.InterferenceMetrics

data class CompassUiState(
    val heading_deg: Double,
    val heading_formatted: String,
    val north_label: String,
    /** Active north reference type: [NorthType.MAGNETIC] or [NorthType.TRUE]. */
    val north_type: NorthType,
    /**
     * Current magnetic declination in degrees (positive east, negative west).
     * Non-zero only when [north_type] == [NorthType.TRUE] and a location is available.
     * Propagated from [MagneticFieldModel.getDeclination] via [NorthTypeEngine].
     * Used by the declination info panel (TSPEC §3.7, AT-B).
     */
    val declination_deg: Float,
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
    /** True when the Android GeomagneticField fallback model is active (WMM2025 expired/unavailable). */
    val fallback_mag_advisory: Boolean,
    /** True when True North is active and using a cached GPS location rather than a fresh fix. */
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
            north_type = NorthType.MAGNETIC,
            declination_deg = 0.0f,
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
