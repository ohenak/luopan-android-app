package com.luopan.compass.bearing

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

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
}
