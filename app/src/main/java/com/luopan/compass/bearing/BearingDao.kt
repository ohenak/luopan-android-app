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

    /** Reactive stream of all records, newest-first with rowid tiebreaker. Phase 4 — TSPEC §4.5 */
    @Query("SELECT * FROM bearing_records ORDER BY captured_at DESC, rowid DESC")
    fun getAllFlow(): Flow<List<BearingRecord>>

    /** Reactive stream filtered by name LIKE '%query%', same ordering. Phase 4 — TSPEC §4.5 */
    @Query("SELECT * FROM bearing_records WHERE name LIKE '%' || :query || '%' ORDER BY captured_at DESC, rowid DESC")
    fun searchFlow(query: String): Flow<List<BearingRecord>>

    @Query("DELETE FROM bearing_records WHERE id = :id")
    suspend fun delete(id: String)
}
