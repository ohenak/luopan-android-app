package com.luopan.compass.calibration

import com.luopan.compass.model.CalibrationQuality
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class CalibrationEngineTest {

    private lateinit var engine: CalibrationEngine

    @Before fun setUp() { engine = CalibrationEngine() }

    private fun addSphereSamples(r: Float = 30f, n: Int = 200) {
        repeat(n) { i ->
            val theta = PI * i / n
            val phi = 2 * PI * i / n
            engine.addSample(
                (r * sin(theta) * cos(phi)).toFloat(),
                (r * sin(theta) * sin(phi)).toFloat(),
                (r * cos(theta)).toFloat()
            )
        }
    }

    @Test fun `returns null with fewer than 9 samples`() {
        repeat(8) { engine.addSample(it.toFloat(), 0f, 0f) }
        assertNull(engine.computeCalibration())
    }

    @Test fun `returns result with sufficient samples`() {
        addSphereSamples()
        assertNotNull(engine.computeCalibration())
    }

    @Test fun `classifyQuality GOOD when residual le 1 and coverage ge 0_6`() {
        assertEquals(CalibrationQuality.GOOD, engine.classifyQuality(0.5f, 0.7f))
        assertEquals(CalibrationQuality.GOOD, engine.classifyQuality(1.0f, 0.6f))
    }

    @Test fun `classifyQuality FAIR when residual le 2 and coverage ge 0_3`() {
        assertEquals(CalibrationQuality.FAIR, engine.classifyQuality(1.5f, 0.5f))
        assertEquals(CalibrationQuality.FAIR, engine.classifyQuality(2.0f, 0.3f))
    }

    @Test fun `classifyQuality POOR when residual gt 2`() {
        assertEquals(CalibrationQuality.POOR, engine.classifyQuality(2.1f, 0.8f))
    }

    @Test fun `classifyQuality POOR when coverage lt 0_3`() {
        assertEquals(CalibrationQuality.POOR, engine.classifyQuality(0.5f, 0.29f))
    }

    @Test fun `residual boundary 1_0 is GOOD`() {
        assertEquals(CalibrationQuality.GOOD, engine.classifyQuality(1.0f, 0.9f))
    }

    @Test fun `residual boundary 2_0 is FAIR`() {
        assertEquals(CalibrationQuality.FAIR, engine.classifyQuality(2.0f, 0.9f))
    }

    @Test fun `applyCorrectedMag subtracts hard iron then applies soft iron`() {
        val raw = floatArrayOf(35f, 5f, -25f)
        val hardIron = floatArrayOf(5f, 5f, 5f)
        val softIron = Array(3) { i -> FloatArray(3) { j -> if (i == j) 1f else 0f } }
        val result = engine.applyCorrectedMag(raw, hardIron, softIron)
        assertEquals(30f, result[0], 0.001f)
        assertEquals(0f, result[1], 0.001f)
        assertEquals(-30f, result[2], 0.001f)
    }

    @Test fun `clearSamples resets count`() {
        addSphereSamples(n = 20)
        engine.clearSamples()
        assertEquals(0, engine.getSampleCount())
    }
}
