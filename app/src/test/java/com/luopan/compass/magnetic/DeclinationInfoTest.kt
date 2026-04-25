package com.luopan.compass.magnetic

import com.luopan.compass.location.LocationResult
import com.luopan.compass.model.NorthType
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [DeclinationInfo] data class population.
 *
 * Covers:
 *  - Coordinate masking to 2 decimal places (PLAN §4 P5.1; FSPEC §2.3)
 *  - Source label derivation from model ID and expiry state (TSPEC §3.3)
 *  - [DeclinationInfo] is null when northType == MAGNETIC (PLAN §4 P5.1)
 *  - last_updated populated from LocationResult timestamp
 *  - valid_until populated from model validity period
 *
 * PLAN P5.1 success criteria.
 */
class DeclinationInfoTest {

    // -----------------------------------------------------------------------
    // Coordinate masking — 2 decimal places
    // -----------------------------------------------------------------------

    @Test
    fun `lat_masked truncates latitude to 2 decimal places`() {
        val info = buildGpsFix(lat = 37.4219999, lon = -122.0840575)
        // 37.4219999 masked to 2 dp → 37.42
        assertEquals("37.42", info.lat_masked)
    }

    @Test
    fun `lon_masked truncates longitude to 2 decimal places`() {
        val info = buildGpsFix(lat = 37.4219999, lon = -122.0840575)
        // -122.0840575 masked to 2 dp → -122.08
        assertEquals("-122.08", info.lon_masked)
    }

    @Test
    fun `lat_masked with positive latitude rounds correctly`() {
        val info = buildGpsFix(lat = 51.505, lon = -0.09)
        // 51.505 rounded to 2 dp → 51.51 (half-up rounding)
        assertEquals("51.51", info.lat_masked)
    }

    @Test
    fun `lon_masked with negative longitude rounds correctly`() {
        val info = buildGpsFix(lat = 51.505, lon = -0.09)
        assertEquals("-0.09", info.lon_masked)
    }

    @Test
    fun `masked coordinates use exactly 2 decimal places even when trailing zeros`() {
        val info = buildGpsFix(lat = 40.0, lon = -105.0)
        assertEquals("40.00", info.lat_masked)
        assertEquals("-105.00", info.lon_masked)
    }

    // -----------------------------------------------------------------------
    // Source label propagation from model ID and expiry state
    // -----------------------------------------------------------------------

    @Test
    fun `source_label is WMM2025 valid when model is WMM2025 and not expired`() {
        val info = buildGpsFix(model = FakeMagneticFieldModel(modelId = "WMM2025", expired = false))
        assertEquals("WMM2025 (valid to 2030)", info.source_label)
    }

    @Test
    fun `source_label is Android model advisory when model is AndroidGeoField`() {
        val info = buildGpsFix(model = FakeMagneticFieldModel(modelId = "AndroidGeoField", expired = false))
        assertEquals("Android model — may be less accurate", info.source_label)
    }

    @Test
    fun `source_label is Android model advisory when WMM2025 is expired`() {
        // expired flag simulates the model being past its validity window
        val info = buildGpsFix(model = FakeMagneticFieldModel(modelId = "WMM2025", expired = true))
        assertEquals("Android model — may be less accurate", info.source_label)
    }

    @Test
    fun `source_label for manual entry location uses WMM2025 manual location label`() {
        val info = DeclinationInfo.build(
            declinationDeg = 8.93f,
            model = FakeMagneticFieldModel(modelId = "WMM2025", expired = false),
            locationResult = LocationResult.ManualEntry(lat = 35.0, lon = 139.0),
            lastUpdatedMs = 1_714_000_000_000L
        )
        assertEquals("WMM2025 (manual location)", info.source_label)
    }

    @Test
    fun `source_label for cached fix uses standard WMM2025 valid label`() {
        val info = DeclinationInfo.build(
            declinationDeg = 8.93f,
            model = FakeMagneticFieldModel(modelId = "WMM2025", expired = false),
            locationResult = LocationResult.CachedFix(lat = 40.0, lon = -105.0, altM = 0.0, ageMs = 86_400_000L),
            lastUpdatedMs = 1_714_000_000_000L
        )
        assertEquals("WMM2025 (valid to 2030)", info.source_label)
    }

    // -----------------------------------------------------------------------
    // declination_deg propagated correctly
    // -----------------------------------------------------------------------

    @Test
    fun `declination_deg is propagated correctly from input`() {
        val info = buildGpsFix(declinationDeg = -7.5f)
        assertEquals(-7.5f, info.declination_deg, 0.001f)
    }

    // -----------------------------------------------------------------------
    // valid_until — WMM2025 vs AndroidGeoField
    // -----------------------------------------------------------------------

    @Test
    fun `valid_until is 2030-01-01 when model is WMM2025 and not expired`() {
        val info = buildGpsFix(model = FakeMagneticFieldModel(modelId = "WMM2025", expired = false))
        assertEquals("2030-01-01", info.valid_until)
    }

    @Test
    fun `valid_until is empty string when model is AndroidGeoField`() {
        val info = buildGpsFix(model = FakeMagneticFieldModel(modelId = "AndroidGeoField", expired = false))
        assertTrue("valid_until should be empty for AndroidGeoField", info.valid_until.isEmpty())
    }

    @Test
    fun `valid_until is empty string when WMM2025 is expired`() {
        val info = buildGpsFix(model = FakeMagneticFieldModel(modelId = "WMM2025", expired = true))
        assertTrue("valid_until should be empty when WMM2025 is expired", info.valid_until.isEmpty())
    }

    // -----------------------------------------------------------------------
    // last_updated — populated from lastUpdatedMs
    // -----------------------------------------------------------------------

    @Test
    fun `last_updated is set to lastUpdatedMs`() {
        val ts = 1_714_000_000_000L
        val info = buildGpsFix(lastUpdatedMs = ts)
        assertEquals(ts, info.last_updated)
    }

    // -----------------------------------------------------------------------
    // buildOrNull — null when northType == MAGNETIC or location unavailable
    // -----------------------------------------------------------------------

    @Test
    fun `buildOrNull returns null when northType is MAGNETIC`() {
        val info = DeclinationInfo.buildOrNull(
            northType = NorthType.MAGNETIC,
            declinationDeg = 8.93f,
            model = FakeMagneticFieldModel(modelId = "WMM2025", expired = false),
            locationResult = LocationResult.GpsFix(lat = 40.0, lon = -105.0, altM = 0.0),
            lastUpdatedMs = 1_714_000_000_000L
        )
        assertNull("DeclinationInfo must be null when northType is MAGNETIC", info)
    }

    @Test
    fun `buildOrNull returns null when northType is GRID`() {
        val info = DeclinationInfo.buildOrNull(
            northType = NorthType.GRID,
            declinationDeg = 8.93f,
            model = FakeMagneticFieldModel(modelId = "WMM2025", expired = false),
            locationResult = LocationResult.GpsFix(lat = 40.0, lon = -105.0, altM = 0.0),
            lastUpdatedMs = 1_714_000_000_000L
        )
        assertNull("DeclinationInfo must be null when northType is GRID", info)
    }

    @Test
    fun `buildOrNull returns non-null when northType is TRUE and location available`() {
        val info = DeclinationInfo.buildOrNull(
            northType = NorthType.TRUE,
            declinationDeg = 8.93f,
            model = FakeMagneticFieldModel(modelId = "WMM2025", expired = false),
            locationResult = LocationResult.GpsFix(lat = 40.0, lon = -105.0, altM = 0.0),
            lastUpdatedMs = 1_714_000_000_000L
        )
        assertNotNull("DeclinationInfo must be non-null when northType is TRUE and location available", info)
    }

    @Test
    fun `buildOrNull returns null when northType is TRUE but location is Unavailable`() {
        val info = DeclinationInfo.buildOrNull(
            northType = NorthType.TRUE,
            declinationDeg = 0.0f,
            model = FakeMagneticFieldModel(modelId = "WMM2025", expired = false),
            locationResult = LocationResult.Unavailable,
            lastUpdatedMs = 0L
        )
        assertNull("DeclinationInfo must be null when location is Unavailable", info)
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun buildGpsFix(
        declinationDeg: Float = 8.93f,
        model: MagneticFieldModel = FakeMagneticFieldModel(modelId = "WMM2025", expired = false),
        lat: Double = 40.0,
        lon: Double = -105.0,
        lastUpdatedMs: Long = 1_714_000_000_000L
    ): DeclinationInfo = DeclinationInfo.build(
        declinationDeg = declinationDeg,
        model = model,
        locationResult = LocationResult.GpsFix(lat = lat, lon = lon, altM = 0.0),
        lastUpdatedMs = lastUpdatedMs
    )
}
