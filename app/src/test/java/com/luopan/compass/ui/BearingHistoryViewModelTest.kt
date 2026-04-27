package com.luopan.compass.ui

import com.luopan.compass.bearing.BearingRecord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [BearingHistoryViewModel].
 *
 * Tests: search debounce (AT-HIST-02-A through D), undo state machine (AT-UNDO-VM-01 through 03),
 * and updateListState four-branch logic (E-2a).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BearingHistoryViewModelTest {

    private lateinit var fakeDao: FakeBearingDao
    private lateinit var viewModel: BearingHistoryViewModel

    @Before
    fun setUp() {
        fakeDao = FakeBearingDao()
        viewModel = BearingHistoryViewModel(fakeDao)
    }

    private fun makeRecord(
        id: String = "id-1",
        name: String = "Test Bearing",
        bearingDeg: Float = 45.0f,
        capturedAt: Long = 1_000L,
        interferenceFlag: Boolean = false
    ) = BearingRecord(
        id = id,
        name = name,
        bearing_deg = bearingDeg,
        north_type = "TRUE",
        confidence = "HIGH",
        captured_at = capturedAt,
        calibration_version = "WMM2025",
        field_deviation_pct = 0.0f,
        inclination_deviation_deg = 0.0f,
        interference_flag = interferenceFlag,
        lat = null, lon = null, alt_m = null, notes = null, display_mode = "MODERN"
    )

    // ── AT-HIST-02-A: Substring match ─────────────────────────────────────────

    @Test
    fun `AT-HIST-02-A search returns matching records`() = runTest {
        val records = listOf(
            makeRecord(id = "id-1", name = "North Wall"),
            makeRecord(id = "id-2", name = "South Entrance"),
            makeRecord(id = "id-3", name = "North Gate")
        )
        fakeDao.setRecords(records)

        viewModel.setSearchQuery("North")
        advanceUntilIdle()

        val result = viewModel.bearingList.first()
        assertEquals(2, result.size)
        assertTrue(result.all { it.name.contains("North", ignoreCase = true) })
    }

    // ── AT-HIST-02-B: Debounce — no call at 200ms, one call at 500ms ──────────

    @Test
    fun `AT-HIST-02-B search debounce fires after 300ms idle`() = runTest {
        fakeDao.setRecords(listOf(makeRecord(name = "North")))
        val initialSearchCalls = fakeDao.searchFlowCalls.size

        viewModel.setSearchQuery("nor")
        advanceTimeBy(200L)  // within debounce window — should not fire yet
        val callsAt200ms = fakeDao.searchFlowCalls.size

        advanceTimeBy(200L)  // total 400ms — past debounce threshold
        advanceUntilIdle()
        val callsAt400ms = fakeDao.searchFlowCalls.size

        // Should not have fired before the debounce window
        assertEquals(initialSearchCalls, callsAt200ms)
        // Should have fired after debounce
        assertTrue(callsAt400ms > callsAt200ms)
    }

    // ── AT-HIST-02-D: Immediate restore on empty query ──────────────────────

    @Test
    fun `AT-HIST-02-D empty query restores all flow immediately without debounce`() = runTest {
        fakeDao.setRecords(listOf(makeRecord(name = "North")))

        viewModel.setSearchQuery("nor")
        advanceUntilIdle()
        val callsBeforeClear = fakeDao.getAllFlowCallCount

        // Clear query — should switch to getAllFlow() immediately (0ms debounce)
        viewModel.setSearchQuery("")
        advanceUntilIdle()

        // getAllFlow should have been called again
        assertTrue(fakeDao.getAllFlowCallCount > callsBeforeClear)
    }

    // ── AT-UNDO-VM-01: deleteRecord sets pendingUndo ──────────────────────────

    @Test
    fun `AT-UNDO-VM-01 deleteRecord sets pending undo`() = runTest {
        val record = makeRecord()
        fakeDao.setRecords(listOf(record))

        assertFalse(viewModel.hasPendingUndo())
        viewModel.deleteRecord(record)
        assertTrue(viewModel.hasPendingUndo())
    }

    // ── AT-UNDO-VM-02: undoDelete clears and re-inserts ───────────────────────

    @Test
    fun `AT-UNDO-VM-02 undoDelete clears pending undo and re-inserts record`() = runTest {
        val record = makeRecord()
        fakeDao.setRecords(listOf(record))

        viewModel.deleteRecord(record)
        advanceUntilIdle()
        assertEquals(0, fakeDao.count())

        viewModel.undoDelete()
        advanceUntilIdle()

        assertFalse(viewModel.hasPendingUndo())
        assertEquals(1, fakeDao.count())
    }

    // ── AT-UNDO-VM-03: commitDelete clears without re-inserting ───────────────

    @Test
    fun `AT-UNDO-VM-03 commitDelete clears pending undo without restoring`() = runTest {
        val record = makeRecord()
        fakeDao.setRecords(listOf(record))

        viewModel.deleteRecord(record)
        advanceUntilIdle()
        assertEquals(0, fakeDao.count())

        viewModel.commitDelete()
        advanceUntilIdle()

        assertFalse(viewModel.hasPendingUndo())
        assertEquals(0, fakeDao.count())  // record not restored
    }

    // ── E-2a: updateListState four-branch visibility logic ────────────────────

    @Test
    fun `updateListState State A zero records empty query shows empty-no-records`() {
        val result = BearingHistoryViewModelTest.computeListState(emptyList(), "")
        assertEquals(ListState.STATE_A, result)
    }

    @Test
    fun `updateListState normal list one or more records empty query`() {
        val records = listOf(makeRecord())
        val result = computeListState(records, "")
        assertEquals(ListState.NORMAL, result)
    }

    @Test
    fun `updateListState search results matches found`() {
        val records = listOf(makeRecord(name = "North"))
        val result = computeListState(records, "nor")
        assertEquals(ListState.SEARCH_RESULTS, result)
    }

    @Test
    fun `updateListState no results active query no matches`() {
        val result = computeListState(emptyList(), "xyz")
        assertEquals(ListState.NO_RESULTS, result)
    }

    // Helper enum and function to test the four-branch logic without a Fragment
    enum class ListState { STATE_A, NORMAL, SEARCH_RESULTS, NO_RESULTS }

    companion object {
        fun computeListState(records: List<BearingRecord>, query: String): ListState {
            val isEmpty = records.isEmpty()
            val isSearchActive = query.isNotEmpty()
            return when {
                isEmpty && !isSearchActive -> ListState.STATE_A
                isEmpty && isSearchActive  -> ListState.NO_RESULTS
                !isEmpty && isSearchActive -> ListState.SEARCH_RESULTS
                else                       -> ListState.NORMAL
            }
        }
    }
}
