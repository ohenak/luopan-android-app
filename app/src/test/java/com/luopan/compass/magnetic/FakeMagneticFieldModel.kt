package com.luopan.compass.magnetic

/**
 * Test double for [MagneticFieldModel].
 * All coordinate-accepting methods return the fixed values supplied at construction.
 *
 * Specified in TSPEC §2.1.
 */
class FakeMagneticFieldModel(
    private val fieldMagnitude: Float = 50f,
    private val inclination: Float = 45f,
    private val declination: Float = 0f,
    private val modelId: String = "FakeModel",
    private val expired: Boolean = false
) : MagneticFieldModel {

    override fun getExpectedFieldMagnitude(latDeg: Double, lonDeg: Double, altM: Double, epochYears: Double): Float =
        fieldMagnitude

    override fun getExpectedInclination(latDeg: Double, lonDeg: Double, altM: Double, epochYears: Double): Float =
        inclination

    override fun getDeclination(latDeg: Double, lonDeg: Double, altM: Double, epochYears: Double): Float =
        declination

    override fun getModelId(): String = modelId

    override fun isExpired(): Boolean = expired
}
