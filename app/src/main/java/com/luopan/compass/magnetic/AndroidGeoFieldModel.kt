package com.luopan.compass.magnetic

import android.hardware.GeomagneticField
import com.luopan.compass.util.Clock

class AndroidGeoFieldModel(private val clock: Clock) : MagneticFieldModel {

    override fun getDeclination(latDeg: Double, lonDeg: Double, altM: Double, epochYears: Double): Float {
        val field = createField(latDeg, lonDeg, altM, epochYears)
        return field.declination
    }

    override fun getExpectedInclination(latDeg: Double, lonDeg: Double, altM: Double, epochYears: Double): Float {
        val field = createField(latDeg, lonDeg, altM, epochYears)
        return field.inclination
    }

    override fun getExpectedFieldMagnitude(latDeg: Double, lonDeg: Double, altM: Double, epochYears: Double): Float {
        val field = createField(latDeg, lonDeg, altM, epochYears)
        return field.fieldStrength
    }

    override fun isExpired(): Boolean = false  // Android model is always current

    override fun getModelId(): String = "AndroidGeoField"

    private fun createField(latDeg: Double, lonDeg: Double, altM: Double, epochYears: Double): GeomagneticField {
        // GeomagneticField takes altitude in meters, time in milliseconds since epoch
        val timeMs = epochYearsToMs(epochYears)
        return GeomagneticField(
            latDeg.toFloat(),
            lonDeg.toFloat(),
            altM.toFloat(),
            timeMs
        )
    }

    private fun epochYearsToMs(epochYears: Double): Long {
        // Convert fractional year to milliseconds since Unix epoch
        // epochYears=2025.0 corresponds to Jan 1, 2025 00:00:00 UTC
        val year = epochYears.toInt()
        val dayOfYear = ((epochYears - year) * 365.25).toLong()
        val jan1Ms = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            set(year, 0, 1, 0, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        return jan1Ms + dayOfYear * 86_400_000L
    }
}
