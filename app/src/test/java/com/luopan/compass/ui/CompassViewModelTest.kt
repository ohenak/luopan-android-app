package com.luopan.compass.ui

import com.luopan.compass.location.LocationResult
import com.luopan.compass.magnetic.FakeMagneticFieldModel
import com.luopan.compass.model.CalDotColor
import com.luopan.compass.model.NorthType
import com.luopan.compass.model.OverallConfidence
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for north-type integration in [CompassViewModel].
 *
 * All tests use [NorthTypeEngine] — the pure, Android-free class that [CompassViewModel]
 * delegates to for north-type computation. Fake dependencies are injected directly.
 *
 * TSPEC §3.7, PLAN §4 P4.1 success criteria.
 */
class CompassViewModelTest {

    // ---------- Legacy test: preserved, no regression ----------

    @Test
    fun `INITIAL state has expected defaults`() {
        val state = CompassUiState.INITIAL
        assertEquals("---", state.heading_formatted)
        assertEquals(-1L, state.calibration_age_days)
        assertEquals(OverallConfidence.POOR, state.confidence)
        assertEquals(CalDotColor.RED, state.cal_dot_color)
        assertTrue(state.show_calibration_cta)
        assertFalse(state.is_stabilizing)
        assertNull(state.last_valid_heading_deg)
    }

    // ---------- NorthType enum ----------

    @Test
    fun `NorthType enum has MAGNETIC TRUE and GRID entries`() {
        // TSPEC §5.6: MAGNETIC and TRUE for Phase 2; GRID defined for AT-G-08 guard
        val entries = NorthType.entries.toSet()
        assertTrue(entries.contains(NorthType.MAGNETIC))
        assertTrue(entries.contains(NorthType.TRUE))
        assertTrue(entries.contains(NorthType.GRID))
    }

    // ---------- NorthTypeEngine: initial state ----------

    @Test
    fun `northType starts as MAGNETIC`() {
        val engine = NorthTypeEngine()
        assertEquals(NorthType.MAGNETIC, engine.northType.value)
    }

    // ---------- NorthTypeEngine: setNorthType ----------

    @Test
    fun `setNorthType TRUE changes northType to TRUE`() {
        val engine = NorthTypeEngine()
        engine.setNorthType(NorthType.TRUE)
        assertEquals(NorthType.TRUE, engine.northType.value)
    }

    @Test
    fun `setNorthType MAGNETIC changes northType back to MAGNETIC`() {
        val engine = NorthTypeEngine()
        engine.setNorthType(NorthType.TRUE)
        engine.setNorthType(NorthType.MAGNETIC)
        assertEquals(NorthType.MAGNETIC, engine.northType.value)
    }

    // ---------- NorthTypeEngine: heading computation ----------

    @Test
    fun `when TRUE with GPS fix, heading includes declination offset`() {
        val engine = NorthTypeEngine()
        engine.setNorthType(NorthType.TRUE)

        val wmmModel = FakeMagneticFieldModel(declination = 8.93f, modelId = "WMM2025")
        val magneticHeading = 45.0
        val location = LocationResult.GpsFix(lat = 40.0, lon = -105.0, altM = 0.0)

        val result = engine.computeHeadingFields(magneticHeading, location, wmmModel, epochYears = 2025.5)

        val expectedHeading = (magneticHeading + 8.93 + 360.0) % 360.0
        assertEquals(expectedHeading, result.displayHeading, 0.5)
    }

    @Test
    fun `when MAGNETIC with GPS fix, heading is unchanged — no declination applied`() {
        val engine = NorthTypeEngine() // default MAGNETIC
        val wmmModel = FakeMagneticFieldModel(declination = 8.93f, modelId = "WMM2025")
        val magneticHeading = 182.7
        val location = LocationResult.GpsFix(lat = 40.0, lon = -105.0, altM = 0.0)

        val result = engine.computeHeadingFields(magneticHeading, location, wmmModel, epochYears = 2025.5)

        assertEquals(magneticHeading, result.displayHeading, 0.001)
    }

    @Test
    fun `when TRUE with Unavailable location, heading is unchanged — no location for declination`() {
        val engine = NorthTypeEngine()
        engine.setNorthType(NorthType.TRUE)

        val wmmModel = FakeMagneticFieldModel(declination = 8.93f, modelId = "WMM2025")
        val magneticHeading = 100.0

        val result = engine.computeHeadingFields(magneticHeading, LocationResult.Unavailable, wmmModel, epochYears = 2025.5)

        // No location → cannot compute declination → return raw magnetic heading
        assertEquals(magneticHeading, result.displayHeading, 0.001)
    }

    // ---------- NorthTypeEngine: north_label ----------

    @Test
    fun `when TRUE with GpsFix, north_label is "True N"`() {
        val engine = NorthTypeEngine()
        engine.setNorthType(NorthType.TRUE)

        val location = LocationResult.GpsFix(lat = 40.0, lon = -105.0, altM = 0.0)
        val model = FakeMagneticFieldModel(declination = 5.0f, modelId = "WMM2025")
        val result = engine.computeHeadingFields(100.0, location, model, 2025.5)

        assertEquals("True N", result.northLabel)
    }

    @Test
    fun `when TRUE with CachedFix, north_label is "True N" — not "True N (cached location)"`() {
        // PLAN §4 P4.1: "True N (cached location)" is NOT a valid label.
        // Cached-vs-fresh distinction belongs in the declination info panel only.
        val engine = NorthTypeEngine()
        engine.setNorthType(NorthType.TRUE)

        val location = LocationResult.CachedFix(lat = 40.0, lon = -105.0, altM = 0.0, ageMs = 86_400_000L)
        val model = FakeMagneticFieldModel(declination = 5.0f, modelId = "WMM2025")
        val result = engine.computeHeadingFields(100.0, location, model, 2025.5)

        assertEquals("True N", result.northLabel)
        assertNotEquals("True N (cached location)", result.northLabel)
    }

    @Test
    fun `when TRUE with ManualEntry, north_label is "True N (manual location)"`() {
        val engine = NorthTypeEngine()
        engine.setNorthType(NorthType.TRUE)

        val location = LocationResult.ManualEntry(lat = 35.0, lon = 139.0)
        val model = FakeMagneticFieldModel(declination = 5.0f, modelId = "WMM2025")
        val result = engine.computeHeadingFields(100.0, location, model, 2025.5)

        assertEquals("True N (manual location)", result.northLabel)
    }

    @Test
    fun `when MAGNETIC, north_label is "Magnetic N" regardless of location state`() {
        val engine = NorthTypeEngine() // default MAGNETIC
        val model = FakeMagneticFieldModel(declination = 5.0f, modelId = "WMM2025")

        // GPS fix
        val resultGps = engine.computeHeadingFields(100.0, LocationResult.GpsFix(40.0, -105.0, 0.0), model, 2025.5)
        assertEquals("Magnetic N", resultGps.northLabel)

        // Cached
        val resultCached = engine.computeHeadingFields(
            100.0, LocationResult.CachedFix(40.0, -105.0, 0.0, 86_400_000L), model, 2025.5
        )
        assertEquals("Magnetic N", resultCached.northLabel)

        // Unavailable
        val resultUnavailable = engine.computeHeadingFields(100.0, LocationResult.Unavailable, model, 2025.5)
        assertEquals("Magnetic N", resultUnavailable.northLabel)
    }

    @Test
    fun `when TRUE with Unavailable location, north_label falls back to "Magnetic N"`() {
        val engine = NorthTypeEngine()
        engine.setNorthType(NorthType.TRUE)

        val model = FakeMagneticFieldModel(declination = 5.0f, modelId = "WMM2025")
        val result = engine.computeHeadingFields(100.0, LocationResult.Unavailable, model, 2025.5)

        // No location → cannot apply True North → show "Magnetic N"
        assertEquals("Magnetic N", result.northLabel)
    }

    // ---------- NorthTypeEngine: location_fallback_advisory ----------

    @Test
    fun `location_fallback_advisory is true when TRUE with CachedFix`() {
        val engine = NorthTypeEngine()
        engine.setNorthType(NorthType.TRUE)

        val location = LocationResult.CachedFix(lat = 40.0, lon = -105.0, altM = 0.0, ageMs = 86_400_000L)
        val model = FakeMagneticFieldModel(declination = 5.0f, modelId = "WMM2025")
        val result = engine.computeHeadingFields(100.0, location, model, 2025.5)

        assertTrue("location_fallback_advisory must be true for CachedFix in TRUE mode",
            result.locationFallbackAdvisory)
    }

    @Test
    fun `location_fallback_advisory is false when TRUE with GpsFix`() {
        val engine = NorthTypeEngine()
        engine.setNorthType(NorthType.TRUE)

        val location = LocationResult.GpsFix(lat = 40.0, lon = -105.0, altM = 0.0)
        val model = FakeMagneticFieldModel(declination = 5.0f, modelId = "WMM2025")
        val result = engine.computeHeadingFields(100.0, location, model, 2025.5)

        assertFalse("location_fallback_advisory must be false for GpsFix", result.locationFallbackAdvisory)
    }

    @Test
    fun `location_fallback_advisory is false in MAGNETIC mode even with CachedFix`() {
        val engine = NorthTypeEngine() // MAGNETIC

        val location = LocationResult.CachedFix(lat = 40.0, lon = -105.0, altM = 0.0, ageMs = 86_400_000L)
        val model = FakeMagneticFieldModel(declination = 5.0f, modelId = "WMM2025")
        val result = engine.computeHeadingFields(100.0, location, model, 2025.5)

        assertFalse("location_fallback_advisory must be false in MAGNETIC mode",
            result.locationFallbackAdvisory)
    }

    // ---------- NorthTypeEngine: fallback_mag_advisory ----------

    @Test
    fun `fallback_mag_advisory is true when active model is AndroidGeoFieldModel`() {
        val engine = NorthTypeEngine()
        engine.setNorthType(NorthType.TRUE)

        // Simulate Android fallback model
        val androidModel = FakeMagneticFieldModel(declination = 4.0f, modelId = "AndroidGeoField")
        val location = LocationResult.GpsFix(lat = 40.0, lon = -105.0, altM = 0.0)
        val result = engine.computeHeadingFields(100.0, location, androidModel, 2025.5)

        assertTrue("fallback_mag_advisory must be true when using AndroidGeoField",
            result.fallbackMagAdvisory)
    }

    @Test
    fun `fallback_mag_advisory is false when active model is WMM2025`() {
        val engine = NorthTypeEngine()
        engine.setNorthType(NorthType.TRUE)

        val wmmModel = FakeMagneticFieldModel(declination = 8.93f, modelId = "WMM2025")
        val location = LocationResult.GpsFix(lat = 40.0, lon = -105.0, altM = 0.0)
        val result = engine.computeHeadingFields(100.0, location, wmmModel, 2025.5)

        assertFalse("fallback_mag_advisory must be false when using WMM2025",
            result.fallbackMagAdvisory)
    }

    @Test
    fun `fallback_mag_advisory is false in MAGNETIC mode regardless of model`() {
        val engine = NorthTypeEngine() // MAGNETIC

        val androidModel = FakeMagneticFieldModel(declination = 4.0f, modelId = "AndroidGeoField")
        val location = LocationResult.GpsFix(lat = 40.0, lon = -105.0, altM = 0.0)
        val result = engine.computeHeadingFields(100.0, location, androidModel, 2025.5)

        assertFalse("fallback_mag_advisory must be false in MAGNETIC mode",
            result.fallbackMagAdvisory)
    }

    // ---------- NorthTypeEngine: heading normalization ----------

    @Test
    fun `heading plus declination is normalized to 0-360 when result goes negative`() {
        val engine = NorthTypeEngine()
        engine.setNorthType(NorthType.TRUE)

        // 5° magnetic + (−10°) declination = −5° → should normalize to 355°
        val model = FakeMagneticFieldModel(declination = -10.0f, modelId = "WMM2025")
        val result = engine.computeHeadingFields(5.0, LocationResult.GpsFix(0.0, 0.0, 0.0), model, 2025.5)

        val expected = (5.0 + (-10.0) + 360.0) % 360.0
        assertEquals(expected, result.displayHeading, 0.001)
        assertTrue("Heading must be in [0, 360)", result.displayHeading >= 0.0 && result.displayHeading < 360.0)
    }

    @Test
    fun `heading plus declination is normalized when result exceeds 360`() {
        val engine = NorthTypeEngine()
        engine.setNorthType(NorthType.TRUE)

        // 355° magnetic + 15° declination = 370° → should normalize to 10°
        val model = FakeMagneticFieldModel(declination = 15.0f, modelId = "WMM2025")
        val result = engine.computeHeadingFields(355.0, LocationResult.GpsFix(0.0, 0.0, 0.0), model, 2025.5)

        val expected = (355.0 + 15.0 + 360.0) % 360.0
        assertEquals(expected, result.displayHeading, 0.001)
        assertTrue("Heading must be in [0, 360)", result.displayHeading >= 0.0 && result.displayHeading < 360.0)
    }

    // ---------- CompassUiState: north_type field ----------

    @Test
    fun `CompassUiState INITIAL has north_type MAGNETIC`() {
        assertEquals(NorthType.MAGNETIC, CompassUiState.INITIAL.north_type)
    }
}
