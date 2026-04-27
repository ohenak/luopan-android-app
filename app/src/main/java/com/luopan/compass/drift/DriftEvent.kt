package com.luopan.compass.drift

/**
 * Events emitted by [IDriftDetector.onFrame].
 *
 * Phase 4 — TSPEC §5.1
 */
enum class DriftEvent {
    /** The drift condition has been met for a sustained period (> 60 s with > 10% deviation). */
    TRIGGERED,

    /**
     * The detector transitioned from COUNTING back to IDLE due to a precondition violation.
     * Informational only — CompassViewModel does not need to act on this event.
     */
    RESET
}
