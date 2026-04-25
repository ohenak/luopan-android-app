package com.luopan.compass.sensor

data class SensorFrame(
    val timestamp_ns: Long,
    val mag_x: Float,
    val mag_y: Float,
    val mag_z: Float,
    val mag_bias_x: Float,
    val mag_bias_y: Float,
    val mag_bias_z: Float,
    val accel_x: Float,
    val accel_y: Float,
    val accel_z: Float,
    val gyro_x: Float?,
    val gyro_y: Float?,
    val gyro_z: Float?
)
