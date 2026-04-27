package com.luopan.compass.drift

import com.luopan.compass.util.Clock
import kotlin.math.sqrt

/**
 * Maintains a rolling 5-second window over the combined 3-axis accelerometer magnitude
 * and returns the population variance of that scalar series.
 *
 * The window is time-based (5,000 ms), not sample-count-based (250 samples),
 * to remain correct regardless of the actual sensor delivery rate.
 *
 * Thread-safety: not thread-safe; must be called from a single thread (Dispatchers.Default
 * in the CompassViewModel sensor loop).
 *
 * Phase 4 — TSPEC §5.2
 */
open class AccelerometerVarianceTracker(
    private val clock: Clock,
    private val windowMs: Long = 5_000L
) {
    private data class Sample(val timestampMs: Long, val magnitude: Float)

    private val samples = ArrayDeque<Sample>()

    /**
     * Records a new accelerometer frame and returns the current population variance of
     * the combined magnitude over the rolling window.
     *
     * @param ax Accelerometer x in m/s²
     * @param ay Accelerometer y in m/s²
     * @param az Accelerometer z in m/s²
     * @return Population variance of the magnitude series in the rolling window, in (m/s²)²;
     *         0f if fewer than 2 samples are in the window.
     */
    open fun update(ax: Float, ay: Float, az: Float): Float {
        val magnitude = sqrt((ax * ax + ay * ay + az * az).toDouble()).toFloat()
        val nowMs = clock.nowMs()

        samples.addLast(Sample(nowMs, magnitude))

        // Evict samples older than windowMs (strict less-than: sample at exact cutoff is kept)
        val cutoffMs = nowMs - windowMs
        while (samples.isNotEmpty() && samples.first().timestampMs < cutoffMs) {
            samples.removeFirst()
        }

        return computeVariance()
    }

    private fun computeVariance(): Float {
        val n = samples.size
        if (n < 2) return 0f
        val mean = samples.sumOf { it.magnitude.toDouble() } / n
        val variance = samples.sumOf { (it.magnitude - mean) * (it.magnitude - mean) } / n
        return variance.toFloat()
    }

    /** Clears all samples. */
    open fun reset() {
        samples.clear()
    }
}
