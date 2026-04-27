package com.luopan.compass.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for pure format functions in [BearingAdapter].
 *
 * AT-HIST-05-D: inclination deviation formatting (truncation toward zero).
 * Field deviation formatting (fraction → percentage string).
 */
class BearingAdapterFormatTest {

    // ── AT-HIST-05-D: inclination deviation formatting ─────────────────────────

    @Test
    fun `formatInclinationDev truncates negative toward zero`() {
        // -2.3 → "-2°"
        assertEquals("-2°", BearingAdapter.formatInclinationDev(-2.3f))
    }

    @Test
    fun `formatInclinationDev truncates positive toward zero`() {
        // 4.7 → "4°"
        assertEquals("4°", BearingAdapter.formatInclinationDev(4.7f))
    }

    @Test
    fun `formatInclinationDev returns zero for zero`() {
        // 0f → "0°"
        assertEquals("0°", BearingAdapter.formatInclinationDev(0f))
    }

    @Test
    fun `formatInclinationDev truncates negative fraction to zero`() {
        // -0.9f → "0°" (truncation toward zero, not floor)
        assertEquals("0°", BearingAdapter.formatInclinationDev(-0.9f))
    }

    @Test
    fun `formatInclinationDev truncates positive fraction to zero`() {
        // 0.9f → "0°"
        assertEquals("0°", BearingAdapter.formatInclinationDev(0.9f))
    }

    // ── Field deviation formatting ─────────────────────────────────────────────

    @Test
    fun `formatFieldDeviation converts 0_25 to 25 percent`() {
        // 0.25 stored → "25%"
        assertEquals("25%", BearingAdapter.formatFieldDeviation(0.25f))
    }

    @Test
    fun `formatFieldDeviation converts 0_0 to 0 percent`() {
        assertEquals("0%", BearingAdapter.formatFieldDeviation(0.0f))
    }

    @Test
    fun `formatFieldDeviation converts 2_5 to 250 percent`() {
        // 2.5 stored (250% deviation) → "250%"
        assertEquals("250%", BearingAdapter.formatFieldDeviation(2.5f))
    }

    @Test
    fun `formatFieldDeviation truncates fractional percent`() {
        // 0.255 stored → "25%" (truncation toward zero via toInt())
        assertEquals("25%", BearingAdapter.formatFieldDeviation(0.255f))
    }
}
