package com.luopan.compass.calibration

import org.junit.Assert.assertEquals
import org.junit.Test

class CalibrationRecordTest {

    private fun makeRecord(id: Int = 1) = CalibrationRecord(
        id = id,
        recorded_at = 1_700_000_000L,
        hard_iron_x = 1.1f,
        hard_iron_y = 2.2f,
        hard_iron_z = 3.3f,
        soft_iron_00 = 1.0f,
        soft_iron_01 = 0.0f,
        soft_iron_02 = 0.0f,
        soft_iron_10 = 0.0f,
        soft_iron_11 = 1.0f,
        soft_iron_12 = 0.0f,
        soft_iron_20 = 0.0f,
        soft_iron_21 = 0.0f,
        soft_iron_22 = 1.0f,
        quality = "GOOD"
    )

    @Test
    fun `instantiate with id=1 and verify all fields`() {
        val record = makeRecord(id = 1)

        assertEquals(1, record.id)
        assertEquals(1_700_000_000L, record.recorded_at)
        assertEquals(1.1f, record.hard_iron_x)
        assertEquals(2.2f, record.hard_iron_y)
        assertEquals(3.3f, record.hard_iron_z)
        assertEquals(1.0f, record.soft_iron_00)
        assertEquals(0.0f, record.soft_iron_01)
        assertEquals(0.0f, record.soft_iron_02)
        assertEquals(0.0f, record.soft_iron_10)
        assertEquals(1.0f, record.soft_iron_11)
        assertEquals(0.0f, record.soft_iron_12)
        assertEquals(0.0f, record.soft_iron_20)
        assertEquals(0.0f, record.soft_iron_21)
        assertEquals(1.0f, record.soft_iron_22)
        assertEquals("GOOD", record.quality)
    }

    @Test
    fun `copy with id=2 works correctly`() {
        val original = makeRecord(id = 1)
        val rollback = original.copy(id = 2)
        assertEquals(2, rollback.id)
        assertEquals(original.recorded_at, rollback.recorded_at)
        assertEquals(original.quality, rollback.quality)
    }
}
