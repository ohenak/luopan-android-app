package com.luopan.compass.bearing

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Test-only in-memory Room database wrapping only BearingDao.
 */
@Database(entities = [BearingRecord::class], version = 1, exportSchema = false)
abstract class TestBearingDatabase : RoomDatabase() {
    abstract fun bearingDao(): BearingDao
}

@RunWith(RobolectricTestRunner::class)
class BearingDaoTest {

    private lateinit var db: TestBearingDatabase
    private lateinit var dao: BearingDao

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, TestBearingDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.bearingDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun makeRecord(
        id: String = "550e8400-e29b-41d4-a716-446655440000",
        name: String = "Test Bearing",
        bearingDeg: Float = 45.0f,
        northType: String = "TRUE",
        confidence: String = "HIGH",
        capturedAt: Long = 1_700_000_000_000L,
        calibrationVersion: String = "WMM2025",
        fieldDeviationPct: Float = 2.5f,
        inclinationDeviationDeg: Float = 1.0f,
        interferenceFlag: Boolean = false,
        lat: Double? = 40.0,
        lon: Double? = -105.0,
        altM: Double? = 1620.0,
        notes: String? = null,
        displayMode: String? = "MODERN"
    ) = BearingRecord(
        id = id,
        name = name,
        bearing_deg = bearingDeg,
        north_type = northType,
        confidence = confidence,
        captured_at = capturedAt,
        calibration_version = calibrationVersion,
        field_deviation_pct = fieldDeviationPct,
        inclination_deviation_deg = inclinationDeviationDeg,
        interference_flag = interferenceFlag,
        lat = lat,
        lon = lon,
        alt_m = altM,
        notes = notes,
        display_mode = displayMode
    )

    // ── insert ────────────────────────────────────────────────────────────────

    @Test
    fun `insert stores a record that can be retrieved`() = runBlocking {
        val record = makeRecord()

        dao.insert(record)

        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals(record.id, all[0].id)
    }

    @Test
    fun `insert stores all 15 fields correctly`() = runBlocking {
        val record = makeRecord(
            id = "test-uuid-1",
            name = "Living Room",
            bearingDeg = 270.0f,
            northType = "MAGNETIC",
            confidence = "MODERATE",
            capturedAt = 1_714_000_000_000L,
            calibrationVersion = "WMM2025",
            fieldDeviationPct = 5.0f,
            inclinationDeviationDeg = 2.5f,
            interferenceFlag = true,
            lat = 37.42,
            lon = -122.08,
            altM = 100.0,
            notes = "By the window",
            displayMode = "MODERN"
        )

        dao.insert(record)

        val all = dao.getAll()
        val saved = all[0]
        assertEquals("test-uuid-1", saved.id)
        assertEquals("Living Room", saved.name)
        assertEquals(270.0f, saved.bearing_deg)
        assertEquals("MAGNETIC", saved.north_type)
        assertEquals("MODERATE", saved.confidence)
        assertEquals(1_714_000_000_000L, saved.captured_at)
        assertEquals("WMM2025", saved.calibration_version)
        assertEquals(5.0f, saved.field_deviation_pct)
        assertEquals(2.5f, saved.inclination_deviation_deg)
        assertTrue(saved.interference_flag)
        assertEquals(37.42, saved.lat!!, 0.0001)
        assertEquals(-122.08, saved.lon!!, 0.0001)
        assertEquals(100.0, saved.alt_m!!, 0.0001)
        assertEquals("By the window", saved.notes)
        assertEquals("MODERN", saved.display_mode)
    }

    @Test
    fun `insert stores record with all nullable fields null`() = runBlocking {
        val record = makeRecord(
            lat = null,
            lon = null,
            altM = null,
            notes = null,
            displayMode = null
        )

        dao.insert(record)

        val all = dao.getAll()
        val saved = all[0]
        assertNull(saved.lat)
        assertNull(saved.lon)
        assertNull(saved.alt_m)
        assertNull(saved.notes)
        assertNull(saved.display_mode)
    }

    // ── getAll ────────────────────────────────────────────────────────────────

    @Test
    fun `getAll returns empty list when no records exist`() = runBlocking {
        val all = dao.getAll()
        assertTrue(all.isEmpty())
    }

    @Test
    fun `getAll returns records ordered by captured_at descending`() = runBlocking {
        val earliest = makeRecord(id = "id-1", capturedAt = 1_000L)
        val middle = makeRecord(id = "id-2", capturedAt = 2_000L)
        val latest = makeRecord(id = "id-3", capturedAt = 3_000L)

        dao.insert(earliest)
        dao.insert(latest)
        dao.insert(middle)

        val all = dao.getAll()
        assertEquals(3, all.size)
        assertEquals("id-3", all[0].id)  // latest first
        assertEquals("id-2", all[1].id)
        assertEquals("id-1", all[2].id)  // earliest last
    }

    @Test
    fun `getAll returns multiple records after multiple inserts`() = runBlocking {
        dao.insert(makeRecord(id = "id-a", capturedAt = 1_000L))
        dao.insert(makeRecord(id = "id-b", capturedAt = 2_000L))
        dao.insert(makeRecord(id = "id-c", capturedAt = 3_000L))

        assertEquals(3, dao.getAll().size)
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    fun `delete removes a record by id`() = runBlocking {
        val record = makeRecord()
        dao.insert(record)

        dao.delete(record.id)

        assertTrue(dao.getAll().isEmpty())
    }

    @Test
    fun `delete removes only the record with matching id`() = runBlocking {
        dao.insert(makeRecord(id = "to-delete", capturedAt = 1_000L))
        dao.insert(makeRecord(id = "to-keep", capturedAt = 2_000L))

        dao.delete("to-delete")

        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals("to-keep", all[0].id)
    }

    @Test
    fun `delete on non-existent id is a no-op`() = runBlocking {
        dao.insert(makeRecord())

        dao.delete("non-existent-id")

        assertEquals(1, dao.getAll().size)
    }

    // ── count ─────────────────────────────────────────────────────────────────

    @Test
    fun `count returns zero when table is empty`() = runBlocking {
        assertEquals(0, dao.count())
    }

    @Test
    fun `count returns correct number after inserts`() = runBlocking {
        assertEquals(0, dao.count())

        dao.insert(makeRecord(id = "id-1", capturedAt = 1_000L))
        assertEquals(1, dao.count())

        dao.insert(makeRecord(id = "id-2", capturedAt = 2_000L))
        assertEquals(2, dao.count())
    }

    @Test
    fun `count decrements after delete`() = runBlocking {
        dao.insert(makeRecord(id = "id-1", capturedAt = 1_000L))
        dao.insert(makeRecord(id = "id-2", capturedAt = 2_000L))
        assertEquals(2, dao.count())

        dao.delete("id-1")
        assertEquals(1, dao.count())
    }

    // ── conflict / ABORT strategy ─────────────────────────────────────────────

    @Test(expected = Exception::class)
    fun `insert with duplicate id throws on ABORT conflict strategy`() = runBlocking {
        val record = makeRecord()
        dao.insert(record)
        // Second insert with same id should throw (OnConflictStrategy.ABORT)
        dao.insert(record)
    }

    // ── interference_flag boolean mapping ─────────────────────────────────────

    @Test
    fun `interference_flag false is stored and retrieved correctly`() = runBlocking {
        dao.insert(makeRecord(interferenceFlag = false))
        assertFalse(dao.getAll()[0].interference_flag)
    }

    @Test
    fun `interference_flag true is stored and retrieved correctly`() = runBlocking {
        dao.insert(makeRecord(interferenceFlag = true))
        assertTrue(dao.getAll()[0].interference_flag)
    }

    // ── getAllFlow (Phase 4 — A-7) ─────────────────────────────────────────────

    @Test
    fun `getAllFlow returns empty list when no records exist`() = runBlocking {
        val result = dao.getAllFlow().first()
        assertTrue("getAllFlow must return empty list on empty table", result.isEmpty())
    }

    @Test
    fun `getAllFlow returns records ordered by captured_at descending`() = runBlocking {
        val earliest = makeRecord(id = "flow-id-1", capturedAt = 1_000L)
        val middle   = makeRecord(id = "flow-id-2", capturedAt = 2_000L)
        val latest   = makeRecord(id = "flow-id-3", capturedAt = 3_000L)

        dao.insert(earliest)
        dao.insert(latest)
        dao.insert(middle)

        val result = dao.getAllFlow().first()
        assertEquals(3, result.size)
        assertEquals("flow-id-3", result[0].id) // newest first
        assertEquals("flow-id-2", result[1].id)
        assertEquals("flow-id-1", result[2].id) // oldest last
    }

    @Test
    fun `getAllFlow is reactive — emits updated list after insert`() = runBlocking {
        // Start with empty list
        val empty = dao.getAllFlow().first()
        assertTrue(empty.isEmpty())

        // Insert a record and collect the new emission
        dao.insert(makeRecord(id = "reactive-1", capturedAt = 5_000L))
        val afterInsert = dao.getAllFlow().first()
        assertEquals(1, afterInsert.size)
        assertEquals("reactive-1", afterInsert[0].id)
    }

    // ── searchFlow (Phase 4 — A-7) ────────────────────────────────────────────

    @Test
    fun `searchFlow returns empty list when no records match`() = runBlocking {
        dao.insert(makeRecord(id = "s-1", name = "North Wall"))
        val result = dao.searchFlow("xyz").first()
        assertTrue("searchFlow must return empty list when no match", result.isEmpty())
    }

    @Test
    fun `searchFlow returns case-insensitive substring matches`() = runBlocking {
        dao.insert(makeRecord(id = "s-1", name = "North Wall", capturedAt = 1_000L))
        dao.insert(makeRecord(id = "s-2", name = "NORTH Door", capturedAt = 2_000L))
        dao.insert(makeRecord(id = "s-3", name = "South Garden", capturedAt = 3_000L))

        val result = dao.searchFlow("north").first()
        assertEquals("searchFlow must return 2 records matching 'north' case-insensitively", 2, result.size)
        val ids = result.map { it.id }
        assertTrue(ids.contains("s-1"))
        assertTrue(ids.contains("s-2"))
        assertFalse(ids.contains("s-3"))
    }

    @Test
    fun `searchFlow results are ordered by captured_at descending`() = runBlocking {
        dao.insert(makeRecord(id = "s-a", name = "Entry Point", capturedAt = 1_000L))
        dao.insert(makeRecord(id = "s-b", name = "Entry Gate",  capturedAt = 3_000L))
        dao.insert(makeRecord(id = "s-c", name = "Entry Hall",  capturedAt = 2_000L))

        val result = dao.searchFlow("entry").first()
        assertEquals(3, result.size)
        assertEquals("s-b", result[0].id) // newest first
        assertEquals("s-c", result[1].id)
        assertEquals("s-a", result[2].id)
    }

    @Test
    fun `searchFlow with empty query returns all records`() = runBlocking {
        dao.insert(makeRecord(id = "s-x", name = "Alpha", capturedAt = 1_000L))
        dao.insert(makeRecord(id = "s-y", name = "Beta",  capturedAt = 2_000L))

        val result = dao.searchFlow("").first()
        assertEquals("searchFlow with empty query must return all records", 2, result.size)
    }

    @Test
    fun `searchFlow is reactive — emits updated results after insert`() = runBlocking {
        dao.insert(makeRecord(id = "s-1", name = "Living Room", capturedAt = 1_000L))
        val initial = dao.searchFlow("living").first()
        assertEquals(1, initial.size)

        dao.insert(makeRecord(id = "s-2", name = "Living Area", capturedAt = 2_000L))
        val afterInsert = dao.searchFlow("living").first()
        assertEquals("searchFlow must emit updated results after insert", 2, afterInsert.size)
    }
}
