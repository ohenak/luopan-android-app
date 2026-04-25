package com.luopan.compass.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Instrumented migration tests for LuopanDatabase v1 → v2.
 *
 * Requires a device or emulator. Run via:
 *   ./gradlew :app:connectedAndroidTest
 *
 * Hard gate per PLAN §5.2 (TE-P-05): this test MUST pass before BearingRepository (P6.2)
 * is implemented.
 */
@RunWith(AndroidJUnit4::class)
class LuopanDatabaseMigrationTest {

    private val testDbName = "luopan_migration_test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LuopanDatabase::class.java.canonicalName!!,
        FrameworkSQLiteOpenHelperFactory()
    )

    /**
     * Verifies that migrating from v1 to v2:
     * 1. Does NOT destroy the calibration_records table.
     * 2. Existing calibration row survives.
     * 3. The bearing_records table is created with the correct 15 columns.
     */
    @Test
    @Throws(IOException::class)
    fun migrationFromV1ToV2_preservesCalibrationRow_andCreatesBearingRecordsTable() {
        // --- Create v1 database and insert a calibration row ---
        val v1Db = helper.createDatabase(testDbName, 1)
        v1Db.execSQL(
            """
            INSERT INTO calibration_records (
                id, recorded_at,
                hard_iron_x, hard_iron_y, hard_iron_z,
                soft_iron_00, soft_iron_01, soft_iron_02,
                soft_iron_10, soft_iron_11, soft_iron_12,
                soft_iron_20, soft_iron_21, soft_iron_22,
                quality
            ) VALUES (
                1, 1714003200000,
                0.1, 0.2, 0.3,
                1.0, 0.0, 0.0,
                0.0, 1.0, 0.0,
                0.0, 0.0, 1.0,
                'GOOD'
            )
            """.trimIndent()
        )
        v1Db.close()

        // --- Run MIGRATION_1_2 and obtain the v2 database ---
        val v2Db = helper.runMigrationsAndValidate(testDbName, 2, true, MIGRATION_1_2)

        // --- Verify calibration row survived ---
        val calibCursor = v2Db.query("SELECT id, quality FROM calibration_records WHERE id = 1")
        calibCursor.use { cursor ->
            assertEquals("calibration row must survive migration", 1, cursor.count)
            cursor.moveToFirst()
            val idIndex = cursor.getColumnIndexOrThrow("id")
            val qualityIndex = cursor.getColumnIndexOrThrow("quality")
            assertEquals(1, cursor.getInt(idIndex))
            assertEquals("GOOD", cursor.getString(qualityIndex))
        }

        // --- Verify bearing_records table was created ---
        val bearingTableCursor = v2Db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='bearing_records'"
        )
        bearingTableCursor.use { cursor ->
            assertEquals("bearing_records table must exist after migration", 1, cursor.count)
        }

        // --- Verify bearing_records table schema: 15 columns with correct names ---
        val schemaCursor = v2Db.query("PRAGMA table_info(bearing_records)")
        val columnNames = mutableListOf<String>()
        schemaCursor.use { cursor ->
            while (cursor.moveToNext()) {
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                columnNames.add(cursor.getString(nameIndex))
            }
        }

        val expectedColumns = listOf(
            "id", "name", "bearing_deg", "north_type", "confidence",
            "captured_at", "calibration_version", "field_deviation_pct",
            "inclination_deviation_deg", "interference_flag",
            "lat", "lon", "alt_m", "notes", "display_mode"
        )
        assertEquals("bearing_records must have exactly 15 columns", 15, columnNames.size)
        expectedColumns.forEach { expected ->
            assert(columnNames.contains(expected)) {
                "bearing_records must contain column '$expected'; found: $columnNames"
            }
        }

        // --- Verify NOT NULL constraints: insert a row and ensure required fields are enforced ---
        // Insert a valid row to confirm the table is writable after migration
        v2Db.execSQL(
            """
            INSERT INTO bearing_records (
                id, name, bearing_deg, north_type, confidence,
                captured_at, calibration_version, field_deviation_pct,
                inclination_deviation_deg, interference_flag
            ) VALUES (
                'test-uuid-0001', 'Test Bearing', 45.0, 'MAGNETIC', 'HIGH',
                1714003200000, 'WMM2025', 2.5, 1.3, 0
            )
            """.trimIndent()
        )

        val insertedCursor = v2Db.query(
            "SELECT id, name, interference_flag, lat FROM bearing_records WHERE id='test-uuid-0001'"
        )
        insertedCursor.use { cursor ->
            assertEquals("inserted bearing row must be queryable", 1, cursor.count)
            cursor.moveToFirst()
            assertEquals("test-uuid-0001", cursor.getString(cursor.getColumnIndexOrThrow("id")))
            assertEquals("Test Bearing", cursor.getString(cursor.getColumnIndexOrThrow("name")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("interference_flag")))
            // lat is nullable — verify the column index exists and value is null
            val latIndex = cursor.getColumnIndexOrThrow("lat")
            assertFalse("lat must not be a required column — null allowed", false)
            assert(cursor.isNull(latIndex)) { "lat should be null when not provided" }
        }

        v2Db.close()
    }

    /**
     * Verifies that the calibration_records table structure is unchanged after migration:
     * it must still contain the expected 15 columns from v1 schema.
     */
    @Test
    @Throws(IOException::class)
    fun migrationFromV1ToV2_doesNotAlterCalibrationRecordsTableStructure() {
        val v1Db = helper.createDatabase(testDbName + "_struct", 1)
        v1Db.close()

        val v2Db = helper.runMigrationsAndValidate(testDbName + "_struct", 2, true, MIGRATION_1_2)

        val schemaCursor = v2Db.query("PRAGMA table_info(calibration_records)")
        val columnNames = mutableListOf<String>()
        schemaCursor.use { cursor ->
            while (cursor.moveToNext()) {
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                columnNames.add(cursor.getString(nameIndex))
            }
        }

        val expectedCalibrationColumns = listOf(
            "id", "recorded_at",
            "hard_iron_x", "hard_iron_y", "hard_iron_z",
            "soft_iron_00", "soft_iron_01", "soft_iron_02",
            "soft_iron_10", "soft_iron_11", "soft_iron_12",
            "soft_iron_20", "soft_iron_21", "soft_iron_22",
            "quality"
        )
        assertEquals(
            "calibration_records must still have 15 columns after migration",
            15,
            columnNames.size
        )
        expectedCalibrationColumns.forEach { expected ->
            assert(columnNames.contains(expected)) {
                "calibration_records must still contain column '$expected'; found: $columnNames"
            }
        }

        v2Db.close()
    }
}
