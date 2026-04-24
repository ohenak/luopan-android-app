package com.luopan.compass.calibration

import com.luopan.compass.model.CalibrationQuality

data class CalibrationResult(
    val hard_iron: Vector3,
    val soft_iron: Matrix3x3,
    val residual_rms: Float,
    val coverage: CoverageStats,
    val quality: CalibrationQuality,
    val target_radius: Float
)
