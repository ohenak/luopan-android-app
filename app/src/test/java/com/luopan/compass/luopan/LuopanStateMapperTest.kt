package com.luopan.compass.luopan

import com.luopan.compass.model.CalDotColor
import com.luopan.compass.model.InterferenceState
import com.luopan.compass.model.NorthType
import com.luopan.compass.model.OverallConfidence
import com.luopan.compass.model.SensorState
import com.luopan.compass.sensor.InterferenceMetrics
import com.luopan.compass.ui.CompassUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Unit tests for [LuopanStateMapper].
 *
 * All tests are pure JVM — no Android dependencies.
 *
 * Test cases per PLAN Task 2.1 acceptance criteria.
 */
class LuopanStateMapperTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun compassState(
        headingDeg: Double,
        confidence: OverallConfidence,
        northType: NorthType = NorthType.TRUE,
        northLabel: String = "True N",
        declinationDeg: Float = 0f
    ): CompassUiState = CompassUiState(
        heading_deg = headingDeg,
        heading_formatted = "%.1f°".format(headingDeg),
        north_label = northLabel,
        north_type = northType,
        declination_deg = declinationDeg,
        confidence = confidence,
        interference_state = InterferenceState.CLEAR,
        interference_metrics = null,
        tilt_deg = 0.0,
        tilt_text = null,
        calibration_age_days = 0L,
        calibration_age_label = "Today",
        cal_dot_color = CalDotColor.GREEN,
        power_saving_advisory = false,
        no_gyroscope_advisory = false,
        fallback_mag_advisory = false,
        location_fallback_advisory = false,
        extreme_latitude_advisory = false,
        location_cache_age_label = null,
        sensor_state = SensorState.NORMAL,
        is_stabilizing = false,
        last_valid_heading_deg = null,
        show_calibration_cta = false
    )

    private fun lockedAt45(): ZuoXiangLock.LockState = ZuoXiangLock.LockState(
        xiangBearing = 45f,
        zuoBearing = 225f,
        xiangMountain = "艮",
        zuoMountain = "坤",
        displayXiangBearing = 45f,
        displayZuoBearing = 225f
    )

    // -------------------------------------------------------------------------
    // AC: mapper_high_180_all_fields_correct
    // -------------------------------------------------------------------------

    @Test
    fun `mapper_high_180_all_fields_correct`() {
        val state = compassState(headingDeg = 180.0, confidence = OverallConfidence.HIGH)

        val result = LuopanStateMapper.map(
            compassState = state,
            lockState = null,
            showRomanization = true,
            showMyLanguage = false
        )

        assertEquals("午", result.mountainChar)
        assertEquals("午", result.earthlyBranchChar)
        assertEquals("☲ 離 南", result.trigramSymbol)
        assertEquals("離", result.trigramName)
        assertEquals("南", result.trigramDirection)
        assertNotNull("fenJinLabel should be non-null at HIGH confidence", result.fenJinLabel)
        // 180° → 壬午分金 [178°, 184°) — FSPEC §4.7 key assertion
        assertEquals("壬午分金", result.fenJinLabel)
        assertEquals(OverallConfidence.HIGH, result.confidence)
        assertEquals("True N", result.northLabel)
        assertEquals(180f, result.bearingDeg, 0.001f)
    }

    // -------------------------------------------------------------------------
    // AC: mapper_moderate_fenjin_null
    // -------------------------------------------------------------------------

    @Test
    fun `mapper_moderate_fenjin_null`() {
        val state = compassState(headingDeg = 180.0, confidence = OverallConfidence.MODERATE)

        val result = LuopanStateMapper.map(
            compassState = state,
            lockState = null,
            showRomanization = false,
            showMyLanguage = false
        )

        assertNull("fenJinLabel must be null at MODERATE confidence", result.fenJinLabel)
        // Character fields should still be populated
        assertNotEquals("—", result.mountainChar)
    }

    // -------------------------------------------------------------------------
    // AC: mapper_poor_characters_populated
    // -------------------------------------------------------------------------

    @Test
    fun `mapper_poor_characters_populated`() {
        val state = compassState(headingDeg = 180.0, confidence = OverallConfidence.POOR)

        val result = LuopanStateMapper.map(
            compassState = state,
            lockState = null,
            showRomanization = false,
            showMyLanguage = false
        )

        assertNotEquals("mountainChar should be populated at POOR", "—", result.mountainChar)
        assertNotEquals("earthlyBranchChar should be populated at POOR", "—", result.earthlyBranchChar)
        assertNull("fenJinLabel must be null at POOR", result.fenJinLabel)
    }

    // -------------------------------------------------------------------------
    // AC: mapper_stabilizing_characters_populated_fenjin_null
    // -------------------------------------------------------------------------

    @Test
    fun `mapper_stabilizing_characters_populated_fenjin_null`() {
        val state = compassState(headingDeg = 180.0, confidence = OverallConfidence.STABILIZING)

        val result = LuopanStateMapper.map(
            compassState = state,
            lockState = null,
            showRomanization = false,
            showMyLanguage = false
        )

        // STABILIZING: dial keeps updating, sectors still reflect live bearing
        assertNotEquals("mountainChar should update during STABILIZING", "—", result.mountainChar)
        assertNotEquals("earthlyBranchChar should update during STABILIZING", "—", result.earthlyBranchChar)
        assertNull("fenJinLabel must be null during STABILIZING", result.fenJinLabel)
    }

    // -------------------------------------------------------------------------
    // AC: mapper_sensor_error_all_dashes
    // -------------------------------------------------------------------------

    @Test
    fun `mapper_sensor_error_all_dashes`() {
        val state = compassState(headingDeg = 180.0, confidence = OverallConfidence.SENSOR_ERROR)

        val result = LuopanStateMapper.map(
            compassState = state,
            lockState = null,
            showRomanization = false,
            showMyLanguage = false
        )

        assertEquals("mountainChar must be '—' on SENSOR_ERROR", "—", result.mountainChar)
        assertEquals("earthlyBranchChar must be '—' on SENSOR_ERROR", "—", result.earthlyBranchChar)
        assertEquals("trigramName must be '—' on SENSOR_ERROR", "—", result.trigramName)
        assertEquals("trigramDirection must be '—' on SENSOR_ERROR", "—", result.trigramDirection)
        assertNull("fenJinLabel must be null on SENSOR_ERROR", result.fenJinLabel)
        assertEquals(OverallConfidence.SENSOR_ERROR, result.confidence)
    }

    // -------------------------------------------------------------------------
    // AC: mapper_sensor_error_lock_state_preserved
    // -------------------------------------------------------------------------

    @Test
    fun `mapper_sensor_error_lock_state_preserved`() {
        val state = compassState(headingDeg = 180.0, confidence = OverallConfidence.SENSOR_ERROR)
        val lockState = lockedAt45()

        val result = LuopanStateMapper.map(
            compassState = state,
            lockState = lockState,
            showRomanization = false,
            showMyLanguage = false
        )

        // Character fields are dashes due to SENSOR_ERROR
        assertEquals("—", result.mountainChar)
        // Lock fields are preserved from lockState even during SENSOR_ERROR
        assertTrue("isLockActive must be true when lockState non-null", result.isLockActive)
        assertNotNull("xiangMountain must be non-null when locked", result.xiangMountain)
        assertNotNull("zuoMountain must be non-null when locked", result.zuoMountain)
        assertEquals("艮", result.xiangMountain)
        assertEquals("坤", result.zuoMountain)
    }

    // -------------------------------------------------------------------------
    // AC: mapper_show_my_language_uses_english
    // -------------------------------------------------------------------------

    @Test
    fun `mapper_show_my_language_uses_english`() {
        // bearing = 180° → Ring 3 = ☲ 離 南 → English: "Li · South"
        val state = compassState(headingDeg = 180.0, confidence = OverallConfidence.HIGH)

        val result = LuopanStateMapper.map(
            compassState = state,
            lockState = null,
            showRomanization = false,
            showMyLanguage = true
        )

        // Ring 3 English — FSPEC §4.8 Ring 3 table: 離 南 → "Li · South"
        // The trigramSymbol (☲) is always retained, but trigramName/trigramDirection use English
        assertNotEquals(
            "trigramName should not be '離' when showMyLanguage=true",
            "離",
            result.trigramName
        )
        // English equivalent from LabelData.english for ring3Labels[4] is "Li · South"
        // trigramName and trigramDirection are split from the English label
        assertTrue(
            "trigramName or result should reflect English when showMyLanguage=true",
            result.mountainChar != "午" || result.trigramName != "離"
        )

        // Ring 5 at 180° → 午 → English: "Horse"
        assertEquals("mountainChar should be English 'Horse' when showMyLanguage=true", "Horse", result.mountainChar)
        // Ring 4 at 180° → 午 → English: "Horse"
        assertEquals("earthlyBranchChar should be English 'Horse' when showMyLanguage=true", "Horse", result.earthlyBranchChar)
    }

    // -------------------------------------------------------------------------
    // AC: mapper_romanization_off_pinyin_empty
    // -------------------------------------------------------------------------

    @Test
    fun `mapper_romanization_off_pinyin_empty`() {
        val state = compassState(headingDeg = 180.0, confidence = OverallConfidence.HIGH)

        val result = LuopanStateMapper.map(
            compassState = state,
            lockState = null,
            showRomanization = false,
            showMyLanguage = false
        )

        assertEquals("mountainPinyin must be empty when showRomanization=false", "", result.mountainPinyin)
        assertEquals("earthlyBranchPinyin must be empty when showRomanization=false", "", result.earthlyBranchPinyin)
    }

    @Test
    fun `mapper_romanization_on_pinyin_populated`() {
        val state = compassState(headingDeg = 180.0, confidence = OverallConfidence.HIGH)

        val result = LuopanStateMapper.map(
            compassState = state,
            lockState = null,
            showRomanization = true,
            showMyLanguage = false
        )

        // 180° → 午 → pinyin "Wǔ"
        assertEquals("Wǔ", result.mountainPinyin)
        assertEquals("Wǔ", result.earthlyBranchPinyin)
    }

    // -------------------------------------------------------------------------
    // AC: northSwitch_doesNotChangeShanLabels
    // -------------------------------------------------------------------------

    @Test
    fun `northSwitch_doesNotChangeShanLabels`() {
        // lockState has xiangBearing=45f → 艮
        // compassState has heading_deg=60.0 → Ring 5 sector 寅 (52.5°–67.5°)
        // This proves the mapper reads 山 labels from lockState (45°→艮), NOT from heading_deg (60°→寅)
        val lockState = lockedAt45()
        val state = compassState(
            headingDeg = 60.0,
            confidence = OverallConfidence.HIGH,
            northType = NorthType.MAGNETIC,
            northLabel = "Mag N"
        )

        val result = LuopanStateMapper.map(
            compassState = state,
            lockState = lockState,
            showRomanization = false,
            showMyLanguage = false
        )

        // Live mountainChar uses heading_deg (60° → 寅 sector [52.5°, 67.5°))
        assertEquals("Live mountainChar should be 寅 from heading_deg=60", "寅", result.mountainChar)
        // Lock mountain labels from lockState (45° → 艮) — NOT from heading_deg
        assertEquals("xiangMountain must come from lockState (45°=艮), not heading_deg", "艮", result.xiangMountain)
        assertEquals("zuoMountain must come from lockState (225°=坤), not heading_deg", "坤", result.zuoMountain)
    }

    // -------------------------------------------------------------------------
    // AC: mapper_sensorError_while_locked_fieldsAreDashes_lockRemains
    // -------------------------------------------------------------------------

    @Test
    fun `mapper_sensorError_while_locked_fieldsAreDashes_lockRemains`() {
        val lockState = ZuoXiangLock.LockState(
            xiangBearing = 45f,
            zuoBearing = 225f,
            xiangMountain = "艮",
            zuoMountain = "坤",
            displayXiangBearing = 45f,
            displayZuoBearing = 225f
        )
        val state = compassState(headingDeg = 180.0, confidence = OverallConfidence.SENSOR_ERROR)

        val result = LuopanStateMapper.map(
            compassState = state,
            lockState = lockState,
            showRomanization = false,
            showMyLanguage = false
        )

        assertEquals("mountainChar must be '—' during SENSOR_ERROR even while locked", "—", result.mountainChar)
        assertTrue("isLockActive must remain true during SENSOR_ERROR while locked", result.isLockActive)
        assertEquals("xiangMountain must remain '艮' during SENSOR_ERROR", "艮", result.xiangMountain)
        assertEquals("zuoMountain must remain '坤' during SENSOR_ERROR", "坤", result.zuoMountain)
        assertEquals("xiangBearing must remain 45f during SENSOR_ERROR", 45f, result.xiangBearing!!, 0.001f)
    }

    // -------------------------------------------------------------------------
    // Additional edge cases: unlocked state
    // -------------------------------------------------------------------------

    @Test
    fun `mapper_unlocked_lock_fields_are_defaults`() {
        val state = compassState(headingDeg = 90.0, confidence = OverallConfidence.HIGH)

        val result = LuopanStateMapper.map(
            compassState = state,
            lockState = null,
            showRomanization = false,
            showMyLanguage = false
        )

        assertTrue("isLockActive must be false when lockState is null", !result.isLockActive)
        assertNull(result.xiangBearing)
        assertNull(result.zuoBearing)
        assertNull(result.xiangMountain)
        assertNull(result.zuoMountain)
        assertNull(result.displayXiangBearing)
        assertNull(result.displayZuoBearing)
    }

    // -------------------------------------------------------------------------
    // Additional: north label pass-through
    // -------------------------------------------------------------------------

    @Test
    fun `mapper_northLabel_magnetic_passthrough`() {
        val state = compassState(
            headingDeg = 90.0,
            confidence = OverallConfidence.HIGH,
            northType = NorthType.MAGNETIC,
            northLabel = "Mag N"
        )

        val result = LuopanStateMapper.map(
            compassState = state,
            lockState = null,
            showRomanization = false,
            showMyLanguage = false
        )

        assertEquals("Mag N", result.northLabel)
    }

    // -------------------------------------------------------------------------
    // Additional: declinationDeg pass-through
    // -------------------------------------------------------------------------

    @Test
    fun `mapper_declinationDeg_passthrough`() {
        val state = compassState(
            headingDeg = 45.0,
            confidence = OverallConfidence.HIGH,
            declinationDeg = -3.5f
        )

        val result = LuopanStateMapper.map(
            compassState = state,
            lockState = null,
            showRomanization = false,
            showMyLanguage = false
        )

        assertEquals(-3.5f, result.declinationDeg, 0.001f)
    }

    // -------------------------------------------------------------------------
    // Additional: trigramSymbol always retained even with showMyLanguage
    // -------------------------------------------------------------------------

    @Test
    fun `mapper_trigramSymbol_retained_in_all_language_modes`() {
        val state = compassState(headingDeg = 180.0, confidence = OverallConfidence.HIGH)

        val resultChinese = LuopanStateMapper.map(state, null, false, false)
        val resultEnglish = LuopanStateMapper.map(state, null, false, true)

        // trigramSymbol is always the full Ring 3 character field "☲ 離 南"
        // When showMyLanguage, only trigramName and trigramDirection switch to English
        // The symbol portion "☲" is always present in trigramSymbol
        assertTrue("trigramSymbol must contain ☲", resultChinese.trigramSymbol.contains("☲"))
        assertTrue("trigramSymbol must still contain ☲ in English mode", resultEnglish.trigramSymbol.contains("☲"))
    }

    // =========================================================================
    // Task 6.1 — Localization toggle tests (BR-08, Flow 7, REQ §5.8)
    // =========================================================================

    // -------------------------------------------------------------------------
    // mapper_showMyLanguage_false_uses_chinese
    // BR-08: default (showMyLanguage=false) always returns zh-Hant characters,
    // regardless of any system-locale setting.
    // -------------------------------------------------------------------------

    @Test
    fun `mapper_showMyLanguage_false_uses_chinese`() {
        // bearing 180° → Ring 5 sector 午, Ring 4 sector 午
        val state = compassState(headingDeg = 180.0, confidence = OverallConfidence.HIGH)

        val result = LuopanStateMapper.map(
            compassState = state,
            lockState = null,
            showRomanization = false,
            showMyLanguage = false
        )

        // Default: Chinese characters, not English
        assertEquals("mountainChar must be zh-Hant '午' when showMyLanguage=false", "午", result.mountainChar)
        assertEquals("earthlyBranchChar must be zh-Hant '午' when showMyLanguage=false", "午", result.earthlyBranchChar)
        // Ring 3 name/direction must also be Chinese
        assertEquals("trigramName must be zh-Hant '離' when showMyLanguage=false", "離", result.trigramName)
        assertEquals("trigramDirection must be zh-Hant '南' when showMyLanguage=false", "南", result.trigramDirection)
    }

    // -------------------------------------------------------------------------
    // mapper_showMyLanguage_ring3_english
    // showMyLanguage=true → Ring 3 trigramName/trigramDirection use English
    // bearing 180° → ☲ 離 南 → english "Li · South" → name="Li", direction="South"
    // -------------------------------------------------------------------------

    @Test
    fun `mapper_showMyLanguage_ring3_english`() {
        val state = compassState(headingDeg = 180.0, confidence = OverallConfidence.HIGH)

        val result = LuopanStateMapper.map(
            compassState = state,
            lockState = null,
            showRomanization = false,
            showMyLanguage = true
        )

        // English equivalents from FSPEC §4.8 Ring 3: 離 南 → "Li · South"
        assertEquals("trigramName must be 'Li' when showMyLanguage=true at 180°", "Li", result.trigramName)
        assertEquals("trigramDirection must be 'South' when showMyLanguage=true at 180°", "South", result.trigramDirection)
        // trigramSymbol (the full character field "☲ 離 南") is always retained
        assertTrue("trigramSymbol must still be zh-Hant full string", result.trigramSymbol.contains("☲"))
    }

    // -------------------------------------------------------------------------
    // mapper_showMyLanguage_ring4_english
    // showMyLanguage=true → Ring 4 uses English zodiac name
    // bearing 180° → 午 → english "Horse"
    // -------------------------------------------------------------------------

    @Test
    fun `mapper_showMyLanguage_ring4_english`() {
        val state = compassState(headingDeg = 180.0, confidence = OverallConfidence.HIGH)

        val result = LuopanStateMapper.map(
            compassState = state,
            lockState = null,
            showRomanization = false,
            showMyLanguage = true
        )

        // FSPEC §4.8 Ring 4: 午 → "Horse"
        assertEquals("earthlyBranchChar must be 'Horse' when showMyLanguage=true at 180°", "Horse", result.earthlyBranchChar)
        assertNotEquals("earthlyBranchChar must NOT be '午' when showMyLanguage=true", "午", result.earthlyBranchChar)
    }

    // -------------------------------------------------------------------------
    // mapper_showMyLanguage_ring5_english
    // showMyLanguage=true → Ring 5 uses English mountain label
    // bearing 180° → 午 (index 13) → english "Horse"
    // -------------------------------------------------------------------------

    @Test
    fun `mapper_showMyLanguage_ring5_english`() {
        val state = compassState(headingDeg = 180.0, confidence = OverallConfidence.HIGH)

        val result = LuopanStateMapper.map(
            compassState = state,
            lockState = null,
            showRomanization = false,
            showMyLanguage = true
        )

        // FSPEC §4.8 Ring 5: 午 → "Horse"
        assertEquals("mountainChar must be 'Horse' when showMyLanguage=true at 180°", "Horse", result.mountainChar)
        assertNotEquals("mountainChar must NOT be '午' when showMyLanguage=true", "午", result.mountainChar)
    }

    // -------------------------------------------------------------------------
    // mapper_showRomanization_true_populates_pinyin
    // showRomanization=true → mountainPinyin is non-empty
    // bearing 180° → 午 → pinyin "Wǔ"
    // -------------------------------------------------------------------------

    @Test
    fun `mapper_showRomanization_true_populates_pinyin`() {
        val state = compassState(headingDeg = 180.0, confidence = OverallConfidence.HIGH)

        val result = LuopanStateMapper.map(
            compassState = state,
            lockState = null,
            showRomanization = true,
            showMyLanguage = false
        )

        // Ring 5 at 180° → 午 → pinyin "Wǔ"
        assertTrue("mountainPinyin must be non-empty when showRomanization=true", result.mountainPinyin.isNotEmpty())
        assertEquals("mountainPinyin must be 'Wǔ' for 午 at 180°", "Wǔ", result.mountainPinyin)
        // Ring 4 at 180° → 午 → pinyin "Wǔ"
        assertEquals("earthlyBranchPinyin must be 'Wǔ' for 午 at 180°", "Wǔ", result.earthlyBranchPinyin)
    }

    // -------------------------------------------------------------------------
    // mapper_showRomanization_false_empty_pinyin
    // showRomanization=false → all pinyin fields are empty string
    // -------------------------------------------------------------------------

    @Test
    fun `mapper_showRomanization_false_empty_pinyin`() {
        val state = compassState(headingDeg = 180.0, confidence = OverallConfidence.HIGH)

        val result = LuopanStateMapper.map(
            compassState = state,
            lockState = null,
            showRomanization = false,
            showMyLanguage = false
        )

        assertEquals("mountainPinyin must be '' when showRomanization=false", "", result.mountainPinyin)
        assertEquals("earthlyBranchPinyin must be '' when showRomanization=false", "", result.earthlyBranchPinyin)
    }

    // -------------------------------------------------------------------------
    // mapper_both_toggles_on
    // showRomanization=true AND showMyLanguage=true → English char + pinyin both present
    // bearing 180° → Ring 5: char="Horse", pinyin="Wǔ"
    //                Ring 4: char="Horse", pinyin="Wǔ"
    //                Ring 3: name="Li", direction="South"
    // -------------------------------------------------------------------------

    @Test
    fun `mapper_both_toggles_on`() {
        val state = compassState(headingDeg = 180.0, confidence = OverallConfidence.HIGH)

        val result = LuopanStateMapper.map(
            compassState = state,
            lockState = null,
            showRomanization = true,
            showMyLanguage = true
        )

        // English labels present
        assertEquals("mountainChar must be English 'Horse'", "Horse", result.mountainChar)
        assertEquals("earthlyBranchChar must be English 'Horse'", "Horse", result.earthlyBranchChar)
        assertEquals("trigramName must be English 'Li'", "Li", result.trigramName)
        assertEquals("trigramDirection must be English 'South'", "South", result.trigramDirection)

        // Pinyin also present (both toggles ON simultaneously — FSPEC Flow 7)
        assertEquals("mountainPinyin must be 'Wǔ' with romanization ON", "Wǔ", result.mountainPinyin)
        assertEquals("earthlyBranchPinyin must be 'Wǔ' with romanization ON", "Wǔ", result.earthlyBranchPinyin)
    }

    // -------------------------------------------------------------------------
    // mapper_english_fallback_uses_chinese_when_empty
    // When LabelData.english is empty, the mapper falls back to LabelData.character.
    // Ring 6 (六十分金) has empty english by design (no §5.8 mapping exists for Ring 6).
    // Even with showMyLanguage=true, fenJinLabel always uses the Ring 6 character field.
    // Bearing 180° → 壬午分金 (HIGH confidence) — confirmed Chinese label.
    // -------------------------------------------------------------------------

    @Test
    fun `mapper_english_fallback_uses_chinese_when_empty`() {
        // showMyLanguage=true; Ring 6 english is "" → must use character "壬午分金"
        val state = compassState(headingDeg = 180.0, confidence = OverallConfidence.HIGH)

        val result = LuopanStateMapper.map(
            compassState = state,
            lockState = null,
            showRomanization = false,
            showMyLanguage = true   // even when English mode is ON
        )

        // Ring 6 english is intentionally empty — fallback to character field
        assertNotNull("fenJinLabel must be non-null at HIGH confidence", result.fenJinLabel)
        assertEquals(
            "fenJinLabel must be zh-Hant '壬午分金' even with showMyLanguage=true (empty english field)",
            "壬午分金",
            result.fenJinLabel
        )

        // Also verify Ring 5 fallback: 午 → english "Horse" (non-empty) → English is used
        assertEquals("mountainChar should be 'Horse' when showMyLanguage=true and english is non-empty", "Horse", result.mountainChar)
    }

    // -------------------------------------------------------------------------
    // PROP-03-034: Canonical readout at 90°
    // At bearing 90°, confidence=HIGH: 卯 (Ring 5), 卯 (Ring 4), ☳ 震 東 (Ring 3),
    // fenJinLabel="壬卯分金" (Ring 6 index 16 at [88°, 94°))
    // -------------------------------------------------------------------------

    @Test
    fun `mapper_90deg_high_all_fields_correct`() {
        val state = compassState(headingDeg = 90.0, confidence = OverallConfidence.HIGH)

        val result = LuopanStateMapper.map(
            compassState = state,
            lockState = null,
            showRomanization = false,
            showMyLanguage = false
        )

        // Ring 5 at 90° → 卯 [82.5°, 97.5°) → index 7
        assertEquals("mountainChar at 90° must be '卯'", "卯", result.mountainChar)
        // Ring 4 at 90° → 卯 [75°, 105°) → index 3
        assertEquals("earthlyBranchChar at 90° must be '卯'", "卯", result.earthlyBranchChar)
        // Ring 3 at 90° → ☳ 震 東 [67.5°, 112.5°) → index 2
        assertTrue("trigramSymbol at 90° must contain '☳'", result.trigramSymbol.contains("☳"))
        assertEquals("trigramName at 90° must be '震'", "震", result.trigramName)
        assertEquals("trigramDirection at 90° must be '東'", "東", result.trigramDirection)
        // Ring 6 at 90° → 壬卯分金 [88°, 94°) → index 16; HIGH → fenJinLabel non-null
        assertNotNull("fenJinLabel must be non-null at HIGH confidence", result.fenJinLabel)
        assertEquals("fenJinLabel at 90° must be '壬卯分金'", "壬卯分金", result.fenJinLabel)
        assertEquals(OverallConfidence.HIGH, result.confidence)
        assertEquals(90f, result.bearingDeg, 0.001f)
    }

    // -------------------------------------------------------------------------
    // mapper_locale_independence
    // BR-08: system locale has ZERO influence on ring label language.
    // Setting Locale.setDefault(Locale.ENGLISH) must NOT cause ring labels to appear in English.
    // When showMyLanguage=false, all labels are zh-Hant regardless of the JVM default locale.
    // -------------------------------------------------------------------------

    @Test
    fun `mapper_locale_independence`() {
        val previousLocale = Locale.getDefault()
        try {
            // Force JVM locale to English — simulates an English-locale device
            Locale.setDefault(Locale.ENGLISH)

            val state = compassState(headingDeg = 180.0, confidence = OverallConfidence.HIGH)

            val result = LuopanStateMapper.map(
                compassState = state,
                lockState = null,
                showRomanization = false,
                showMyLanguage = false   // toggle OFF — system locale must NOT matter
            )

            // BR-08: ring labels must remain in Traditional Chinese regardless of system locale
            assertEquals(
                "mountainChar must be zh-Hant '午' regardless of Locale.ENGLISH system locale",
                "午",
                result.mountainChar
            )
            assertEquals(
                "earthlyBranchChar must be zh-Hant '午' regardless of Locale.ENGLISH system locale",
                "午",
                result.earthlyBranchChar
            )
            assertEquals(
                "trigramName must be zh-Hant '離' regardless of Locale.ENGLISH system locale",
                "離",
                result.trigramName
            )
            assertEquals(
                "trigramDirection must be zh-Hant '南' regardless of Locale.ENGLISH system locale",
                "南",
                result.trigramDirection
            )
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    // -------------------------------------------------------------------------
    // LuopanState.INITIAL — companion object invariants
    // -------------------------------------------------------------------------

    @Test
    fun `LuopanState INITIAL northLabel is Mag N matching default north reference`() {
        // The ViewModel defaults to NorthType.MAGNETIC at startup, so INITIAL.northLabel
        // must be "Mag N" — not "True N" — to avoid a flash of wrong state on first render.
        assertEquals("Mag N", LuopanState.INITIAL.northLabel)
    }
}
