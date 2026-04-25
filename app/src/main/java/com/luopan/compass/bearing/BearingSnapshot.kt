package com.luopan.compass.bearing

import com.luopan.compass.model.InterferenceState
import com.luopan.compass.model.NorthType
import com.luopan.compass.model.OverallConfidence

/**
 * Immutable snapshot of the compass state at the instant the capture button was tapped.
 *
 * Created by [com.luopan.compass.ui.CompassViewModel.captureBearing] immediately when the
 * user taps the FAB, before any dialog is shown. [tapTimestampMs] is the wall-clock ms
 * recorded via [com.luopan.compass.util.Clock.nowMs] at that moment (PM-T-01).
 *
 * TSPEC §3.6 — data class in com.luopan.compass.bearing.
 */
data class BearingSnapshot(
    /** Compass heading normalized to [0.0, 360.0). */
    val bearingDeg: Float,
    /** TRUE or MAGNETIC. GRID is rejected by [BearingCaptureUseCase.execute]. */
    val northType: NorthType,
    /** Overall confidence level at tap time. */
    val confidence: OverallConfidence,
    /**
     * Interference state at tap time. Sole source for [com.luopan.compass.bearing.BearingRecord.interference_flag]
     * (BR-10, AT-E-10). OverallConfidence.POOR does NOT set the flag.
     */
    val interferenceState: InterferenceState,
    /** InterferenceMetrics.fieldDeviation * 100. */
    val fieldDeviationPct: Float,
    /** InterferenceMetrics.inclinationDeviation_deg. */
    val inclinationDeviationDeg: Float,
    /** Latitude in decimal degrees — null if GPS toggle is OFF or location unavailable. */
    val latDeg: Double?,
    /** Longitude in decimal degrees — null if GPS toggle is OFF or location unavailable. */
    val lonDeg: Double?,
    /** Altitude in meters — null if GPS toggle is OFF or location unavailable. */
    val altM: Double?,
    /** Trimmed name, length 1–100. */
    val name: String,
    /** Null if not entered; empty string is coerced to null by [BearingCaptureUseCase]. */
    val notes: String?,
    /** Phase 2 always writes "MODERN". */
    val displayMode: String = "MODERN",
    /** Whether the GPS toggle is ON in the capture dialog. */
    val includeLocation: Boolean,
    /**
     * Wall-clock milliseconds at the instant the capture button was tapped (PM-T-01).
     *
     * Populated by [com.luopan.compass.ui.CompassViewModel.captureBearing] via
     * [com.luopan.compass.util.Clock.nowMs] BEFORE any dialog is shown. Used as
     * [com.luopan.compass.bearing.BearingRecord.captured_at] — the use case does NOT
     * call [com.luopan.compass.util.Clock.nowMs] at execute time for this field.
     */
    val tapTimestampMs: Long
)
