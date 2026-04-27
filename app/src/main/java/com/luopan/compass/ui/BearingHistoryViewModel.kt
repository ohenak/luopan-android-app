package com.luopan.compass.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.luopan.compass.bearing.BearingDao
import com.luopan.compass.bearing.BearingRecord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

class BearingHistoryViewModel(private val dao: BearingDao) : ViewModel() {

    // Search query state — retained across configuration changes within the same Fragment lifecycle
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // In-memory pending undo record (null = no active undo)
    private var pendingUndo: BearingRecord? = null

    /**
     * The active list Flow: switches between getAllFlow() and searchFlow(query) based on query state.
     *
     * Empty query → getAllFlow() immediately (no debounce).
     * Non-empty query → searchFlow(debounced query) after 300 ms idle.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val bearingList: Flow<List<BearingRecord>> = _searchQuery
        .debounce { query -> if (query.isEmpty()) 0L else 300L }
        .flatMapLatest { query ->
            if (query.isEmpty()) dao.getAllFlow()
            else dao.searchFlow(query)
        }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Deletes the record immediately and saves it as the pending undo record.
     * If a prior undo is pending, it is replaced (prior deletion is permanent).
     */
    fun deleteRecord(record: BearingRecord) {
        pendingUndo = record
        viewModelScope.launch(Dispatchers.IO) {
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
        viewModelScope.launch(Dispatchers.IO) {
            dao.insert(record)
        }
    }

    /** Clears the pending undo without restoring. Called when Snackbar times out. */
    fun commitDelete() {
        pendingUndo = null
    }

    fun hasPendingUndo(): Boolean = pendingUndo != null

    class Factory(private val dao: BearingDao) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == BearingHistoryViewModel::class.java)
            return BearingHistoryViewModel(dao) as T
        }
    }
}
