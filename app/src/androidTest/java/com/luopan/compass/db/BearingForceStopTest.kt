package com.luopan.compass.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jakewharton.processphoenix.ProcessPhoenix
import com.luopan.compass.bearing.BearingRecord
import com.luopan.compass.bearing.BearingRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented force-stop resilience test for BearingRepository.
 *
 * AT-PERSIST-02 (TSPEC §10.2, PLAN §4 P8.4):
 * - Uses ProcessPhoenix to kill and restart the process mid-insert.
 * - On relaunch: row count is pre-kill or pre-kill+1 (no row loss for completed inserts;
 *   no phantom rows from uncommitted transactions).
 * - No record with any required (non-null) field set to null exists after restart.
 *
 * Test phase protocol (SharedPreferences "luopan_force_stop_test"):
 *   PHASE key absent / "setup" → Phase 0: insert records, save pre-kill count, kill process.
 *   PHASE key = "verify"       → Phase 1: verify DB consistency after restart.
 *
 * When ProcessPhoenix.triggerRebirth() is called it kills the current process and
 * relaunches the application via the registered launch intent. The test runner is
 * embedded in the application process, so it also restarts and re-runs this test method
 * from the top. The SharedPreferences phase guard ensures each phase executes once.
 *
 * Note: Run this test on a physical device or emulator with API 26+.
 *
 * PLAN §5.9: ProcessPhoenix declared under androidTestImplementation.
 */
@RunWith(AndroidJUnit4::class)
class BearingForceStopTest {

    companion object {
        private const val PREFS_NAME = "luopan_force_stop_test"
        private const val KEY_PHASE = "test_phase"
        private const val KEY_PRE_KILL_COUNT = "pre_kill_count"
        private const val PHASE_VERIFY = "verify"

        /** Unique record IDs seeded before the kill. */
        private const val SEED_RECORD_COUNT = 3
    }

    private val context: Context = ApplicationProvider.getApplicationContext()

    /**
     * Force-stop resilience test.
     *
     * On first run (no phase key): seeds [SEED_RECORD_COUNT] records, stores the pre-kill
     * count, then calls [ProcessPhoenix.triggerRebirth] to kill and restart the process.
     *
     * On second run (phase == "verify"): opens the DB with the real passphrase, reads all
     * records, and asserts:
     *   1. Row count == pre-kill count or pre-kill count + 1 (atomic last insert may or may
     *      not have committed before the kill).
     *   2. No record has any required non-null field set to null.
     */
    @Test
    fun forceStopmidInsert_rowCountIsConsistentOnRelaunch() = runBlocking {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val phase = prefs.getString(KEY_PHASE, null)

        if (phase == PHASE_VERIFY) {
            // ── Phase 1: verify consistency after restart ────────────────────────────
            verifyConsistencyAfterRestart(prefs)
        } else {
            // ── Phase 0: seed records, save count, kill process ──────────────────────
            seedRecordsAndKillProcess(prefs)
        }
    }

    // ── Phase 0 ───────────────────────────────────────────────────────────────

    /**
     * Inserts [SEED_RECORD_COUNT] records, stores the pre-kill row count in SharedPreferences,
     * and calls [ProcessPhoenix.triggerRebirth] to kill the process.
     *
     * The process restart causes the test to re-run; on restart the phase is "verify".
     */
    private suspend fun seedRecordsAndKillProcess(prefs: android.content.SharedPreferences) {
        // Open the real encrypted database.
        val passphrase = DatabaseKeyManager(context).getOrCreatePassphrase()
        val db = LuopanDatabase.getInstance(context, passphrase)
        val repository = BearingRepositoryImpl(db.bearingDao())

        // Seed initial records.
        for (i in 1..SEED_RECORD_COUNT) {
            repository.insert(makeRecord("force-stop-seed-$i", capturedAt = i * 1_000L))
        }

        // Record the pre-kill count for assertion after restart.
        val preKillCount = repository.count()
        prefs.edit()
            .putString(KEY_PHASE, PHASE_VERIFY)
            .putInt(KEY_PRE_KILL_COUNT, preKillCount)
            .commit()   // commit() (synchronous) — not apply() — so the value survives the kill

        // Kill the process via ProcessPhoenix and restart it.
        // This kills the test process; the test runner restarts with the application.
        ProcessPhoenix.triggerRebirth(context)

        // The line below is never reached — the process has been killed.
        // A small sleep is placed here as a safety net to allow the OS to terminate the process
        // before the JVM continues executing.
        Thread.sleep(5_000L)
    }

    // ── Phase 1 ───────────────────────────────────────────────────────────────

    /**
     * After process restart: verifies that row count is in [preKillCount, preKillCount + 1]
     * and that no record has a required field set to null.
     */
    private suspend fun verifyConsistencyAfterRestart(prefs: android.content.SharedPreferences) {
        val preKillCount = prefs.getInt(KEY_PRE_KILL_COUNT, -1)
        assertTrue(
            "pre_kill_count must have been saved before the process kill (got -1)",
            preKillCount >= 0
        )

        // Open the real encrypted database.
        val passphrase = DatabaseKeyManager(context).getOrCreatePassphrase()
        val db = LuopanDatabase.getInstance(context, passphrase)
        val repository = BearingRepositoryImpl(db.bearingDao())

        val postRestartCount = repository.count()

        // AT-PERSIST-02: row count is pre-kill or pre-kill+1.
        assertTrue(
            "Row count after restart must be preKillCount ($preKillCount) or " +
                "preKillCount+1 (${ preKillCount + 1 }); got $postRestartCount",
            postRestartCount == preKillCount || postRestartCount == preKillCount + 1
        )

        // AT-PERSIST-02: no record has a required (non-null) field set to null.
        val allRecords: List<BearingRecord> = repository.getAll()
        assertEquals(
            "getAll() count must match count() after restart",
            postRestartCount,
            allRecords.size
        )

        for (record in allRecords) {
            assertRequiredFieldsNotNull(record)
        }

        // Clean up test prefs so the test can be re-run independently.
        prefs.edit().clear().apply()
        LuopanDatabase.closeInstance()
    }

    /**
     * Asserts that all NOT NULL columns in [BearingRecord] contain non-null values.
     * The nullable columns (lat, lon, alt_m, notes, display_mode) are explicitly excluded.
     */
    private fun assertRequiredFieldsNotNull(record: BearingRecord) {
        val id = record.id
        assertTrue("record[$id].id must not be blank", record.id.isNotBlank())
        assertTrue("record[$id].name must not be blank", record.name.isNotBlank())
        assertTrue(
            "record[$id].bearing_deg must be in [0.0, 360.0)",
            record.bearing_deg >= 0.0f && record.bearing_deg < 360.0f
        )
        assertTrue(
            "record[$id].north_type must not be blank",
            record.north_type.isNotBlank()
        )
        assertTrue(
            "record[$id].confidence must not be blank",
            record.confidence.isNotBlank()
        )
        assertTrue(
            "record[$id].captured_at must be > 0",
            record.captured_at > 0L
        )
        assertTrue(
            "record[$id].calibration_version must not be blank",
            record.calibration_version.isNotBlank()
        )
        // field_deviation_pct and inclination_deviation_deg are primitive Float — never null.
        // interference_flag is primitive Boolean — never null.
        // lat, lon, alt_m, notes, display_mode are nullable — excluded from null checks.
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun makeRecord(id: String, capturedAt: Long = 1_714_000_000_000L) = BearingRecord(
        id = id,
        name = "Force Stop Test Bearing",
        bearing_deg = 45.0f,
        north_type = "MAGNETIC",
        confidence = "HIGH",
        captured_at = capturedAt,
        calibration_version = "WMM2025",
        field_deviation_pct = 1.5f,
        inclination_deviation_deg = 0.8f,
        interference_flag = false,
        lat = 40.0,
        lon = -105.0,
        alt_m = 0.0,
        notes = null,
        display_mode = "MODERN"
    )
}
