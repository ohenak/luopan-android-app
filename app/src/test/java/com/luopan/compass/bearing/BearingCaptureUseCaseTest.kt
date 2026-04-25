package com.luopan.compass.bearing

import com.luopan.compass.model.InterferenceState
import com.luopan.compass.model.NorthType
import com.luopan.compass.model.OverallConfidence
import com.luopan.compass.util.FakeClock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [BearingCaptureUseCase].
 *
 * TSPEC §10.1 — BearingCaptureUseCaseTest:
 *   - interference_flag = true when InterferenceState.MODERATE
 *   - interference_flag = true when InterferenceState.WARNING
 *   - interference_flag = false when CLEAR + POOR confidence (AT-E-10)
 *   - captured_at == snapshot.tapTimestampMs (PM-T-01: not execute-time clock)
 *   - north_type propagated from snapshot
 *   - GRID throws IllegalStateException
 *   - notes empty string coerced to null
 *   - id is valid UUID format
 */
class BearingCaptureUseCaseTest {

    private lateinit var fakeClock: FakeClock
    private lateinit var fakeBearingRepository: FakeBearingRepository
    private lateinit var useCase: BearingCaptureUseCase

    @Before
    fun setUp() {
        fakeClock = FakeClock(currentMs = 0L)
        fakeBearingRepository = FakeBearingRepository()
        useCase = BearingCaptureUseCase(bearingRepository = fakeBearingRepository)
    }

    // ── interference_flag derivation (BR-10, AT-E-10) ─────────────────────────

    @Test
    fun `interference_flag is true when interferenceState is MODERATE`() = runBlocking {
        val snapshot = makeSnapshot(
            interferenceState = InterferenceState.MODERATE,
            confidence = OverallConfidence.HIGH
        )

        val result = useCase.execute(snapshot)

        assertTrue(result.interference_flag)
    }

    @Test
    fun `interference_flag is true when interferenceState is WARNING`() = runBlocking {
        val snapshot = makeSnapshot(
            interferenceState = InterferenceState.WARNING,
            confidence = OverallConfidence.HIGH
        )

        val result = useCase.execute(snapshot)

        assertTrue(result.interference_flag)
    }

    @Test
    fun `interference_flag is false when interferenceState is CLEAR even with POOR confidence (AT-E-10)`() = runBlocking {
        // AT-E-10: OverallConfidence.POOR alone does NOT set interference_flag
        val snapshot = makeSnapshot(
            interferenceState = InterferenceState.CLEAR,
            confidence = OverallConfidence.POOR
        )

        val result = useCase.execute(snapshot)

        assertFalse(result.interference_flag)
    }

    @Test
    fun `interference_flag is false when interferenceState is CLEAR with HIGH confidence`() = runBlocking {
        val snapshot = makeSnapshot(
            interferenceState = InterferenceState.CLEAR,
            confidence = OverallConfidence.HIGH
        )

        val result = useCase.execute(snapshot)

        assertFalse(result.interference_flag)
    }

    // ── captured_at == tapTimestampMs (PM-T-01) ────────────────────────────────

    @Test
    fun `captured_at equals snapshot tapTimestampMs not execute-time clock (PM-T-01)`() = runBlocking {
        // Snap at T=1000; execute at T=15000 — captured_at must be 1000
        fakeClock.set(1_000L)
        val tapTimestampMs = fakeClock.nowMs()          // 1000 — as CompassViewModel records it

        fakeClock.set(15_000L)                           // clock advances before execute() is called
        val snapshot = makeSnapshot(tapTimestampMs = tapTimestampMs)

        val result = useCase.execute(snapshot)

        assertEquals("captured_at must equal tap timestamp, not execute-time clock",
            1_000L, result.captured_at)
    }

    // ── north_type propagated ──────────────────────────────────────────────────

    @Test
    fun `north_type TRUE is written to BearingRecord`() = runBlocking {
        val snapshot = makeSnapshot(northType = NorthType.TRUE)

        val result = useCase.execute(snapshot)

        assertEquals("TRUE", result.north_type)
    }

    @Test
    fun `north_type MAGNETIC is written to BearingRecord`() = runBlocking {
        val snapshot = makeSnapshot(northType = NorthType.MAGNETIC)

        val result = useCase.execute(snapshot)

        assertEquals("MAGNETIC", result.north_type)
    }

    // ── GRID guard ─────────────────────────────────────────────────────────────

    @Test
    fun `execute throws IllegalStateException when northType is GRID`() {
        val snapshot = makeSnapshot(northType = NorthType.GRID)

        var thrownException: Exception? = null
        try {
            runBlocking { useCase.execute(snapshot) }
        } catch (e: IllegalStateException) {
            thrownException = e
        }

        assertNotNull("Expected IllegalStateException to be thrown", thrownException)
    }

    // ── notes empty-string coercion ────────────────────────────────────────────

    @Test
    fun `notes empty string is coerced to null in BearingRecord`() = runBlocking {
        val snapshot = makeSnapshot(notes = "")

        val result = useCase.execute(snapshot)

        assertNull(result.notes)
    }

    @Test
    fun `notes blank string is coerced to null in BearingRecord`() = runBlocking {
        val snapshot = makeSnapshot(notes = "   ")

        val result = useCase.execute(snapshot)

        assertNull(result.notes)
    }

    @Test
    fun `notes non-empty string is stored in BearingRecord`() = runBlocking {
        val snapshot = makeSnapshot(notes = "North wall")

        val result = useCase.execute(snapshot)

        assertEquals("North wall", result.notes)
    }

    // ── id is UUID format ──────────────────────────────────────────────────────

    @Test
    fun `id is a valid UUID v4 format`() = runBlocking {
        val snapshot = makeSnapshot()

        val result = useCase.execute(snapshot)

        val uuidPattern = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
        assertTrue("id must be UUID v4, was: ${result.id}", uuidPattern.matches(result.id))
    }

    // ── record is persisted ────────────────────────────────────────────────────

    @Test
    fun `execute persists the record via BearingRepository`() = runBlocking {
        val snapshot = makeSnapshot()

        val result = useCase.execute(snapshot)

        assertEquals(1, fakeBearingRepository.insertedRecords.size)
        assertEquals(result.id, fakeBearingRepository.insertedRecords[0].id)
    }

    // ── returned record matches snapshot ──────────────────────────────────────

    @Test
    fun `returned record bearing_deg matches snapshot bearingDeg`() = runBlocking {
        val snapshot = makeSnapshot(bearingDeg = 135.5f)

        val result = useCase.execute(snapshot)

        assertEquals(135.5f, result.bearing_deg)
    }

    @Test
    fun `returned record confidence matches snapshot confidence`() = runBlocking {
        val snapshot = makeSnapshot(confidence = OverallConfidence.MODERATE)

        val result = useCase.execute(snapshot)

        assertEquals("MODERATE", result.confidence)
    }

    // ── location fields only included when includeLocation = true ─────────────

    @Test
    fun `lat lon alt are null when includeLocation is false`() = runBlocking {
        val snapshot = makeSnapshot(
            latDeg = 40.0,
            lonDeg = -105.0,
            altM = 1620.0,
            includeLocation = false
        )

        val result = useCase.execute(snapshot)

        assertNull(result.lat)
        assertNull(result.lon)
        assertNull(result.alt_m)
    }

    @Test
    fun `lat lon alt are stored when includeLocation is true`() = runBlocking {
        val snapshot = makeSnapshot(
            latDeg = 40.0,
            lonDeg = -105.0,
            altM = 1620.0,
            includeLocation = true
        )

        val result = useCase.execute(snapshot)

        assertEquals(40.0, result.lat!!, 0.0001)
        assertEquals(-105.0, result.lon!!, 0.0001)
        assertEquals(1620.0, result.alt_m!!, 0.0001)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun makeSnapshot(
        bearingDeg: Float = 45.0f,
        northType: NorthType = NorthType.MAGNETIC,
        confidence: OverallConfidence = OverallConfidence.HIGH,
        interferenceState: InterferenceState = InterferenceState.CLEAR,
        fieldDeviationPct: Float = 2.0f,
        inclinationDeviationDeg: Float = 1.0f,
        latDeg: Double? = 40.0,
        lonDeg: Double? = -105.0,
        altM: Double? = 1620.0,
        name: String = "Test Bearing",
        notes: String? = null,
        displayMode: String = "MODERN",
        includeLocation: Boolean = true,
        tapTimestampMs: Long = fakeClock.nowMs(),
        calibrationVersion: String = "AndroidGeoField"
    ) = BearingSnapshot(
        bearingDeg = bearingDeg,
        northType = northType,
        confidence = confidence,
        interferenceState = interferenceState,
        fieldDeviationPct = fieldDeviationPct,
        inclinationDeviationDeg = inclinationDeviationDeg,
        latDeg = latDeg,
        lonDeg = lonDeg,
        altM = altM,
        name = name,
        notes = notes,
        displayMode = displayMode,
        includeLocation = includeLocation,
        tapTimestampMs = tapTimestampMs,
        calibrationVersion = calibrationVersion
    )
}

// ── Test doubles ─────────────────────────────────────────────────────────────

/**
 * In-memory [BearingRepository] fake — records all [insert] calls for assertion.
 */
class FakeBearingRepository : BearingRepository {
    val insertedRecords = mutableListOf<BearingRecord>()

    override suspend fun insert(record: BearingRecord) {
        insertedRecords.add(record)
    }

    override suspend fun count(): Int = insertedRecords.size

    override suspend fun getAll(): List<BearingRecord> = insertedRecords.toList()

    override suspend fun delete(id: String) {
        insertedRecords.removeAll { it.id == id }
    }
}
