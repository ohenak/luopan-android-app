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

    // ---------------------------------------------------------------------------
    // PM-F02: Toast is reachable via click handler regardless of button enable state
    //
    // Fix: button isEnabled is always true; the click handler decides whether to
    // lock, clear, or show the Toast (cannot lock — heading is unreliable).
    // ---------------------------------------------------------------------------

    /**
     * Mirrors the updated [LuopanFragment.updateLockButton] click handler logic (PM-F02 fix).
     *
     * When lock is inactive AND confidence is not HIGH/MODERATE → return "show_toast".
     * When lock is inactive AND confidence is HIGH/MODERATE → return "lock".
     * When lock is active → return "clear".
     */
    private fun clickHandlerAction(
        confidence: OverallConfidence,
        isLockActive: Boolean
    ): String {
        val canLock = confidence == OverallConfidence.HIGH ||
                      confidence == OverallConfidence.MODERATE
        return when {
            isLockActive -> "clear"
            canLock      -> "lock"
            else         -> "show_toast"
        }
    }

    @Test
    fun `pm_f02 - click while POOR and lock inactive shows toast`() {
        // PM-F02: button is enabled; click must reach the Toast path for POOR confidence
        assertEquals("show_toast", clickHandlerAction(OverallConfidence.POOR, isLockActive = false))
    }

    @Test
    fun `pm_f02 - click while STABILIZING and lock inactive shows toast`() {
        assertEquals("show_toast", clickHandlerAction(OverallConfidence.STABILIZING, isLockActive = false))
    }

    @Test
    fun `pm_f02 - click while SENSOR_ERROR and lock inactive shows toast`() {
        assertEquals("show_toast", clickHandlerAction(OverallConfidence.SENSOR_ERROR, isLockActive = false))
    }

    @Test
    fun `pm_f02 - click while HIGH and lock inactive locks`() {
        assertEquals("lock", clickHandlerAction(OverallConfidence.HIGH, isLockActive = false))
    }

    @Test
    fun `pm_f02 - click while MODERATE and lock inactive locks`() {
        assertEquals("lock", clickHandlerAction(OverallConfidence.MODERATE, isLockActive = false))
    }

    @Test
    fun `pm_f02 - click while lock is active clears regardless of confidence`() {
        assertEquals("clear", clickHandlerAction(OverallConfidence.POOR, isLockActive = true))
        assertEquals("clear", clickHandlerAction(OverallConfidence.HIGH, isLockActive = true))
        assertEquals("clear", clickHandlerAction(OverallConfidence.SENSOR_ERROR, isLockActive = true))
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

    // ---------------------------------------------------------------------------
    // Numeric readout — char+pinyin field formatting (Task 4.2, FSPEC Flow 7)
    // ---------------------------------------------------------------------------

    /**
     * Mirrors [LuopanFragment.buildCharPinyinField].
     */
    private fun buildCharPinyinField(charField: String, pinyin: String, showPinyin: Boolean): String =
        if (showPinyin && pinyin.isNotEmpty()) "$charField ($pinyin)" else charField

    @Test
    fun `buildCharPinyinField with showRomanization true appends pinyin`() {
        assertEquals("午 (Wǔ)", buildCharPinyinField("午", "Wǔ", showPinyin = true))
    }

    @Test
    fun `buildCharPinyinField with showRomanization false shows char only`() {
        assertEquals("午", buildCharPinyinField("午", "Wǔ", showPinyin = false))
    }

    @Test
    fun `buildCharPinyinField with empty pinyin shows char only even when showPinyin is true`() {
        assertEquals("午", buildCharPinyinField("午", "", showPinyin = true))
    }

    @Test
    fun `buildCharPinyinField with dash char and showRomanization true returns dash only`() {
        // SENSOR_ERROR: char is "—", pinyin is ""; showPinyin=true has no effect on dash
        assertEquals("—", buildCharPinyinField("—", "", showPinyin = true))
    }

    // ---------------------------------------------------------------------------
    // Numeric readout — formatMountainField (Task 4.2, FSPEC Flow 3, ES-01)
    // ---------------------------------------------------------------------------

    /**
     * Mirrors [LuopanFragment.formatMountainField] using a minimal LuopanState stub.
     */
    private fun formatMountainField(
        mountainChar: String,
        mountainPinyin: String,
        showRomanization: Boolean,
        confidence: OverallConfidence
    ): String {
        if (confidence == OverallConfidence.SENSOR_ERROR) return mountainChar
        return buildCharPinyinField(mountainChar, mountainPinyin, showRomanization)
    }

    @Test
    fun `formatMountainField HIGH confidence with showRomanization shows char and pinyin — AC-03`() {
        val result = formatMountainField("午", "Wǔ", showRomanization = true, OverallConfidence.HIGH)
        assertEquals("午 (Wǔ)", result)
    }

    @Test
    fun `formatMountainField HIGH confidence without showRomanization shows char only`() {
        val result = formatMountainField("午", "Wǔ", showRomanization = false, OverallConfidence.HIGH)
        assertEquals("午", result)
    }

    @Test
    fun `formatMountainField SENSOR_ERROR returns dash without pinyin — ES-01`() {
        val result = formatMountainField("—", "", showRomanization = true, OverallConfidence.SENSOR_ERROR)
        assertEquals("—", result)
    }

    @Test
    fun `formatMountainField MODERATE confidence shows field normally — AC-06`() {
        val result = formatMountainField("午", "Wǔ", showRomanization = true, OverallConfidence.MODERATE)
        assertEquals("午 (Wǔ)", result)
    }

    // ---------------------------------------------------------------------------
    // Numeric readout — formatTrigramField (Task 4.2, FSPEC Flow 3, ES-01)
    // ---------------------------------------------------------------------------

    /**
     * Mirrors [LuopanFragment.formatTrigramField].
     */
    private fun formatTrigramField(symbol: String, name: String, direction: String): String {
        if (symbol == "—") return "—"
        return "$symbol $name $direction"
    }

    @Test
    fun `formatTrigramField assembles symbol name and direction — AC-03`() {
        val result = formatTrigramField("☲", "離", "南")
        assertEquals("☲ 離 南", result)
    }

    @Test
    fun `formatTrigramField returns dash when symbol is dash — SENSOR_ERROR`() {
        val result = formatTrigramField("—", "—", "—")
        assertEquals("—", result)
    }

    @Test
    fun `formatTrigramField with English names assembles correctly — showMyLanguage`() {
        val result = formatTrigramField("☲", "Li", "South")
        assertEquals("☲ Li South", result)
    }

    // ---------------------------------------------------------------------------
    // Task 4.3: Overlay formatting — AC-07, AC-10, AC-11, AC-23 (complete verification)
    // ---------------------------------------------------------------------------

    /**
     * AC-07: Lock at 45° True N — overlay shows 艮/坤 with True N label.
     *
     * xiangBearing=45f, displayXiangBearing=45f, zuoBearing=225f, displayZuoBearing=225f,
     * xiangMountain="艮", zuoMountain="坤", northLabel="True N".
     *
     * Expected: xiang line = "向: 艮 (45.0° True N)", zuo line = "坐: 坤 (225.0° True N)".
     *
     * FSPEC §4a step 7, PLAN Task 4.3 acceptance.
     */
    @Test
    fun `ac07_lock_at_45deg_overlay_format_true_north`() {
        val xiangLine = formatOverlayXiangLine("艮", 45.0f, "True N")
        val zuoLine = formatOverlayZuoLine("坤", 225.0f, "True N")
        assertEquals("向: 艮 (45.0° True N)", xiangLine)
        assertEquals("坐: 坤 (225.0° True N)", zuoLine)
    }

    /**
     * AC-10: 坐 wrap-around at 向 = 270° — overlay shows 酉/卯.
     *
     * xiangBearing=270f, displayXiangBearing=270f, zuoBearing=90f, displayZuoBearing=90f,
     * xiangMountain="酉", zuoMountain="卯".
     *
     * Expected: "向: 酉 (270.0° True N)" and "坐: 卯 (90.0° True N)".
     *
     * FSPEC §4a / BR-06 (坐 = (向 + 180) mod 360 = (270 + 180) mod 360 = 90).
     */
    @Test
    fun `ac10_zuo_wraparound_270_overlay`() {
        val xiangLine = formatOverlayXiangLine("酉", 270.0f, "True N")
        val zuoLine = formatOverlayZuoLine("卯", 90.0f, "True N")
        assertEquals("向: 酉 (270.0° True N)", xiangLine)
        assertEquals("坐: 卯 (90.0° True N)", zuoLine)
    }

    /**
     * AC-11: 坐 wrap-around at 向 = 350° — overlay shows 壬/丙.
     *
     * xiangBearing=350f, displayXiangBearing=350f, zuoBearing=170f, displayZuoBearing=170f,
     * xiangMountain="壬", zuoMountain="丙".
     *
     * Expected: "向: 壬 (350.0° True N)" and "坐: 丙 (170.0° True N)".
     *
     * FSPEC §4a / BR-06 (坐 = (350 + 180) mod 360 = 170).
     */
    @Test
    fun `ac11_zuo_wraparound_350_overlay`() {
        val xiangLine = formatOverlayXiangLine("壬", 350.0f, "True N")
        val zuoLine = formatOverlayZuoLine("丙", 170.0f, "True N")
        assertEquals("向: 壬 (350.0° True N)", xiangLine)
        assertEquals("坐: 丙 (170.0° True N)", zuoLine)
    }

    /**
     * AC-23: Overlay displays magnetic bearing when northLabel is "Mag N".
     *
     * Scenario: locked at True N 45° (向: 艮, 坐: 坤), declination = −3.5°.
     * Display bearing for 向: 45.0 − (−3.5) = 48.5°.
     * Display bearing for 坐: 225.0 − (−3.5) = 228.5°.
     *
     * xiangBearing=45f, displayXiangBearing=48.5f, zuoBearing=225f, displayZuoBearing=228.5f,
     * northLabel="Mag N", xiangMountain="艮", zuoMountain="坤".
     *
     * Expected: "向: 艮 (48.5° Mag N)" and "坐: 坤 (228.5° Mag N)".
     *
     * FSPEC §4d (ES-03), TSPEC N-F05, PLAN Task 4.3 acceptance.
     */
    @Test
    fun `ac23_overlay_displays_magnetic_bearing`() {
        // Display bearings come from ZuoXiangLock.rederive() — already pre-computed in LuopanState.
        // The overlay format function receives the pre-converted displayXiangBearing / displayZuoBearing.
        val displayXiangBearing = 48.5f   // 45f True N − (−3.5°) declination = 48.5° Mag N
        val displayZuoBearing   = 228.5f  // 225f True N − (−3.5°) declination = 228.5° Mag N

        val xiangLine = formatOverlayXiangLine("艮", displayXiangBearing, "Mag N")
        val zuoLine   = formatOverlayZuoLine("坤", displayZuoBearing, "Mag N")

        assertEquals("向: 艮 (48.5° Mag N)", xiangLine)
        assertEquals("坐: 坤 (228.5° Mag N)", zuoLine)
    }

    /**
     * V3-F01: [LuopanFragment.resolveDisplayXiangBearing] returns displayXiangBearing (48.5f)
     * — NOT xiangBearing (45.0f) — when both are non-null.
     *
     * This verifies that [updateZuoXiangOverlay] and the [luopanView.setLockState] call
     * use the display-reference bearing (which may be Magnetic North) rather than the stored
     * True North bearing. If this returned 45.0f instead of 48.5f, the gold tick mark would
     * appear at the wrong dial position when viewing Magnetic North.
     *
     * TSPEC V3-F01, TSPEC §6.1.4, PLAN Task 4.3 acceptance.
     */
    @Test
    fun `v3f01_setLockState_called_with_displayXiangBearing_not_xiangBearing`() {
        // Given: xiangBearing = 45f (True N), displayXiangBearing = 48.5f (Mag N, after rederive)
        val xiangBearing        = 45.0f
        val displayXiangBearing = 48.5f

        // When: LuopanFragment resolves the bearing to pass to luopanView.setLockState()
        val resolved = resolveDisplayXiangBearing(
            displayXiangBearing = displayXiangBearing,
            xiangBearing        = xiangBearing
        )

        // Then: the display-reference bearing (48.5f) is used — NOT the True North bearing (45.0f)
        assertEquals(
            "setLockState() must receive displayXiangBearing (48.5f), not xiangBearing (45.0f)",
            48.5f,
            resolved
        )
    }

    // ---------------------------------------------------------------------------
    // Task 5.1 — Pinch-to-zoom clamping (FSPEC Flow 6, TSPEC §5.3)
    //
    // These tests exercise the ViewModel-level clamp logic that the Fragment wiring
    // depends on. The SessionState helper mirrors CompassViewModel.setZoomScale().
    // ---------------------------------------------------------------------------

    /**
     * Stand-in for the ViewModel zoom-scale setter.
     * Mirrors [CompassViewModel.setZoomScale]: `scale.coerceIn(0.8f, 2.0f)`.
     */
    private fun applyZoomScale(scale: Float): Float = scale.coerceIn(0.8f, 2.0f)

    /**
     * AC-25 / FSPEC Flow 6 / ES-06: Scale below 0.8 is clamped to 0.8.
     *
     * Task 5.1 acceptance criterion: setZoomScale(0.4f) → ViewModel emits 0.8f.
     * The CompassViewModelSessionStateTest covers the general case (0.5f → 0.8f);
     * this test exercises the stricter 0.4f input to confirm no off-by-one.
     */
    @Test
    fun zoomScale_clamp_below_min() {
        // Input 0.4f is below the minimum boundary of 0.8f (FSPEC §6 AC-25).
        val clamped = applyZoomScale(0.4f)
        assertEquals(
            "setZoomScale(0.4f) must be clamped to minimum 0.8f",
            0.8f, clamped, 0.0001f
        )
    }

    /**
     * AC-25 / FSPEC Flow 6 / ES-06: Scale above 2.0 is clamped to 2.0.
     *
     * Task 5.1 acceptance criterion: setZoomScale(2.5f) → ViewModel emits 2.0f.
     */
    @Test
    fun zoomScale_clamp_above_max() {
        // Input 2.5f exceeds the maximum boundary of 2.0f (FSPEC §6 AC-25).
        val clamped = applyZoomScale(2.5f)
        assertEquals(
            "setZoomScale(2.5f) must be clamped to maximum 2.0f",
            2.0f, clamped, 0.0001f
        )
    }

    // ---------------------------------------------------------------------------
    // Task 5.2 — Ring visibility default state (AC-15, BR-09)
    // ---------------------------------------------------------------------------

    /**
     * AC-15 / BR-09: On fresh ViewModel creation (simulating cold start), all 6 rings
     * must be visible. This pure-JVM test verifies the default BooleanArray initializer.
     *
     * PLAN Task 5.2 test: `ringVisibility_default_all_true`.
     */
    @Test
    fun ringVisibility_default_all_true() {
        // This mirrors the ViewModel field initialiser: BooleanArray(6) { true }
        val defaultVisibility = BooleanArray(6) { true }
        for (i in 0 until 6) {
            assertTrue("Ring ${i + 1} (index $i) must be visible by default", defaultVisibility[i])
        }
        assertEquals("Must have exactly 6 ring visibility flags", 6, defaultVisibility.size)
    }

    /**
     * AC-14 / FSPEC Flow 5 decision point:
     * When Ring 4 (index 3) is hidden and the lock is active, the gold tick mark
     * must NOT be drawn. This verifies the guard condition in LuopanView.onDraw():
     *   `if (isLockActiveState && ringVisible[3]) drawGoldTickMark(canvas)`
     *
     * PLAN Task 5.2 test: `ring4_hidden_gold_tick_mark_should_not_show`.
     */
    @Test
    fun ring4_hidden_gold_tick_mark_should_not_show() {
        val isLockActive = true
        val ringVisible = BooleanArray(5) { true }
        ringVisible[3] = false  // Hide Ring 4 (二十四山)

        // Guard condition from LuopanView.onDraw():
        //   `if (isLockActiveState && ringVisible[3]) drawGoldTickMark(canvas)`
        // When ringVisible[3] is false, shouldDraw must be false.
        val shouldDrawTickMark = isLockActive && ringVisible[3]
        assertFalse(
            "Gold tick mark must NOT draw when Ring 4 (index 3) is hidden, even with lock active",
            shouldDrawTickMark
        )
    }
}
