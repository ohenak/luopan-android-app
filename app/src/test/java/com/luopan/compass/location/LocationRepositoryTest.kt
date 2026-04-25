package com.luopan.compass.location

import android.content.SharedPreferences
import com.luopan.compass.util.FakeClock
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * In-memory fake for SharedPreferences — no Android framework required.
 */
class FakeSharedPreferences : SharedPreferences {

    private val map = mutableMapOf<String, Any?>()

    inner class FakeEditor : SharedPreferences.Editor {
        private val edits = mutableMapOf<String, Any?>()
        private var clearPending = false

        override fun putString(key: String, value: String?) = apply { edits[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { edits[key] = values }
        override fun putInt(key: String, value: Int) = apply { edits[key] = value }
        override fun putLong(key: String, value: Long) = apply { edits[key] = value }
        override fun putFloat(key: String, value: Float) = apply { edits[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { edits[key] = value }
        override fun remove(key: String) = apply { edits[key] = null }
        override fun clear() = apply { clearPending = true }

        override fun commit(): Boolean {
            flush()
            return true
        }

        override fun apply() {
            flush()
        }

        private fun flush() {
            if (clearPending) map.clear()
            edits.forEach { (k, v) ->
                if (v == null) map.remove(k) else map[k] = v
            }
        }
    }

    override fun getAll(): Map<String, *> = map.toMap()
    override fun getString(key: String, defValue: String?) = map.getOrDefault(key, defValue) as? String ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?) =
        @Suppress("UNCHECKED_CAST") (map.getOrDefault(key, defValues) as? Set<String>)?.toMutableSet() ?: defValues
    override fun getInt(key: String, defValue: Int) = (map.getOrDefault(key, defValue) as? Int) ?: defValue
    override fun getLong(key: String, defValue: Long) = (map.getOrDefault(key, defValue) as? Long) ?: defValue
    override fun getFloat(key: String, defValue: Float) = (map.getOrDefault(key, defValue) as? Float) ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = (map.getOrDefault(key, defValue) as? Boolean) ?: defValue
    override fun contains(key: String) = map.containsKey(key)
    override fun edit(): SharedPreferences.Editor = FakeEditor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
}

class LocationRepositoryTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var clock: FakeClock
    private lateinit var repo: LocationRepository

    companion object {
        private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000
    }

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        clock = FakeClock(currentMs = 1_000_000L)
        repo = LocationRepository(prefs, clock)
    }

    // --- GPS fix ---

    @Test
    fun `onGpsFix emits GpsFix with correct coordinates`() {
        repo.onGpsFix(lat = 37.7749, lon = -122.4194, altM = 15.0)

        val result = repo.location.value
        assertTrue("Expected GpsFix but was $result", result is LocationResult.GpsFix)
        result as LocationResult.GpsFix
        assertEquals(37.7749, result.lat, 1e-9)
        assertEquals(-122.4194, result.lon, 1e-9)
        assertEquals(15.0, result.altM, 1e-9)
    }

    @Test
    fun `resolveLocation returns GpsFix when active GPS fix is present`() {
        repo.onGpsFix(lat = 51.5074, lon = -0.1278, altM = 5.0)

        val result = repo.resolveLocation()
        assertTrue(result is LocationResult.GpsFix)
    }

    // --- No GPS, cache within 30 days ---

    @Test
    fun `resolveLocation returns CachedFix with correct age when cache is within 30 days`() {
        // Seed cache via a GPS fix at time=0
        clock.set(0L)
        val seedRepo = LocationRepository(prefs, clock)
        seedRepo.onGpsFix(lat = 48.8566, lon = 2.3522, altM = 35.0)

        // Advance 7 days, create fresh repo (simulate process restart)
        val sevenDaysMs = 7L * 24 * 60 * 60 * 1000
        clock.set(sevenDaysMs)
        val freshRepo = LocationRepository(prefs, clock)

        val result = freshRepo.resolveLocation()
        assertTrue("Expected CachedFix but was $result", result is LocationResult.CachedFix)
        result as LocationResult.CachedFix
        assertEquals(48.8566, result.lat, 1e-9)
        assertEquals(2.3522, result.lon, 1e-9)
        assertEquals(35.0, result.altM, 1e-9)
        assertEquals(sevenDaysMs, result.ageMs)
    }

    // --- Cache exactly at 30-day boundary (still valid) ---

    @Test
    fun `resolveLocation returns CachedFix when cache age is exactly 30 days`() {
        clock.set(0L)
        val seedRepo = LocationRepository(prefs, clock)
        seedRepo.onGpsFix(lat = 35.6762, lon = 139.6503, altM = 40.0)

        clock.set(THIRTY_DAYS_MS)
        val freshRepo = LocationRepository(prefs, clock)

        val result = freshRepo.resolveLocation()
        assertTrue("Expected CachedFix at exactly 30d but was $result", result is LocationResult.CachedFix)
        result as LocationResult.CachedFix
        assertEquals(THIRTY_DAYS_MS, result.ageMs)
    }

    // --- Cache at 30 days + 1ms (expired) ---

    @Test
    fun `resolveLocation returns Unavailable when cache age exceeds 30 days by 1ms`() {
        clock.set(0L)
        val seedRepo = LocationRepository(prefs, clock)
        seedRepo.onGpsFix(lat = 40.7128, lon = -74.0060, altM = 10.0)

        clock.set(THIRTY_DAYS_MS + 1L)
        val freshRepo = LocationRepository(prefs, clock)

        val result = freshRepo.resolveLocation()
        assertTrue("Expected Unavailable when cache is 30d+1ms old but was $result", result is LocationResult.Unavailable)
    }

    // --- No GPS, no cache ---

    @Test
    fun `resolveLocation returns Unavailable when no GPS and no cache`() {
        val result = repo.resolveLocation()
        assertTrue("Expected Unavailable but was $result", result is LocationResult.Unavailable)
    }

    @Test
    fun `location StateFlow starts as Unavailable`() {
        assertTrue(repo.location.value is LocationResult.Unavailable)
    }

    // --- Manual entry overrides cache ---

    @Test
    fun `setManualLocation overrides cache and emits ManualEntry`() {
        // Seed cache
        clock.set(0L)
        val seedRepo = LocationRepository(prefs, clock)
        seedRepo.onGpsFix(lat = 48.8566, lon = 2.3522, altM = 35.0)

        // New repo, cache valid but we set manual
        clock.set(1L)
        val freshRepo = LocationRepository(prefs, clock)
        freshRepo.setManualLocation(lat = 22.3193, lon = 114.1694)

        val result = freshRepo.location.value
        assertTrue("Expected ManualEntry but was $result", result is LocationResult.ManualEntry)
        result as LocationResult.ManualEntry
        assertEquals(22.3193, result.lat, 1e-9)
        assertEquals(114.1694, result.lon, 1e-9)
        assertEquals(0.0, result.altM, 1e-9) // default altitude
    }

    @Test
    fun `resolveLocation returns ManualEntry when manual is set and no active GPS fix`() {
        repo.setManualLocation(lat = 22.3193, lon = 114.1694)

        val result = repo.resolveLocation()
        assertTrue(result is LocationResult.ManualEntry)
    }

    // --- clearManualLocation falls back to cache ---

    @Test
    fun `clearManualLocation falls back to CachedFix when valid cache exists`() {
        // Seed cache via GPS fix
        clock.set(0L)
        val seedRepo = LocationRepository(prefs, clock)
        seedRepo.onGpsFix(lat = 48.8566, lon = 2.3522, altM = 35.0)

        // New instance with manual set, then cleared
        clock.set(1_000L)
        val freshRepo = LocationRepository(prefs, clock)
        freshRepo.setManualLocation(lat = 22.3193, lon = 114.1694)
        freshRepo.clearManualLocation()

        val result = freshRepo.location.value
        assertTrue("Expected CachedFix after clearManual but was $result", result is LocationResult.CachedFix)
    }

    @Test
    fun `clearManualLocation falls back to Unavailable when no cache exists`() {
        repo.setManualLocation(lat = 22.3193, lon = 114.1694)
        repo.clearManualLocation()

        val result = repo.location.value
        assertTrue("Expected Unavailable after clearManual with no cache but was $result", result is LocationResult.Unavailable)
    }

    // --- onGpsUnavailable ---

    @Test
    fun `onGpsUnavailable transitions GpsFix to CachedFix when cache is valid`() {
        // First get a GPS fix (which also writes to cache)
        repo.onGpsFix(lat = 37.7749, lon = -122.4194, altM = 15.0)
        assertTrue(repo.location.value is LocationResult.GpsFix)

        // Advance time slightly, then declare GPS unavailable
        clock.advance(1_000L)
        repo.onGpsUnavailable()

        val result = repo.location.value
        assertTrue("Expected CachedFix after gpsUnavailable but was $result", result is LocationResult.CachedFix)
    }

    @Test
    fun `onGpsUnavailable does not change state when already not GpsFix`() {
        // Start with no cache, no GPS → Unavailable
        repo.onGpsUnavailable()
        assertTrue(repo.location.value is LocationResult.Unavailable)
    }

    // --- Cache survives process restart ---

    @Test
    fun `GPS fix is persisted and readable by a new LocationRepository instance`() {
        repo.onGpsFix(lat = 55.7558, lon = 37.6173, altM = 144.0)

        // Simulate process restart: same prefs, same clock, new instance
        val restarted = LocationRepository(prefs, clock)
        val result = restarted.resolveLocation()

        // The new instance starts with state=Unavailable, but resolveLocation should find the cache
        assertTrue("Expected CachedFix from persisted prefs but was $result", result is LocationResult.CachedFix)
        result as LocationResult.CachedFix
        assertEquals(55.7558, result.lat, 1e-9)
        assertEquals(37.6173, result.lon, 1e-9)
        assertEquals(144.0, result.altM, 1e-9)
    }
}
