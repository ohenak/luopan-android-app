package com.luopan.compass.ui

/**
 * The set of heading-related UI fields computed by [NorthTypeEngine] per sensor frame.
 *
 * All fields here are derived from the magnetic heading, north type, location result,
 * and active magnetic field model. They are pure data — no Android lifecycle dependency.
 */
data class HeadingFields(
    /** True heading in degrees [0, 360) — equals magnetic heading when MAGNETIC mode or no location. */
    val displayHeading: Double,
    /** North reference label for the heading readout — "True N", "True N (manual location)", or "Magnetic N". */
    val northLabel: String,
    /** True when True North is active and the location is a cached GPS fix (not a fresh fix). */
    val locationFallbackAdvisory: Boolean,
    /** True when True North is active and the active magnetic field model is AndroidGeoFieldModel. */
    val fallbackMagAdvisory: Boolean,
    /**
     * Magnetic declination in degrees (positive east, negative west) applied to the displayed heading.
     * Non-zero only when northType=TRUE and a location is available.
     * Zero when northType=MAGNETIC or no location is available.
     *
     * Used by CompassUiState.declination_deg and the declination info panel (TSPEC §3.7, AT-B).
     */
    val declination_deg: Float = 0.0f
)
