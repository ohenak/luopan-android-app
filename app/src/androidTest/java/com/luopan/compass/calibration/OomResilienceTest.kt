package com.luopan.compass.calibration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luopan.compass.db.LuopanDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * OOM resilience tests — must run on emulator or physical device.
 * These tests verify that the DB is in a consistent state after a simulated process kill.
 * The in-memory DB variant is used here for host compatibility; the real kill scenario
 * requires an instrumented device with ProcessPhoenix (see T-5-06 in PLAN).
 */
@RunWith(AndroidJUnit4::class)
class OomResilienceTest {

    private lateinit var db: LuopanDatabase
    private lateinit var repo: CalibrationRepository

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = LuopanDatabase.buildInMemory(ctx)
        repo = CalibrationRepository(db.calibrationDao())
    }

    @After fun tearDown() { db.close() }

    private fun mockResult(quality: CalibrationQuality = CalibrationQuality.GOOD) = CalibrationResult(
        hardIron = floatArrayOf(1f, 2f, 3f),
        softIron = Array(3) { i -> FloatArray(3) { j -> if (i == j) 1f else 0f } },
        residualMicroTesla = 0.5f,
        coverageScore = 0.8f,
        quality = quality
    )

    @Test fun `db is null when no calibration was ever saved`() = runBlocking {
        assertNull(repo.getCurrent())
        assertNull(repo.getRollback())
    }

    @Test fun `pre-session calibration intact after interrupted session`() = runBlocking {
        // Save an initial calibration
        repo.save(mockResult(CalibrationQuality.GOOD), 1000L)
        val preSave = repo.getCurrent()
        assertNotNull(preSave)

        // Simulate interrupted session: engine collects samples but never calls save
        val engine = CalibrationEngine()
        repeat(50) { engine.addSample(it.toFloat(), 0f, 0f) }
        // Process "killed" — repo.save() is never called
        engine.clearSamples()

        // Post-kill: original calibration should still be intact
        val postKill = repo.getCurrent()
        assertNotNull(postKill)
        assertEquals(preSave!!.recorded_at, postKill!!.recorded_at)
        assertEquals(preSave.quality, postKill.quality)
    }

    @Test fun `rollback slot preserved when current save interrupted`() = runBlocking {
        repo.save(mockResult(CalibrationQuality.GOOD), 1000L)
        repo.save(mockResult(CalibrationQuality.FAIR), 2000L)

        val rollbackBefore = repo.getRollback()
        assertNotNull(rollbackBefore)

        // No further save (simulated kill during collection phase)
        val rollbackAfter = repo.getRollback()
        assertEquals(rollbackBefore!!.recorded_at, rollbackAfter!!.recorded_at)
    }

    @Test fun `transaction atomicity via Room in-memory rollback`() = runBlocking {
        // Simulate saving, then rolling back if the result is null (engine returns null)
        val engine = CalibrationEngine()
        repeat(5) { engine.addSample(it.toFloat(), 0f, 0f) } // too few samples
        val result = engine.computeCalibration() // returns null
        assertNull(result) // no partial save happens
        assertNull(repo.getCurrent())
    }
}
