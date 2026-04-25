package com.luopan.compass.calibration

import com.luopan.compass.model.CalibrationQuality
import org.junit.Assert.assertEquals
import org.junit.Test

class CalibrationDataClassesTest {

    @Test
    fun `MagSample copy produces structural equality`() {
        val sample = MagSample(x = 1.0f, y = 2.0f, z = 3.0f, timestamp_ns = 12345L)
        assertEquals(sample, sample.copy())
    }

    @Test
    fun `Vector3 copy produces structural equality`() {
        val v = Vector3(x = 0.1f, y = 0.2f, z = 0.3f)
        assertEquals(v, v.copy())
    }

    @Test
    fun `Matrix3x3 copy produces structural equality`() {
        val m = Matrix3x3(
            m00 = 1.0f, m01 = 0.0f, m02 = 0.0f,
            m10 = 0.0f, m11 = 1.0f, m12 = 0.0f,
            m20 = 0.0f, m21 = 0.0f, m22 = 1.0f
        )
        assertEquals(m, m.copy())
    }

    @Test
    fun `CoverageStats copy produces structural equality`() {
        val c = CoverageStats(coverage_x = 0.8f, coverage_y = 0.75f, coverage_z = 0.7f)
        assertEquals(c, c.copy())
    }

    @Test
    fun `CalibrationResult equality uses array content`() {
        val si = Array(3) { i -> FloatArray(3) { j -> if (i == j) 1f else 0f } }
        val a = CalibrationResult(
            hardIron = floatArrayOf(1f, 2f, 3f),
            softIron = si,
            residualMicroTesla = 0.35f,
            coverageScore = 0.9f,
            quality = CalibrationQuality.GOOD
        )
        val b = CalibrationResult(
            hardIron = floatArrayOf(1f, 2f, 3f),
            softIron = Array(3) { i -> FloatArray(3) { j -> if (i == j) 1f else 0f } },
            residualMicroTesla = 0.35f,
            coverageScore = 0.9f,
            quality = CalibrationQuality.GOOD
        )
        assertEquals(a, b)
    }

    @Test
    fun `CalibrationResult copy with changed quality only modifies quality`() {
        val original = CalibrationResult(
            hardIron = floatArrayOf(1f, 2f, 3f),
            softIron = Array(3) { i -> FloatArray(3) { j -> if (i == j) 1f else 0f } },
            residualMicroTesla = 0.35f,
            coverageScore = 0.9f,
            quality = CalibrationQuality.GOOD
        )
        val modified = original.copy(quality = CalibrationQuality.FAIR)
        assertEquals(CalibrationQuality.FAIR, modified.quality)
        assertEquals(original.residualMicroTesla, modified.residualMicroTesla)
        assertEquals(original.coverageScore, modified.coverageScore)
    }
}
