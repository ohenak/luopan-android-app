package com.luopan.compass.calibration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.luopan.compass.db.LuopanDatabase
import com.luopan.compass.model.CalibrationQuality
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CalibrationRepositoryTest {

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

    @Test fun `getCurrent returns null on empty db`() = runBlocking {
        assertNull(repo.getCurrent())
    }

    @Test fun `save stores as current (id=1)`() = runBlocking {
        repo.save(mockResult(), 1000L)
        val current = repo.getCurrent()
        assertNotNull(current)
        assertEquals(1, current!!.id)
        assertEquals("GOOD", current.quality)
    }

    @Test fun `second save promotes first to rollback`() = runBlocking {
        repo.save(mockResult(CalibrationQuality.GOOD), 1000L)
        repo.save(mockResult(CalibrationQuality.FAIR), 2000L)
        assertEquals("FAIR", repo.getCurrent()!!.quality)
        assertEquals("GOOD", repo.getRollback()!!.quality)
    }

    @Test fun `rollback restores previous calibration`() = runBlocking {
        repo.save(mockResult(CalibrationQuality.GOOD), 1000L)
        repo.save(mockResult(CalibrationQuality.FAIR), 2000L)
        val success = repo.rollback()
        assertTrue(success)
        assertEquals("GOOD", repo.getCurrent()!!.quality)
        assertNull(repo.getRollback())
    }

    @Test fun `rollback returns false when no rollback exists`() = runBlocking {
        assertFalse(repo.rollback())
    }

    @Test fun `toRecord and fromRecord are inverse`() {
        val original = mockResult()
        val record = repo.toRecord(original, id = 1, timestamp = 12345L)
        val restored = repo.fromRecord(record)
        assertArrayEquals(original.hardIron, restored.hardIron, 0.001f)
        assertEquals(original.quality, restored.quality)
    }

    @Test fun `hard iron values persisted correctly`() = runBlocking {
        repo.save(mockResult(), 1000L)
        val record = repo.getCurrent()!!
        assertEquals(1f, record.hard_iron_x, 0.001f)
        assertEquals(2f, record.hard_iron_y, 0.001f)
        assertEquals(3f, record.hard_iron_z, 0.001f)
    }
}
