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
    fun `CalibrationResult copy produces structural equality`() {
        val result = CalibrationResult(
            hard_iron = Vector3(1.0f, 2.0f, 3.0f),
            soft_iron = Matrix3x3(
                1.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f,
                0.0f, 0.0f, 1.0f
            ),
            residual_rms = 0.35f,
            coverage = CoverageStats(0.9f, 0.85f, 0.8f),
            quality = CalibrationQuality.GOOD,
            target_radius = 50.0f
        )
        assertEquals(result, result.copy())
    }

    @Test
    fun `CalibrationResult copy with changed field only modifies that field`() {
        val original = CalibrationResult(
            hard_iron = Vector3(1.0f, 2.0f, 3.0f),
            soft_iron = Matrix3x3(
                1.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f,
                0.0f, 0.0f, 1.0f
            ),
            residual_rms = 0.35f,
            coverage = CoverageStats(0.9f, 0.85f, 0.8f),
            quality = CalibrationQuality.GOOD,
            target_radius = 50.0f
        )
        val modified = original.copy(quality = CalibrationQuality.FAIR)
        assertEquals(CalibrationQuality.FAIR, modified.quality)
        assertEquals(original.hard_iron, modified.hard_iron)
        assertEquals(original.soft_iron, modified.soft_iron)
        assertEquals(original.residual_rms, modified.residual_rms)
        assertEquals(original.coverage, modified.coverage)
        assertEquals(original.target_radius, modified.target_radius)
    }
}
