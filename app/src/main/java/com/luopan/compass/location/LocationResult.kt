package com.luopan.compass.location

sealed class LocationResult {
    data class GpsFix(val lat: Double, val lon: Double, val altM: Double) : LocationResult()
    data class CachedFix(val lat: Double, val lon: Double, val altM: Double, val ageMs: Long) : LocationResult()
    data class ManualEntry(val lat: Double, val lon: Double, val altM: Double = 0.0) : LocationResult()
    object Unavailable : LocationResult()
}
