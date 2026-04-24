package com.luopan.compass.calibration

import org.junit.Assert.assertEquals
import org.junit.Test

class CalibrationRecordTest {

    private fun makeRecord(id: Int = 1) = CalibrationRecord(
        id = id,
        device_model = "Pixel 7",
        sensor_fingerprint = "fp-abc123",
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
        residual_rms = 0.45f,
        quality = "GOOD",
        saved_at_utc = 1_700_000_000L,
        wmm_expected_field_ut = 50.5f,
        wmm_source = "GeomagneticField",
        coverage_x = 0.8f,
        coverage_y = 0.75f,
        coverage_z = 0.7f
    )

    @Test
    fun `instantiate with id=1 and verify all 23 fields`() {
        val record = makeRecord(id = 1)

        assertEquals(1, record.id)
        assertEquals("Pixel 7", record.device_model)
        assertEquals("fp-abc123", record.sensor_fingerprint)
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
        assertEquals(0.45f, record.residual_rms)
        assertEquals("GOOD", record.quality)
        assertEquals(1_700_000_000L, record.saved_at_utc)
        assertEquals(50.5f, record.wmm_expected_field_ut)
        assertEquals("GeomagneticField", record.wmm_source)
        assertEquals(0.8f, record.coverage_x)
        assertEquals(0.75f, record.coverage_y)
        assertEquals(0.7f, record.coverage_z)
        assertEquals(1, record.calibration_schema_version)
    }

    @Test
    fun `copy with id=2 works correctly`() {
        val original = makeRecord(id = 1)
        val rollback = original.copy(id = 2)
        assertEquals(2, rollback.id)
        assertEquals(original.device_model, rollback.device_model)
        assertEquals(original.sensor_fingerprint, rollback.sensor_fingerprint)
        assertEquals(original.residual_rms, rollback.residual_rms)
        assertEquals(original.quality, rollback.quality)
    }
}
