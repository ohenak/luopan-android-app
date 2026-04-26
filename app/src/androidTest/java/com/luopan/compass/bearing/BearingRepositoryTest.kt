package com.luopan.compass.bearing

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
 * Instrumented CRUD round-trip test for [BearingRepositoryImpl].
 *
 * PLAN P6.2 success criteria:
 * - insert(BearingRecord) persists via BearingDao; getAll() returns saved record
 * - getById() is NOT tested (not part of TSPEC §3.5 interface)
 * - All operations are suspend functions on Dispatchers.IO (verified by behaviour)
 *
 * Uses [Room.inMemoryDatabaseBuilder] per implementation notes — in-memory DB is
 * sufficient for the CRUD round-trip test; the encryption check (BearingEncryptionTest)
 * is in P8.4 and is NOT part of this phase.
 */
@RunWith(AndroidJUnit4::class)
class BearingRepositoryTest {

    private lateinit var db: LuopanDatabase
    private lateinit var repository: BearingRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = LuopanDatabase.buildInMemory(ctx)
        repository = BearingRepositoryImpl(db.bearingDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── insert + getAll ───────────────────────────────────────────────────────

    @Test
    fun insert_persists_record_and_getAll_returns_it() = runBlocking {
        val record = makeRecord(id = "uuid-1")

        repository.insert(record)

        val all = repository.getAll()
        assertEquals(1, all.size)
        assertEquals("uuid-1", all[0].id)
    }

    @Test
    fun insert_stores_all_fields_correctly() = runBlocking {
        val record = makeRecord(
            id = "uuid-fields",
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

        repository.insert(record)

        val saved = repository.getAll()[0]
        assertEquals("uuid-fields", saved.id)
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
    fun insert_record_with_all_nullable_fields_null() = runBlocking {
        val record = makeRecord(
            id = "uuid-nulls",
            lat = null,
            lon = null,
            altM = null,
            notes = null,
            displayMode = null
        )

        repository.insert(record)

        val saved = repository.getAll()[0]
        assertNull(saved.lat)
        assertNull(saved.lon)
        assertNull(saved.alt_m)
        assertNull(saved.notes)
        assertNull(saved.display_mode)
    }

    // ── getAll ordering ───────────────────────────────────────────────────────

    @Test
    fun getAll_returns_records_ordered_by_captured_at_descending() = runBlocking {
        repository.insert(makeRecord(id = "id-early", capturedAt = 1_000L))
        repository.insert(makeRecord(id = "id-latest", capturedAt = 3_000L))
        repository.insert(makeRecord(id = "id-middle", capturedAt = 2_000L))

        val all = repository.getAll()

        assertEquals(3, all.size)
        assertEquals("id-latest", all[0].id)
        assertEquals("id-middle", all[1].id)
        assertEquals("id-early", all[2].id)
    }

    @Test
    fun getAll_returns_empty_list_when_no_records() = runBlocking {
        assertTrue(repository.getAll().isEmpty())
    }

    // ── count ─────────────────────────────────────────────────────────────────

    @Test
    fun count_returns_zero_when_table_is_empty() = runBlocking {
        assertEquals(0, repository.count())
    }

    @Test
    fun count_returns_number_of_inserted_records() = runBlocking {
        repository.insert(makeRecord(id = "id-1", capturedAt = 1_000L))
        assertEquals(1, repository.count())

        repository.insert(makeRecord(id = "id-2", capturedAt = 2_000L))
        assertEquals(2, repository.count())
    }

    @Test
    fun count_decrements_after_delete() = runBlocking {
        repository.insert(makeRecord(id = "id-1", capturedAt = 1_000L))
        repository.insert(makeRecord(id = "id-2", capturedAt = 2_000L))

        repository.delete("id-1")

        assertEquals(1, repository.count())
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    fun delete_removes_the_record_with_matching_id() = runBlocking {
        repository.insert(makeRecord(id = "to-delete", capturedAt = 1_000L))
        repository.insert(makeRecord(id = "to-keep", capturedAt = 2_000L))

        repository.delete("to-delete")

        val all = repository.getAll()
        assertEquals(1, all.size)
        assertEquals("to-keep", all[0].id)
    }

    @Test
    fun delete_on_nonexistent_id_is_a_noop() = runBlocking {
        repository.insert(makeRecord(id = "id-1"))

        repository.delete("does-not-exist")

        assertEquals(1, repository.count())
    }

    // ── CRUD round-trip ───────────────────────────────────────────────────────

    @Test
    fun crud_roundtrip_insert_count_getAll_delete() = runBlocking {
        val r1 = makeRecord(id = "round-1", capturedAt = 1_000L)
        val r2 = makeRecord(id = "round-2", capturedAt = 2_000L)

        // Insert two records
        repository.insert(r1)
        repository.insert(r2)
        assertEquals(2, repository.count())

        // Retrieve them (newest first)
        val all = repository.getAll()
        assertEquals("round-2", all[0].id)
        assertEquals("round-1", all[1].id)

        // Delete one
        repository.delete("round-1")
        assertEquals(1, repository.count())
        assertEquals("round-2", repository.getAll()[0].id)

        // Delete the other
        repository.delete("round-2")
        assertEquals(0, repository.count())
        assertTrue(repository.getAll().isEmpty())
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun makeRecord(
        id: String = "test-uuid",
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
}
