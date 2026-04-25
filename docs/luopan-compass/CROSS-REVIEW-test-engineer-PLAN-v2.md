# Cross-Review: TE — PLAN-luopan-p2-true-north-capture (Iteration 2)

**Reviewer role:** Senior Test Engineer
**Document reviewed:** PLAN-luopan-p2-true-north-capture.md v0.2
**Prior review:** CROSS-REVIEW-test-engineer-PLAN.md (Iteration 1 — Need Attention; 7 High, 3 Medium findings)
**Date:** 2026-04-24

---

## Summary

All ten Iteration 1 findings (TE-P-01 through TE-P-10) are **verified resolved** in v0.2. The plan is
substantially improved: the correct `interference_flag` derivation, `captured_at` tap-time semantics,
`WallClock` naming, canonical interface signatures, `BearingForceStopTest`, the NOAA pinned vector, the
migration gate ordering, all eight TSPEC test class mappings, the four mandatory `CompassViewModelTest`
cases, and the `"bearing_records.db"` filename are all correctly addressed.

However, review of v0.2 against the approved TSPEC (v0.2.1) reveals **five new issues** introduced or
left uncorrected in the revision: two package name mismatches that will cause compile errors when
implemented, one spurious method name on `MagneticFieldModelProvider`, one API mismatch on
`BearingRepository`, and one magnitude-tolerance typo in the P2.2 phase-table row. None of the new
issues are architectural blockers, but the two package naming issues (NEW-1, NEW-2) will cause import
errors at test integration time if not fixed before implementation begins.

---

## Verification of Iteration 1 Findings

| Finding | Status | Evidence |
|---------|--------|----------|
| **TE-P-01** — `BearingForceStopTest` in P8.4 | RESOLVED | P8.4 phase table description, Test Targets column, and §5.9 (`ProcessPhoenix` dependency note) all include `BearingForceStopTest` (AT-PERSIST-02). |
| **TE-P-02** — `interference_flag` from `InterferenceState` | RESOLVED | P6.1 phase table and P6.1 success criteria both state: `interference_flag = true` when `snapshot.interferenceState ∈ {MODERATE, WARNING}`; `OverallConfidence.POOR` alone does NOT set this flag (FSPEC BR-10, AT-E-10). POOR+CLEAR→false test case is explicit. |
| **TE-P-03** — `captured_at = snapshot.tapTimestampMs` | RESOLVED | P6.1 phase table, P6.1 success criteria, and §5.4 all correctly specify that `captured_at` equals `snapshot.tapTimestampMs`; use case does NOT call `clock.nowMs()` for this field. The `FakeClock` T=1000/T=15000 verification scenario is present. |
| **TE-P-04** — NOAA pinned vector in P8.1 | RESOLVED | P8.1 phase table row and P8.1 success criteria both contain the exact vector: `lat=40.0°N, lon=−105.0°W, altM=0.0, epochYears=2025.5`, with all three assertion values and tolerances, and the "must be first assertion / must not use live calculator" constraints. P2.2 success criteria also carry the pinned vector. |
| **TE-P-05** — Migration test in Batch 4, before P6.2 | RESOLVED | Batch 4 explicitly lists `LuopanDatabaseMigrationTest (P8.4 gate)`. Batch 5 lists P6.2 with precondition "LuopanDatabaseMigrationTest passed". §5.2 and P8.4 success criteria both label the migration test a "hard gate" before P6.2. |
| **TE-P-06** — `WallClock` (not `SystemClock`) | RESOLVED | P1.1 phase table, P1.1 success criteria, and §5.8 all specify `WallClock`; the word `SystemClock` does not appear in implementation context. §5.8 also prohibits the default-parameter pattern. |
| **TE-P-07** — Correct interface signatures | RESOLVED | P1.1 lists `isExpired()` (no parameters) and `getDeclination(latDeg, lonDeg, altM, epochYears)`, `getExpectedInclination(latDeg, lonDeg, altM, epochYears)`, `getExpectedFieldMagnitude(latDeg, lonDeg, altM, epochYears)`. P4.2 and P8.x use `getExpectedFieldMagnitude` and `getExpectedInclination`. No `getMagnitude()` or `getInclination()` shorthand remains. |
| **TE-P-08** — All 8 test classes mapped | RESOLVED | `WmmDeclinationAccuracyTest` → P8.1; `LocationCacheAgeLabelTest`, `NoGpsDialogTest`, `InterferenceBaselineUpgradeTest` → P8.2; `BearingForceStopTest` → P8.4; `LocationPermissionRationaleTest`, `GridNorthAbsenceTest`, `PermissionDeniedCaptureTest` → P8.5. All 8 appear in the respective phase table rows and success criteria. |
| **TE-P-09** — `CompassViewModelTest` 4 mandatory cases | RESOLVED | P8.3 phase table and P8.3 success criteria list all four cases verbatim: (a) 200 ms heading budget with FakeSensorFusion; (b) 60 s WMM expiry debounce with FakeClock.advance(59_999L); (c) captureButtonEnabled BR-CAP-08 with FakeBearingCaptureUseCase; (d) extreme latitude confidence cap with inclination=80.0°. Test-double types and key assertion values are present. |
| **TE-P-10** — `"bearing_records.db"` in P8.4 | RESOLVED | P8.4 phase table and P8.4 success criteria both reference `getDatabasePath("bearing_records.db")` and explicitly prohibit `"luopan.db"` in Phase 2 tests. |

---

## New Findings in v0.2

| ID | Severity | Phase/Section | Finding | Recommendation |
|----|----------|---------------|---------|----------------|
| **NEW-1** | Medium | P2.2, P2.3, P2.4, P5.1 (phase table) | **Wrong package name for magnetic model classes.** The phase-table Description column for P2.2, P2.3, P2.4, and P5.1 places these classes in `com.luopan.compass.wmm`. TSPEC §1.2 specifies the canonical package is `com.luopan.compass.magnetic`. An implementer following the phase-table description will create files under `wmm/`; the test suite (which imports from `com.luopan.compass.magnetic`) will fail to compile. Note: P8.1 and P8.2 success criteria correctly reference `com.luopan.compass.magnetic` (line 265) — the conflict is confined to the phase-table Description column. | Replace `com.luopan.compass.wmm` with `com.luopan.compass.magnetic` in the P2.2, P2.3, P2.4, and P5.1 phase-table Description cells to match TSPEC §1.2. |
| **NEW-2** | Medium | P6.1, P6.2 (phase table) | **Wrong package name for bearing capture classes.** The phase-table Description column for P6.1 (`BearingCaptureUseCase`) and P6.2 (`BearingRepository`) places them in `com.luopan.compass.capture`. TSPEC §1.2 specifies the canonical package is `com.luopan.compass.bearing`. The same discrepancy would cause compile errors when tests import `BearingCaptureUseCase` or `BearingRepository` from `com.luopan.compass.bearing`. The P8.2 success criteria already use the correct `com.luopan.compass.bearing` reference (line 273). | Replace `com.luopan.compass.capture` with `com.luopan.compass.bearing` in the P6.1 and P6.2 phase-table Description cells to match TSPEC §1.2. |
| **NEW-3** | Medium | P2.4 (phase table and success criteria) | **`MagneticFieldModelProvider` exposes a non-existent method `activeSourceLabel()`.** The P2.4 phase-table row states "Exposes `activeSourceLabel(): String`" and the P2.4 success criteria state "`activeSourceLabel()` returns `\"WMM2025 (valid to 2030)\"` or `\"Android model — may be less accurate\"`." TSPEC §3.3 defines `MagneticFieldModelProvider` with four methods: `activeModel()`, `evaluate()`, `getLastResult()`, and `hasModelChanged()`. There is no `activeSourceLabel()`. A source-label string is derivable from `activeModel().getModelId()` at the call site, but the method itself is not part of the TSPEC contract. An implementer who adds `activeSourceLabel()` will have a method the TSPEC-authored tests do not test, and `MagneticFieldModelProviderTest` will not assert on it. | Remove `activeSourceLabel()` from the P2.4 phase-table Description and P2.4 success criteria. The source label for the declination info panel is obtained by the caller from `activeModel().getModelId()` per TSPEC §3.3 and §3.7. If a convenience method is genuinely desired, it must be added to TSPEC §3.3 first. |
| **NEW-4** | Low | P6.2 (phase table and success criteria) | **`BearingRepository` API diverges from TSPEC §3.5.** The P6.2 phase-table Description exposes `save(BearingRecord)` and `getById(id)`. TSPEC §3.5 specifies `insert(record: BearingRecord)` (not `save()`), `count(): Int`, `getAll()`, and `delete(id)` — `getById(id)` is absent; `count()` is absent. The P6.2 success criteria say "`save(BearingRecord)` persists via BearingDao" and the P6.3 description says "invoke `BearingRepository.save()`", which would fail to compile against a TSPEC-compliant implementation that exposes `insert()`. The `count()` method is also needed by `BearingCaptureUseCase` to generate the default capture name "Bearing [N+1]" (TSPEC §3.5). | Align the P6.2 phase-table Description and success criteria with TSPEC §3.5: rename `save()` to `insert()`, add `count()`, and remove `getById(id)` (not in TSPEC for Phase 2). Update the P6.3 description to call `BearingRepository.insert()` rather than `BearingRepository.save()`. |
| **NEW-5** | Low | P2.2 (phase table Test Targets column) | **Magnitude tolerance is `±100 nT` in the phase-table row, but `±200 nT` everywhere else.** The P2.2 phase-table Test Targets cell states "±100 nT for magnitude." The P2.2 success criteria (line 160), the P8.1 phase-table row, the P8.1 success criteria, and TSPEC §10.1 all specify `±200 nT` for the total-field magnitude tolerance. This inconsistency in the phase-table row may cause a future code reviewer or implementer to write a tighter assertion than TSPEC mandates, producing a test that fails even for a correct WMM implementation. | Correct the P2.2 Test Targets column to read `±200 nT for magnitude`, matching TSPEC §10.1, P2.2 success criteria, and P8.1. |

---

## Additional Observations (No Action Required)

These observations are recorded for completeness but are not findings requiring a PLAN revision:

1. **P3.1 phase-table uses `FusedLocationProviderClient`; TSPEC §3.4 and §6.2 use `LocationManager.GPS_PROVIDER`.** The P3.1 phase-table Description mentions "request fresh GPS fix via `FusedLocationProviderClient`." TSPEC §3.4, §6.2, §8.1, and §10.1 consistently specify `LocationManager.requestLocationUpdates()` with `GPS_PROVIDER` and a `ShadowLocationManager` Robolectric shadow for unit testing. This is visible in the phase-table only, not in the P3.1 success criteria (which are API-neutral). Since the success criteria and TSPEC are authoritative over the phase-table prose description, no plan change is strictly required — but the description is misleading. Consider correcting the P3.1 Description to reference `LocationManager.GPS_PROVIDER` to avoid a wasted implementation choice at P3.1.

2. **P2.2 phase-table `isExpired()` description is incomplete.** The P2.2 Description column says "`isExpired()` returns true when `epochYear ≥ 2030.0`" but omits the lower-bound condition (`< 2025.0`). The P2.2 success criteria (line 161) correctly list both bounds. Since the success criteria are authoritative, no correctness risk exists, but the phase-table description is misleading for a reader who does not read the full success-criteria section.

3. **P3.1 uses `LocationResult` in the phase-table Description and P3.1 / P7.2 success criteria; TSPEC §3.4 uses `LocationState`.** The type names `GpsFix`/`CachedFix(ageMs)`/`ManualEntry` in the plan description differ from TSPEC's `GpsFresh`/`GpsCached`/`Manual`. P8.5 success criteria already use `LocationState.Unavailable` (the TSPEC name), so the discrepancy is within the phase-table Description only. No compile risk: the TSPEC is authoritative for type names.

---

## Recommendation: Approved with Conditions

Iteration 1's seven High findings and three Medium findings are all resolved. The PLAN is significantly
improved and structurally sound. However, NEW-1 and NEW-2 (Medium) specify wrong package names in the
phase-table Description rows that will cause compile errors if an implementer follows the phase table
without cross-referencing TSPEC §1.2. NEW-3 (Medium) introduces a non-existent method that will not be
tested. These three issues must be corrected before implementation begins for Batch 1/Batch 2 phases.

NEW-4 (Low) and NEW-5 (Low) are correction items that should be addressed in the same revision. The
additional observations do not require plan changes but may be addressed at the author's discretion.

**Conditions for approval (must fix before implementation):**
- NEW-1: Correct `com.luopan.compass.wmm` → `com.luopan.compass.magnetic` in P2.2, P2.3, P2.4, P5.1 descriptions.
- NEW-2: Correct `com.luopan.compass.capture` → `com.luopan.compass.bearing` in P6.1, P6.2 descriptions.
- NEW-3: Remove `activeSourceLabel()` from P2.4 description and success criteria.

**Should also fix in same revision:**
- NEW-4: Align `BearingRepository` API to TSPEC §3.5 (`insert()` not `save()`; add `count()`; remove `getById()`).
- NEW-5: Correct P2.2 phase-table Test Targets tolerance from `±100 nT` to `±200 nT`.
