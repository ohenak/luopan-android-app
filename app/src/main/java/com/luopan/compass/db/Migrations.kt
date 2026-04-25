package com.luopan.compass.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS bearing_records (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                bearing_deg REAL NOT NULL,
                north_type TEXT NOT NULL,
                confidence TEXT NOT NULL,
                captured_at INTEGER NOT NULL,
                calibration_version TEXT NOT NULL,
                field_deviation_pct REAL NOT NULL,
                inclination_deviation_deg REAL NOT NULL,
                interference_flag INTEGER NOT NULL,
                lat REAL,
                lon REAL,
                alt_m REAL,
                notes TEXT,
                display_mode TEXT
            )
            """.trimIndent()
        )
    }
}
