package com.luopan.compass.ui

import com.luopan.compass.model.CalDotColor
import com.luopan.compass.model.InterferenceState
import com.luopan.compass.model.OverallConfidence
import com.luopan.compass.model.SensorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompassUiStateTest {

    @Test
    fun `INITIAL show_calibration_cta is true`() {
        assertTrue(CompassUiState.INITIAL.show_calibration_cta)
    }

    @Test
    fun `INITIAL heading_formatted is triple dash`() {
        assertEquals("---", CompassUiState.INITIAL.heading_formatted)
    }

    @Test
    fun `INITIAL calibration_age_days is -1`() {
        assertEquals(-1L, CompassUiState.INITIAL.calibration_age_days)
    }

    @Test
    fun `all 20 fields are accessible by name on INITIAL`() {
        val s = CompassUiState.INITIAL

        assertEquals(0.0, s.heading_deg, 0.0)
        assertEquals("---", s.heading_formatted)
        assertEquals("Magnetic N", s.north_label)
        assertEquals(OverallConfidence.POOR, s.confidence)
        assertEquals(InterferenceState.CLEAR, s.interference_state)
        assertNull(s.interference_metrics)
        assertEquals(0.0, s.tilt_deg, 0.0)
        assertNull(s.tilt_text)
        assertEquals(-1L, s.calibration_age_days)
        assertEquals("Uncalibrated", s.calibration_age_label)
        assertEquals(CalDotColor.RED, s.cal_dot_color)
        assertFalse(s.power_saving_advisory)
        assertFalse(s.no_gyroscope_advisory)
        assertFalse(s.fallback_mag_advisory)
        assertFalse(s.location_fallback_advisory)
        assertFalse(s.extreme_latitude_advisory)
        assertEquals(SensorState.NORMAL, s.sensor_state)
        assertFalse(s.is_stabilizing)
        assertNull(s.last_valid_heading_deg)
        assertTrue(s.show_calibration_cta)
    }

    @Test
    fun `copy with one changed field preserves all others`() {
        val original = CompassUiState.INITIAL
        val modified = original.copy(heading_deg = 180.0)

        assertEquals(180.0, modified.heading_deg, 0.0)
        // All other fields must be unchanged
        assertEquals(original.heading_formatted, modified.heading_formatted)
        assertEquals(original.north_label, modified.north_label)
        assertEquals(original.confidence, modified.confidence)
        assertEquals(original.interference_state, modified.interference_state)
        assertEquals(original.interference_metrics, modified.interference_metrics)
        assertEquals(original.tilt_deg, modified.tilt_deg, 0.0)
        assertEquals(original.tilt_text, modified.tilt_text)
        assertEquals(original.calibration_age_days, modified.calibration_age_days)
        assertEquals(original.calibration_age_label, modified.calibration_age_label)
        assertEquals(original.cal_dot_color, modified.cal_dot_color)
        assertEquals(original.power_saving_advisory, modified.power_saving_advisory)
        assertEquals(original.no_gyroscope_advisory, modified.no_gyroscope_advisory)
        assertEquals(original.fallback_mag_advisory, modified.fallback_mag_advisory)
        assertEquals(original.location_fallback_advisory, modified.location_fallback_advisory)
        assertEquals(original.extreme_latitude_advisory, modified.extreme_latitude_advisory)
        assertEquals(original.sensor_state, modified.sensor_state)
        assertEquals(original.is_stabilizing, modified.is_stabilizing)
        assertEquals(original.last_valid_heading_deg, modified.last_valid_heading_deg)
        assertEquals(original.show_calibration_cta, modified.show_calibration_cta)
    }
}
