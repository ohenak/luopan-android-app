package com.luopan.compass.luopan

import com.luopan.compass.model.OverallConfidence
import com.luopan.compass.ui.CompassUiState

/**
 * Pure mapper: [CompassUiState] + session state → [LuopanState].
 *
 * Design: pure `object` — no Android dependencies, no side effects, no mutable state.
 * Called by `CompassViewModel` in the sensor collection coroutine (`Dispatchers.Default`).
 *
 * Confidence-gating rules:
 *  - SENSOR_ERROR: all character fields → "—", fenJinLabel → null; lock fields preserved from [lockState].
 *  - HIGH: all fields fully populated, including fenJinLabel.
 *  - MODERATE / POOR / STABILIZING: all character fields populated, fenJinLabel → null.
 *
 * Localization rules:
 *  - showMyLanguage=true: use [LabelData.english] where non-empty in place of [LabelData.character].
 *  - showRomanization=true: pinyin fields reflect [LabelData.pinyin]; when false, pinyin fields are "".
 *
 * Lock state rules (TSPEC §2.1, FSPEC §4a–4d):
 *  - lockState non-null: isLockActive=true, all lock bearing and mountain fields populated from lockState.
 *  - lockState null: isLockActive=false, all lock fields null.
 *  - Lock fields are ALWAYS from [lockState], never from the live bearing — even during SENSOR_ERROR.
 *
 * Ring 2 character format: The [LabelData.character] for Ring 2 is "☲ 離 南" (symbol + name + direction
 * space-separated). The mapper extracts trigramSymbol (full character field), trigramName, trigramDirection.
 * The trigram symbol (☲) is always retained regardless of language mode (FSPEC §4.8, TSPEC §5.1).
 */
object LuopanStateMapper {

    /**
     * Maps compass state and session parameters to a fully-populated [LuopanState].
     *
     * @param compassState Current compass reading from the sensor pipeline.
     * @param lockState    Current 坐向 lock state from [ZuoXiangLock], or null if unlocked.
     * @param showRomanization Whether to include pinyin romanization in output fields.
     * @param showMyLanguage   Whether to use English equivalents for character fields.
     */
    fun map(
        compassState: CompassUiState,
        lockState: ZuoXiangLock.LockState?,
        showRomanization: Boolean,
        showMyLanguage: Boolean
    ): LuopanState {
        val bearingDeg = compassState.heading_deg.toFloat()
        val isSensorError = compassState.confidence == OverallConfidence.SENSOR_ERROR
        val isHigh = compassState.confidence == OverallConfidence.HIGH

        // Perform sector lookups (cheap pure functions; results discarded on SENSOR_ERROR)
        val ring4Label = RingLabelProvider.ring4Label(SectorLookup.ring4(bearingDeg))
        val ring3Label = RingLabelProvider.ring3Label(SectorLookup.ring3(bearingDeg))
        val ring2Label = RingLabelProvider.ring2Label(SectorLookup.ring2(bearingDeg))
        val ring5Label = if (isHigh) RingLabelProvider.ring5Label(SectorLookup.ring5(bearingDeg)) else null

        // ---- Ring 4 (二十四山) ----
        val mountainChar: String
        val mountainPinyin: String
        val mountainEnglish: String

        if (isSensorError) {
            mountainChar = "—"
            mountainPinyin = ""
            mountainEnglish = ""
        } else {
            mountainEnglish = ring4Label.english
            mountainChar = if (showMyLanguage && ring4Label.english.isNotEmpty()) ring4Label.english else ring4Label.character
            mountainPinyin = if (showRomanization) ring4Label.pinyin else ""
        }

        // ---- Ring 3 (十二地支) ----
        val earthlyBranchChar: String
        val earthlyBranchPinyin: String

        if (isSensorError) {
            earthlyBranchChar = "—"
            earthlyBranchPinyin = ""
        } else {
            earthlyBranchChar = if (showMyLanguage && ring3Label.english.isNotEmpty()) ring3Label.english else ring3Label.character
            earthlyBranchPinyin = if (showRomanization) ring3Label.pinyin else ""
        }

        // ---- Ring 2 (後天八卦) ----
        // Ring 2 character is formatted as "☲ 離 南" (symbol + 卦名 + 方位, space-separated).
        // trigramSymbol = full character field (retained in all modes per FSPEC §4.8).
        // trigramName and trigramDirection are extracted and optionally localized.
        val trigramSymbol: String
        val trigramName: String
        val trigramDirection: String

        if (isSensorError) {
            trigramSymbol = "—"
            trigramName = "—"
            trigramDirection = "—"
        } else {
            trigramSymbol = ring2Label.character  // Always the zh-Hant field (e.g. "☲ 離 南")
            if (showMyLanguage && ring2Label.english.isNotEmpty()) {
                // English format: "Li · South" → split on " · " for name/direction
                val parts = ring2Label.english.split(" · ")
                trigramName = if (parts.size >= 1) parts[0] else ring2Label.english
                trigramDirection = if (parts.size >= 2) parts[1] else ""
            } else {
                // Parse from "☲ 離 南" → ["☲", "離", "南"]
                val parts = ring2Label.character.split(" ")
                trigramName = if (parts.size >= 2) parts[1] else ""
                trigramDirection = if (parts.size >= 3) parts[2] else ""
            }
        }

        // ---- Ring 5 (六十分金) ----
        val fenJinLabel: String? = if (isHigh && ring5Label != null) ring5Label.character else null

        // ---- 坐向 lock fields ----
        val isLockActive: Boolean
        val xiangBearing: Float?
        val zuoBearing: Float?
        val displayXiangBearing: Float?
        val displayZuoBearing: Float?
        val xiangMountain: String?
        val zuoMountain: String?

        if (lockState != null) {
            isLockActive = true
            xiangBearing = lockState.xiangBearing
            zuoBearing = lockState.zuoBearing
            displayXiangBearing = lockState.displayXiangBearing
            displayZuoBearing = lockState.displayZuoBearing
            xiangMountain = lockState.xiangMountain
            zuoMountain = lockState.zuoMountain
        } else {
            isLockActive = false
            xiangBearing = null
            zuoBearing = null
            displayXiangBearing = null
            displayZuoBearing = null
            xiangMountain = null
            zuoMountain = null
        }

        return LuopanState(
            bearingDeg = bearingDeg,
            northLabel = compassState.north_label,
            declinationDeg = compassState.declination_deg,
            confidence = compassState.confidence,
            mountainChar = mountainChar,
            mountainPinyin = mountainPinyin,
            mountainEnglish = mountainEnglish,
            earthlyBranchChar = earthlyBranchChar,
            earthlyBranchPinyin = earthlyBranchPinyin,
            trigramSymbol = trigramSymbol,
            trigramName = trigramName,
            trigramDirection = trigramDirection,
            fenJinLabel = fenJinLabel,
            isLockActive = isLockActive,
            xiangBearing = xiangBearing,
            zuoBearing = zuoBearing,
            displayXiangBearing = displayXiangBearing,
            displayZuoBearing = displayZuoBearing,
            xiangMountain = xiangMountain,
            zuoMountain = zuoMountain,
            showRomanization = showRomanization,
            showMyLanguage = showMyLanguage
        )
    }
}
