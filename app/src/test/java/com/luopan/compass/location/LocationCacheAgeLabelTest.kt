package com.luopan.compass.location

import com.luopan.compass.ui.CacheAgeLabelFormatter
import com.luopan.compass.util.FakeClock
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for cache age label formatting (AT-C).
 *
 * TSPEC §10.1 — LocationCacheAgeLabelTest:
 *   FakeClock set to exactly 15 × 86_400_000 ms after cache timestamp.
 *   Assert cache age label = "15 days ago" (floor division — no ±1 day tolerance).
 *
 * FSPEC §2.3 step 7 / PLAN §4 P8.2 success criteria.
 */
class LocationCacheAgeLabelTest {

    private lateinit var clock: FakeClock
    private lateinit var prefs: FakeSharedPreferences
    private lateinit var repo: LocationRepository

    private val cacheTimestampMs = 1_000_000_000L   // arbitrary fixed anchor point

    companion object {
        private const val MS_PER_DAY = 86_400_000L
    }

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        clock = FakeClock(currentMs = cacheTimestampMs)
        repo = LocationRepository(prefs, clock)

        // Seed a GPS fix so the cache timestamp is written into prefs
        repo.onGpsFix(lat = 40.0, lon = -105.0, altM = 0.0)
    }

    // ── AT-C: Floor-division label at exactly 15 days ────────────────────────

    /**
     * AT-C primary assertion: exactly 15 whole days after the cache timestamp.
     *
     * Floor division: floor(15 × 86_400_000 / 86_400_000) = 15.
     * No rounding, no ±1 tolerance.
     */
    @Test
    fun `cache age label is 15 days ago when exactly 15 days have elapsed`() {
        val elapsedMs = 15L * MS_PER_DAY

        val ageLabel = CacheAgeLabelFormatter.format(elapsedMs)

        assertEquals("15 days ago", ageLabel)
    }

    /**
     * CachedFix.ageMs at exactly 15 days resolves to floor-divided label of "15 days ago".
     *
     * Wires CacheAgeLabelFormatter to an actual CachedFix obtained from LocationRepository
     * so that the end-to-end data flow is exercised.
     */
    @Test
    fun `CachedFix ageMs at exactly 15 days produces 15 days ago label via formatter`() {
        // Advance clock exactly 15 days past the cache timestamp
        clock.advance(15L * MS_PER_DAY)
        val freshRepo = LocationRepository(prefs, clock)

        val result = freshRepo.resolveLocation()
        val cachedFix = result as LocationResult.CachedFix

        val label = CacheAgeLabelFormatter.format(cachedFix.ageMs)
        assertEquals("15 days ago", label)
    }

    // ── Edge cases: floor division is exact ──────────────────────────────────

    /**
     * One millisecond less than a full day → "0 days ago" (floor division rounds down).
     */
    @Test
    fun `cache age label is 0 days ago when elapsed is one millisecond less than a full day`() {
        val elapsedMs = MS_PER_DAY - 1L
        assertEquals("0 days ago", CacheAgeLabelFormatter.format(elapsedMs))
    }

    /**
     * Exactly one day → "1 days ago".
     */
    @Test
    fun `cache age label is 1 days ago when exactly one day has elapsed`() {
        assertEquals("1 days ago", CacheAgeLabelFormatter.format(MS_PER_DAY))
    }

    /**
     * One millisecond past exactly one day → still "1 days ago" (floor division).
     */
    @Test
    fun `cache age label is 1 days ago when one day plus 1 ms have elapsed`() {
        assertEquals("1 days ago", CacheAgeLabelFormatter.format(MS_PER_DAY + 1L))
    }

    /**
     * Zero elapsed → "0 days ago".
     */
    @Test
    fun `cache age label is 0 days ago for zero elapsed`() {
        assertEquals("0 days ago", CacheAgeLabelFormatter.format(0L))
    }

    /**
     * Exactly 30 days → "30 days ago" (the outer boundary of the cache validity window).
     */
    @Test
    fun `cache age label is 30 days ago at cache validity boundary of exactly 30 days`() {
        val thirtyDays = 30L * MS_PER_DAY
        assertEquals("30 days ago", CacheAgeLabelFormatter.format(thirtyDays))
    }
}
