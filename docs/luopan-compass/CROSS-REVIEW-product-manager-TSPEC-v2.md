# Cross-Review: PM — TSPEC-luopan-p2-true-north-capture (Iteration 2)

**Reviewer role:** Product Manager
**Document reviewed:** TSPEC-luopan-p2-true-north-capture.md v0.2-draft
**Prior review:** CROSS-REVIEW-product-manager-TSPEC.md (Iteration 1 — Approved with Minor Issues)
**Date:** 2026-04-24

---

## Review Scope

This iteration 2 review verifies that the four Medium/Low findings from iteration 1 (PM-T-01, PM-T-03, PM-T-04, PM-T-06) are resolved in v0.2-draft, checks the remaining traceability table, and scans for any new scope compliance issues introduced in the revision.

---

## Verification of Prior Findings

### PM-T-01 (Medium) — `captured_at` must record tap time via `tapTimestampMs`

**Status: RESOLVED**

`BearingSnapshot` now includes `val tapTimestampMs: Long` (§3.6), populated by `CompassViewModel.captureBearing()` via `clock.nowMs()` before any dialog is shown. `BearingCaptureUseCase.execute()` sets `BearingRecord.captured_at` to `snapshot.tapTimestampMs`, not to `clock.nowMs()` at execute time. The key implementation note in §3.6 explicitly states: "The use case does NOT use `clock.nowMs()` at execute time for `captured_at`; the tap timestamp is carried through the snapshot." The test in §10.1 (`BearingCaptureUseCaseTest`) verifies this with a `FakeClock` set to T=1000 at snap time and T=15000 at execute time, asserting `captured_at == 1000`. This fully resolves the product fidelity concern raised in iteration 1.

---

### PM-T-03 (Low) — `LocationRepository` must be in `BearingCaptureUseCase` constructor

**Status: RESOLVED**

`BearingCaptureUseCase` now lists `LocationRepository` as a constructor parameter (§3.6 constructor table and Kotlin code block). The §1.3 dependency graph also lists `LocationRepository` under `BearingCaptureUseCase`. The PM-T-03 resolution note in §3.6 clarifies the intended runtime contract: the ViewModel populates location fields in the snapshot at tap time, and the use case uses the snapshotted values directly in Phase 2; the `LocationRepository` constructor dependency makes the contract explicit for future phases. The §1.3 graph and §3.6 constructor are now consistent.

---

### PM-T-04 (Low) — WMM loading timing must use async deferred with await; no main-thread blocking

**Status: RESOLVED**

§5.2 now contains a dedicated "Cold-start impact and lazy-loading init path (PM-T-04)" block with a numbered four-step init sequence:

1. `CompassViewModel.init {}` launches `viewModelScope.async(Dispatchers.IO) { Wmm2025Model(context, clock) }` — the `Deferred<Wmm2025Model?>`.
2. WMM coefficients are loaded via a `Mutex`-protected deferred on `Dispatchers.IO`; `MagneticFieldModelProvider.getModel()` calls `awaitCoefficients()` if unresolved.
3. No blocking on the main thread; cold starts resolve WMM on the first sensor tick (100–300 ms after `onStart`).
4. When `setNorthType(TRUE)` is called before the deferred completes, `CompassViewModel` calls `wmm.await()` inside a `viewModelScope.launch(Dispatchers.IO)` block — `await()` never blocks the main thread.

§8.3 confirms `Wmm2025Model` is held in a `Deferred<Wmm2025Model?>` via `viewModelScope.async(Dispatchers.IO)`. The init path is fully specified and unambiguous.

---

### PM-T-06 (Info) — `NETWORK_PROVIDER`/`PASSIVE_PROVIDER` removed from GPS strategy

**Status: RESOLVED**

§6.3 now carries a dedicated subsection "Location Provider Scope (PM-T-06)" with normative language: "`NETWORK_PROVIDER` and `PASSIVE_PROVIDER` are NOT used in Phase 2." The section provides clear rationale (accuracy labeling concern) and describes the fallback behavior when `GPS_PROVIDER` is unavailable. §6.1 (Priority Chain table) contains only GPS_PROVIDER steps. No reference to `NETWORK_PROVIDER` or `PASSIVE_PROVIDER` appears anywhere in the document.

---

## Previously Low Findings — Status Check

### PM-T-02 (Low) — REQ-DECL-02 absent from §0 traceability table

**Status: RESOLVED**

The §0 table now contains two rows covering REQ-DECL-02:
- `§7.2 — Declination Info Panel | REQ-DECL-02 | FSPEC-DECLPANEL §2.3`
- `§7 — UI Changes | REQ-DECL-01, REQ-DECL-02, REQ-CAPTURE-02 | FSPEC-TOGGLE, FSPEC-DECLPANEL, FSPEC-CAPTURE`

---

### PM-T-05 (Low) — `getAll()` and `delete()` not annotated as Phase 4 stubs

**Status: PARTIALLY ADDRESSED — MINOR REMAINING**

Both `BearingRepository.getAll()` and `BearingRepository.delete()` include KDoc comments noting "Used in Phase 4 for the history screen; present now to avoid a future migration" (§3.5) and "Used in Phase 4; present now" respectively. These annotations make the Phase 4 intent clear. However, neither method carries an explicit "not called from any Phase 2 UI path" contract, as recommended in iteration 1. This is a minor documentation gap — there is no risk of premature feature exposure given the note text, and the deferred list in §9 cross-references these methods implicitly. No new finding raised; noting for completeness.

---

### PM-T-07 (Info) — `CompassUiState` additions not shown as a full data class

**Status: PARTIALLY ADDRESSED**

§3.7 lists new `CompassUiState` fields as commented code (not a full data class block). The new fields (`northType`, `declinationDeg`, `locationState`, `extremeLatitudeAdvisory`, `wmmModelId`, `captureButtonEnabled`) are enumerated but the full class definition is absent. A `DeclinationInfo` data class is referenced in §7.2 but not defined in the TSPEC. This remains a low-priority documentation gap (noted in iteration 1 as "Info" severity) and does not affect implementation correctness.

---

## Additional Traceability Scan

The §0 Requirements Traceability table is compared against all in-scope REQ IDs from REQ-luopan-p2-true-north-capture.md §5:

| REQ-ID | In §0 table? | Coverage |
|--------|-------------|---------|
| REQ-NORTH-01 | Yes (§2, §3.1, §5) | Full |
| REQ-NORTH-02 | Yes (§2, §3.2) | Full |
| REQ-NORTH-03 | Yes (§3.4, §6) | Full |
| REQ-NORTH-04 | Yes (§3.7, §7) | Full |
| REQ-DECL-01 | Yes (§3.7, §7) | Full |
| REQ-DECL-02 | Yes (§7.2, §7) | Full (resolved PM-T-02) |
| REQ-CAPTURE-01 | Yes (§3.5, §3.6, §4) | Full |
| REQ-CAPTURE-02 | Yes (§7) | Full |
| REQ-CAPTURE-04 | Yes (§3.5, §4) | Full |
| REQ-CAPTURE-06 | Yes (§6) | Full |
| REQ-NFR-08 | Yes (§8, §10.4) | Full |
| REQ-DETECT-01/02 | Yes (§3.8) | Full |

No traceability gaps remain among in-scope requirements. The deferred requirements (REQ-CAPTURE-03, REQ-CAPTURE-05, REQ-DECL-03, REQ-DISPLAY-04–06) are correctly excluded and not implemented.

---

## Scope Compliance Check — New Content in v0.2-draft

The following new sections and content were added to address TE cross-review findings (TE-T-01 through TE-T-10). Scope compliance is verified for each:

| New content | Scope risk? | Assessment |
|-------------|-------------|------------|
| NOAA-pinned WMM test vector in §10.1 | None | Test infrastructure only; no product behavior added |
| `bearing_records.db` canonical filename note in §4.5 | None | Normative clarification; aligns with FSPEC AT-PERSIST-01 |
| AT-B/C/D/F test classes in §10.1 | None | Test coverage only |
| `CompassViewModelTest` in §10.1 | None | Test coverage only |
| BR-CAP-08 concurrency test in §10.1 | None | Test coverage; validates FSPEC BR-CAP-08 |
| `WallClock` DI contract (TE-T-06) in §2.2 | None | Enforcement convention for existing `Clock` interface |
| `LocationPermissionRationaleTest` in §10.3 | None | Tests FSPEC-GPS §2.4 step 2a / BR-LOC-04 |

No new scope creep identified. All additions are test strategy, naming normalization, or DI contract clarifications.

---

## New Observations (Minor)

### OBS-1 (Info) — Minor internal inconsistency in §5.2 construction narrative

§5.2 states both: (a) "`Wmm2025Model` loads the file in its constructor" (eager), and (b) "WMM coefficients are loaded lazily on first access via a `Mutex`-protected `val coefficients`" (lazy). The two statements appear to contradict: the constructor either loads eagerly or lazily. Reading in context, the intended design is that the constructor starts the loading (constructor called on `Dispatchers.IO`) and the `Mutex`-protected pattern handles the case where `MagneticFieldModelProvider.getModel()` is called before the constructor has completed. This is a minor narrative clarity issue; it does not create an implementation ambiguity given the four-step init sequence that follows. No action required before implementation.

### OBS-2 (Info) — `DeclinationInfo` data class referenced but not defined

§7.2 references a `DeclinationInfo` data class exposed as `StateFlow<DeclinationInfo>` from `CompassViewModel`, but no definition of this class appears in the TSPEC. The fields can be inferred from §7.2's layout specification (declination value, source label, coordinates, coordinates type, last-updated date, cache age). Engineering teams will need to define this class during implementation. This is consistent with PM-T-07 from iteration 1 (Info severity) — acceptable given TSPEC scope.

---

## Summary

All four Medium/Low findings from iteration 1 are resolved:

| Finding | Original Severity | Iteration 2 Status |
|---------|------------------|-------------------|
| PM-T-01 — `tapTimestampMs` for tap-time `captured_at` | Medium | RESOLVED |
| PM-T-03 — `LocationRepository` in `BearingCaptureUseCase` constructor | Low | RESOLVED |
| PM-T-04 — WMM async deferred/await, no main-thread blocking | Low | RESOLVED |
| PM-T-06 — NETWORK/PASSIVE providers removed | Info | RESOLVED |

The two remaining observations (OBS-1, OBS-2) are narrative clarity notes, both Info severity, and do not block implementation. Requirements traceability is complete for all in-scope Phase 2 REQ IDs. No new scope creep was introduced in v0.2-draft.

---

## Recommendation: Approved

All four iteration 1 PM findings have been fully addressed in v0.2-draft. The TSPEC is ready for PLAN authoring. The two new observations are informational only and may be resolved as part of normal implementation detail during the PLAN or coding phase.
