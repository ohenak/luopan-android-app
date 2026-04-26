package com.luopan.compass.luopan

import com.luopan.compass.model.OverallConfidence

/**
 * Luopan-mode UI state. Emitted by [CompassViewModel] as part of [CompassUiState].
 *
 * All fields required to render the dial, numerical readout panel, and 坐向 overlay are
 * contained within this state object.
 *
 * TYPE DECISIONS:
 * - Bearing fields (xiangBearing, zuoBearing, displayXiangBearing, displayZuoBearing): Float?
 *   — consistent with Android sensor API (SensorEvent.values[] is Float). WMM declination is
 *   also Float. The minimum sector width (6° for Ring 6) is far larger than Float epsilon,
 *   making Float precision adequate. See TSPEC §2.1 for full rationale.
 * - declinationDeg: Float — mirrors CompassUiState.declination_deg (already Float from WMM API).
 *   Exposed for informational display in the View only; the View never re-applies declination.
 *
 * SENSOR_ERROR state: character fields show "—"; fenJinLabel is null.
 * STABILIZING state: character fields continue updating from live bearing; fenJinLabel is null.
 */
data class LuopanState(
    // ---- Current bearing (for readout panel display) ----
    val bearingDeg: Float,              // Display bearing from CompassUiState.heading_deg.toFloat()
    val northLabel: String,             // "True N" or "Mag N" — from CompassUiState.north_label
    val declinationDeg: Float,          // From CompassUiState.declination_deg; informational only
    val confidence: OverallConfidence,  // HIGH | MODERATE | POOR | STABILIZING | SENSOR_ERROR

    // ---- Ring 5 — 二十四山 ----
    val mountainChar: String,           // e.g. "午"; "—" on SENSOR_ERROR; English if showMyLanguage
    val mountainPinyin: String,         // e.g. "Wǔ"; "" when showRomanization=false
    val mountainEnglish: String,        // e.g. "Horse"; "" on SENSOR_ERROR (for "Show in my language")

    // ---- Ring 4 — 十二地支 ----
    val earthlyBranchChar: String,      // e.g. "午"; "—" on SENSOR_ERROR
    val earthlyBranchPinyin: String,    // e.g. "Wǔ"; "" when showRomanization=false

    // ---- Ring 3 — 後天八卦 ----
    val trigramSymbol: String,          // Full Ring 3 character field, e.g. "☲ 離 南"; always retained
    val trigramName: String,            // e.g. "離"; "—" on SENSOR_ERROR; English name if showMyLanguage
    val trigramDirection: String,       // e.g. "南"; "—" on SENSOR_ERROR; English direction if showMyLanguage

    // ---- Ring 6 — 六十分金 ----
    val fenJinLabel: String?,           // e.g. "壬午分金"; null when confidence != HIGH

    // ---- 坐向 lock state ----
    // All bearing fields use Float? (TSPEC §2.1).
    val isLockActive: Boolean,
    val xiangBearing: Float?,           // ALWAYS True North bearing at lock time; null = unlocked
    val zuoBearing: Float?,             // ALWAYS True North: (xiangBearing + 180f) % 360f; null = unlocked
    val displayXiangBearing: Float?,    // Display-reference bearing (current north ref); null = unlocked
    val displayZuoBearing: Float?,      // Display-reference bearing (current north ref); null = unlocked
    val xiangMountain: String?,         // Ring 5 山 for 向 (null = unlocked); derived from True N
    val zuoMountain: String?,           // Ring 5 山 for 坐 (null = unlocked); derived from True N

    // ---- Localization toggles ----
    val showRomanization: Boolean,      // true when pinyin should be shown alongside characters
    val showMyLanguage: Boolean         // true when English equivalents should replace Chinese characters
) {
    companion object {
        /**
         * Safe initial state used before the sensor pipeline emits the first reading.
         *
         * All character fields default to "—" (pre-sensor-error-like state).
         * northLabel defaults to "True N" per the app default north reference at startup.
         */
        val INITIAL = LuopanState(
            bearingDeg = 0f,
            northLabel = "True N",
            declinationDeg = 0f,
            confidence = OverallConfidence.POOR,
            mountainChar = "—",
            mountainPinyin = "",
            mountainEnglish = "",
            earthlyBranchChar = "—",
            earthlyBranchPinyin = "",
            trigramSymbol = "—",
            trigramName = "—",
            trigramDirection = "—",
            fenJinLabel = null,
            isLockActive = false,
            xiangBearing = null,
            zuoBearing = null,
            displayXiangBearing = null,
            displayZuoBearing = null,
            xiangMountain = null,
            zuoMountain = null,
            showRomanization = false,
            showMyLanguage = false
        )
    }
}
