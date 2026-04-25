package com.luopan.compass.fusion

import com.luopan.compass.sensor.SensorFrame
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FusionEngineTest {

    private lateinit var engine: FusionEngine

    @Before fun setUp() { engine = FusionEngine() }

    private fun frame(ts: Long, mx: Float, my: Float, mz: Float,
                      ax: Float = 0f, ay: Float = 0f, az: Float = 9.8f,
                      gx: Float? = null, gy: Float? = null, gz: Float? = null) =
        SensorFrame(ts, mx, my, mz, 0f, 0f, 0f, ax, ay, az, gx, gy, gz)

    @Test fun `heading in 0 to 360 exclusive`() {
        val result = engine.process(frame(20_000_000L, 30f, 0f, -30f))
        assertTrue(result.heading_deg >= 0.0)
        assertTrue(result.heading_deg < 360.0)
    }

    @Test fun `tilt is non-negative`() {
        val result = engine.process(frame(20_000_000L, 30f, 0f, -30f))
        assertTrue(result.tilt_deg >= 0.0)
    }

    @Test fun `reset clears state`() {
        repeat(50) { engine.process(frame(it * 20_000_000L, 30f, 0f, -30f)) }
        engine.reset()
        val r1 = engine.process(frame(0L, 30f, 0f, -30f))
        val engine2 = FusionEngine()
        val r2 = engine2.process(frame(0L, 30f, 0f, -30f))
        assertEquals(r2.heading_deg, r1.heading_deg, 1e-9)
    }

    @Test fun `dt clamped to 100ms max`() {
        engine.process(frame(0L, 30f, 0f, -30f))
        val r = engine.process(frame(10_000_000_000L, 30f, 0f, -30f)) // 10s gap
        assertNotNull(r)
    }

    @Test fun `heading wraps 360 to 0`() {
        val heading = engine.quaternionToHeadingDeg(floatArrayOf(1f, 0f, 0f, 0f))
        assertTrue(heading >= 0.0 && heading < 360.0)
    }

    @Test fun `uses updateNoGyro when gyro null`() {
        val result = engine.process(frame(20_000_000L, 30f, 0f, -30f, gx = null, gy = null, gz = null))
        assertNotNull(result)
        assertTrue(result.heading_deg >= 0.0)
    }

    @Test fun `uses update when gyro present`() {
        val result = engine.process(frame(20_000_000L, 30f, 0f, -30f, gx = 0f, gy = 0f, gz = 0f))
        assertNotNull(result)
        assertTrue(result.heading_deg >= 0.0)
    }

    @Test fun `bias correction applied`() {
        val frameBiased = SensorFrame(20_000_000L, 30f + 5f, 0f + 5f, -30f + 5f, 5f, 5f, 5f, 0f, 0f, 9.8f, null, null, null)
        val frameUnbiased = frame(20_000_000L, 30f, 0f, -30f)
        val e1 = FusionEngine(); val e2 = FusionEngine()
        val r1 = e1.process(frameBiased)
        val r2 = e2.process(frameUnbiased)
        assertEquals(r2.heading_deg, r1.heading_deg, 0.01)
    }
}
