package com.luopan.compass.fusion

data class FusionResult(
    val heading_deg: Double,
    val pitch_deg: Double,
    val roll_deg: Double,
    val tilt_deg: Double,
    val timestamp_ns: Long
)
