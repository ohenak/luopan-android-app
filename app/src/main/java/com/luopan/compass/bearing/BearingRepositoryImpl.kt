package com.luopan.compass.bearing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Room-backed implementation of [BearingRepository].
 *
 * TSPEC §3.5 — wraps [BearingDao]; all operations run on [Dispatchers.IO].
 * Does NOT own encryption setup — that remains in [LuopanDatabase] / [DatabaseKeyManager].
 *
 * @param dao The Room DAO injected by the caller (typically obtained from [LuopanDatabase]).
 */
class BearingRepositoryImpl(private val dao: BearingDao) : BearingRepository {

    /**
     * Inserts a new [BearingRecord] atomically. Suspends until the insert completes.
     * Throws on constraint violation or storage failure (caller handles).
     */
    override suspend fun insert(record: BearingRecord) = withContext(Dispatchers.IO) {
        dao.insert(record)
    }

    /**
     * Returns the total count of records in bearing_records.
     * Used to compute the default capture name "Bearing [N+1]".
     */
    override suspend fun count(): Int = withContext(Dispatchers.IO) {
        dao.count()
    }

    /**
     * Returns all [BearingRecord]s ordered by [BearingRecord.captured_at] descending.
     * Used in Phase 4 for the history screen.
     */
    override suspend fun getAll(): List<BearingRecord> = withContext(Dispatchers.IO) {
        dao.getAll()
    }

    /**
     * Deletes a single record by id. No-op if the id does not exist.
     * Used in Phase 4.
     */
    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dao.delete(id)
    }
}
