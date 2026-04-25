package com.luopan.compass.ui

import com.luopan.compass.model.CalDotColor
import com.luopan.compass.model.OverallConfidence
import com.luopan.compass.model.SensorState
import org.junit.Assert.*
import org.junit.Test

class CompassViewModelTest {

    @Test fun `INITIAL state has expected defaults`() {
        val state = CompassUiState.INITIAL
        assertEquals("---", state.heading_formatted)
        assertEquals(-1L, state.calibration_age_days)
        assertEquals(OverallConfidence.POOR, state.confidence)
        assertEquals(CalDotColor.RED, state.cal_dot_color)
        assertTrue(state.show_calibration_cta)
        assertFalse(state.is_stabilizing)
        assertNull(state.last_valid_heading_deg)
    }
}
