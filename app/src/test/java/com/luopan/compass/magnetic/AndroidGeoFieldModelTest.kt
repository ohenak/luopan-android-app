package com.luopan.compass.magnetic

import com.luopan.compass.util.FakeClock
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidGeoFieldModelTest {

    @Test
    fun `isExpired always returns false`() {
        val model = AndroidGeoFieldModel(FakeClock())
        assertFalse(model.isExpired())
    }

    @Test
    fun `getModelId returns AndroidGeoField`() {
        val model = AndroidGeoFieldModel(FakeClock())
        assertEquals("AndroidGeoField", model.getModelId())
    }

    @Test
    fun `getDeclination returns non-NaN for valid location`() {
        val model = AndroidGeoFieldModel(FakeClock())
        val declination = model.getDeclination(40.0, -105.0, 0.0, 2025.5)
        assertFalse(declination.isNaN())
        // Should be in a reasonable range for Colorado (east declination ~8-12 degrees)
        assertTrue("Expected declination in (5, 15) but was $declination",
            declination > 5.0f && declination < 15.0f)
    }

    @Test
    fun `getExpectedFieldMagnitude returns positive value`() {
        val model = AndroidGeoFieldModel(FakeClock())
        val magnitude = model.getExpectedFieldMagnitude(40.0, -105.0, 0.0, 2025.5)
        assertTrue("Expected positive magnitude but was $magnitude", magnitude > 0f)
    }

    @Test
    fun `getExpectedInclination returns non-NaN for valid location`() {
        val model = AndroidGeoFieldModel(FakeClock())
        val inclination = model.getExpectedInclination(40.0, -105.0, 0.0, 2025.5)
        assertFalse("Expected non-NaN inclination", inclination.isNaN())
        // Northern hemisphere: downward dip, inclination should be positive and significant
        assertTrue("Expected inclination > 0 for northern hemisphere but was $inclination",
            inclination > 0f)
    }

    @Test
    fun `isExpired returns false regardless of clock value`() {
        val clock = FakeClock(currentMs = Long.MAX_VALUE)
        val model = AndroidGeoFieldModel(clock)
        assertFalse(model.isExpired())
    }
}
