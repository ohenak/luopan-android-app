package com.luopan.compass.bearing

import com.luopan.compass.model.InterferenceState
import com.luopan.compass.model.NorthType
import java.util.UUID

/**
 * Orchestrates the bearing capture flow: validates the snapshot, derives [BearingRecord.interference_flag]
 * from [BearingSnapshot.interferenceState], constructs the [BearingRecord], and persists it.
 *
 * TSPEC §3.6 — pure domain class; no Android context, no ViewModel lifecycle.
 *
 * Key invariants:
 * - `interference_flag` is derived solely from [BearingSnapshot.interferenceState] ∈ {MODERATE, WARNING}
 *   (BR-10, AT-E-10). [BearingSnapshot.confidence] == POOR does NOT set the flag.
 * - `captured_at` is set to [BearingSnapshot.tapTimestampMs], NOT to [Clock.nowMs] at execute time
 *   (PM-T-01). The tap timestamp is carried through the snapshot from [captureBearing].
 * - `calibration_version` is read from [BearingSnapshot.calibrationVersion], which is populated
 *   by [com.luopan.compass.ui.CompassViewModel] at tap time. The use case does NOT touch the model.
 * - [NorthType.GRID] is rejected as a programming-error guard (AT-G-08).
 */
class BearingCaptureUseCase(
    private val bearingRepository: BearingRepository
) : BearingCapturePort {
    /**
     * Executes the save. Constructs a [BearingRecord] from the snapshot and persists it.
     *
     * @param snapshot Immutable snapshot taken at capture button tap time.
     *                 [BearingSnapshot.tapTimestampMs] is used for [BearingRecord.captured_at] (PM-T-01).
     * @return The saved [BearingRecord].
     * @throws IllegalStateException if [BearingSnapshot.northType] == [NorthType.GRID].
     */
    override suspend fun execute(snapshot: BearingSnapshot): BearingRecord {
        // Programming-error guard (AT-G-08): GRID must never be written to BearingRecord
        check(snapshot.northType != NorthType.GRID) {
            "GRID north type must never be written to BearingRecord"
        }

        // BR-10, AT-E-10: interference_flag is derived solely from interferenceState.
        // OverallConfidence.POOR does NOT set this flag.
        val interferenceFlag = snapshot.interferenceState == InterferenceState.MODERATE
                || snapshot.interferenceState == InterferenceState.WARNING

        // PM-T-01: captured_at is the tap timestamp carried in the snapshot,
        // NOT clock.nowMs() at execute time.
        val capturedAt = snapshot.tapTimestampMs

        // Notes coercion: empty or blank string → null (TSPEC §3.6)
        val notes = snapshot.notes?.trim()?.ifEmpty { null }

        // calibration_version carried in the snapshot (set at tap time by CompassViewModel)
        val calibrationVersion = snapshot.calibrationVersion

        val record = BearingRecord(
            id = UUID.randomUUID().toString(),
            name = snapshot.name,
            bearing_deg = snapshot.bearingDeg,
            north_type = snapshot.northType.name,
            confidence = snapshot.confidence.name,
            captured_at = capturedAt,
            calibration_version = calibrationVersion,
            field_deviation_pct = snapshot.fieldDeviationPct,
            inclination_deviation_deg = snapshot.inclinationDeviationDeg,
            interference_flag = interferenceFlag,
            lat = if (snapshot.includeLocation) snapshot.latDeg else null,
            lon = if (snapshot.includeLocation) snapshot.lonDeg else null,
            alt_m = if (snapshot.includeLocation) snapshot.altM else null,
            notes = notes,
            display_mode = snapshot.displayMode
        )

        bearingRepository.insert(record)
        return record
    }
}
