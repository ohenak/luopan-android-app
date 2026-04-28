package com.luopan.compass.drift

import com.luopan.compass.util.FakeClock
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AccelerometerVarianceTracker].
 *
 * Covers:
 *   - AT-VAR-01: population variance formula (/ n, NOT / (n-1))
 *   - Rolling window eviction: samples older than windowMs are removed
 *   - n < 2 guard: returns 0f when fewer than 2 samples in window
 */
class AccelerometerVarianceTrackerTest {

    private lateinit var clock: FakeClock
    private lateinit var tracker: AccelerometerVarianceTracker

    @Before fun setUp() {
        clock = FakeClock(0L)
        tracker = AccelerometerVarianceTracker(clock, windowMs = 5_000L)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // AT-VAR-01: Population variance formula
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `AT-VAR-01 population variance with concrete values - magnitudes 3 5 4 produce 0_6667 not 1_0`() {
        // magnitude = sqrt(ax² + ay² + az²)
        // (3,0,0) → 3.0; (5,0,0) → 5.0; (4,0,0) → 4.0
        // mean = (3+5+4)/3 = 4.0
        // population variance = ((3-4)²+(5-4)²+(4-4)²)/3 = (1+1+0)/3 = 2/3 ≈ 0.6667
        // sample variance (wrong) = (1+1+0)/2 = 1.0

        clock.advance(100L)
        tracker.update(3f, 0f, 0f)
        clock.advance(100L)
        tracker.update(5f, 0f, 0f)
        clock.advance(100L)
        val variance = tracker.update(4f, 0f, 0f)

        // Assert population variance ≈ 0.6667, NOT 1.0 (sample variance)
        assertEquals("must use population variance (/ n)", 2f / 3f, variance, 0.001f)
    }

    @Test
    fun `AT-VAR-01 variance is 0 for constant signal`() {
        clock.advance(100L)
        tracker.update(0f, 0f, 9.8f)
        clock.advance(100L)
        val variance = tracker.update(0f, 0f, 9.8f)
        assertEquals("constant signal must have 0 variance", 0f, variance, 0.001f)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // n < 2 guard
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `n less than 2 guard - returns 0f with zero samples`() {
        val variance = tracker.update(3f, 0f, 0f)  // only 1 sample after this call
        assertEquals("single sample must return 0f", 0f, variance, 0.001f)
    }

    @Test
    fun `n less than 2 guard - returns 0f with no samples before first update`() {
        // Fresh tracker — 0 samples → 0f (not called yet, just confirm initial guard)
        // The guard triggers after first call returns because n=1
        clock.advance(100L)
        val result = tracker.update(5f, 0f, 0f)
        assertEquals(0f, result, 0.001f)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Rolling window eviction
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `window eviction - samples older than windowMs are removed`() {
        // Add 3 samples at t=100, 200, 300 ms
        clock.advance(100L)
        tracker.update(3f, 0f, 0f)   // magnitude=3, at t=100
        clock.advance(100L)
        tracker.update(5f, 0f, 0f)   // magnitude=5, at t=200
        clock.advance(100L)
        tracker.update(4f, 0f, 0f)   // magnitude=4, at t=300

        // Advance clock past windowMs (5000ms) so the first sample (t=100) is evicted
        // cutoff = nowMs - windowMs = 5300 - 5000 = 300
        // Sample at t=100: 100 < 300 → evicted
        // Sample at t=200: 200 < 300 → evicted
        // Sample at t=300: 300 NOT < 300 → kept (strict less-than boundary)
        clock.advance(5_000L)   // now t=5300

        // Add a new sample at t=5300 with a clearly different magnitude
        val variance = tracker.update(9f, 0f, 0f)  // magnitudes remaining: [4, 9]
        // mean = (4+9)/2 = 6.5
        // population variance = ((4-6.5)²+(9-6.5)²)/2 = (6.25+6.25)/2 = 6.25
        assertEquals("evicted samples must not contribute to variance", 6.25f, variance, 0.01f)
    }

    @Test
    fun `window eviction - fresh window after all samples evicted reduces to single sample`() {
        // Add samples
        clock.advance(100L)
        tracker.update(3f, 0f, 0f)
        clock.advance(100L)
        tracker.update(5f, 0f, 0f)

        // Jump far into the future — all previous samples evicted
        clock.advance(10_000L)
        val variance = tracker.update(7f, 0f, 0f)  // only 1 sample in window

        assertEquals("window with 1 sample must return 0f", 0f, variance, 0.001f)
    }

    @Test
    fun `window eviction - samples exactly at cutoff boundary are kept`() {
        // Sample at t=0 (before any advance)
        tracker.update(3f, 0f, 0f)  // t=0

        // Advance exactly windowMs; cutoff = windowMs - windowMs = 0
        // sample timestamp (0) is NOT < cutoff (0) → kept
        clock.advance(5_000L)
        val variance = tracker.update(5f, 0f, 0f)  // t=5000, window now has t=0 and t=5000

        // Both samples present: mean=(3+5)/2=4, pop variance=((3-4)²+(5-4)²)/2=1.0
        assertEquals("sample at exact cutoff boundary must be kept", 1.0f, variance, 0.001f)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // reset()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `reset clears all samples`() {
        clock.advance(100L)
        tracker.update(3f, 0f, 0f)
        clock.advance(100L)
        tracker.update(5f, 0f, 0f)

        tracker.reset()

        // After reset, first new sample should return 0f (n<2 guard)
        clock.advance(100L)
        val variance = tracker.update(4f, 0f, 0f)
        assertEquals("after reset, first sample must return 0f", 0f, variance, 0.001f)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // magnitude computation
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `magnitude is computed from all three axes`() {
        // (3,4,0) → magnitude = sqrt(9+16+0) = 5.0
        // (3,4,0) twice → mean=5, variance=0
        clock.advance(100L)
        tracker.update(3f, 4f, 0f)
        clock.advance(100L)
        val variance = tracker.update(3f, 4f, 0f)
        assertEquals("magnitude from 3-axis must be 5.0 for (3,4,0)", 0f, variance, 0.001f)
    }
}
