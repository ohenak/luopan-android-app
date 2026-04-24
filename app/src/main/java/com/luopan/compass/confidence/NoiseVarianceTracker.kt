package com.luopan.compass.confidence

import com.luopan.compass.diagnostics.DiagnosticBuffer
import kotlin.math.abs

class NoiseVarianceTracker(private val windowSize: Int = 50) {

    private val buffer = DiagnosticBuffer<Double>(windowSize)

    fun addSample(magnitude: Double) { buffer.add(magnitude) }

    fun getVariance(): Double {
        if (buffer.size < 2) return 0.0
        val p25 = buffer.percentile(0.25)
        val p75 = buffer.percentile(0.75)
        return abs(p75 - p25) / 2.0
    }

    fun getSampleCount(): Int = buffer.size
}
