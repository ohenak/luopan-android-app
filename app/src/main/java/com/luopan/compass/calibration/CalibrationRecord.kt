package com.luopan.compass.calibration

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calibration_records")
data class CalibrationRecord(
    @PrimaryKey val id: Int,  // 1 = current, 2 = rollback
    val device_model: String,
    val sensor_fingerprint: String,
    val hard_iron_x: Float,
    val hard_iron_y: Float,
    val hard_iron_z: Float,
    val soft_iron_00: Float,
    val soft_iron_01: Float,
    val soft_iron_02: Float,
    val soft_iron_10: Float,
    val soft_iron_11: Float,
    val soft_iron_12: Float,
    val soft_iron_20: Float,
    val soft_iron_21: Float,
    val soft_iron_22: Float,
    val residual_rms: Float,
    val quality: String,  // "GOOD" | "FAIR" | "POOR"
    val saved_at_utc: Long,
    val wmm_expected_field_ut: Float,  // -1.0f = fallback 50 µT
    val wmm_source: String,  // "GeomagneticField" | "fallback_50uT"
    val coverage_x: Float,
    val coverage_y: Float,
    val coverage_z: Float,
    val calibration_schema_version: Int = 1
)
