# Cross-Review: TE — PLAN-luopan-p2-true-north-capture

**Reviewer role:** Senior Test Engineer
**Document reviewed:** PLAN-luopan-p2-true-north-capture.md
**Date:** 2026-04-25

---

## Summary

The PLAN provides a reasonable high-level execution structure for Phase 2 and correctly identifies
the major implementation phases. However, it contains several significant gaps from a test
engineering perspective:

1. **Eight TSPEC test classes are not mapped to any PLAN phase.** This means the implementer has
   no signal to write those tests, and CI will not have them.
2. **AT-PERSIST-02 (`BearingForceStopTest`) is entirely absent** from P8.4.
3. **The `interference_flag` derivation in P6.1 is wrong** — the success criterion specifies
   `confidence == POOR` as the trigger, directly contradicting BR-10 and AT-E-10.
4. **`captured_at` semantics in P6.1 / §5.4 are wrong** — the PLAN says to call `Clock.nowMs()`
   at `execute()` time; the TSPEC/FSPEC require the tap-time timestamp carried in `BearingSnapshot`.
5. **The specific NOAA reference vector** (`lat=40.0, lon=−105.0, epoch=2025.5`) mandated as the
   primary pinned assertion by TSPEC §10.1 is not called out in P2.2 or P8.1.
6. **P8.4 is placed in Batch 6 (after P6.2)**, meaning the migration test that is declared a
   "hard gate" in §5.2 runs after the production code that depends on the migrated schema.
7. **Production class `WallClock` is renamed `SystemClock`** in P1.1, conflicting with the TSPEC
   and creating a real collision risk with `android.os.SystemClock`.
8. **Interface method names in P1.1 and P4.2** diverge from the TSPEC-canonical signatures.

Seven findings are **High** severity; three are **Medium**. No finding is a fundamental architectural
blocker, but findings TE-P-01, TE-P-03, TE-P-04, and TE-P-06 must be resolved before implementation
begins to avoid rework.

---

## Findings

| ID | Severity | Phase | Finding | Recommendation |
|----|----------|-------|---------|----------------|
| TE-P-01 | High | P8.4 | **AT-PERSIST-02 (`BearingForceStopTest`) missing.** TSPEC §10.2 specifies `BearingForceStopTest` (force-stop resilience, AT-PERSIST-02) as a required instrumented test. P8.4 lists only `LuopanDatabaseMigrationTest` and `BearingEncryptionTest`. There is no PLAN phase that adds `BearingForceStopTest` or references AT-PERSIST-02 anywhere in the document. | Add `BearingForceStopTest` to P8.4 description, success criteria, and the P8.4 test-targets column. Add the `ProcessPhoenix` dependency note (already in TSPEC) to §5 Key Implementation Notes. |
| TE-P-02 | High | P6.1 | **`interference_flag` derivation is wrong.** P6.1 success criteria state: "`interference_flag = true` when `confidence == POOR`." This contradicts FSPEC BR-10, AT-E-10, and TSPEC §10.1 `BearingCaptureUseCaseTest`, all of which specify that `interference_flag` is derived from `InterferenceState ∈ {MODERATE, WARNING}` — not from `OverallConfidence.POOR`. A POOR-confidence capture with `InterferenceState.CLEAR` must produce `interference_flag = false`. | Replace P6.1 success criterion with: "`interference_flag = true` when `InterferenceState` is `MODERATE` or `WARNING`; `= false` when `CLEAR`, even if `OverallConfidence == POOR` (AT-E-10)." Update §5 if it restates this logic. |
| TE-P-03 | High | P6.1, §5.4 | **`captured_at` semantics conflict with TSPEC PM-T-01.** PLAN §5.4 states: "Use `Clock.nowMs()` injected at use-case construction time, called at the start of `execute()`." TSPEC §3.6 (PM-T-01 resolution) and FSPEC BR-09 require `captured_at = snapshot.tapTimestampMs` — the wall-clock milliseconds recorded by `CompassViewModel.captureBearing()` *before any dialog is shown*, carried through `BearingSnapshot`. The use case must NOT call `Clock.nowMs()` for `captured_at`; it reads the tap timestamp from the snapshot. The `BearingCaptureUseCaseTest` mandatory assertion explicitly tests this: set `FakeClock` to T=1000 at snap time and T=15000 at execute time; assert `captured_at == 1000`. | Rewrite §5.4: "`captured_at` must equal `snapshot.tapTimestampMs`, populated by `CompassViewModel.captureBearing()` via `clock.nowMs()` before any dialog is shown. `BearingCaptureUseCase.execute()` reads this value from the snapshot — it does NOT call `clock.nowMs()` for `captured_at`." Update P6.1 success criterion to match. |
| TE-P-04 | High | P2.2, P8.1 | **Primary NOAA-pinned test vector not specified.** TSPEC §10.1 declares the `lat=40.0, lon=−105.0, altM=0.0, epochYears=2025.5` vector as the **required primary assertion** for `Wmm2025ModelTest` (TE-T-01; REQ §8 TE-REQ-02) with tolerances `±0.1°` declination, `±0.5°` inclination, `±200 nT` total field. P2.2 says "≥5 NOAA reference test vectors" and P8.1 says "full coverage per TSPEC" — neither names the specific vector. Without this explicit callout, an implementer may omit or replace the primary pinned assertion with a live-calculator call, which the TSPEC explicitly prohibits. | Add to P2.2 and P8.1 success criteria: "Primary pinned vector: `lat=40.0, lon=−105.0, altM=0.0, epochYears=2025.5`. Assertions: `getDeclination() == +8.93° ± 0.1°`, `getExpectedInclination() == +66.0° ± 0.5°`, `getExpectedFieldMagnitude() == 52300 nT ± 200 nT`. This vector must be present, must be the first assertion, and must not be replaced with a call to a live NOAA calculator." |
| TE-P-05 | High | P8.4 (Batch 6) | **Migration test ordering violates the stated hard gate.** §5.2 declares `LuopanDatabaseMigrationTest` in P8.4 a "hard gate" before merging P1.3 changes. However, the Batch schedule places P8.4 in **Batch 6** (after P6.2 `BearingRepository` in Batch 5), meaning `BearingRepository` production code is implemented and reviewed against an untested migration. TSPEC §10.2 and REQ §10.2 make explicit that `MigrationTestHelper` must verify the migration before the new data layer is used. | Move `LuopanDatabaseMigrationTest` from P8.4 into **P1.3** as a mandatory success-criteria gate. P1.3 already partially acknowledges this: "P8.4 is a hard gate" — but the test must be in P1.3's own phase or in a new P1.3a that must precede P6.2 in the dependency graph. If the full P8.4 phase must remain separate, add a dependency edge `P1.3 → LuopanDatabaseMigrationTest` that blocks P6.2 in Batch 5. |
| TE-P-06 | High | P1.1 | **Production Clock implementation named `SystemClock`, not `WallClock`.** P1.1 success criteria state: "`SystemClock` delegates to `System.currentTimeMillis()`." TSPEC §2.2 specifies the production class is named **`WallClock`** precisely to avoid shadowing `android.os.SystemClock`, which is imported in every Android file. Using the name `SystemClock` recreates the exact collision the TSPEC was designed to prevent. | Replace all occurrences of `SystemClock` in P1.1 and §5.x with `WallClock` to match TSPEC §2.2. |
| TE-P-07 | High | P1.1, P4.2 | **Interface method names deviate from TSPEC canonical signatures.** P1.1 declares `isExpired(epochYear)` (with a parameter) and P4.2 references `getMagnitude()` and `getInclination()`. TSPEC §2.1 specifies: `isExpired()` (no parameters — epoch year is computed internally via the injected `Clock`), `getExpectedFieldMagnitude(latDeg, lonDeg, altM, epochYears)`, and `getExpectedInclination(latDeg, lonDeg, altM, epochYears)`. Divergent names will cause compile errors when the test suite (which uses the canonical names from TSPEC) is integrated. | Update P1.1 to show `isExpired()` with no parameters. Update P4.2, P2.2, P2.3, P2.4, and P8.x wherever `getMagnitude()` / `getInclination()` appear, replacing with `getExpectedFieldMagnitude()` / `getExpectedInclination()`. Align all success criteria method names with TSPEC §2.1 exactly. |
| TE-P-08 | Medium | P8.x | **Eight TSPEC test classes have no PLAN phase mapping.** The following TSPEC §10 test classes are specified but do not appear anywhere in the PLAN phases or success criteria: (1) `WmmDeclinationAccuracyTest` (AT-B unit, TSPEC §10.1); (2) `LocationCacheAgeLabelTest` (AT-C, TSPEC §10.1); (3) `NoGpsDialogTest` (AT-D, TSPEC §10.1); (4) `InterferenceBaselineUpgradeTest` (AT-F, TSPEC §10.1); (5) `BearingForceStopTest` (AT-PERSIST-02, TSPEC §10.2 — also Finding TE-P-01); (6) `LocationPermissionRationaleTest` (BR-LOC-04, TSPEC §10.3); (7) `GridNorthAbsenceTest` (AT-G-08, TSPEC §10.3); (8) `PermissionDeniedCaptureTest` (AT-G-09, TSPEC §10.3). Without PLAN inclusion, implementers have no signal to write these tests. | Add the unmapped test classes to their natural home phases: `WmmDeclinationAccuracyTest` → P8.1 or P8.3; `LocationCacheAgeLabelTest` → P8.2; `NoGpsDialogTest` → P8.2 or P8.5; `InterferenceBaselineUpgradeTest` → P8.1 or P8.2; `LocationPermissionRationaleTest` and `PermissionDeniedCaptureTest` → P8.5; `GridNorthAbsenceTest` → P8.5. At minimum, add a note under each test phase referencing the TSPEC class names. |
| TE-P-09 | Medium | P8.3 | **`CompassViewModelTest` P8.3 success criteria are weaker than TSPEC §10.1 mandatory cases.** P8.3 lists: "north toggle heading delta, advisory flags, extreme latitude, WMM interference baseline." TSPEC §10.1 specifies **four mandatory cases** with precise mechanics: (a) 200 ms heading budget with `FakeSensorFusion`; (b) 60 s WMM expiry debounce (two `checkWmmExpiry()` calls within 59 999 ms assert one `isExpired()` call); (c) `captureButtonEnabled` BR-CAP-08 (slow `FakeBearingCaptureUseCase` suspend test); (d) extreme latitude confidence cap with `inclination = 80.0°` assertion. None of the mandatory case mechanics (test doubles, timing, assertion values) appear in P8.3. | Expand P8.3 success criteria to list all four TSPEC §10.1 mandatory cases with the test-double types and key assertion values. Reference TSPEC §10.1 `CompassViewModelTest` block explicitly. |
| TE-P-10 | Medium | P8.4 | **P8.4 encryption test description should reference the canonical database filename.** P8.4 refers to `BearingEncryptionTest` but does not state the canonical filename `"bearing_records.db"`. TSPEC §4.5 normative note and §10.2 both require tests to use `targetContext.getDatabasePath("bearing_records.db")`. Without this callout there is a risk of a test using the old Phase 1 `"luopan.db"` name. | Add to P8.4 success criteria: "All raw-file database access in tests uses `targetContext.getDatabasePath(\"bearing_records.db\")`. The name `\"luopan.db\"` must not appear in any Phase 2 test (TE-T-02 normative)." |

---

## Recommendation: Need Attention

The PLAN cannot be handed to an implementer in its current state without risk of:
- Incorrect `interference_flag` behavior shipping (TE-P-02 — a functional correctness bug)
- Incorrect `captured_at` timestamps in every saved record (TE-P-03 — a data integrity bug)
- Missing AT-PERSIST-02 force-stop resilience test (TE-P-01)
- Migration test running after the production code it gates (TE-P-05)
- `SystemClock` naming collision with `android.os.SystemClock` (TE-P-06)
- Interface signature mismatches causing compile failures at test integration (TE-P-07)

Findings TE-P-01 through TE-P-07 must be resolved before implementation starts.
TE-P-08 through TE-P-10 should be addressed in the same revision to ensure test completeness.
