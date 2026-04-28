package com.luopan.compass.drift

import com.luopan.compass.model.InterferenceState
import com.luopan.compass.util.FakeClock
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DriftDetector].
 *
 * Covers AT-CAL-03-A through AT-CAL-03-F, AT-CAL-03-B2, and the
 * "COUNTING past 60s stays COUNTING when deviation ≤ 10%" rule (FSPEC-CAL-03 backport).
 *
 * Phase 4 — PLAN B-5
 */
class DriftDetectorTest {

    private lateinit var clock: FakeClock
    private lateinit var detector: DriftDetector

    // Valid precondition values
    private val lowVariance = 0.005f             // < 0.01 threshold → precondition met
    private val highVariance = 0.05f             // ≥ 0.01 threshold → precondition violated
    private val expectedFieldUt = 50.0f
    private val measuredAboveThreshold = 56.0f   // |56-50|/50 = 12% > 10% → TRIGGERED
    private val measuredAtThreshold = 55.0f       // |55-50|/50 = 10% NOT > 10% → no trigger
    private val measuredBelowThreshold = 54.9f    // |54.9-50|/50 = 9.8% < 10% → no trigger
    private val measuredNoDeviation = 50.0f       // 0% deviation

    @Before fun setUp() {
        clock = FakeClock(0L)
        detector = DriftDetector(clock)
    }

    // ─── AT-CAL-03-A: Timer resets when precondition is violated ─────────────

    @Test
    fun `AT-CAL-03-A timer reset when accel variance precondition violated`() {
        // Start counting: 45 seconds in
        driveCounting(45_000L)
        // Violate precondition: high accel variance
        val event = detector.onFrame(highVariance, measuredAboveThreshold, InterferenceState.CLEAR, expectedFieldUt)
        assertEquals("precondition violation must return RESET", DriftEvent.RESET, event)
        assertNull("timer must be reset to null after violation", detector.elapsedCountingMs())
    }

    @Test
    fun `AT-CAL-03-A timer reset when interferenceState is WARNING`() {
        driveCounting(45_000L)
        val event = detector.onFrame(lowVariance, measuredAboveThreshold, InterferenceState.WARNING, expectedFieldUt)
        assertEquals(DriftEvent.RESET, event)
        assertNull(detector.elapsedCountingMs())
    }

    @Test
    fun `AT-CAL-03-A timer reset when expectedFieldUt is 0`() {
        driveCounting(45_000L)
        val event = detector.onFrame(lowVariance, measuredAboveThreshold, InterferenceState.CLEAR, 0.0f)
        assertEquals(DriftEvent.RESET, event)
        assertNull(detector.elapsedCountingMs())
    }

    // ─── AT-CAL-03-B: TRIGGERED after 60 s with > 10% deviation ──────────────

    @Test
    fun `AT-CAL-03-B TRIGGERED after 60 s with 12 percent deviation`() {
        driveCounting(60_001L)
        val event = detector.onFrame(lowVariance, measuredAboveThreshold, InterferenceState.CLEAR, expectedFieldUt)
        assertEquals("must return TRIGGERED after 60s with >10% deviation", DriftEvent.TRIGGERED, event)
    }

    @Test
    fun `AT-CAL-03-B detector returns to IDLE after TRIGGERED`() {
        driveCounting(60_001L)
        detector.onFrame(lowVariance, measuredAboveThreshold, InterferenceState.CLEAR, expectedFieldUt)
        // After TRIGGERED, timer must be reset
        assertNull("timer must be null after TRIGGERED", detector.elapsedCountingMs())
    }

    // ─── AT-CAL-03-B2: COUNTING past 60 s stays COUNTING when deviation ≤ 10% ─

    @Test
    fun `AT-CAL-03-B2 COUNTING past 60 s stays COUNTING when deviation exactly 10 percent`() {
        // Advance past 60 seconds
        driveCounting(60_001L)
        // measuredAtThreshold → 10% deviation, NOT > 10%, so no TRIGGERED
        val event = detector.onFrame(lowVariance, measuredAtThreshold, InterferenceState.CLEAR, expectedFieldUt)
        assertNull("deviation exactly 10% must NOT trigger", event)
        // Timer must still be running (COUNTING, not IDLE)
        assertNotNull("must still be COUNTING when deviation ≤ 10%", detector.elapsedCountingMs())
    }

    @Test
    fun `AT-CAL-03-B2 COUNTING past 60 s stays COUNTING when deviation below 10 percent`() {
        driveCounting(60_001L)
        val event = detector.onFrame(lowVariance, measuredBelowThreshold, InterferenceState.CLEAR, expectedFieldUt)
        assertNull("deviation below 10% must NOT trigger", event)
        assertNotNull("must still be COUNTING", detector.elapsedCountingMs())
    }

    @Test
    fun `AT-CAL-03-B2 TRIGGERED fires when deviation crosses 10 percent after long COUNTING`() {
        // 90 seconds of counting with deviation just below threshold
        driveCounting(90_000L)
        // Now deviation crosses threshold → TRIGGERED
        val event = detector.onFrame(lowVariance, measuredAboveThreshold, InterferenceState.CLEAR, expectedFieldUt)
        assertEquals("TRIGGERED must fire when deviation crosses threshold after long COUNTING", DriftEvent.TRIGGERED, event)
    }

    // ─── AT-CAL-03-C: No TRIGGERED before 60 s ────────────────────────────────

    @Test
    fun `AT-CAL-03-C no TRIGGERED before 60 s even with high deviation`() {
        driveCounting(59_999L)
        val event = detector.onFrame(lowVariance, measuredAboveThreshold, InterferenceState.CLEAR, expectedFieldUt)
        assertNull("must NOT trigger before 60 s", event)
    }

    @Test
    fun `AT-CAL-03-C exactly at 60 s not enough - needs greater than 60 s`() {
        driveCounting(60_000L)
        // elapsedMs = 60000, which is NOT > 60000 (strict greater-than)
        val event = detector.onFrame(lowVariance, measuredAboveThreshold, InterferenceState.CLEAR, expectedFieldUt)
        assertNull("exactly 60s must NOT trigger (requires > 60s)", event)
    }

    // ─── AT-CAL-03-D: expectedFieldUt = 0.0 disables detection ───────────────

    @Test
    fun `AT-CAL-03-D expectedFieldUt 0 disables detection - no COUNTING starts`() {
        // With expectedFieldUt = 0, precondition 3 fails → stays IDLE
        repeat(10) {
            clock.advance(1_000L)
            val event = detector.onFrame(lowVariance, measuredAboveThreshold, InterferenceState.CLEAR, 0.0f)
            assertNull("expectedFieldUt=0 must disable detection", event)
        }
        assertNull("must remain IDLE when expectedFieldUt=0", detector.elapsedCountingMs())
    }

    @Test
    fun `AT-CAL-03-D IDLE when expectedFieldUt is 0 - returns null not RESET`() {
        // First call: IDLE, precondition not met → returns null (not RESET)
        clock.advance(100L)
        val event = detector.onFrame(lowVariance, measuredAboveThreshold, InterferenceState.CLEAR, 0.0f)
        assertNull("IDLE→IDLE transition must return null, not RESET", event)
    }

    // ─── AT-CAL-03-E: No hysteresis — TRIGGERED then requires new 60 s window ─

    @Test
    fun `AT-CAL-03-E post-trigger requires new 60 s window - no immediate re-trigger`() {
        // First TRIGGERED
        driveCounting(60_001L)
        detector.onFrame(lowVariance, measuredAboveThreshold, InterferenceState.CLEAR, expectedFieldUt)

        // Now drive 59 more seconds — should NOT trigger again
        var secondTrigger: DriftEvent? = null
        repeat(59) {
            clock.advance(1_000L)
            val e = detector.onFrame(lowVariance, measuredAboveThreshold, InterferenceState.CLEAR, expectedFieldUt)
            if (e == DriftEvent.TRIGGERED) secondTrigger = e
        }
        assertNull("after TRIGGERED, must require new 60s window before re-triggering", secondTrigger)
    }

    @Test
    fun `AT-CAL-03-E post-trigger triggers again after new 60 s window`() {
        // First trigger
        driveCounting(60_001L)
        detector.onFrame(lowVariance, measuredAboveThreshold, InterferenceState.CLEAR, expectedFieldUt)

        // New 60+ second window → should trigger again
        driveCounting(60_001L)
        val event = detector.onFrame(lowVariance, measuredAboveThreshold, InterferenceState.CLEAR, expectedFieldUt)
        assertEquals("must TRIGGER again after new 60s window", DriftEvent.TRIGGERED, event)
    }

    // ─── AT-CAL-03-F: MODERATE interference does not block counting ───────────

    @Test
    fun `AT-CAL-03-F MODERATE interference does not prevent counting or triggering`() {
        // MODERATE is not WARNING — precondition is met
        driveCounting(60_001L, interferenceState = InterferenceState.MODERATE)
        val event = detector.onFrame(lowVariance, measuredAboveThreshold, InterferenceState.MODERATE, expectedFieldUt)
        assertEquals("MODERATE interference must not prevent triggering", DriftEvent.TRIGGERED, event)
    }

    // ─── reset() method ───────────────────────────────────────────────────────

    @Test
    fun `reset transitions to IDLE from COUNTING`() {
        driveCounting(30_000L)
        assertNotNull("must be COUNTING before reset", detector.elapsedCountingMs())
        detector.reset()
        assertNull("must be IDLE after reset", detector.elapsedCountingMs())
    }

    @Test
    fun `reset is idempotent when already IDLE`() {
        detector.reset()  // already IDLE
        assertNull(detector.elapsedCountingMs())
    }

    // ─── Boundary: IDLE returns null without precondition met ─────────────────

    @Test
    fun `IDLE returns null when preconditions not met`() {
        clock.advance(100L)
        val event = detector.onFrame(highVariance, measuredAboveThreshold, InterferenceState.WARNING, 0.0f)
        assertNull(event)
    }

    @Test
    fun `IDLE returns null when preconditions ARE met - starts COUNTING silently`() {
        clock.advance(100L)
        val event = detector.onFrame(lowVariance, measuredNoDeviation, InterferenceState.CLEAR, expectedFieldUt)
        assertNull("IDLE→COUNTING transition returns null", event)
        assertNotNull("must be COUNTING after preconditions met", detector.elapsedCountingMs())
    }

    // ─── elapsedCountingMs() ──────────────────────────────────────────────────

    @Test
    fun `elapsedCountingMs returns null when IDLE`() {
        assertNull(detector.elapsedCountingMs())
    }

    @Test
    fun `elapsedCountingMs tracks elapsed time correctly`() {
        clock.advance(1_000L)
        detector.onFrame(lowVariance, measuredNoDeviation, InterferenceState.CLEAR, expectedFieldUt)
        clock.advance(5_000L)
        val elapsed = detector.elapsedCountingMs()
        assertNotNull(elapsed)
        assertEquals("elapsed must match clock advance", 5_000L, elapsed)
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    /**
     * Drive the detector from IDLE into COUNTING, then advance the clock by [elapsedMs]
     * from the moment COUNTING started, calling onFrame once per step to keep preconditions met.
     *
     * After this call, the detector is COUNTING with [elapsedMs] elapsed since COUNTING started.
     * The deviation used during driving is 0% (measuredNoDeviation) to avoid triggering.
     *
     * @param elapsedMs Total elapsed ms from COUNTING start (not from clock=0)
     * @param stepMs    Clock step per onFrame() call while driving
     * @param interferenceState InterferenceState to use during driving
     */
    private fun driveCounting(
        elapsedMs: Long,
        stepMs: Long = 1_000L,
        interferenceState: InterferenceState = InterferenceState.CLEAR
    ) {
        // Start COUNTING: advance clock and call onFrame once to transition from IDLE to COUNTING.
        // countingStartMs will be set to clock.nowMs() after this advance.
        clock.advance(1L)
        detector.onFrame(lowVariance, measuredNoDeviation, interferenceState, expectedFieldUt)
        // Now advance the remaining elapsedMs from the countingStartMs point
        var remaining = elapsedMs
        while (remaining > 0) {
            val step = minOf(stepMs, remaining)
            clock.advance(step)
            if (remaining > stepMs) {
                // Not the final step: drive with no-deviation to keep counting without triggering
                detector.onFrame(lowVariance, measuredNoDeviation, interferenceState, expectedFieldUt)
            }
            remaining -= step
        }
        // After the loop, clock has advanced elapsedMs from countingStart.
        // The test then calls onFrame() with the actual deviation to check behaviour.
    }
}
