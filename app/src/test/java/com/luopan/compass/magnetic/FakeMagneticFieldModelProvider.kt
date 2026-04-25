package com.luopan.compass.magnetic

import com.luopan.compass.util.FakeClock

/**
 * Test double for [MagneticFieldModelProvider].
 *
 * Allows injection of any [MagneticFieldModel] as the active model without requiring
 * the real [AndroidGeoFieldModel] or [Wmm2025Model] (which need Android hardware /
 * asset loading). The [activeModel] always returns [activeModelOverride].
 */
class FakeMagneticFieldModelProvider(
    private val activeModelOverride: MagneticFieldModel
) {
    private var lastResult: WmmResult? = null

    fun activeModel(): MagneticFieldModel = activeModelOverride

    fun evaluate(latDeg: Double, lonDeg: Double, altM: Double, epochYears: Double): WmmResult {
        val model = activeModelOverride
        val result = WmmResult(
            declination = model.getDeclination(latDeg, lonDeg, altM, epochYears),
            inclination = model.getExpectedInclination(latDeg, lonDeg, altM, epochYears),
            totalField  = model.getExpectedFieldMagnitude(latDeg, lonDeg, altM, epochYears)
        )
        lastResult = result
        return result
    }

    fun getLastResult(): WmmResult? = lastResult
}
