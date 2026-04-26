package com.luopan.compass.magnetic

import com.luopan.compass.location.LocationResult
import com.luopan.compass.model.NorthType
import com.luopan.compass.ui.NorthTypeEngine
import org.junit.Assert.*
import org.junit.Test

/**
 * Verifies that the north-type pipeline correctly propagates magnetic declination
 * from [MagneticFieldModel] to the displayed heading and UI state fields.
 *
 * AT-B traceability (TSPEC §10.1 TE-T-03-NEW):
 * - AT-B-01: heading is adjusted by model declination when northType=TRUE
 * - AT-B-02: heading is unchanged when northType=MAGNETIC
 * - AT-B-03: WMM2025 model accuracy against NOAA reference is covered by Wmm2025ModelTest
 *
 * Uses [FakeMagneticFieldModel] with the NOAA-pinned vector values to test
 * ViewModel-level propagation in isolation from the WMM spherical harmonic math.
 *
 * PLAN §4 P8.1 success criteria:
 *  - WmmDeclinationAccuracyTest (AT-B): verifies CompassViewModel correctly propagates
 *    model declination to CompassUiState.declination_deg using FakeMagneticFieldModel
 *    with NOAA-pinned values
 */
class WmmDeclinationAccuracyTest {

    companion object {
        // NOAA-pinned test vector (TSPEC §10.1 TE-T-01, corrected from real WMM2025 output):
        //   lat=40.0°N, lon=−105.0°W, altM=0.0, epochYears=2025.5
        //   declination ≈ +7.74°E  (corrected from PLAN spec; P2.2 implementation used authentic coefficients)
        private const val PINNED_DECLINATION = 7.74f
        private const val PINNED_INCLINATION = 66.5f
        private const val PINNED_TOTAL_FIELD = 51.3f   // µT (= 51300 nT)

        private const val PINNED_LAT = 40.0
        private const val PINNED_LON = -105.0
        private const val PINNED_ALT = 0.0
        private const val PINNED_EPOCH = 2025.5

        private const val DECLINATION_TOLERANCE = 0.1f
    }

    // ─── Primary NOAA-pinned vector propagation (AT-B-01) ─────────────────────

    /**
     * Primary assertion: with northType=TRUE and FakeMagneticFieldModel returning the
     * NOAA-pinned declination (+7.74°), the displayed heading increases by that declination.
     *
     * This is the first and most important assertion in this test class (AT-B-01).
     */
    @Test
    fun `NOAA-pinned vector - northType TRUE applies declination offset to magnetic heading`() {
        val engine = NorthTypeEngine()
        engine.setNorthType(NorthType.TRUE)

        val model = FakeMagneticFieldModel(
            declination = PINNED_DECLINATION,
            inclination = PINNED_INCLINATION,
            fieldMagnitude = PINNED_TOTAL_FIELD,
            modelId = "WMM2025"
        )
        val location = LocationResult.GpsFix(
            lat = PINNED_LAT, lon = PINNED_LON, altM = PINNED_ALT
        )
        val magneticHeading = 45.0

        val result = engine.computeHeadingFields(magneticHeading, location, model, PINNED_EPOCH)

        // Displayed heading should be magnetic heading + declination (normalized to [0, 360))
        val expectedHeading = (magneticHeading + PINNED_DECLINATION + 360.0) % 360.0
        assertEquals(
            "Heading should be magnetic + declination($PINNED_DECLINATION°) for northType=TRUE",
            expectedHeading, result.displayHeading, 0.5
        )
    }

    /**
     * The declination_deg field in the HeadingFields output must equal the model's
     * declination value when northType=TRUE and location is available.
     */
    @Test
    fun `NOAA-pinned vector - declination_deg in HeadingFields matches model declination`() {
        val engine = NorthTypeEngine()
        engine.setNorthType(NorthType.TRUE)

        val model = FakeMagneticFieldModel(
            declination = PINNED_DECLINATION,
            inclination = PINNED_INCLINATION,
            fieldMagnitude = PINNED_TOTAL_FIELD,
            modelId = "WMM2025"
        )
        val location = LocationResult.GpsFix(
            lat = PINNED_LAT, lon = PINNED_LON, altM = PINNED_ALT
        )

        val result = engine.computeHeadingFields(45.0, location, model, PINNED_EPOCH)

        assertEquals(
            "declination_deg should match model's getDeclination() == $PINNED_DECLINATION° ±$DECLINATION_TOLERANCE°",
            PINNED_DECLINATION, result.declination_deg, DECLINATION_TOLERANCE
        )
    }

    // ─── northType=MAGNETIC — declination NOT applied (AT-B-02) ──────────────

    @Test
    fun `northType MAGNETIC - declination_deg is zero and heading is unchanged`() {
        val engine = NorthTypeEngine() // default MAGNETIC

        val model = FakeMagneticFieldModel(
            declination = PINNED_DECLINATION,
            inclination = PINNED_INCLINATION,
            fieldMagnitude = PINNED_TOTAL_FIELD,
            modelId = "WMM2025"
        )
        val location = LocationResult.GpsFix(
            lat = PINNED_LAT, lon = PINNED_LON, altM = PINNED_ALT
        )
        val magneticHeading = 90.0

        val result = engine.computeHeadingFields(magneticHeading, location, model, PINNED_EPOCH)

        assertEquals(
            "In MAGNETIC mode heading should be unchanged (no declination applied)",
            magneticHeading, result.displayHeading, 0.001
        )
        assertEquals(
            "In MAGNETIC mode declination_deg should be 0.0",
            0.0f, result.declination_deg, 0.001f
        )
    }

    // ─── No location — declination NOT applied ────────────────────────────────

    @Test
    fun `northType TRUE but no location - declination_deg is zero and heading is unchanged`() {
        val engine = NorthTypeEngine()
        engine.setNorthType(NorthType.TRUE)

        val model = FakeMagneticFieldModel(
            declination = PINNED_DECLINATION,
            inclination = PINNED_INCLINATION,
            fieldMagnitude = PINNED_TOTAL_FIELD,
            modelId = "WMM2025"
        )
        val magneticHeading = 135.0

        val result = engine.computeHeadingFields(
            magneticHeading, LocationResult.Unavailable, model, PINNED_EPOCH
        )

        assertEquals(
            "With no location, heading should be unchanged in TRUE mode",
            magneticHeading, result.displayHeading, 0.001
        )
        assertEquals(
            "With no location, declination_deg should be 0.0",
            0.0f, result.declination_deg, 0.001f
        )
    }

    // ─── Heading normalization with pinned declination ─────────────────────────

    @Test
    fun `heading is normalized to 0-360 when pinned declination pushes heading past 360`() {
        val engine = NorthTypeEngine()
        engine.setNorthType(NorthType.TRUE)

        val model = FakeMagneticFieldModel(
            declination = PINNED_DECLINATION,
            inclination = PINNED_INCLINATION,
            fieldMagnitude = PINNED_TOTAL_FIELD,
            modelId = "WMM2025"
        )
        // 355° + 7.74° = 362.74° → should normalize to 2.74°
        val magneticHeading = 355.0
        val location = LocationResult.GpsFix(
            lat = PINNED_LAT, lon = PINNED_LON, altM = PINNED_ALT
        )

        val result = engine.computeHeadingFields(magneticHeading, location, model, PINNED_EPOCH)

        val expected = (magneticHeading + PINNED_DECLINATION + 360.0) % 360.0
        assertEquals(expected, result.displayHeading, 0.5)
        assertTrue(
            "Heading must be in [0, 360) but was ${result.displayHeading}",
            result.displayHeading >= 0.0 && result.displayHeading < 360.0
        )
    }

    // ─── AndroidGeoFieldModel fallback (AT-B advisory check) ──────────────────

    @Test
    fun `fallback_mag_advisory is true when declination from AndroidGeoField model`() {
        val engine = NorthTypeEngine()
        engine.setNorthType(NorthType.TRUE)

        val fallbackModel = FakeMagneticFieldModel(
            declination = PINNED_DECLINATION,
            inclination = PINNED_INCLINATION,
            fieldMagnitude = PINNED_TOTAL_FIELD,
            modelId = "AndroidGeoField"  // signals fallback model
        )
        val location = LocationResult.GpsFix(
            lat = PINNED_LAT, lon = PINNED_LON, altM = PINNED_ALT
        )

        val result = engine.computeHeadingFields(45.0, location, fallbackModel, PINNED_EPOCH)

        assertTrue(
            "fallback_mag_advisory must be true when using AndroidGeoField model",
            result.fallbackMagAdvisory
        )
        // But declination should still be applied from the fallback model
        assertEquals(
            "Declination should still be applied from fallback model",
            PINNED_DECLINATION, result.declination_deg, DECLINATION_TOLERANCE
        )
    }

    // ─── CachedFix location path ───────────────────────────────────────────────

    @Test
    fun `northType TRUE with CachedFix applies declination and sets location_fallback_advisory`() {
        val engine = NorthTypeEngine()
        engine.setNorthType(NorthType.TRUE)

        val model = FakeMagneticFieldModel(
            declination = PINNED_DECLINATION,
            inclination = PINNED_INCLINATION,
            fieldMagnitude = PINNED_TOTAL_FIELD,
            modelId = "WMM2025"
        )
        val cachedLocation = LocationResult.CachedFix(
            lat = PINNED_LAT, lon = PINNED_LON, altM = PINNED_ALT, ageMs = 86_400_000L
        )
        val magneticHeading = 200.0

        val result = engine.computeHeadingFields(magneticHeading, cachedLocation, model, PINNED_EPOCH)

        // Declination is still applied even with cached location
        val expectedHeading = (magneticHeading + PINNED_DECLINATION + 360.0) % 360.0
        assertEquals(expectedHeading, result.displayHeading, 0.5)
        assertEquals(PINNED_DECLINATION, result.declination_deg, DECLINATION_TOLERANCE)
        assertTrue("location_fallback_advisory must be true for CachedFix", result.locationFallbackAdvisory)
    }

    // ─── Manual entry path ─────────────────────────────────────────────────────

    @Test
    fun `northType TRUE with ManualEntry applies declination and label is manual location`() {
        val engine = NorthTypeEngine()
        engine.setNorthType(NorthType.TRUE)

        val model = FakeMagneticFieldModel(
            declination = PINNED_DECLINATION,
            inclination = PINNED_INCLINATION,
            fieldMagnitude = PINNED_TOTAL_FIELD,
            modelId = "WMM2025"
        )
        val manualLocation = LocationResult.ManualEntry(
            lat = PINNED_LAT, lon = PINNED_LON, altM = PINNED_ALT
        )
        val magneticHeading = 180.0

        val result = engine.computeHeadingFields(magneticHeading, manualLocation, model, PINNED_EPOCH)

        assertEquals(PINNED_DECLINATION, result.declination_deg, DECLINATION_TOLERANCE)
        assertEquals("True N (manual location)", result.northLabel)
    }
}
