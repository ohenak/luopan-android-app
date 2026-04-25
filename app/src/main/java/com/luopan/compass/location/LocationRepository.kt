package com.luopan.compass.location

import android.content.SharedPreferences
import com.luopan.compass.util.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocationRepository(
    private val prefs: SharedPreferences,
    private val clock: Clock
) {
    companion object {
        private const val KEY_LAT = "cache_lat"
        private const val KEY_LON = "cache_lon"
        private const val KEY_ALT = "cache_alt"
        private const val KEY_TIMESTAMP = "cache_timestamp_ms"
        private const val CACHE_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
    }

    private val _location = MutableStateFlow<LocationResult>(LocationResult.Unavailable)
    val location: StateFlow<LocationResult> = _location.asStateFlow()

    private var manualLocation: LocationResult.ManualEntry? = null

    /** Called when a fresh GPS fix arrives. Stores in cache and updates state. */
    fun onGpsFix(lat: Double, lon: Double, altM: Double) {
        prefs.edit()
            .putLong(KEY_LAT, java.lang.Double.doubleToRawLongBits(lat))
            .putLong(KEY_LON, java.lang.Double.doubleToRawLongBits(lon))
            .putLong(KEY_ALT, java.lang.Double.doubleToRawLongBits(altM))
            .putLong(KEY_TIMESTAMP, clock.nowMs())
            .apply()
        _location.value = LocationResult.GpsFix(lat, lon, altM)
    }

    /** Sets a manual coordinate entry. altM defaults to 0.0 (sea level) when caller has no elevation data. */
    fun setManualLocation(lat: Double, lon: Double, altM: Double = 0.0) {
        manualLocation = LocationResult.ManualEntry(lat, lon, altM)
        _location.value = manualLocation!!
    }

    /** Clears manual entry (fall back to GPS/cache). */
    fun clearManualLocation() {
        manualLocation = null
        _location.value = resolveFromCache()
    }

    /** Returns best available location result. */
    fun resolveLocation(): LocationResult {
        return when {
            _location.value is LocationResult.GpsFix -> _location.value
            manualLocation != null -> manualLocation!!
            else -> resolveFromCache()
        }
    }

    private fun resolveFromCache(): LocationResult {
        val timestamp = prefs.getLong(KEY_TIMESTAMP, -1L)
        if (timestamp == -1L) return LocationResult.Unavailable

        val ageMs = clock.nowMs() - timestamp
        if (ageMs > CACHE_MAX_AGE_MS) return LocationResult.Unavailable  // > 30 days

        val lat = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LAT, 0L))
        val lon = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LON, 0L))
        val alt = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_ALT, 0L))
        return LocationResult.CachedFix(lat, lon, alt, ageMs)
    }

    /** Resets GPS state (no active fix). Falls back to cache/manual. */
    fun onGpsUnavailable() {
        if (_location.value is LocationResult.GpsFix) {
            _location.value = resolveFromCache()
        }
    }
}
