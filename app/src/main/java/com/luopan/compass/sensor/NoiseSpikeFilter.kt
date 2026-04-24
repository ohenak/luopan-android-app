package com.luopan.compass.sensor

import kotlin.math.abs
import kotlin.math.sqrt

class NoiseSpikeFilter(
    val windowSize: Int = 5,
    val spikeThreshold: Float = 50f
) {
    private val window = ArrayDeque<FloatArray>(windowSize)

    fun filter(x: Float, y: Float, z: Float): FloatArray? {
        val current = floatArrayOf(x, y, z)
        if (window.isEmpty()) {
            window.addLast(current)
            return current
        }
        val mean = window.fold(FloatArray(3)) { acc, s ->
            acc[0] += s[0]; acc[1] += s[1]; acc[2] += s[2]; acc
        }.map { it / window.size }

        val deviation = sqrt(
            (x - mean[0]) * (x - mean[0]) +
            (y - mean[1]) * (y - mean[1]) +
            (z - mean[2]) * (z - mean[2])
        )

        if (deviation > spikeThreshold) return null

        if (window.size >= windowSize) window.removeFirst()
        window.addLast(current)
        return current
    }

    fun reset() { window.clear() }
}
