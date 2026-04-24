package com.luopan.compass.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SensorFrameTest {

    private fun makeFrame(
        gyro_x: Float? = 0.1f,
        gyro_y: Float? = 0.2f,
        gyro_z: Float? = 0.3f
    ) = SensorFrame(
        timestamp_ns = 1_000_000L,
        mag_x = 10.0f,
        mag_y = 11.0f,
        mag_z = 12.0f,
        mag_bias_x = 0.5f,
        mag_bias_y = 0.6f,
        mag_bias_z = 0.7f,
        accel_x = 0.0f,
        accel_y = 0.0f,
        accel_z = 9.81f,
        gyro_x = gyro_x,
        gyro_y = gyro_y,
        gyro_z = gyro_z
    )

    @Test
    fun `copy with all non-null fields preserves structural equality`() {
        val original = makeFrame()
        val copy = original.copy()
        assertEquals(original, copy)
    }

    @Test
    fun `copy changes only the specified field`() {
        val original = makeFrame()
        val modified = original.copy(mag_x = 99.0f)
        assertEquals(99.0f, modified.mag_x)
        assertEquals(original.mag_y, modified.mag_y)
        assertEquals(original.timestamp_ns, modified.timestamp_ns)
    }

    @Test
    fun `gyro fields are null when instantiated with null gyro`() {
        val frame = makeFrame(gyro_x = null, gyro_y = null, gyro_z = null)
        assertNull(frame.gyro_x)
        assertNull(frame.gyro_y)
        assertNull(frame.gyro_z)
    }
}
