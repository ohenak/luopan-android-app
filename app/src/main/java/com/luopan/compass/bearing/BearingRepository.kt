package com.luopan.compass.bearing

/**
 * Repository interface for [BearingRecord] persistence.
 *
 * TSPEC §3.5 — all operations are suspend functions.
 * The concrete implementation ([BearingRepositoryImpl]) is provided in P6.2.
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
}
