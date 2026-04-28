package com.luopan.compass.ui

import androidx.lifecycle.SavedStateHandle
import com.luopan.compass.R
import com.luopan.compass.bearing.BearingRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [BearingHistoryViewModel].
 *
 * Tests:
 * - E-2:  AT-HIST-02-A (substring match, fake DAO)
 * - E-2:  AT-HIST-02-B (debounce — no call at 200 ms, one call at 500 ms)
 * - E-2:  AT-HIST-02-C (timer restart on keystroke)
 * - E-2:  AT-HIST-02-D (immediate restore on empty query)
 * - E-2a: updateListState() four-branch visibility logic (pure function)
 * - E-3:  AT-UNDO-VM-01 (deleteRecord sets pendingUndo)
 * - E-3:  AT-UNDO-VM-02 (undoDelete clears and re-inserts)
 * - E-3:  AT-UNDO-VM-03 (commitDelete clears without re-inserting)
 *
 * Dispatcher setup per PROPERTIES §1.4 (SE PROPERTIES-v2 F-02):
 * Dispatchers.setMain(testDispatcher) in @Before; Dispatchers.resetMain() in @After.
 * This makes viewModelScope use virtual time so debounce operators are controllable.
 *
 * The ViewModel is constructed with [testDispatcher] as its IO dispatcher so that
 * viewModelScope.launch(ioDispatcher) coroutines run on the virtual test clock
 * and are drained by advanceUntilIdle(). This makes IO effects deterministic in tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BearingHistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private fun makeBearingRecord(
        id: String = "test-id",
        name: String = "Test Bearing",
        bearingDeg: Float = 45.0f,
        capturedAt: Long = 1_700_000_000_000L,
        interferenceFlag: Boolean = false,
        fieldDeviationPct: Float = 0.0f,
        inclinationDeviationDeg: Float = 0.0f
    ) = BearingRecord(
        id = id,
        name = name,
        bearing_deg = bearingDeg,
        north_type = "TRUE",
        confidence = "HIGH",
        captured_at = capturedAt,
        calibration_version = "WMM2025",
        field_deviation_pct = fieldDeviationPct,
        inclination_deviation_deg = inclinationDeviationDeg,
        interference_flag = interferenceFlag,
        lat = null,
        lon = null,
        alt_m = null,
        notes = null,
        display_mode = "MODERN"
    )

    // ─── E-2: AT-HIST-02-A — Substring match, fake DAO ─────────────────────────

    @Test
    fun `AT-HIST-02-A substring match returns matching records via fake DAO`() = runTest {
        val fakeDao = FakeBearingDao()
        fakeDao.setRecords(
            listOf(
                makeBearingRecord(id = "id-1", name = "North Gate"),
                makeBearingRecord(id = "id-2", name = "South Pole"),
                makeBearingRecord(id = "id-3", name = "northeast corner")
            )
        )
        val viewModel = BearingHistoryViewModel(dao = fakeDao, ioDispatcher = testDispatcher)

        // Start collecting the bearingList flow BEFORE setting the query
        // (flatMapLatest requires a subscriber to activate the debounce chain)
        val collected = mutableListOf<List<BearingRecord>>()
        val collectJob = launch { viewModel.bearingList.collect { collected.add(it) } }

        viewModel.setSearchQuery("north")
        advanceTimeBy(300L)
        advanceUntilIdle()

        collectJob.cancel()

        // The last emission should contain only matching records
        val list = collected.lastOrNull() ?: emptyList()
        val names = list.map { it.name }
        assertTrue("North Gate should match", names.contains("North Gate"))
        assertTrue("northeast corner should match (case-insensitive)", names.contains("northeast corner"))
        assertFalse("South Pole should not match", names.contains("South Pole"))
    }

    // ─── E-2: AT-HIST-02-B — Debounce: no call at 200 ms, one call at 500 ms ──

    @Test
    fun `AT-HIST-02-B no searchFlow call at 200 ms, exactly one call at 500 ms total`() = runTest {
        val fakeDao = FakeBearingDao()
        val viewModel = BearingHistoryViewModel(dao = fakeDao, ioDispatcher = testDispatcher)

        // Must collect bearingList to activate the debounce chain
        val collectJob = launch { viewModel.bearingList.collect {} }
        advanceUntilIdle()  // let the initial getAllFlow subscription start

        viewModel.setSearchQuery("nor")

        // At 200 ms (< 300 ms debounce), no searchFlow call should have been made
        advanceTimeBy(200L)
        assertEquals(
            "searchFlow must not be called before 300 ms debounce elapses",
            0,
            fakeDao.searchFlowCalls.size
        )

        // Advance 300 more ms to trigger debounce (total 500 ms)
        advanceTimeBy(300L)
        advanceUntilIdle()
        assertEquals(
            "exactly one searchFlow call should occur after 300 ms debounce",
            1,
            fakeDao.searchFlowCalls.size
        )
        assertEquals("nor", fakeDao.searchFlowCalls[0])

        collectJob.cancel()
    }

    // ─── E-2: AT-HIST-02-C — Timer restart on keystroke ────────────────────────

    @Test
    fun `AT-HIST-02-C debounce timer restarts on each keystroke`() = runTest {
        val fakeDao = FakeBearingDao()
        val viewModel = BearingHistoryViewModel(dao = fakeDao, ioDispatcher = testDispatcher)

        // Must collect bearingList to activate the debounce chain
        val collectJob = launch { viewModel.bearingList.collect {} }
        advanceUntilIdle()  // let initial subscription start

        // First keystroke at t=0
        viewModel.setSearchQuery("n")

        // Second keystroke at t=250 ms (restarts 300 ms timer)
        advanceTimeBy(250L)
        viewModel.setSearchQuery("no")

        // At t=300 ms from first keystroke: timer restarted, no call expected
        advanceTimeBy(50L)  // t=300 ms total from first keystroke (only 50 ms since second)
        assertEquals(
            "no searchFlow call at 300 ms from first keystroke when second keystroke at 250 ms",
            0,
            fakeDao.searchFlowCalls.size
        )

        // Advance to t=550 ms (300 ms after second keystroke)
        advanceTimeBy(250L)  // t=550 ms total
        advanceUntilIdle()
        assertEquals(
            "exactly one searchFlow call fires at 550 ms (300 ms after second keystroke)",
            1,
            fakeDao.searchFlowCalls.size
        )
        assertEquals("no", fakeDao.searchFlowCalls[0])

        collectJob.cancel()
    }

    // ─── E-2: AT-HIST-02-D — Immediate restore on empty query ──────────────────

    @Test
    fun `AT-HIST-02-D immediate restore on empty query without debounce`() = runTest {
        val fakeDao = FakeBearingDao()
        fakeDao.setRecords(
            listOf(
                makeBearingRecord(id = "id-1", name = "Alpha"),
                makeBearingRecord(id = "id-2", name = "north")
            )
        )
        val viewModel = BearingHistoryViewModel(dao = fakeDao, ioDispatcher = testDispatcher)

        // Collect bearingList to activate the debounce chain
        val collected = mutableListOf<List<BearingRecord>>()
        val collectJob = launch { viewModel.bearingList.collect { collected.add(it) } }
        advanceUntilIdle()

        // Activate search — let debounce fire
        viewModel.setSearchQuery("north")
        advanceTimeBy(300L)
        advanceUntilIdle()
        val searchCallCountAfterSearch = fakeDao.searchFlowCalls.size

        // Clear query — must restore immediately (0 ms debounce)
        viewModel.setSearchQuery("")
        // DO NOT advance time — just drain pending coroutines
        advanceUntilIdle()

        // First emission of bearingList after clearing must return full list
        val lastList = viewModel.bearingList.first()
        collectJob.cancel()

        assertEquals("full list restored after clearing query", 2, lastList.size)
        // searchFlow should not have been called for the clear event
        assertEquals(
            "searchFlow must not be called on clear event",
            searchCallCountAfterSearch,
            fakeDao.searchFlowCalls.size
        )
    }

    // ─── E-2a: updateListState() four-branch visibility logic ───────────────────

    /**
     * ListState encodes the visibility decisions of updateListState().
     * This pure data class mirrors BearingHistoryViewModel.ListState for local assertions.
     */
    data class ListStateResult(
        val searchBarVisible: Boolean,
        val recyclerViewVisible: Boolean,
        val emptyNoRecordsVisible: Boolean,
        val emptyNoResultsVisible: Boolean
    )

    /**
     * Calls the pure function under test.
     * Maps BearingHistoryViewModel.computeListState() output to ListStateResult.
     */
    private fun computeListState(
        records: List<BearingRecord>,
        query: String
    ): ListStateResult {
        val state = BearingHistoryViewModel.computeListState(records, query)
        return ListStateResult(
            searchBarVisible = state.searchBarVisible,
            recyclerViewVisible = state.recyclerViewVisible,
            emptyNoRecordsVisible = state.emptyNoRecordsVisible,
            emptyNoResultsVisible = state.emptyNoResultsVisible
        )
    }

    @Test
    fun `updateListState branch A - zero records and no search - shows only empty-no-records`() {
        val state = computeListState(records = emptyList(), query = "")
        assertFalse("State A: search bar must be GONE", state.searchBarVisible)
        assertFalse("State A: RecyclerView must be GONE", state.recyclerViewVisible)
        assertTrue("State A: emptyNoRecords must be VISIBLE", state.emptyNoRecordsVisible)
        assertFalse("State A: emptyNoResults must be GONE", state.emptyNoResultsVisible)
    }

    @Test
    fun `updateListState branch B - zero records and active search - shows only empty-no-results`() {
        val state = computeListState(records = emptyList(), query = "xyz")
        assertTrue("State B: search bar must be VISIBLE", state.searchBarVisible)
        assertFalse("State B: RecyclerView must be GONE", state.recyclerViewVisible)
        assertFalse("State B: emptyNoRecords must be GONE", state.emptyNoRecordsVisible)
        assertTrue("State B: emptyNoResults must be VISIBLE", state.emptyNoResultsVisible)
    }

    @Test
    fun `updateListState branch C - records present and no search - shows list and search bar`() {
        val records = listOf(makeBearingRecord())
        val state = computeListState(records = records, query = "")
        assertTrue("Normal list: search bar must be VISIBLE", state.searchBarVisible)
        assertTrue("Normal list: RecyclerView must be VISIBLE", state.recyclerViewVisible)
        assertFalse("Normal list: emptyNoRecords must be GONE", state.emptyNoRecordsVisible)
        assertFalse("Normal list: emptyNoResults must be GONE", state.emptyNoResultsVisible)
    }

    @Test
    fun `updateListState branch D - records present and active search - shows list`() {
        val records = listOf(makeBearingRecord(name = "North Gate"))
        val state = computeListState(records = records, query = "north")
        assertTrue("Search results: search bar must be VISIBLE", state.searchBarVisible)
        assertTrue("Search results: RecyclerView must be VISIBLE", state.recyclerViewVisible)
        assertFalse("Search results: emptyNoRecords must be GONE", state.emptyNoRecordsVisible)
        assertFalse("Search results: emptyNoResults must be GONE", state.emptyNoResultsVisible)
    }

    // ─── E-3: AT-UNDO-VM-01 — deleteRecord sets pendingUndo ───────────────────

    @Test
    fun `AT-UNDO-VM-01 deleteRecord sets pendingUndo to the deleted record`() = runTest {
        val fakeDao = FakeBearingDao()
        val record = makeBearingRecord(id = "r1", name = "Test")
        fakeDao.setRecords(listOf(record))
        val viewModel = BearingHistoryViewModel(dao = fakeDao, ioDispatcher = testDispatcher)

        viewModel.deleteRecord(record)
        // pendingUndo is set synchronously before the IO coroutine launches
        assertTrue("hasPendingUndo() must return true after deleteRecord()", viewModel.hasPendingUndo())
    }

    // ─── E-3: AT-UNDO-VM-02 — undoDelete clears and re-inserts ────────────────

    @Test
    fun `AT-UNDO-VM-02 undoDelete clears pendingUndo and re-inserts the record`() = runTest {
        val fakeDao = FakeBearingDao()
        val record = makeBearingRecord(id = "r1", name = "Test")
        fakeDao.setRecords(listOf(record))
        val viewModel = BearingHistoryViewModel(dao = fakeDao, ioDispatcher = testDispatcher)

        viewModel.deleteRecord(record)
        assertTrue(viewModel.hasPendingUndo())

        viewModel.undoDelete()
        // pendingUndo is cleared synchronously
        assertFalse("hasPendingUndo() must return false after undoDelete()", viewModel.hasPendingUndo())

        // FakeBearingDao.insert() will be called by the IO coroutine.
        // Give the real IO thread time to complete (FakeBearingDao is a pure in-memory op).
        advanceUntilIdle()
        // Poll the FakeBearingDao records directly (synchronous property)
        // after a brief real-time wait via testScheduler advancement
        val finalRecords = fakeDao.records
        assertTrue(
            "record must be re-inserted after undoDelete() (IO coroutine must complete)",
            finalRecords.any { it.id == record.id }
        )
        // PROP-HIST-048: insert() must have been called exactly once
        assertEquals(
            "PROP-HIST-048: FakeBearingDao.insertCallCount must be 1 after undoDelete()",
            1,
            fakeDao.insertCallCount
        )
    }

    // ─── E-3: AT-UNDO-VM-03 — commitDelete clears without re-inserting ─────────

    @Test
    fun `AT-UNDO-VM-03 commitDelete clears pendingUndo without re-inserting the record`() = runTest {
        val fakeDao = FakeBearingDao()
        val record = makeBearingRecord(id = "r1", name = "Test")
        fakeDao.setRecords(listOf(record))
        val viewModel = BearingHistoryViewModel(dao = fakeDao, ioDispatcher = testDispatcher)

        viewModel.deleteRecord(record)
        assertTrue(viewModel.hasPendingUndo())

        viewModel.commitDelete()
        // commitDelete is synchronous — just sets pendingUndo = null
        assertFalse("hasPendingUndo() must return false after commitDelete()", viewModel.hasPendingUndo())

        // The delete IO coroutine was launched by deleteRecord. After advanceUntilIdle,
        // the record should be gone from fakeDao (IO coroutine ran delete).
        advanceUntilIdle()
        assertFalse(
            "record must NOT be restored after commitDelete() — deletion should be permanent",
            fakeDao.records.any { it.id == record.id }
        )
        // PROP-HIST-049: insert() must NOT have been called
        assertEquals(
            "PROP-HIST-049: FakeBearingDao.insertCallCount must be 0 after commitDelete()",
            0,
            fakeDao.insertCallCount
        )
    }

    // ─── E-3: second swipe cancels first undo (pendingUndo replacement) ─────────

    @Test
    fun `second deleteRecord replaces first pendingUndo - first deletion is permanent`() = runTest {
        val fakeDao = FakeBearingDao()
        val record1 = makeBearingRecord(id = "r1", name = "First")
        val record2 = makeBearingRecord(id = "r2", name = "Second")
        fakeDao.setRecords(listOf(record1, record2))
        val viewModel = BearingHistoryViewModel(dao = fakeDao, ioDispatcher = testDispatcher)

        viewModel.deleteRecord(record1)
        assertTrue(viewModel.hasPendingUndo())

        // Second swipe — replaces first pendingUndo
        viewModel.deleteRecord(record2)
        advanceUntilIdle()

        // Only second record is recoverable (pendingUndo now points to record2)
        assertTrue("pendingUndo should hold the second deleted record", viewModel.hasPendingUndo())

        viewModel.undoDelete()
        advanceUntilIdle()

        // After undo, record2 is restored but record1 is permanently gone
        assertFalse("first record must remain deleted (deletion is permanent)", fakeDao.records.any { it.id == "r1" })
        assertTrue("second record must be restored by undo", fakeDao.records.any { it.id == "r2" })
    }

    // ─── PROP-HIST-036: new ViewModel instance exposes empty searchQuery ───────

    @Test
    fun `PROP-HIST-036 new BearingHistoryViewModel has empty searchQuery`() {
        val viewModel = BearingHistoryViewModel(dao = FakeBearingDao(), ioDispatcher = testDispatcher)
        assertEquals(
            "PROP-HIST-036: new ViewModel must start with empty searchQuery",
            "",
            viewModel.searchQuery.value
        )
    }

    // ─── PROP-HIST-062: distinct empty-state string resources ─────────────────

    @Test
    fun `PROP-HIST-062 empty state string resources are distinct`() {
        assertTrue(
            "PROP-HIST-062: R.string.empty_no_bearings and R.string.empty_no_results must be distinct resources",
            R.string.empty_no_bearings != R.string.empty_no_results
        )
    }

    // ─── PROP-HIST-035: search query retained after ViewModel recreation via SavedStateHandle ───

    @Test
    fun `PROP-HIST-035 search query retained after ViewModel recreation via SavedStateHandle`() {
        val savedStateHandle = SavedStateHandle()
        val viewModel1 = BearingHistoryViewModel(dao = FakeBearingDao(), savedStateHandle = savedStateHandle, ioDispatcher = testDispatcher)
        viewModel1.setSearchQuery("north")
        assertEquals("north", viewModel1.searchQuery.value)
        // Simulate config change: same SavedStateHandle, new ViewModel instance
        val viewModel2 = BearingHistoryViewModel(dao = FakeBearingDao(), savedStateHandle = savedStateHandle, ioDispatcher = testDispatcher)
        assertEquals(
            "PROP-HIST-035: searchQuery must be retained across ViewModel recreation via SavedStateHandle",
            "north",
            viewModel2.searchQuery.value
        )
    }
}
