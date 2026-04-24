package com.luopan.compass.sensor

data class InterferenceMetrics(
    val fieldMagnitude_uT: Float,
    val expectedField_uT: Float,
    val fieldDeviation: Float,       // fraction: 0.25 = 25%
    val inclination_deg: Float,
    val expectedInclination_deg: Float,
    val inclinationDeviation_deg: Float
)
