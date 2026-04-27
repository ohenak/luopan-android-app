package com.luopan.compass.sensor

import kotlin.math.abs
import kotlin.math.sqrt

class NoiseSpikeFilter(
    val windowSize: Int = 5,
    val spikeThreshold: Float = 50f,
    private val maxConsecutiveRejects: Int = 10
) {
    private val window = ArrayDeque<FloatArray>(windowSize)
    private var consecutiveRejects: Int = 0

    fun filter(x: Float, y: Float, z: Float): FloatArray? {
        val current = floatArrayOf(x, y, z)
        if (window.isEmpty()) {
            window.addLast(current)
            consecutiveRejects = 0
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

        if (deviation > spikeThreshold) {
            consecutiveRejects++
            if (consecutiveRejects >= maxConsecutiveRejects) {
                // Filter got stuck on a legitimate field change — accept and reset window
                window.clear()
                window.addLast(current)
                consecutiveRejects = 0
                return current
            }
            return null
        }

        consecutiveRejects = 0
        if (window.size >= windowSize) window.removeFirst()
        window.addLast(current)
        return current
    }

    fun reset() {
        window.clear()
        consecutiveRejects = 0
    }
}
