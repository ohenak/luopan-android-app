package com.luopan.compass.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luopan.compass.bearing.BearingRecord
import com.luopan.compass.db.LuopanDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Performance test for BearingDao.getAllFlow() with 500 seeded records.
 *
 * NFR: Initial load of 500 bearing records must complete in < 500 ms
 * (TSPEC §9 / PLAN Phase G task G-1; isolated from BearingHistoryFragmentTest.kt
 * per TE PLAN-v1 F-07 to avoid slow data-seeding bleed into functional test runs).
 *
 * DEVICE-ONLY: This test requires an Android device or emulator. It must not be
 * run as a JVM unit test because it exercises Room's actual SQLite engine via
 * an in-memory database. The :app:connectedDebugAndroidTest Gradle target is the
 * correct execution path.
 *
 * CI gate: non-blocking advisory — record the elapsed time as a metric and
 * fail only when the load time exceeds the 500 ms budget.
 */
@RunWith(AndroidJUnit4::class)
class BearingHistoryPerfTest {

    private lateinit var db: LuopanDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = LuopanDatabase.buildInMemory(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * Seeds 500 BearingRecord rows into an in-memory Room database and then times
     * a single `getAllFlow().first()` emission on [Dispatchers.IO].
     *
     * Pass condition: elapsed time < 500 ms (PLAN G-1 / TSPEC NFR).
     */
    @Test
    fun initialLoad500Records_completesWithin500ms() {
        // --- Arrange: seed 500 records ---
        val dao = db.bearingDao()
        val baseTime = System.currentTimeMillis() - (500 * 1_000L) // oldest first
        runBlocking(Dispatchers.IO) {
            repeat(RECORD_COUNT) { i ->
                dao.insert(buildRecord(index = i, capturedAt = baseTime + i * 1_000L))
            }
        }

        // --- Act: time the first emission of getAllFlow() on IO dispatcher ---
        val startMs = System.currentTimeMillis()
        val records = runBlocking(Dispatchers.IO) {
            dao.getAllFlow().first()
        }
        val elapsedMs = System.currentTimeMillis() - startMs

        // --- Assert ---
        assertTrue(
            "getAllFlow().first() returned ${records.size} records (expected $RECORD_COUNT)",
            records.size == RECORD_COUNT
        )
        assertTrue(
            "Initial load of $RECORD_COUNT records took ${elapsedMs} ms, exceeding the 500 ms budget",
            elapsedMs < LOAD_BUDGET_MS
        )
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun buildRecord(index: Int, capturedAt: Long): BearingRecord = BearingRecord(
        id = UUID.randomUUID().toString(),
        name = "Bearing $index",
        bearing_deg = (index % 360).toFloat(),
        north_type = "TRUE",
        confidence = "HIGH",
        captured_at = capturedAt,
        calibration_version = "WMM2020",
        field_deviation_pct = 0.01f,
        inclination_deviation_deg = 1.0f,
        interference_flag = false,
        lat = null,
        lon = null,
        alt_m = null,
        notes = null,
        display_mode = "MODERN"
    )

    companion object {
        private const val RECORD_COUNT = 500

        /**
         * 500 ms initial-load budget per PLAN G-1 / TSPEC NFR.
         * Applies to `getAllFlow().first()` on [Dispatchers.IO] with an in-memory Room DB.
         */
        private const val LOAD_BUDGET_MS = 500L
    }
}
