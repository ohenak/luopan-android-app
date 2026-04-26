package com.luopan.compass.magnetic

/**
 * Abstracts the geomagnetic field model used for declination, inclination, and
 * field-magnitude computations.
 *
 * All coordinate-accepting methods are pure functions: given the same four parameters
 * they always return the same result and have no observable side effects. They are
 * safe to call from any thread.
 *
 * No implementation may make a network call. All computations are offline.
 */
interface MagneticFieldModel {

    /**
     * Magnetic declination (angle between magnetic north and geographic north) in decimal degrees.
     * Positive values indicate east declination; negative values indicate west declination.
     *
     * @param latDeg     Geographic latitude in decimal degrees. Range: [−90.0, 90.0].
     * @param lonDeg     Geographic longitude in decimal degrees. Range: [−180.0, 180.0).
     * @param altM       Altitude above WGS-84 ellipsoid in meters. Use 0.0 when unavailable.
     * @param epochYears Decimal year. Computed as: year + dayOfYear / 365.25.
     *                   Example: 2025-07-02 ≈ 2025.5.
     * @return Declination in degrees. Range: (−180.0, 180.0].
     */
    fun getDeclination(latDeg: Double, lonDeg: Double, altM: Double, epochYears: Double): Float

    /**
     * Expected magnetic inclination (dip angle) in degrees.
     * Positive values indicate downward dip (northern hemisphere typical).
     *
     * @param latDeg     Geographic latitude in decimal degrees.
     * @param lonDeg     Geographic longitude in decimal degrees.
     * @param altM       Altitude in meters.
     * @param epochYears Decimal year (see getDeclination for formula).
     * @return Inclination in degrees. Range: [−90.0, 90.0].
     */
    fun getExpectedInclination(latDeg: Double, lonDeg: Double, altM: Double, epochYears: Double): Float

    /**
     * Expected total field magnitude in µT for use as the interference detection baseline.
     *
     * @param latDeg     Geographic latitude in decimal degrees. Range: [−90.0, 90.0].
     * @param lonDeg     Geographic longitude in decimal degrees. Range: [−180.0, 180.0).
     * @param altM       Altitude above WGS-84 ellipsoid in meters. Use 0.0 when unavailable.
     * @param epochYears Decimal year. Computed as: year + dayOfYear / 365.25.
     * @return Total field magnitude in µT. Always > 0.
     */
    fun getExpectedFieldMagnitude(latDeg: Double, lonDeg: Double, altM: Double, epochYears: Double): Float

    /**
     * Returns the model identifier string written into BearingRecord.calibration_version.
     * Canonical values: "WMM2025" for Wmm2025Model; "AndroidGeoField" for AndroidGeoFieldModel.
     * The returned string is non-empty and stable across app restarts.
     */
    fun getModelId(): String

    /**
     * Returns true when the model is outside its valid epoch range and the provider
     * should switch to the fallback model.
     *
     * For Wmm2025Model: true when epochYears < 2025.0 or epochYears >= 2030.0
     * (epoch year is computed internally via the injected Clock).
     * For AndroidGeoFieldModel: always returns false (no expiry concept).
     */
    fun isExpired(): Boolean
}
