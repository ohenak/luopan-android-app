package com.luopan.compass.ui

import com.luopan.compass.bearing.BearingDao
import com.luopan.compass.bearing.BearingRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Recording fake implementation of [BearingDao] for unit testing [BearingHistoryViewModel].
 *
 * Tracks calls to [getAllFlow] and [searchFlow] so tests can assert invocation count and arguments.
 * Backed by an in-memory [MutableStateFlow<List<BearingRecord>>] for reactive emission control.
 */
class FakeBearingDao : BearingDao {

    private val _records = MutableStateFlow<List<BearingRecord>>(emptyList())

    /** Recorded arguments passed to searchFlow(), in order of invocation. */
    val searchFlowCalls = mutableListOf<String>()

    /** Number of times getAllFlow() was called. */
    var getAllFlowCallCount = 0
        private set

    fun setRecords(records: List<BearingRecord>) {
        _records.value = records
    }

    override fun getAllFlow(): Flow<List<BearingRecord>> {
        getAllFlowCallCount++
        return _records
    }

    override fun searchFlow(query: String): Flow<List<BearingRecord>> {
        searchFlowCalls.add(query)
        return _records.map { records ->
            records.filter { it.name.contains(query, ignoreCase = true) }
        }
    }

    override suspend fun insert(record: BearingRecord) {
        _records.value = _records.value + record
    }

    override suspend fun getAll(): List<BearingRecord> = _records.value

    override suspend fun count(): Int = _records.value.size

    override suspend fun delete(id: String) {
        _records.value = _records.value.filter { it.id != id }
    }
}
