package com.luopan.compass.fusion

import kotlin.math.sqrt

class MadgwickFilter(var beta: Float = 0.05f) {

    var q0 = 1f; var q1 = 0f; var q2 = 0f; var q3 = 0f

    fun reset() { q0 = 1f; q1 = 0f; q2 = 0f; q3 = 0f }

    fun update(
        ax: Float, ay: Float, az: Float,
        gx: Float, gy: Float, gz: Float,
        mx: Float, my: Float, mz: Float,
        dt: Float
    ) {
        var q0 = this.q0; var q1 = this.q1; var q2 = this.q2; var q3 = this.q3

        // Normalise accelerometer
        var recipNorm = invSqrt(ax * ax + ay * ay + az * az)
        val ax2 = ax * recipNorm; val ay2 = ay * recipNorm; val az2 = az * recipNorm

        // Normalise magnetometer; skip update if degenerate
        val magSq = mx * mx + my * my + mz * mz
        if (magSq == 0f) return
        recipNorm = invSqrt(magSq)
        val mx2 = mx * recipNorm; val my2 = my * recipNorm; val mz2 = mz * recipNorm

        // Reference direction of Earth's magnetic field
        val hx = 2f * mx2 * (0.5f - q2 * q2 - q3 * q3) + 2f * my2 * (q1 * q2 - q0 * q3) + 2f * mz2 * (q1 * q3 + q0 * q2)
        val hy = 2f * mx2 * (q1 * q2 + q0 * q3) + 2f * my2 * (0.5f - q1 * q1 - q3 * q3) + 2f * mz2 * (q2 * q3 - q0 * q1)
        val bx = sqrt((hx * hx + hy * hy).toDouble()).toFloat()
        val bz = 2f * mx2 * (q1 * q3 - q0 * q2) + 2f * my2 * (q2 * q3 + q0 * q1) + 2f * mz2 * (0.5f - q1 * q1 - q2 * q2)

        // Gradient descent algorithm corrective step
        val s0 = -2f * q2 * (2f * q1 * q3 - 2f * q0 * q2 - ax2) +
                  2f * q1 * (2f * q0 * q1 + 2f * q2 * q3 - ay2) +
                 -bz * q2 * (bx * (0.5f - q2 * q2 - q3 * q3) + bz * (q1 * q3 - q0 * q2) - mx2) +
                 (-bx * q3 + bz * q1) * (bx * (q1 * q2 - q0 * q3) + bz * (q0 * q1 + q2 * q3) - my2) +
                  bx * q2 * (bx * (q0 * q2 + q1 * q3) + bz * (0.5f - q1 * q1 - q2 * q2) - mz2)

        val s1 =  2f * q3 * (2f * q1 * q3 - 2f * q0 * q2 - ax2) +
                  2f * q0 * (2f * q0 * q1 + 2f * q2 * q3 - ay2) +
                 -4f * q1 * (1f - 2f * q1 * q1 - 2f * q2 * q2 - az2) +  // Note: simplified from full derivation
                  bz * q3 * (bx * (0.5f - q2 * q2 - q3 * q3) + bz * (q1 * q3 - q0 * q2) - mx2) +
                 (bx * q2 + bz * q0) * (bx * (q1 * q2 - q0 * q3) + bz * (q0 * q1 + q2 * q3) - my2) +
                 (bx * q3 - 2f * bz * q1) * (bx * (q0 * q2 + q1 * q3) + bz * (0.5f - q1 * q1 - q2 * q2) - mz2)

        val s2 = -2f * q0 * (2f * q1 * q3 - 2f * q0 * q2 - ax2) +
                  2f * q3 * (2f * q0 * q1 + 2f * q2 * q3 - ay2) +
                 -4f * q2 * (1f - 2f * q1 * q1 - 2f * q2 * q2 - az2) +
                 (-2f * bx * q2 - bz * q0) * (bx * (0.5f - q2 * q2 - q3 * q3) + bz * (q1 * q3 - q0 * q2) - mx2) +
                 (bx * q1 + bz * q3) * (bx * (q1 * q2 - q0 * q3) + bz * (q0 * q1 + q2 * q3) - my2) +
                 (bx * q0 - 2f * bz * q2) * (bx * (q0 * q2 + q1 * q3) + bz * (0.5f - q1 * q1 - q2 * q2) - mz2)  // corrected sign

        val s3 =  2f * q1 * (2f * q1 * q3 - 2f * q0 * q2 - ax2) +
                 -2f * q2 * (2f * q0 * q1 + 2f * q2 * q3 - ay2) +
                 (-2f * bx * q3 + bz * q1) * (bx * (0.5f - q2 * q2 - q3 * q3) + bz * (q1 * q3 - q0 * q2) - mx2) +
                 (-bx * q0 + bz * q2) * (bx * (q1 * q2 - q0 * q3) + bz * (q0 * q1 + q2 * q3) - my2) +
                  bx * q1 * (bx * (q0 * q2 + q1 * q3) + bz * (0.5f - q1 * q1 - q2 * q2) - mz2)

        // Normalise step magnitude; guard against zero gradient (no correction applied)
        val stepSq = s0 * s0 + s1 * s1 + s2 * s2 + s3 * s3
        recipNorm = if (stepSq > 0f) invSqrt(stepSq) else 0f

        // Apply feedback step
        val qDot0 = 0.5f * (-q1 * gx - q2 * gy - q3 * gz) - beta * s0 * recipNorm
        val qDot1 = 0.5f * ( q0 * gx + q2 * gz - q3 * gy) - beta * s1 * recipNorm
        val qDot2 = 0.5f * ( q0 * gy - q1 * gz + q3 * gx) - beta * s2 * recipNorm
        val qDot3 = 0.5f * ( q0 * gz + q1 * gy - q2 * gx) - beta * s3 * recipNorm

        q0 += qDot0 * dt; q1 += qDot1 * dt; q2 += qDot2 * dt; q3 += qDot3 * dt

        recipNorm = invSqrt(q0 * q0 + q1 * q1 + q2 * q2 + q3 * q3)
        this.q0 = q0 * recipNorm; this.q1 = q1 * recipNorm
        this.q2 = q2 * recipNorm; this.q3 = q3 * recipNorm
    }

    fun updateNoGyro(
        ax: Float, ay: Float, az: Float,
        mx: Float, my: Float, mz: Float,
        dt: Float
    ) = update(ax, ay, az, 0f, 0f, 0f, mx, my, mz, dt)

    fun getQuaternion(): FloatArray = floatArrayOf(q0, q1, q2, q3)

    private fun invSqrt(x: Float): Float = (1.0 / sqrt(x.toDouble())).toFloat()
}
