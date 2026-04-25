package com.luopan.compass.magnetic

import com.luopan.compass.util.FakeClock
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Calendar
import java.util.TimeZone

/**
 * Unit tests for MagneticFieldModelProvider.
 *
 * Exercises the expired/non-expired model selection branches using FakeClock
 * injected into Wmm2025Model to control the epoch year.
 *
 * Per TSPEC §3.3 and PLAN §4.P2.4 success criteria:
 *  - Returns Wmm2025Model when wmm.isExpired() == false
 *  - Returns AndroidGeoFieldModel when wmm.isExpired() == true
 *  - Returns AndroidGeoFieldModel when wmm == null
 *  - getLastResult() returns null before first evaluate() call
 *  - getLastResult() returns cached WmmResult after evaluate()
 */
class MagneticFieldModelProviderTest {

    private val cofFile = File("src/main/res/raw/wmm2025_cof.txt")

    /**
     * Converts a target epoch year to milliseconds since Unix epoch.
     * e.g. epochYear=2027.0 → January 1, 2027 00:00 UTC
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

    // ── Model selection: non-expired WMM ────────────────────────────────────

    @Test
    fun `activeModel returns Wmm2025Model when wmm is not expired`() {
        val clock = FakeClock(epochYearToMs(2027.0))   // within 2025.0–2030.0 range
        val wmm = Wmm2025Model.fromFile(cofFile, clock)
        val fallback = AndroidGeoFieldModel(FakeClock())
        val provider = MagneticFieldModelProvider(wmm = wmm, fallback = fallback)

        val active = provider.activeModel()

        assertSame("Provider should return Wmm2025Model when WMM is not expired", wmm, active)
    }

    @Test
    fun `activeModel returns AndroidGeoFieldModel when wmm is expired`() {
        val clock = FakeClock(epochYearToMs(2031.0))   // past 2030.0 expiry
        val wmm = Wmm2025Model.fromFile(cofFile, clock)
        val fallback = AndroidGeoFieldModel(FakeClock())
        val provider = MagneticFieldModelProvider(wmm = wmm, fallback = fallback)

        val active = provider.activeModel()

        assertSame("Provider should return fallback when WMM is expired", fallback, active)
    }

    @Test
    fun `activeModel returns AndroidGeoFieldModel when wmm is null`() {
        val fallback = AndroidGeoFieldModel(FakeClock())
        val provider = MagneticFieldModelProvider(wmm = null, fallback = fallback)

        val active = provider.activeModel()

        assertSame("Provider should return fallback when wmm is null", fallback, active)
    }

    // ── Model selection: pre-validity WMM ───────────────────────────────────

    @Test
    fun `activeModel returns AndroidGeoFieldModel when wmm clock is before validity window`() {
        val clock = FakeClock(epochYearToMs(2024.5))   // before 2025.0 start
        val wmm = Wmm2025Model.fromFile(cofFile, clock)
        val fallback = AndroidGeoFieldModel(FakeClock())
        val provider = MagneticFieldModelProvider(wmm = wmm, fallback = fallback)

        val active = provider.activeModel()

        assertSame("Provider should return fallback when WMM clock is before validity window", fallback, active)
    }

    // ── Cached WmmResult: getLastResult ──────────────────────────────────────

    @Test
    fun `getLastResult returns null before evaluate is called`() {
        val clock = FakeClock(epochYearToMs(2027.0))
        val wmm = Wmm2025Model.fromFile(cofFile, clock)
        val fallback = AndroidGeoFieldModel(FakeClock())
        val provider = MagneticFieldModelProvider(wmm = wmm, fallback = fallback)

        assertNull("getLastResult() should be null before evaluate() is called", provider.getLastResult())
    }

    @Test
    fun `getLastResult returns cached WmmResult after evaluate is called`() {
        val clock = FakeClock(epochYearToMs(2027.0))
        val wmm = Wmm2025Model.fromFile(cofFile, clock)
        val fallback = AndroidGeoFieldModel(FakeClock())
        val provider = MagneticFieldModelProvider(wmm = wmm, fallback = fallback)

        val result = provider.evaluate(40.0, -105.0, 0.0, 2027.0)

        assertNotNull("getLastResult() should be non-null after evaluate()", provider.getLastResult())
        assertSame("getLastResult() should return the same WmmResult returned by evaluate()",
            result, provider.getLastResult())
    }

    @Test
    fun `evaluate returns a valid WmmResult with non-NaN fields`() {
        val clock = FakeClock(epochYearToMs(2027.0))
        val wmm = Wmm2025Model.fromFile(cofFile, clock)
        val fallback = AndroidGeoFieldModel(FakeClock())
        val provider = MagneticFieldModelProvider(wmm = wmm, fallback = fallback)

        val result = provider.evaluate(40.0, -105.0, 0.0, 2027.0)

        assertFalse("declination should not be NaN", result.declination.isNaN())
        assertFalse("inclination should not be NaN", result.inclination.isNaN())
        assertFalse("totalField should not be NaN", result.totalField.isNaN())
        assertTrue("totalField should be positive", result.totalField > 0f)
    }

    // ── hasModelChanged ──────────────────────────────────────────────────────

    @Test
    fun `hasModelChanged returns false when model has not changed`() {
        val clock = FakeClock(epochYearToMs(2027.0))
        val wmm = Wmm2025Model.fromFile(cofFile, clock)
        val fallback = AndroidGeoFieldModel(FakeClock())
        val provider = MagneticFieldModelProvider(wmm = wmm, fallback = fallback)

        // First call to activeModel sets the baseline
        provider.activeModel()
        // Calling again without changing the clock should report no change
        assertFalse("hasModelChanged() should be false when model has not changed",
            provider.hasModelChanged())
    }

    @Test
    fun `hasModelChanged returns true when model switches from WMM to fallback`() {
        val clock = FakeClock(epochYearToMs(2027.0))
        val wmm = Wmm2025Model.fromFile(cofFile, clock)
        val fallback = AndroidGeoFieldModel(FakeClock())
        val provider = MagneticFieldModelProvider(wmm = wmm, fallback = fallback)

        // First call: WMM is active
        provider.activeModel()
        // Advance clock past 2030.0 to trigger expiry
        clock.set(epochYearToMs(2031.0))
        // activeModel() now returns fallback — model has changed
        provider.activeModel()

        assertTrue("hasModelChanged() should be true after WMM expires and fallback becomes active",
            provider.hasModelChanged())
    }
}
