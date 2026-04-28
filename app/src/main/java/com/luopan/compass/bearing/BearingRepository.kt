package com.luopan.compass.bearing

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for [BearingRecord] persistence.
 *
 * TSPEC §3.5 — all suspend functions; TSPEC §4.5 — reactive Flow methods.
 */
interface BearingRepository {
    /** Inserts a new [BearingRecord]. */
    suspend fun insert(record: BearingRecord)

    /** Returns the total count of stored records. */
    suspend fun count(): Int

    /** Returns all records ordered by [BearingRecord.captured_at] descending. */
    suspend fun getAll(): List<BearingRecord>

    /** Deletes the record with the given [id]. No-op if the id does not exist. */
    suspend fun delete(id: String)

    /**
     * Returns a reactive stream of all records, newest-first.
     * Phase 4 — TSPEC §4.5
     */
    fun getAllFlow(): Flow<List<BearingRecord>>

    /**
     * Returns a reactive stream of records whose name contains [query] (case-insensitive).
     * An empty [query] matches all records.
     * Phase 4 — TSPEC §4.5
     */
    fun searchFlow(query: String): Flow<List<BearingRecord>>
}
