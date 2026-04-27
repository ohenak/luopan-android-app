package com.luopan.compass.calibration

import com.luopan.compass.db.CalibrationDao
import com.luopan.compass.model.CalibrationQuality

class CalibrationRepository(private val dao: CalibrationDao) {

    suspend fun getCurrent(): CalibrationRecord? = dao.getCurrent()

    suspend fun getRollback(): CalibrationRecord? = dao.getRollback()

    suspend fun save(result: CalibrationResult, timestamp: Long) {
        val existing = dao.getCurrent()
        if (existing != null) {
            dao.upsert(existing.copy(id = 2))  // promote current to rollback
        }
        dao.upsert(toRecord(result, id = 1, timestamp = timestamp))
    }

    suspend fun rollback(): Boolean {
        val rollback = dao.getRollback() ?: return false
        dao.upsert(rollback.copy(id = 1))
        dao.delete(2)
        return true
    }

    fun toRecord(result: CalibrationResult, id: Int, timestamp: Long): CalibrationRecord {
        val si = result.softIron
        return CalibrationRecord(
            id = id,
            recorded_at = timestamp,
            hard_iron_x = result.hardIron[0],
            hard_iron_y = result.hardIron[1],
            hard_iron_z = result.hardIron[2],
            soft_iron_00 = si[0][0], soft_iron_01 = si[0][1], soft_iron_02 = si[0][2],
            soft_iron_10 = si[1][0], soft_iron_11 = si[1][1], soft_iron_12 = si[1][2],
            soft_iron_20 = si[2][0], soft_iron_21 = si[2][1], soft_iron_22 = si[2][2],
            quality = result.quality.name,
            expected_field_ut = result.sphereRadius_uT
        )
    }

    fun fromRecord(record: CalibrationRecord): CalibrationResult {
        val hardIron = floatArrayOf(record.hard_iron_x, record.hard_iron_y, record.hard_iron_z)
        val softIron = Array(3) { i ->
            when (i) {
                0 -> floatArrayOf(record.soft_iron_00, record.soft_iron_01, record.soft_iron_02)
                1 -> floatArrayOf(record.soft_iron_10, record.soft_iron_11, record.soft_iron_12)
                else -> floatArrayOf(record.soft_iron_20, record.soft_iron_21, record.soft_iron_22)
            }
        }
        return CalibrationResult(
            hardIron = hardIron,
            softIron = softIron,
            residualMicroTesla = 0f,
            coverageScore = 0f,
            quality = CalibrationQuality.valueOf(record.quality)
        )
    }
}
