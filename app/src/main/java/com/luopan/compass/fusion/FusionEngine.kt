package com.luopan.compass.fusion

import com.luopan.compass.sensor.SensorFrame
import kotlin.math.atan2
import kotlin.math.sqrt

class FusionEngine(private val filter: MadgwickFilter = MadgwickFilter()) {

    private var lastTimestampNs: Long = -1L

    fun reset() {
        filter.reset()
        lastTimestampNs = -1L
    }

    fun process(frame: SensorFrame): FusionResult {
        val dt = if (lastTimestampNs < 0) 0.02f
                 else ((frame.timestamp_ns - lastTimestampNs) / 1_000_000_000.0).toFloat()
                      .coerceIn(0.001f, 0.1f)
        lastTimestampNs = frame.timestamp_ns

        val corrMagX = frame.mag_x - frame.mag_bias_x
        val corrMagY = frame.mag_y - frame.mag_bias_y
        val corrMagZ = frame.mag_z - frame.mag_bias_z

        if (frame.gyro_x != null && frame.gyro_y != null && frame.gyro_z != null) {
            filter.update(
                frame.accel_x, frame.accel_y, frame.accel_z,
                frame.gyro_x, frame.gyro_y, frame.gyro_z,
                corrMagX, corrMagY, corrMagZ, dt
            )
        } else {
            filter.updateNoGyro(
                frame.accel_x, frame.accel_y, frame.accel_z,
                corrMagX, corrMagY, corrMagZ, dt
            )
        }

        val heading = quaternionToHeadingDeg(filter.getQuaternion())
        val (pitch, roll, tilt) = quaternionToEuler(filter.getQuaternion())

        return FusionResult(
            heading_deg = heading,
            pitch_deg = pitch,
            roll_deg = roll,
            tilt_deg = tilt,
            timestamp_ns = frame.timestamp_ns
        )
    }

    internal fun quaternionToHeadingDeg(q: FloatArray): Double {
        val q0 = q[0]; val q1 = q[1]; val q2 = q[2]; val q3 = q[3]
        // Rotation matrix elements R[1,0] and R[0,0] of body-to-world
        val R10 = 2f * (q1 * q2 + q0 * q3)
        val R00 = 1f - 2f * (q2 * q2 + q3 * q3)
        val heading = Math.toDegrees(atan2(R10.toDouble(), R00.toDouble()))
        return ((heading % 360.0) + 360.0) % 360.0
    }

    private fun quaternionToEuler(q: FloatArray): Triple<Double, Double, Double> {
        val q0 = q[0]; val q1 = q[1]; val q2 = q[2]; val q3 = q[3]
        val sinp = 2f * (q0 * q2 - q3 * q1)
        val pitch = if (Math.abs(sinp) >= 1.0) Math.copySign(90.0, sinp.toDouble())
                    else Math.toDegrees(Math.asin(sinp.toDouble()))
        val sinr = 2f * (q0 * q1 + q2 * q3)
        val cosr = 1f - 2f * (q1 * q1 + q2 * q2)
        val roll = Math.toDegrees(atan2(sinr.toDouble(), cosr.toDouble()))
        val tilt = sqrt(pitch * pitch + roll * roll)
        return Triple(pitch, roll, tilt)
    }
}
