package com.luopan.compass.drift

import com.luopan.compass.model.InterferenceState
import com.luopan.compass.util.FakeClock
import org.junit.Assert.*
import org.junit.Test

/**
 * Integration test: AT-CAL-INT-01
 *
 * Wires real [AccelerometerVarianceTracker] and real [DriftDetector] together and drives
 * them through a complete 60-second drift detection cycle.
 *
 * Two-phase structure (TE TSPEC-v1 F-04):
 *   Phase 1 (58 s): Drives frames and asserts TRIGGERED is NEVER emitted — guards against
 *                   a broken implementation that fires too early.
 *   Phase 2 (4 s):  Advances past 60 s and asserts TRIGGERED eventually fires.
 *
 * Uses gravity-only accelerometer input (0, 0, 9.8 m/s²) to keep variance below the
 * ACCEL_VARIANCE_THRESHOLD (0.01). This simulates a stationary device.
 *
 * Scenario: expectedFieldUt = 50.0 µT, measuredMagnitudeUt = 56.0 µT → 12% deviation > 10%.
 *
 * Phase 4 — PLAN B-7/B-8; TSPEC §9.3
 */
class DriftDetectorIntegrationTest {

    @Test
    fun `AT-CAL-INT-01 real DriftDetector and AccelerometerVarianceTracker wiring`() {
        val clock = FakeClock(0L)
        val tracker = AccelerometerVarianceTracker(clock)
        val detector = DriftDetector(clock)

        val expectedFieldUt = 50.0f
        val measuredMagnitudeUt = 56.0f  // 12% deviation > 10% threshold

        // ─── Phase 1: Pre-threshold negative assertion ────────────────────────
        // Drive 58 seconds of frames (below the 60-second window).
        // Each frame: advance 50ms, update tracker (gravity-only = stationary), call detector.
        // Assert that TRIGGERED is NEVER emitted during this phase.
        // Guards against a broken implementation that fires before the threshold.
        var triggeredEarly = false
        repeat(1160) {  // 1160 × 50 ms = 58,000 ms = 58 seconds
            clock.advance(50L)
            val accVariance = tracker.update(0f, 0f, 9.8f)
            val event = detector.onFrame(
                accVariance = accVariance,
                measuredMagnitudeUt = measuredMagnitudeUt,
                interferenceState = InterferenceState.CLEAR,
                expectedFieldUt = expectedFieldUt
            )
            if (event == DriftEvent.TRIGGERED) triggeredEarly = true
        }
        assertFalse("TRIGGERED must NOT fire before 60 seconds", triggeredEarly)

        // ─── Phase 2: Post-threshold positive assertion ───────────────────────
        // Advance another 4 seconds to cross the 60-second mark.
        // Assert TRIGGERED fires within these frames.
        var triggeredAfterThreshold = false
        repeat(80) {  // 80 × 50 ms = 4,000 ms; total elapsed = 62 seconds from counting start
            if (triggeredAfterThreshold) return@repeat  // stop once triggered
            clock.advance(50L)
            val accVariance = tracker.update(0f, 0f, 9.8f)
            val event = detector.onFrame(
                accVariance = accVariance,
                measuredMagnitudeUt = measuredMagnitudeUt,
                interferenceState = InterferenceState.CLEAR,
                expectedFieldUt = expectedFieldUt
            )
            if (event == DriftEvent.TRIGGERED) triggeredAfterThreshold = true
        }
        assertTrue("TRIGGERED must fire after 60 seconds with 12% deviation", triggeredAfterThreshold)
    }

    @Test
    fun `AT-CAL-INT-01 accVariance from stationary device stays below threshold`() {
        // Verify that gravity-only input produces accVariance < 0.01 (the precondition threshold)
        // so Phase 1 of the main integration test is valid.
        val clock = FakeClock(0L)
        val tracker = AccelerometerVarianceTracker(clock)

        var lastVariance = 0f
        repeat(200) {  // 200 × 50ms = 10 seconds of gravity-only frames
            clock.advance(50L)
            lastVariance = tracker.update(0f, 0f, 9.8f)
        }

        // Gravity-only (constant 9.8 m/s²) should produce near-zero variance
        assertTrue(
            "gravity-only variance must be < 0.01 (DriftDetector precondition); got $lastVariance",
            lastVariance < 0.01f
        )
    }
}
