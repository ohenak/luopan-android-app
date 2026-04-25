package com.luopan.compass.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.luopan.compass.calibration.CalibrationRecord

@Dao
interface CalibrationDao {
    @Query("SELECT * FROM calibration_records WHERE id = 1")
    suspend fun getCurrent(): CalibrationRecord?

    @Query("SELECT * FROM calibration_records WHERE id = 2")
    suspend fun getRollback(): CalibrationRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: CalibrationRecord)

    @Query("DELETE FROM calibration_records WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("SELECT * FROM calibration_records")
    suspend fun getAll(): List<CalibrationRecord>
}
