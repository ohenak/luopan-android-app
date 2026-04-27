package com.luopan.compass.ui

/**
 * State of the drift-based recalibration banner in BearingHistoryFragment.
 *
 * Phase 4 — TSPEC §6.4; used by CompassViewModel.driftBannerState StateFlow.
 */
enum class DriftBannerState {
    /** Banner is hidden — no drift detected or within cooldown period. */
    HIDDEN,

    /** Banner is visible — sustained drift detected, user should recalibrate. */
    VISIBLE
}
