package com.luopan.compass.bearing

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bearing_records")
data class BearingRecord(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,                          // UUID v4, e.g. "550e8400-e29b-41d4-a716-446655440000"

    @ColumnInfo(name = "name")
    val name: String,                        // TEXT NOT NULL; length 1–100 trimmed

    @ColumnInfo(name = "bearing_deg")
    val bearing_deg: Float,                  // REAL NOT NULL; range [0.0, 360.0)

    @ColumnInfo(name = "north_type")
    val north_type: String,                  // TEXT NOT NULL; "TRUE" or "MAGNETIC"

    @ColumnInfo(name = "confidence")
    val confidence: String,                  // TEXT NOT NULL; "HIGH", "MODERATE", or "POOR"

    @ColumnInfo(name = "captured_at")
    val captured_at: Long,                   // INTEGER NOT NULL; UTC epoch ms

    @ColumnInfo(name = "calibration_version")
    val calibration_version: String,         // TEXT NOT NULL; MagneticFieldModel.getModelId()

    @ColumnInfo(name = "field_deviation_pct")
    val field_deviation_pct: Float,          // REAL NOT NULL; >= 0.0

    @ColumnInfo(name = "inclination_deviation_deg")
    val inclination_deviation_deg: Float,    // REAL NOT NULL; >= 0.0

    @ColumnInfo(name = "interference_flag")
    val interference_flag: Boolean,          // INTEGER NOT NULL; 0 or 1

    @ColumnInfo(name = "lat")
    val lat: Double?,                        // REAL NULL

    @ColumnInfo(name = "lon")
    val lon: Double?,                        // REAL NULL

    @ColumnInfo(name = "alt_m")
    val alt_m: Double?,                      // REAL NULL

    @ColumnInfo(name = "notes")
    val notes: String?,                      // TEXT NULL

    @ColumnInfo(name = "display_mode")
    val display_mode: String?                // TEXT NULL; nullable for records written before this field existed; Phase 2 always writes "MODERN"
)
