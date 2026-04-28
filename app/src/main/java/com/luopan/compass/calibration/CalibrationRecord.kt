package com.luopan.compass.calibration

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calibration_records")
data class CalibrationRecord(
    @PrimaryKey val id: Int,  // 1 = current, 2 = rollback
    val recorded_at: Long,
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
    val quality: String,  // "GOOD" | "FAIR" | "POOR"

    // Phase 4 addition: sphere radius from fitEllipsoid() in µT; 0.0 if not computed
    @ColumnInfo(name = "expected_field_ut", defaultValue = "0.0")
    val expected_field_ut: Float = 0.0f
)
