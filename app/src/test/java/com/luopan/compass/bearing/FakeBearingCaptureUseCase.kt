package com.luopan.compass.bearing

import kotlinx.coroutines.CompletableDeferred

/**
 * Test double for [BearingCaptureUseCase] that can be made to suspend indefinitely.
 *
 * Implements [BearingCapturePort] for injection into [com.luopan.compass.ui.CompassViewModel].
 *
 * Used in TSPEC §10.1 TE-T-04 (c) to verify that [captureButtonEnabled] is
 * false during the suspend and true after completion (BR-CAP-08).
 *
 * ## Usage
 *
 * ```kotlin
 * val fakeUseCase = FakeBearingCaptureUseCase()
 * // captureBearing(snapshot) is called; execute() suspends
 * // assert captureButtonEnabled == false
 * fakeUseCase.complete()   // unblock the suspend
 * // assert captureButtonEnabled == true
 * ```
 */
class FakeBearingCaptureUseCase : BearingCapturePort {

    /** Latch that controls when execute() completes. Reset with [reset]. */
    private var executionLatch = CompletableDeferred<Unit>()

    /** Record of all snapshots passed to execute(). */
    val executedSnapshots = mutableListOf<BearingSnapshot>()

    /**
     * Suspends until [complete] is called. This simulates a slow database write.
     */
    override suspend fun execute(snapshot: BearingSnapshot): BearingRecord {
        executedSnapshots.add(snapshot)
        executionLatch.await()
        return makeRecord(snapshot)
    }

    /**
     * Unblocks any coroutine waiting in [execute].
     */
    fun complete() {
        executionLatch.complete(Unit)
    }

    /**
     * Resets the latch so that a subsequent call to [execute] suspends again.
     */
    fun reset() {
        executionLatch = CompletableDeferred()
    }

    private fun makeRecord(snapshot: BearingSnapshot) = BearingRecord(
        id = "fake-id",
        name = snapshot.name,
        bearing_deg = snapshot.bearingDeg,
        north_type = snapshot.northType.name,
        confidence = snapshot.confidence.name,
        captured_at = snapshot.tapTimestampMs,
        calibration_version = "FakeModel",
        field_deviation_pct = snapshot.fieldDeviationPct,
        inclination_deviation_deg = snapshot.inclinationDeviationDeg,
        interference_flag = false,
        lat = null,
        lon = null,
        alt_m = null,
        notes = null,
        display_mode = snapshot.displayMode
    )
}
