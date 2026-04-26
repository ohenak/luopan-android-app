package com.luopan.compass.ui

import com.luopan.compass.luopan.LuopanState
import com.luopan.compass.model.OverallConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure logic helpers extracted from [LuopanFragment].
 *
 * These tests cover:
 * - Lock-button enable logic (PM-Q01 fix — TSPEC §6.2 updateLockButton)
 * - Overlay visibility rule (TSPEC §6.2 updateZuoXiangOverlay)
 * - Display-bearing resolution (V3-F01 fix: displayXiangBearing ?: xiangBearing)
 * - Overlay text formatting (FSPEC §4a step 7a)
 * - Numeric readout bearing formatting (TSPEC §6.2 updateNumericReadout)
 *
 * All tests are pure JVM — no Android or Fragment dependencies.
 */
class LuopanFragmentLogicTest {

    // ---------------------------------------------------------------------------
    // Helpers — mirrors the static helpers declared in LuopanFragment
    // ---------------------------------------------------------------------------

    /**
     * Mirrors [LuopanFragment.isLockButtonEnabled]:
     * enabled when confidence is HIGH or MODERATE, OR when lock is already active
     * (PM-Q01 fix — allows "Clear 向" even at POOR confidence).
     */
    private fun isLockButtonEnabled(confidence: OverallConfidence, isLockActive: Boolean): Boolean {
        val canLock = confidence == OverallConfidence.HIGH || confidence == OverallConfidence.MODERATE
        return canLock || isLockActive
    }

    /**
     * Mirrors [LuopanFragment.lockButtonLabel].
     */
    private fun lockButtonLabel(isLockActive: Boolean): String =
        if (isLockActive) "Clear 向" else "Lock 向"

    /**
     * Mirrors [LuopanFragment.isOverlayVisible].
     */
    private fun isOverlayVisible(isLockActive: Boolean): Boolean = isLockActive

    /**
     * Mirrors [LuopanFragment.resolveDisplayXiangBearing] — V3-F01 fix:
     * prefer displayXiangBearing; fall back to xiangBearing when null.
     */
    private fun resolveDisplayXiangBearing(
        displayXiangBearing: Float?,
        xiangBearing: Float?
    ): Float? = displayXiangBearing ?: xiangBearing

    /**
     * Mirrors [LuopanFragment.resolveDisplayZuoBearing] — V3-F01 fix.
     */
    private fun resolveDisplayZuoBearing(
        displayZuoBearing: Float?,
        zuoBearing: Float?
    ): Float? = displayZuoBearing ?: zuoBearing

    /**
     * Mirrors [LuopanFragment.formatOverlayXiangLine]:
     * "向: [mountain] ([bearing]° [northLabel])"
     */
    private fun formatOverlayXiangLine(
        xiangMountain: String,
        displayBearing: Float,
        northLabel: String
    ): String = "向: $xiangMountain (%.1f° $northLabel)".format(displayBearing)

    /**
     * Mirrors [LuopanFragment.formatOverlayZuoLine]:
     * "坐: [mountain] ([bearing]° [northLabel])"
     */
    private fun formatOverlayZuoLine(
        zuoMountain: String,
        displayBearing: Float,
        northLabel: String
    ): String = "坐: $zuoMountain (%.1f° $northLabel)".format(displayBearing)

    /**
     * Mirrors [LuopanFragment.formatBearingDisplay]:
     * bearing to 1 decimal place with "°" suffix.
     */
    private fun formatBearingDisplay(bearingDeg: Float): String = "%.1f°".format(bearingDeg)

    // ---------------------------------------------------------------------------
    // Lock button — enable / disable (PM-Q01 fix, TSPEC §6.2, BR-05)
    // ---------------------------------------------------------------------------

    @Test
    fun `lock button is enabled when confidence is HIGH and lock is inactive`() {
        assertTrue(isLockButtonEnabled(OverallConfidence.HIGH, isLockActive = false))
    }

    @Test
    fun `lock button is enabled when confidence is MODERATE and lock is inactive`() {
        assertTrue(isLockButtonEnabled(OverallConfidence.MODERATE, isLockActive = false))
    }

    @Test
    fun `lock button is disabled when confidence is POOR and lock is inactive`() {
        assertFalse(isLockButtonEnabled(OverallConfidence.POOR, isLockActive = false))
    }

    @Test
    fun `lock button is disabled when confidence is STABILIZING and lock is inactive`() {
        assertFalse(isLockButtonEnabled(OverallConfidence.STABILIZING, isLockActive = false))
    }

    @Test
    fun `lock button is disabled when confidence is SENSOR_ERROR and lock is inactive`() {
        assertFalse(isLockButtonEnabled(OverallConfidence.SENSOR_ERROR, isLockActive = false))
    }

    /**
     * PM-Q01 fix: when a lock is already active, the button must remain enabled
     * even at POOR confidence so the user can still tap "Clear 向".
     */
    @Test
    fun `lock button is enabled when confidence is POOR but lock is active — PM-Q01 fix`() {
        assertTrue(isLockButtonEnabled(OverallConfidence.POOR, isLockActive = true))
    }

    @Test
    fun `lock button is enabled when confidence is STABILIZING but lock is active — PM-Q01 fix`() {
        assertTrue(isLockButtonEnabled(OverallConfidence.STABILIZING, isLockActive = true))
    }

    @Test
    fun `lock button is enabled when confidence is SENSOR_ERROR but lock is active — PM-Q01 fix`() {
        assertTrue(isLockButtonEnabled(OverallConfidence.SENSOR_ERROR, isLockActive = true))
    }

    @Test
    fun `lock button is enabled when confidence is HIGH and lock is active`() {
        assertTrue(isLockButtonEnabled(OverallConfidence.HIGH, isLockActive = true))
    }

    // ---------------------------------------------------------------------------
    // Lock button — label (TSPEC §6.2)
    // ---------------------------------------------------------------------------

    @Test
    fun `lock button label is Lock xiang when lock is inactive`() {
        assertEquals("Lock 向", lockButtonLabel(isLockActive = false))
    }

    @Test
    fun `lock button label is Clear xiang when lock is active`() {
        assertEquals("Clear 向", lockButtonLabel(isLockActive = true))
    }

    // ---------------------------------------------------------------------------
    // Overlay visibility (TSPEC §6.2 updateZuoXiangOverlay)
    // ---------------------------------------------------------------------------

    @Test
    fun `overlay is not visible when lock is inactive`() {
        assertFalse(isOverlayVisible(isLockActive = false))
    }

    @Test
    fun `overlay is visible when lock is active`() {
        assertTrue(isOverlayVisible(isLockActive = true))
    }

    // ---------------------------------------------------------------------------
    // Display-bearing resolution — V3-F01 fix (TSPEC §6.2 updateZuoXiangOverlay)
    // ---------------------------------------------------------------------------

    @Test
    fun `resolveDisplayXiangBearing returns displayXiangBearing when non-null`() {
        val result = resolveDisplayXiangBearing(displayXiangBearing = 48.5f, xiangBearing = 45.0f)
        assertEquals(48.5f, result)
    }

    @Test
    fun `resolveDisplayXiangBearing falls back to xiangBearing when displayXiangBearing is null`() {
        val result = resolveDisplayXiangBearing(displayXiangBearing = null, xiangBearing = 45.0f)
        assertEquals(45.0f, result)
    }

    @Test
    fun `resolveDisplayXiangBearing returns null when both are null`() {
        val result = resolveDisplayXiangBearing(displayXiangBearing = null, xiangBearing = null)
        assertNull(result)
    }

    @Test
    fun `resolveDisplayZuoBearing returns displayZuoBearing when non-null`() {
        val result = resolveDisplayZuoBearing(displayZuoBearing = 228.5f, zuoBearing = 225.0f)
        assertEquals(228.5f, result)
    }

    @Test
    fun `resolveDisplayZuoBearing falls back to zuoBearing when displayZuoBearing is null`() {
        val result = resolveDisplayZuoBearing(displayZuoBearing = null, zuoBearing = 225.0f)
        assertEquals(225.0f, result)
    }

    // ---------------------------------------------------------------------------
    // Overlay text formatting (FSPEC §4a step 7a, AC-07, AC-08, AC-10, AC-11, AC-23)
    // ---------------------------------------------------------------------------

    @Test
    fun `formatOverlayXiangLine produces correct format at 45 degrees True N — AC-07`() {
        val result = formatOverlayXiangLine("艮", 45.0f, "True N")
        assertEquals("向: 艮 (45.0° True N)", result)
    }

    @Test
    fun `formatOverlayZuoLine produces correct format at 225 degrees True N — AC-07`() {
        val result = formatOverlayZuoLine("坤", 225.0f, "True N")
        assertEquals("坐: 坤 (225.0° True N)", result)
    }

    @Test
    fun `formatOverlayXiangLine produces correct format at 90 degrees True N — AC-08`() {
        val result = formatOverlayXiangLine("卯", 90.0f, "True N")
        assertEquals("向: 卯 (90.0° True N)", result)
    }

    @Test
    fun `formatOverlayZuoLine produces correct format at 270 degrees True N — AC-08`() {
        val result = formatOverlayZuoLine("酉", 270.0f, "True N")
        assertEquals("坐: 酉 (270.0° True N)", result)
    }

    @Test
    fun `formatOverlayXiangLine produces correct format at 270 degrees True N — AC-10`() {
        val result = formatOverlayXiangLine("酉", 270.0f, "True N")
        assertEquals("向: 酉 (270.0° True N)", result)
    }

    @Test
    fun `formatOverlayZuoLine wraps correctly at 90 degrees True N — AC-10`() {
        val result = formatOverlayZuoLine("卯", 90.0f, "True N")
        assertEquals("坐: 卯 (90.0° True N)", result)
    }

    @Test
    fun `formatOverlayXiangLine produces correct format at 350 degrees True N — AC-11`() {
        val result = formatOverlayXiangLine("壬", 350.0f, "True N")
        assertEquals("向: 壬 (350.0° True N)", result)
    }

    @Test
    fun `formatOverlayZuoLine produces correct format at 170 degrees True N — AC-11`() {
        val result = formatOverlayZuoLine("丙", 170.0f, "True N")
        assertEquals("坐: 丙 (170.0° True N)", result)
    }

    @Test
    fun `formatOverlayXiangLine shows magnetic bearing when north type is Mag N — AC-23`() {
        // Locked at 45 True N, declination −3.5°: display = 45 − (−3.5) = 48.5
        val result = formatOverlayXiangLine("艮", 48.5f, "Mag N")
        assertEquals("向: 艮 (48.5° Mag N)", result)
    }

    @Test
    fun `formatOverlayZuoLine shows magnetic bearing when north type is Mag N — AC-23`() {
        // Locked 坐 at 225 True N, declination −3.5°: display = 225 − (−3.5) = 228.5
        val result = formatOverlayZuoLine("坤", 228.5f, "Mag N")
        assertEquals("坐: 坤 (228.5° Mag N)", result)
    }

    // ---------------------------------------------------------------------------
    // Numeric readout — bearing formatting (TSPEC §6.2 updateNumericReadout)
    // ---------------------------------------------------------------------------

    @Test
    fun `formatBearingDisplay formats to one decimal with degree symbol`() {
        assertEquals("180.0°", formatBearingDisplay(180.0f))
    }

    @Test
    fun `formatBearingDisplay formats fractional bearing correctly`() {
        assertEquals("45.5°", formatBearingDisplay(45.5f))
    }

    @Test
    fun `formatBearingDisplay formats zero correctly`() {
        assertEquals("0.0°", formatBearingDisplay(0.0f))
    }

    @Test
    fun `formatBearingDisplay formats near-360 correctly`() {
        assertEquals("359.9°", formatBearingDisplay(359.9f))
    }
}
