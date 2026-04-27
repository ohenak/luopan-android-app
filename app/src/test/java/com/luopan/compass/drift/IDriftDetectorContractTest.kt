package com.luopan.compass.drift

import com.luopan.compass.model.InterferenceState
import org.junit.Assert.*
import org.junit.Test

/**
 * Structural/contract test for [IDriftDetector] interface and [DriftEvent] enum.
 *
 * Verifies:
 *   - [DriftEvent] has TRIGGERED and RESET constants
 *   - [IDriftDetector] has the correct onFrame() and reset() signatures
 *   - [FakeDriftDetector] compiles and is assignable to [IDriftDetector]
 *   - onFrame() returns DriftEvent? (nullable)
 *
 * Phase 4 — PLAN B-2a (TE PLAN-v1 F-01)
 */
class IDriftDetectorContractTest {

    // ─── DriftEvent enum ─────────────────────────────────────────────────────

    @Test
    fun `DriftEvent TRIGGERED constant exists`() {
        val event: DriftEvent = DriftEvent.TRIGGERED
        assertEquals(DriftEvent.TRIGGERED, event)
    }

    @Test
    fun `DriftEvent RESET constant exists`() {
        val event: DriftEvent = DriftEvent.RESET
        assertEquals(DriftEvent.RESET, event)
    }

    @Test
    fun `DriftEvent has exactly two values`() {
        assertEquals(2, DriftEvent.values().size)
    }

    @Test
    fun `DriftEvent values are TRIGGERED and RESET`() {
        val names = DriftEvent.values().map { it.name }.toSet()
        assertTrue(names.contains("TRIGGERED"))
        assertTrue(names.contains("RESET"))
    }

    // ─── IDriftDetector interface contract ────────────────────────────────────

    @Test
    fun `FakeDriftDetector is assignable to IDriftDetector`() {
        // If FakeDriftDetector implements IDriftDetector, this compiles and holds
        val detector: IDriftDetector = FakeDriftDetector(nextEvent = null)
        assertNotNull(detector)
    }

    @Test
    fun `DriftDetector is assignable to IDriftDetector`() {
        // DriftDetector must implement IDriftDetector
        val fakeClock = com.luopan.compass.util.FakeClock(0L)
        val detector: IDriftDetector = DriftDetector(fakeClock)
        assertNotNull(detector)
    }

    @Test
    fun `IDriftDetector onFrame returns null by default on FakeDriftDetector`() {
        val detector: IDriftDetector = FakeDriftDetector(nextEvent = null)
        val result: DriftEvent? = detector.onFrame(
            accVariance = 0.005f,
            measuredMagnitudeUt = 50.0f,
            interferenceState = InterferenceState.CLEAR,
            expectedFieldUt = 50.0f
        )
        assertNull(result)
    }

    @Test
    fun `IDriftDetector onFrame returns configured DriftEvent from FakeDriftDetector`() {
        val detector: IDriftDetector = FakeDriftDetector(nextEvent = DriftEvent.TRIGGERED)
        val result: DriftEvent? = detector.onFrame(
            accVariance = 0.005f,
            measuredMagnitudeUt = 56.0f,
            interferenceState = InterferenceState.CLEAR,
            expectedFieldUt = 50.0f
        )
        assertEquals(DriftEvent.TRIGGERED, result)
    }

    @Test
    fun `IDriftDetector reset can be called without error`() {
        val detector: IDriftDetector = FakeDriftDetector(nextEvent = null)
        detector.reset()  // must not throw
    }

    @Test
    fun `onFrame signature accepts all four required parameters`() {
        // This is a compile-time test — if signature is wrong this won't compile.
        val detector: IDriftDetector = FakeDriftDetector(nextEvent = DriftEvent.RESET)
        val result = detector.onFrame(
            accVariance = 0.001f,
            measuredMagnitudeUt = 48.0f,
            interferenceState = InterferenceState.MODERATE,
            expectedFieldUt = 50.0f
        )
        assertEquals(DriftEvent.RESET, result)
    }
}
