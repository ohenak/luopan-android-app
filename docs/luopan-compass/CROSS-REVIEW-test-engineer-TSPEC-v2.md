# Cross-Review: TE — TSPEC-luopan-p2-true-north-capture (Iteration 2)

**Reviewer role:** Senior Test Engineer  
**Document reviewed:** TSPEC-luopan-p2-true-north-capture.md (v0.2-draft)  
**Prior review:** CROSS-REVIEW-test-engineer-TSPEC.md (iteration 1; Recommendation: Need Attention)  
**Date:** 2026-04-24  

---

## Summary

v0.2-draft resolves both blockers and all three High findings from iteration 1. The WMM unit-test vector is correctly pinned to `lat=40.0, lon=−105.0, epoch=2025.5` with all three required assertions; the canonical database filename is `bearing_records.db` throughout §4.5 and §10.2 with a normative note; AT-B, AT-C, AT-D, and AT-F test classes are all present; `CompassViewModelTest` contains all four mandated cases; and the concurrent double-tap test is specified. The `WallClock` DI contract (TE-T-06) and `LocationPermissionRationaleTest` (TE-T-10) are both addressed. Three of the five iteration-1 Medium findings (TE-T-07 via the `tapTimestampMs` mechanism, TE-T-08 via the migration round-trip assertion, and TE-T-09 partially via the ViewModel unit test) have been addressed to varying degrees.

Three new issues were identified in v0.2-draft. One is High: the concurrent double-tap test in `BearingCaptureUseCaseTest` mixes ViewModel-level state (`captureBearing()`, `captureButtonEnabled`) into a use-case test, creating an untestable or architecturally incorrect assertion boundary that will likely cause the test to fail or be rewritten at implementation time. One is Medium: the `WmmDeclinationAccuracyTest` for AT-B uses a `FakeMagneticFieldModel` rather than the real WMM engine, which tests ViewModel propagation but leaves AT-B-03's accuracy assertion (|computed_declination − R| ≤ 0.1° against NOAA reference) without any test mapping. One is Low: a residual sentence in §4.5 still refers to `"luopan.db"` (the Phase 1 database name), creating a latent reader-confusion risk even though the normative note for the canonical name is correct.

The two remaining un-addressed Minor findings from iteration 1 (TE-T-11 and TE-T-12) are also noted below; both are minor and do not prevent implementation approval.

---

## Prior Finding Status

| Prior ID | Severity | Status | Notes |
|----------|----------|--------|-------|
| TE-T-01 | BLOCKER | **Resolved** | `Wmm2025ModelTest` §10.1 specifies `lat=40.0, lon=−105.0, altM=0.0, epochYears=2025.5` as the primary NOAA-pinned vector with all three assertions (declination +8.93°±0.1°, inclination +66.0°±0.5°, totalField 52300 nT±200 nT). Pinned vector is the first assertion; supplementary vectors are optional. |
| TE-T-02 | BLOCKER | **Resolved** | §4.5 normative note declares `"bearing_records.db"` as the canonical filename and updates `Room.databaseBuilder` accordingly. `BearingEncryptionTest` in §10.2 uses `targetContext.getDatabasePath("bearing_records.db")` with a bolded normative callout. |
| TE-T-03 | High | **Resolved** | All four test classes added to §10.1: `WmmDeclinationAccuracyTest` (AT-B), `LocationCacheAgeLabelTest` (AT-C), `NoGpsDialogTest` (AT-D), `InterferenceBaselineUpgradeTest` (AT-F). Key assertions are specified for each. See new finding TE-T-03-NEW regarding AT-B accuracy coverage. |
| TE-T-04 | High | **Resolved** | `CompassViewModelTest` added to §10.1 with all four mandatory cases: (a) heading budget, (b) 60 s WMM expiry debounce, (c) `captureButtonEnabled` BR-CAP-08 disable/re-enable, (d) extreme latitude confidence cap at inclination=80.0°. |
| TE-T-05 | High | **Partially Resolved — new issue raised** | Concurrent double-tap test is now specified in `BearingCaptureUseCaseTest`. However, the test references `captureBearing()` and `captureButtonEnabled`, which are ViewModel-level concerns, not `BearingCaptureUseCase` concerns. See new finding TE-T-05-NEW. `CompassViewModelTest` case (c) also covers this at the correct layer. |
| TE-T-06 | Medium | **Resolved** | §2.2 explicitly prohibits `class Foo(private val clock: Clock = WallClock())` and states: "Constructor parameters of type `Clock` MUST NOT have default values." `Wmm2025Model` constructor signature in §3.1 is `class Wmm2025Model(context: Context, private val clock: Clock)` with no default. |
| TE-T-07 | Medium | **Resolved** | `tapTimestampMs` field added to `BearingSnapshot` (§3.6). `BearingCaptureUseCaseTest` verifies `captured_at == snapshot.tapTimestampMs` using `FakeClock` at T=1000 vs T=15000, directly testing the intent. The accepted delta is implicit (the test asserts equality, not a bound), which is stricter than the recommendation and acceptable. |
| TE-T-08 | Medium | **Resolved** | `LuopanDatabaseMigrationTest` (§10.2) now specifies: "Creates a v1 database with a `calibration_records` row, runs `MIGRATION_1_2`, verifies `bearing_records` table exists... Verifies `calibration_records` is untouched." The round-trip data preservation assertion is present. |
| TE-T-09 | Medium | **Partially Resolved** | `CompassViewModelTest` case (d) covers AT-G-01 (advisory fires) and AT-G-02 (confidence capped at Moderate) at the unit level. AT-G-04 recovery (advisory clears on new location with inclination < 80°) and AT-G-03 (interference_flag NOT set by latitude alone) have no test mapping in §10. No Espresso test verifies the amber banner's visual appearance. See TE-T-09 open gap below. |
| TE-T-10 | Minor | **Resolved** | `LocationPermissionRationaleTest` added to §10.3 with full "Continue"/"Not now" button assertions and rationale dialog text check. |
| TE-T-11 | Minor | **Unresolved** | `MagneticFieldModelProviderTest` confirms `getLastResult()` returns the cached value after `evaluate()`, but does not specify a spy-based invocation count assertion verifying the underlying model is called exactly once per `evaluate()` (BR-11). Carried forward. |
| TE-T-12 | Minor | **Unresolved** | No explicit test for `bearing_location_consent_shown` SharedPreferences write and idempotence (`execute()` second time leaves flag `true`). `BearingCaptureFlowTest` covers the UI path but not the prefs flag directly. Carried forward. |

---

## New Findings (v0.2-draft)

| ID | Severity | Section | Finding | Recommendation |
|----|----------|---------|---------|----------------|
| TE-T-05-NEW | **High** | §10.1 `BearingCaptureUseCaseTest` | The concurrent execution test (TE-T-05 resolution) invokes `captureBearing()` and checks `captureButtonEnabled`. Both are `CompassViewModel` members — `BearingCaptureUseCase.execute()` has no knowledge of `captureButtonEnabled` (a `StateFlow` on the ViewModel) and no method named `captureBearing()`. The test as written targets the wrong class boundary: a unit test for `BearingCaptureUseCase` cannot observe `captureButtonEnabled`. At implementation time this test will either fail to compile (no `captureButtonEnabled` on the use case) or be rewritten to test the ViewModel instead. `CompassViewModelTest` case (c) already correctly covers this at the ViewModel layer with a slow `FakeBearingCaptureUseCase`, but the `BearingCaptureUseCaseTest` concurrent test row conflates the two. | Remove the concurrent execution test from `BearingCaptureUseCaseTest` and retain it exclusively in `CompassViewModelTest` case (c), which already specifies the correct test: slow `FakeBearingCaptureUseCase` suspended + `captureButtonEnabled == false` check + re-enable check. If a `BearingCaptureUseCase`-level concurrency invariant is desired (e.g., that `execute()` itself is idempotent when called twice on the same snapshot), define a separate narrow test in `BearingCaptureUseCaseTest` that does not reference ViewModel state — for example, two parallel coroutine calls to `execute()` with a `FakeBearingRepository` that counts inserts and asserts count == 2 (since the use case itself does not deduplicate — deduplication is the ViewModel's responsibility via the button disable). |
| TE-T-03-NEW | **Medium** | §10.1 `WmmDeclinationAccuracyTest` | AT-B-03 requires `|computed_declination − R| ≤ 0.1°` against the NOAA WMM2025 reference for the test location. The `WmmDeclinationAccuracyTest` row uses `FakeMagneticFieldModel` returning a fixed declination of 8.93°, which tests only that `CompassViewModel` correctly propagates the model's output — not that the WMM spherical harmonic math produces the correct result. The actual WMM accuracy is only tested in `Wmm2025ModelTest` (which uses the real model against the pinned vector). However, AT-B is a scenario test linked to FSPEC-TNORTH and should include a test that exercises the real `Wmm2025Model` in integration — not just a fake. As specified, no test verifies that `Wmm2025Model.getDeclination(40.0, −105.0, 0.0, 2025.5)` produces the correct value AND that `CompassViewModel` uses it for the displayed heading end-to-end. | Rename `WmmDeclinationAccuracyTest` to `WmmDeclinationPropagationTest` and clarify its scope: it tests ViewModel propagation only (with FakeMagneticFieldModel). Add a note in the `Wmm2025ModelTest` row stating that AT-B-03 accuracy is covered there (since the NOAA-pinned vector in `Wmm2025ModelTest` directly satisfies AT-B-03). Add a traceability note in the AT-B row of §10.1: "AT-B-03 accuracy assertion is covered by `Wmm2025ModelTest` primary pinned vector; `WmmDeclinationPropagationTest` covers AT-B-01/B-02 (offline, ViewModel propagation)." |
| TE-T-02-NEW | **Low** | §4.5 | The normative note correctly establishes `"bearing_records.db"` as the canonical filename. However, the paragraph immediately before the normative note still reads: "The `bearing_records` table is automatically covered by the same database encryption because it is in the same `luopan.db` database file." This contradicts the normative note in the same section and will cause reader confusion — a developer reading only the prose (not the normative note) may use `luopan.db` in test setup. | Replace `"luopan.db"` in that sentence with `"bearing_records.db"` to align the prose with the normative note. The sentence should read: "The `bearing_records` table is automatically covered by the same database encryption because it is in the same `bearing_records.db` database file." |

---

## Open Gaps from Iteration 1 (Carried Forward, Not New)

| ID | Severity | Section | Status | Path to Resolution |
|----|----------|---------|--------|--------------------|
| TE-T-09 | Medium | §10.1, §10.3 | **Partially Open** | AT-G-04 (advisory clears on new location with inclination < 80°) has no test entry. AT-G-03 (interference_flag not set by latitude alone during capture) has no test entry. The `CompassViewModelTest` case (d) covers AT-G-01 and AT-G-02 (advisory fires; cap applied). Recommend adding AT-G-04 as a fifth case in `CompassViewModelTest`: inject `FakeMagneticFieldModel` at inclination=83° → confirm `extremeLatitudeAdvisory=true`; then change injected inclination to 79° → confirm `extremeLatitudeAdvisory=false` and confidence re-evaluates. AT-G-03 can be a single assertion in `BearingCaptureUseCaseTest`: confirm `interference_flag=false` when all interference metrics are CLEAR even when latitude advisory is active. |
| TE-T-11 | Minor | §10.1 `MagneticFieldModelProviderTest` | **Open** | BR-11 single-evaluation contract (model called exactly once per `evaluate()`; `getLastResult()` does not re-invoke) is not tested with an invocation count assertion. Low priority for implementation start. |
| TE-T-12 | Minor | §10.1/§10.3 | **Open** | `bearing_location_consent_shown` SharedPreferences flag write-and-idempotence is not tested in isolation. Low priority for implementation start. |

---

## Recommendation: Approved with Conditions

All blockers are resolved. The High findings from iteration 1 are resolved or superseded by new test coverage at the correct abstraction level. The TSPEC may proceed to implementation with the following conditions:

1. **Mandatory before implementation start (High — TE-T-05-NEW):** Remove or correct the concurrent execution test in `BearingCaptureUseCaseTest` to eliminate the ViewModel/use-case boundary confusion. The equivalent coverage already exists in `CompassViewModelTest` case (c); the conflated row in `BearingCaptureUseCaseTest` will fail to compile as written and must not be carried into the implementation phase unresolved.

2. **Strongly recommended before implementation start (Medium — TE-T-03-NEW):** Add traceability from AT-B-03 to `Wmm2025ModelTest` and rename/scope `WmmDeclinationAccuracyTest` to clarify it tests propagation only. Without this, AT-B will appear uncovered in the QA traceability matrix even though coverage exists in `Wmm2025ModelTest`.

3. **Can be addressed in implementation (Low — TE-T-02-NEW, Medium — TE-T-09, Minor — TE-T-11/12):** The residual `"luopan.db"` prose reference in §4.5 is a documentation cleanup. TE-T-09 partial gaps (AT-G-03/G-04) and the two carried-forward Minor findings are low risk for implementation start but should be closed before Phase 2 test execution.

The underlying architectural decisions — constructor injection throughout, `Clock` DI contract, `tapTimestampMs` carry-through, canonical DB filename, WMM vector pinning, and separation of `MagneticFieldModel` concerns — are all sound and correctly specified. No architectural rework is needed.
