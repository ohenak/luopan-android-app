package com.luopan.compass.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.luopan.compass.bearing.BearingDao
import com.luopan.compass.bearing.BearingRecord
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/**
 * Fragment-scoped ViewModel for [BearingHistoryFragment].
 *
 * Owns:
 * - Reactive list display via [bearingList] (debounced search or full list)
 * - Search query state via [searchQuery]
 * - In-memory pending-undo state for swipe-to-delete
 *
 * Fragment-scoped (not activityViewModels) so search query is cleared when
 * the Fragment is destroyed on tab navigation (TSPEC §6.3).
 *
 * Phase 4 — PLAN E-4; TSPEC §6.3
 */
class BearingHistoryViewModel(
    private val dao: BearingDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    // ─── Search query state ─────────────────────────────────────────────────────

    /** Current search query. Empty string = no active search. */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // ─── Bearing list Flow ──────────────────────────────────────────────────────

    /**
     * The active list Flow: switches between getAllFlow() and searchFlow(query) based on query.
     *
     * Empty query → getAllFlow() immediately (0 ms debounce — immediate restore on clear).
     * Non-empty query → searchFlow(debounced query) after 300 ms idle.
     *
     * Debounce strategy: `debounce { query -> if (query.isEmpty()) 0L else 300L }` in a single
     * operator chain avoids a conditional flatMapLatest split (TSPEC §6.3).
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val bearingList: Flow<List<BearingRecord>> = _searchQuery
        .debounce { query -> if (query.isEmpty()) 0L else 300L }
        .flatMapLatest { query ->
            if (query.isEmpty()) dao.getAllFlow()
            else dao.searchFlow(query)
        }

    // ─── Search query setter ────────────────────────────────────────────────────

    /**
     * Updates the search query.
     *
     * Non-empty query: debounced 300 ms before triggering [dao.searchFlow].
     * Empty query: immediately switches back to [dao.getAllFlow] (0 ms debounce).
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // ─── Pending undo state machine ─────────────────────────────────────────────

    /** In-memory pending undo record (null = no active undo). NOT persisted. */
    private var pendingUndo: BearingRecord? = null

    /**
     * Deletes the record immediately from the DAO and saves it as the pending undo record.
     *
     * If a prior undo is pending, it is replaced — prior deletion becomes permanent.
     * Single active undo: second swipe cancels first (TSPEC §7.3).
     */
    fun deleteRecord(record: BearingRecord) {
        pendingUndo = record
        viewModelScope.launch(ioDispatcher) {
            dao.delete(record.id)
        }
    }

    /**
     * Re-inserts the pending undo record. Clears the pending undo state.
     * No-op if no undo is pending.
     */
    fun undoDelete() {
        val record = pendingUndo ?: return
        pendingUndo = null
        viewModelScope.launch(ioDispatcher) {
            dao.insert(record)
        }
    }

    /**
     * Clears the pending undo without restoring the record.
     * Called when the Snackbar times out or is dismissed without tapping "Undo".
     */
    fun commitDelete() {
        pendingUndo = null
    }

    /** Returns true when a record is awaiting a potential undo. */
    fun hasPendingUndo(): Boolean = pendingUndo != null

    // ─── Factory ────────────────────────────────────────────────────────────────

    class Factory(
        private val dao: BearingDao,
        private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == BearingHistoryViewModel::class.java) {
                "Factory only creates BearingHistoryViewModel"
            }
            return BearingHistoryViewModel(dao, ioDispatcher) as T
        }
    }

    // ─── Companion: pure functions testable without Android runtime ──────────────

    companion object {

        /**
         * Computes the visibility state for the bearing history list UI.
         *
         * Four branches (TSPEC §7.1, PLAN E-2a):
         * A. records.isEmpty && !isSearchActive → State A (zero bearings)
         * B. records.isEmpty && isSearchActive  → State B (no search results)
         * C. !records.isEmpty (any query)       → normal list
         *
         * @param records The current list of bearing records.
         * @param query   The current search query (empty = not active).
         * @return [ListState] encoding visibility decisions for each UI element.
         */
        fun computeListState(records: List<*>, query: String): ListState {
            val isEmpty = records.isEmpty()
            val isSearchActive = query.isNotEmpty()
            return when {
                isEmpty && !isSearchActive -> ListState(
                    searchBarVisible     = false,
                    recyclerViewVisible  = false,
                    emptyNoRecordsVisible = true,
                    emptyNoResultsVisible = false
                )
                isEmpty && isSearchActive -> ListState(
                    searchBarVisible     = true,
                    recyclerViewVisible  = false,
                    emptyNoRecordsVisible = false,
                    emptyNoResultsVisible = true
                )
                else -> ListState(
                    searchBarVisible     = true,
                    recyclerViewVisible  = true,
                    emptyNoRecordsVisible = false,
                    emptyNoResultsVisible = false
                )
            }
        }
    }

    /**
     * Visibility state for all four UI elements in the bearing history list.
     * Returned by [computeListState] — a pure function testable on the JVM.
     */
    data class ListState(
        val searchBarVisible: Boolean,
        val recyclerViewVisible: Boolean,
        val emptyNoRecordsVisible: Boolean,
        val emptyNoResultsVisible: Boolean
    )
}
