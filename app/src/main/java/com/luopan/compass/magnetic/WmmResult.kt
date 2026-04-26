package com.luopan.compass.magnetic

/**
 * Return type from Wmm2025Model.evaluate() — contains all three magnetic field components
 * computed in a single spherical harmonic synthesis pass.
 */
data class WmmResult(
    val declination: Float,   // degrees, positive east, negative west
    val inclination: Float,   // degrees, positive downward (northern hemisphere positive)
    val totalField: Float     // total field magnitude in µT (always > 0)
)
