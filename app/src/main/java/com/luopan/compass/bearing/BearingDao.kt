package com.luopan.compass.bearing

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BearingDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: BearingRecord)

    @Query("SELECT COUNT(*) FROM bearing_records")
    suspend fun count(): Int

    @Query("SELECT * FROM bearing_records ORDER BY captured_at DESC")
    suspend fun getAll(): List<BearingRecord>

    @Query("DELETE FROM bearing_records WHERE id = :id")
    suspend fun delete(id: String)

    /**
     * Returns a reactive stream of all bearing records, sorted newest-first.
     * Secondary sort by rowid provides a deterministic tiebreaker for same-millisecond inserts.
     *
     * Phase 4 — TSPEC §4.5
     */
    @Query("SELECT * FROM bearing_records ORDER BY captured_at DESC, rowid DESC")
    fun getAllFlow(): Flow<List<BearingRecord>>

    /**
     * Returns a reactive stream of bearing records whose name contains [query] (case-insensitive).
     * An empty [query] matches all records.
     *
     * Phase 4 — TSPEC §4.5
     */
    @Query("SELECT * FROM bearing_records WHERE name LIKE '%' || :query || '%' ORDER BY captured_at DESC, rowid DESC")
    fun searchFlow(query: String): Flow<List<BearingRecord>>
}
