package com.luopan.compass.fusion

import org.junit.Assert.assertEquals
import org.junit.Test

class FusionResultTest {

    @Test
    fun `copy produces structurally equal instance`() {
        val result = FusionResult(
            heading_deg = 270.5,
            pitch_deg = 5.0,
            roll_deg = -3.0,
            tilt_deg = 6.0,
            timestamp_ns = 123_456_789L
        )
        val copy = result.copy()
        assertEquals(result, copy)
    }

    @Test
    fun `copy with changed heading only modifies that field`() {
        val result = FusionResult(
            heading_deg = 180.0,
            pitch_deg = 1.0,
            roll_deg = 2.0,
            tilt_deg = 3.0,
            timestamp_ns = 999L
        )
        val modified = result.copy(heading_deg = 90.0)
        assertEquals(90.0, modified.heading_deg, 0.0)
        assertEquals(result.pitch_deg, modified.pitch_deg, 0.0)
        assertEquals(result.roll_deg, modified.roll_deg, 0.0)
        assertEquals(result.tilt_deg, modified.tilt_deg, 0.0)
        assertEquals(result.timestamp_ns, modified.timestamp_ns)
    }
}
