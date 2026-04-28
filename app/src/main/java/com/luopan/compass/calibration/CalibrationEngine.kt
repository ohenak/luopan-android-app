package com.luopan.compass.calibration

import androidx.annotation.VisibleForTesting
import com.luopan.compass.model.CalibrationQuality
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresBuilder
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer
import org.apache.commons.math3.linear.Array2DRowRealMatrix
import org.apache.commons.math3.linear.ArrayRealVector
import org.apache.commons.math3.linear.MatrixUtils
import kotlin.math.abs
import kotlin.math.sqrt

class CalibrationEngine {

    private val samples = mutableListOf<FloatArray>()

    fun addSample(x: Float, y: Float, z: Float) {
        samples.add(floatArrayOf(x, y, z))
    }

    fun getSampleCount(): Int = samples.size

    fun clearSamples() { samples.clear() }

    /**
     * Fit ellipsoid to samples and return CalibrationResult.
     * Requires at least 9 samples.
     */
    fun computeCalibration(): CalibrationResult? {
        if (samples.size < 9) return null
        return try {
            fitEllipsoid(samples.toList())
        } catch (e: Exception) {
            null
        }
    }

    @VisibleForTesting
    fun classifyQuality(residual: Float, coverage: Float): CalibrationQuality {
        return when {
            residual <= 1.0f && coverage >= 0.6f -> CalibrationQuality.GOOD
            residual <= 2.0f && coverage >= 0.3f -> CalibrationQuality.FAIR
            else -> CalibrationQuality.POOR
        }
    }

    fun getCoverageScore(sampleList: List<FloatArray> = samples): Float {
        if (sampleList.isEmpty()) return 0f
        val xs = sampleList.map { it[0] }
        val ys = sampleList.map { it[1] }
        val zs = sampleList.map { it[2] }
        val xRange = xs.max() - xs.min()
        val yRange = ys.max() - ys.min()
        val zRange = zs.max() - zs.min()
        val maxRange = maxOf(xRange, yRange, zRange)
        if (maxRange == 0f) return 0f
        // per-axis coverage = axis_range / max(all axis ranges); return worst axis
        return minOf(xRange, yRange, zRange) / maxRange
    }

    fun getPerAxisCoverage(sampleList: List<FloatArray> = samples): Triple<Float, Float, Float> {
        if (sampleList.isEmpty()) return Triple(0f, 0f, 0f)
        val xs = sampleList.map { it[0] }
        val ys = sampleList.map { it[1] }
        val zs = sampleList.map { it[2] }
        val xRange = xs.max() - xs.min()
        val yRange = ys.max() - ys.min()
        val zRange = zs.max() - zs.min()
        val maxRange = maxOf(xRange, yRange, zRange)
        if (maxRange == 0f) return Triple(0f, 0f, 0f)
        return Triple(xRange / maxRange, yRange / maxRange, zRange / maxRange)
    }

    fun applyCorrectedMag(raw: FloatArray, hardIron: FloatArray, softIron: Array<FloatArray>): FloatArray {
        val centered = floatArrayOf(raw[0] - hardIron[0], raw[1] - hardIron[1], raw[2] - hardIron[2])
        val result = FloatArray(3)
        for (i in 0..2) {
            result[i] = softIron[i][0] * centered[0] + softIron[i][1] * centered[1] + softIron[i][2] * centered[2]
        }
        return result
    }

    private fun fitEllipsoid(pts: List<FloatArray>): CalibrationResult {
        val n = pts.size
        // Simple sphere fit: find center (hard-iron) by minimizing sum of (dist - r)^2
        // Initial guess: centroid
        var cx = pts.map { it[0] }.average().toFloat()
        var cy = pts.map { it[1] }.average().toFloat()
        var cz = pts.map { it[2] }.average().toFloat()

        // Iterative centroid refinement (simplified LM-like approach for sphere)
        repeat(50) {
            val dists = pts.map { p ->
                val dx = p[0] - cx; val dy = p[1] - cy; val dz = p[2] - cz
                sqrt((dx*dx + dy*dy + dz*dz).toDouble()).toFloat()
            }
            val r = dists.average().toFloat()
            var gradX = 0f; var gradY = 0f; var gradZ = 0f
            pts.forEachIndexed { i, p ->
                val d = dists[i]
                if (d > 0f) {
                    val w = (d - r) / d
                    gradX += w * (p[0] - cx)
                    gradY += w * (p[1] - cy)
                    gradZ += w * (p[2] - cz)
                }
            }
            cx += gradX / n * 0.1f
            cy += gradY / n * 0.1f
            cz += gradZ / n * 0.1f
        }

        val hardIron = floatArrayOf(cx, cy, cz)
        val dists = pts.map { p ->
            val dx = p[0] - cx; val dy = p[1] - cy; val dz = p[2] - cz
            sqrt((dx*dx + dy*dy + dz*dz).toDouble()).toFloat()
        }
        val r = dists.average().toFloat()
        val residual = dists.map { abs(it - r) }.average().toFloat()
        val coverage = getCoverageScore(pts)

        // Identity soft-iron (scale to unit sphere)
        val softIron = Array(3) { i -> FloatArray(3) { j -> if (i == j) (if (r > 0f) 1f / r else 1f) else 0f } }

        return CalibrationResult(
            hardIron = hardIron,
            softIron = softIron,
            residualMicroTesla = residual,
            coverageScore = coverage,
            quality = classifyQuality(residual, coverage),
            sphereRadius_uT = r
        )
    }
}

data class CalibrationResult(
    val hardIron: FloatArray,
    val softIron: Array<FloatArray>,
    val residualMicroTesla: Float,
    val coverageScore: Float,
    val quality: CalibrationQuality,
    val sphereRadius_uT: Float = 0.0f  // Phase 4: sphere radius r from fitEllipsoid(); 0.0 if not computed
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CalibrationResult) return false
        return hardIron.contentEquals(other.hardIron) &&
               softIron.contentDeepEquals(other.softIron) &&
               residualMicroTesla == other.residualMicroTesla &&
               coverageScore == other.coverageScore &&
               quality == other.quality &&
               sphereRadius_uT == other.sphereRadius_uT
    }
    override fun hashCode(): Int {
        var result = hardIron.contentHashCode()
        result = 31 * result + softIron.contentDeepHashCode()
        result = 31 * result + residualMicroTesla.hashCode()
        result = 31 * result + coverageScore.hashCode()
        result = 31 * result + quality.hashCode()
        result = 31 * result + sphereRadius_uT.hashCode()
        return result
    }
}
