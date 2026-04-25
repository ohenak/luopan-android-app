package com.luopan.compass.magnetic

import com.luopan.compass.util.FakeClock
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Calendar
import java.util.TimeZone

/**
 * Unit tests for Wmm2025Model against NOAA WMM2025 reference test vectors.
 *
 * Tests load the COF file directly from the filesystem (not Android resources),
 * using the same file path as the bundled raw resource.
 *
 * All expected values derived from the official NOAA WMM2025.COF coefficients
 * (https://www.ncei.noaa.gov/sites/default/files/2024-12/WMM2025COF.zip).
 *
 * Primary NOAA-pinned vector (TE-T-01 mandatory first assertion):
 *   lat=40.0°N, lon=−105.0°W, altM=0.0, epochYears=2025.5
 *   getDeclination() == +7.74° ± 0.1°
 *   getExpectedInclination() == +66.5° ± 0.5°
 *   getExpectedFieldMagnitude() == 51294 nT == 51.3 µT ± 200 nT == ± 0.2 µT
 */
class Wmm2025ModelTest {

    private lateinit var model: Wmm2025Model
    private val cofFile = File("src/main/res/raw/wmm2025_cof.txt")

    /**
     * Converts a target epoch year to the ms-since-epoch that FakeClock should return.
     * epochYear=2025.5 → approximately July 2, 2025 00:00 UTC
     */
    private fun epochYearToMs(epochYear: Double): Long {
        val year = epochYear.toInt()
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(year, 0, 1, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val jan1Ms = cal.timeInMillis
        val daysInYear = if (cal.getActualMaximum(Calendar.DAY_OF_YEAR) == 366) 366.0 else 365.0
        val fractionalDay = (epochYear - year) * daysInYear
        return jan1Ms + (fractionalDay * 86_400_000L).toLong()
    }

    @Before
    fun setUp() {
        // FakeClock set to mid-2025 (within valid 2025.0-2030.0 range)
        val clock = FakeClock(epochYearToMs(2027.0))
        model = Wmm2025Model.fromFile(cofFile, clock)
    }

    // ─── Primary NOAA-pinned vector (TE-T-01 — mandatory first assertion) ─────

    @Test
    fun `primary NOAA pinned vector - Colorado 2025_5 declination within 0_1 deg`() {
        // Official WMM2025 value: lat=40.0°N, lon=−105.0°W, altM=0.0, epochYears=2025.5 → +7.74°E
        val decl = model.getDeclination(latDeg = 40.0, lonDeg = -105.0, altM = 0.0, epochYears = 2025.5)
        assertEquals("Declination should be +7.74°E ±0.1°", 7.74f, decl, 0.1f)
    }

    @Test
    fun `primary NOAA pinned vector - Colorado 2025_5 inclination within 0_5 deg`() {
        // Official WMM2025 value: +66.48°
        val incl = model.getExpectedInclination(latDeg = 40.0, lonDeg = -105.0, altM = 0.0, epochYears = 2025.5)
        assertEquals("Inclination should be +66.5° ±0.5°", 66.5f, incl, 0.5f)
    }

    @Test
    fun `primary NOAA pinned vector - Colorado 2025_5 field magnitude within 200 nT`() {
        // Official WMM2025 value: 51294 nT = 51.3 µT; tolerance ±200 nT = ±0.2 µT
        val mag = model.getExpectedFieldMagnitude(latDeg = 40.0, lonDeg = -105.0, altM = 0.0, epochYears = 2025.5)
        assertEquals("Field magnitude should be 51.3 µT ±0.2 µT", 51.3f, mag, 0.2f)
    }

    // ─── Additional NOAA reference test vectors (≥5 total including the primary) ─

    @Test
    fun `equatorial location - Null Island 2025_0 declination within 0_1 deg`() {
        // Official WMM2025 value: lat=0.0, lon=0.0, altM=0.0, epochYears=2025.0 → -4.02°
        val decl = model.getDeclination(latDeg = 0.0, lonDeg = 0.0, altM = 0.0, epochYears = 2025.0)
        assertEquals("Null Island declination ~-4.02° ±0.1°", -4.02f, decl, 0.1f)
    }

    @Test
    fun `equatorial location - Null Island 2025_0 field magnitude within 200 nT`() {
        val mag = model.getExpectedFieldMagnitude(latDeg = 0.0, lonDeg = 0.0, altM = 0.0, epochYears = 2025.0)
        // Official WMM2025 value: 31840 nT = 31.84 µT
        assertEquals("Null Island field magnitude ~31.84 µT ±0.2 µT", 31.84f, mag, 0.2f)
    }

    @Test
    fun `southern hemisphere - Sydney Australia 2025_0 declination within 0_1 deg`() {
        // Official WMM2025 value: lat=-33.87, lon=151.21, altM=0.0, epochYears=2025.0 → +12.96°E
        val decl = model.getDeclination(latDeg = -33.87, lonDeg = 151.21, altM = 0.0, epochYears = 2025.0)
        assertEquals("Sydney declination ~12.96°E ±0.1°", 12.96f, decl, 0.1f)
    }

    @Test
    fun `southern hemisphere - Sydney Australia 2025_0 inclination within 0_5 deg`() {
        // Southern hemisphere: downward dip is negative by convention (field dips into earth
        // from southern surface → inclination negative for southern hemisphere WMM convention)
        val incl = model.getExpectedInclination(latDeg = -33.87, lonDeg = 151.21, altM = 0.0, epochYears = 2025.0)
        // Official WMM2025 value: ~-64.74° at Sydney
        assertEquals("Sydney inclination ~-64.74° ±0.5°", -64.74f, incl, 0.5f)
    }

    @Test
    fun `northern high latitude - Svalbard 2025_0 declination within 0_1 deg`() {
        // Official WMM2025 value: lat=78.22, lon=15.64, altM=0.0, epochYears=2025.0 → +12.38°E
        val decl = model.getDeclination(latDeg = 78.22, lonDeg = 15.64, altM = 0.0, epochYears = 2025.0)
        assertEquals("Svalbard declination ~12.38°E ±0.1°", 12.38f, decl, 0.1f)
    }

    @Test
    fun `northern high latitude - Svalbard 2025_0 inclination within 0_5 deg`() {
        // High northern latitude: very steep inclination
        val incl = model.getExpectedInclination(latDeg = 78.22, lonDeg = 15.64, altM = 0.0, epochYears = 2025.0)
        // Official WMM2025 value: ~82.61° at Svalbard (near pole, very steep dip)
        assertEquals("Svalbard inclination ~82.61° ±0.5°", 82.61f, incl, 0.5f)
    }

    @Test
    fun `western hemisphere - London UK 2025_0 declination within 0_1 deg`() {
        // Official WMM2025 value: lat=51.5, lon=-0.1, altM=0.0, epochYears=2025.0 → +0.93°E
        val decl = model.getDeclination(latDeg = 51.5, lonDeg = -0.1, altM = 0.0, epochYears = 2025.0)
        assertEquals("London declination ~0.93°E ±0.1°", 0.93f, decl, 0.1f)
    }

    @Test
    fun `secular variation - Colorado heading changes between 2025_0 and 2030_0`() {
        // Verify secular variation coefficients are applied: value should change over 5 years
        val decl2025 = model.getDeclination(40.0, -105.0, 0.0, 2025.0)
        val decl2030 = model.getDeclination(40.0, -105.0, 0.0, 2030.0)
        // They must differ (secular variation); both should be in reasonable range
        assertNotEquals("Declination should change with secular variation",
            decl2025.toDouble(), decl2030.toDouble(), 0.01)
    }

    // ─── isExpired() tests ─────────────────────────────────────────────────────

    @Test
    fun `isExpired returns false when clock set to epoch year 2027_0`() {
        val clock = FakeClock(epochYearToMs(2027.0))
        val m = Wmm2025Model.fromFile(cofFile, clock)
        assertFalse("isExpired() should return false for 2027.0", m.isExpired())
    }

    @Test
    fun `isExpired returns true when clock set to epoch year 2030_5 (past upper bound)`() {
        val clock = FakeClock(epochYearToMs(2030.5))
        val m = Wmm2025Model.fromFile(cofFile, clock)
        assertTrue("isExpired() should return true for 2030.5 (>= 2030.0)", m.isExpired())
    }

    @Test
    fun `isExpired returns true when clock set to epoch year exactly 2030_0 (boundary)`() {
        val clock = FakeClock(epochYearToMs(2030.0))
        val m = Wmm2025Model.fromFile(cofFile, clock)
        assertTrue("isExpired() should return true for 2030.0 (>= 2030.0)", m.isExpired())
    }

    @Test
    fun `isExpired returns true when clock set to epoch year 2024_9 (before lower bound)`() {
        val clock = FakeClock(epochYearToMs(2024.9))
        val m = Wmm2025Model.fromFile(cofFile, clock)
        assertTrue("isExpired() should return true for 2024.9 (< 2025.0)", m.isExpired())
    }

    @Test
    fun `isExpired returns false when clock set to epoch year exactly 2025_0 (lower boundary valid)`() {
        val clock = FakeClock(epochYearToMs(2025.0))
        val m = Wmm2025Model.fromFile(cofFile, clock)
        assertFalse("isExpired() should return false for 2025.0 (>= 2025.0)", m.isExpired())
    }

    @Test
    fun `isExpired returns false when clock set to epoch year 2029_9 (just before expiry)`() {
        val clock = FakeClock(epochYearToMs(2029.9))
        val m = Wmm2025Model.fromFile(cofFile, clock)
        assertFalse("isExpired() should return false for 2029.9 (< 2030.0)", m.isExpired())
    }

    // ─── getModelId() ─────────────────────────────────────────────────────────

    @Test
    fun `getModelId returns WMM2025`() {
        assertEquals("WMM2025", model.getModelId())
    }

    // ─── WmmResult data class ─────────────────────────────────────────────────

    @Test
    fun `WmmResult contains all three field components`() {
        val result = model.evaluate(40.0, -105.0, 0.0, 2025.5)
        assertFalse("declination must not be NaN", result.declination.isNaN())
        assertFalse("inclination must not be NaN", result.inclination.isNaN())
        assertFalse("totalField must not be NaN", result.totalField.isNaN())
        assertTrue("totalField must be positive", result.totalField > 0f)
    }
}
