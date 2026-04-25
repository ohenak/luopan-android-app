# Cross-Review: SE — PROPERTIES-luopan-p2-true-north-capture (Iteration 2)

**Reviewer role:** Senior Software Engineer
**Document reviewed:** PROPERTIES-luopan-p2-true-north-capture.md v0.2
**Prior review:** CROSS-REVIEW-software-engineer-PROPERTIES.md (iteration 1, "Need Attention")
**Date:** 2026-04-24

---

## Summary

All 7 findings from iteration 1 (SE-P-01 through SE-P-07) have been correctly resolved in v0.2. The critical type/value discrepancies are gone, the method names are aligned to TSPEC §2.1, the test class table has been substantially repaired, the boundary granularity is now 1 ms, the hardcoded EMA constants have been removed, and `ProcessPhoenix` is correctly specified.

However, v0.2 introduces a new pattern of non-TSPEC class/method names in `Given`/`When`/`Then` test bodies — specifically `TrueNorthTransformer`, `NorthTypeToggle`, `DeclinationProvider`, `LocationResolver`, and `locationProvider.startLocationUpdates()`. None of these classes or methods appear in the TSPEC architecture; a developer following these property bodies verbatim would invent classes that conflict with the TSPEC-specified design. Two behavioral coverage gaps were also identified: the pre-capture interference warning dialog and the no-GPS manual coordinate entry dialog each have a dedicated TSPEC test class (`InterferenceWarningCaptureTest`, `NoGpsDialogTest`) but no corresponding PROPERTIES entry.

The document cannot be approved while test bodies reference non-existent classes (HIGH findings). The coverage gaps are addressable in the same pass.

---

## Verification of Iteration 1 Findings

| SE Finding | Status | Evidence |
|-----------|--------|---------|
| SE-P-01 (Critical): PROP-NORTH-03 altM=0.0 | **Resolved** | PROP-NORTH-03 Given: `altM=0.0`; note explicitly references TSPEC §10.1 canonical vector |
| SE-P-02 (Critical): PROP-SCHEMA-01 id = String (UUID v4) | **Resolved** | Schema table row 1: `id \| String (UUID v4, TEXT primary key — NOT auto-increment Long)`; Then clause: `id.returnType.classifier == String::class` |
| SE-P-03 (High): PROP-INTERFERENCE-01 `getExpectedFieldMagnitude(latDeg, lonDeg, altM, epochYears)` | **Resolved** | Property body and Then clause both use the TSPEC §2.1 canonical name and parameter list |
| SE-P-03 (High): PROP-INTERFERENCE-02 `getExpectedInclination(latDeg, lonDeg, altM, epochYears)` | **Resolved** | Property body and Then clause both use the TSPEC §2.1 canonical name; old name `getInclination_deg` is gone |
| SE-P-04 (High): All Test Class entries aligned to TSPEC §10 | **Resolved** | All 37 summary-table Test Class entries now map to TSPEC §10 class names. No invented class names remain in the summary tables. |
| SE-P-05 (Moderate): PROP-LOCATION-03 1 ms boundary | **Resolved** | Sub-case B `FakeClock.set(T + 30 * 86_400_000L + 1L)` added; Sub-case C (1 s overshoot) retained as confirming case |
| SE-P-06 (Moderate): PROP-INTERFERENCE-03 no hardcoded constants | **Resolved** | Constants removed; property now asserts behavioral absence of WMM call and positive finite EMA value, with explicit note that specific numeric seeds are NOT asserted |
| SE-P-07 (Low): PROP-PERSIST-02 uses ProcessPhoenix | **Resolved** | When clause specifies `ProcessPhoenix.triggerRebirth(context)`; note explicitly prohibits `Process.killProcess()` |

---

## New Findings in v0.2

| ID | Severity | Property | Finding | Recommendation |
|----|----------|---------|---------|----------------|
| SE-P2-01 | **High** | PROP-NORTH-01 | **`TrueNorthTransformer` does not exist in the TSPEC architecture.** The When clause calls `TrueNorthTransformer.apply(magneticHeading_deg, northType, location)`. The TSPEC §3.7 specifies that declination application is performed inline by `CompassViewModel` — there is no `TrueNorthTransformer` class in any TSPEC package listing. A developer writing a test against this property would invent a class that conflicts with the architecture. The TSPEC-aligned test point for the same behavior is `CompassViewModelTest` case (a) (already listed in the summary table for PROP-NORTH-01). | Replace the When clause with an invocation on `CompassViewModel` using injected `FakeMagneticFieldModel` and `FakeClock`, consistent with `CompassViewModelTest` case (a) in TSPEC §10.1. |
| SE-P2-02 | **High** | PROP-DECL-01 | **`NorthTypeToggle.toggle()` does not exist in the TSPEC architecture.** The When clause calls `NorthTypeToggle.toggle(current)`. The TSPEC §3.7 specifies `CompassViewModel.setNorthType(NorthType)` as the sole entry point for north type changes; there is no `NorthTypeToggle` class in any TSPEC package. The TSPEC-aligned test for GRID-unreachability is `TrueNorthToggleTest` (listed correctly in the summary table) and `GridNorthAbsenceTest`. | Replace the When clause to test via `CompassViewModel.setNorthType()` plus TSPEC `GridNorthAbsenceTest`, or restructure the Given/When/Then to test the `NorthType` enum directly (checking that `NorthType.GRID` is not returned from any reachable code path) without inventing a `NorthTypeToggle` helper class. |
| SE-P2-03 | **High** | PROP-NORTH-04, PROP-DECL-03 | **`DeclinationProvider` does not exist in the TSPEC architecture.** Both PROP-NORTH-04 (Given/When/Then) and PROP-DECL-03 (Given/When/Then) call `DeclinationProvider(wmmModel, androidGeoFieldModel)` and `DeclinationProvider.getDeclination(location, epochMs)`. There is no `DeclinationProvider` class in the TSPEC package listing (`com.luopan.compass.magnetic` contains `MagneticFieldModel`, `Wmm2025Model`, `AndroidGeoFieldModel`, `MagneticFieldModelProvider`, and `WmmResult`). The fallback behavior tested in PROP-NORTH-04 is exercised in `MagneticFieldModelProviderTest` (listed correctly in the summary table). PROP-DECL-03's network-isolation assertion applies to `Wmm2025Model` itself or to the `MagneticFieldModelProvider.evaluate()` call path. | For PROP-NORTH-04: replace `DeclinationProvider` references with `MagneticFieldModelProvider`; the test double for the expired model is a `FakeMagneticFieldModel` with `isExpired() = true`. For PROP-DECL-03: rewrite the property to assert that `MagneticFieldModelProvider.evaluate()` (or `Wmm2025Model.getDeclination()`) completes without any network call; remove the `OkHttpClient` interceptor framing (WMM has no HTTP client) and instead verify the computation returns without throwing under a network-disabled environment. |
| SE-P2-04 | **Moderate** | PROP-LOCATION-01 | **`LocationResolver.resolve()` does not exist in the TSPEC architecture.** The When clause calls `LocationResolver.resolve()`. The TSPEC §3.4 defines `LocationRepository` (not `LocationResolver`) with method `resolvedLocation(): CachedLocation?` and `locationState: StateFlow<LocationState>`. The property's priority-chain behavior is tested in `LocationRepositoryTest` (listed correctly in the summary table). | Replace `LocationResolver.resolve()` with `LocationRepository.resolvedLocation()` (or with observation of `locationState`) throughout PROP-LOCATION-01. |
| SE-P2-05 | **Moderate** | PROP-LOCATION-02 | **`locationProvider.startLocationUpdates()` does not exist in the TSPEC architecture.** The Then clause asserts that `locationProvider.startLocationUpdates()` has been called. The TSPEC §3.4 exposes `LocationRepository.onStart(scope: CoroutineScope)` as the lifecycle entry point; `LocationRepository` uses `LocationManager.requestLocationUpdates()` internally. There is no `locationProvider` field or `startLocationUpdates()` method in the TSPEC design. | Replace the Then clause to spy on `LocationRepository.onStart(scope)` being called during `CompassActivity.onStart()`, or verify that `LocationRepository.locationState` emits a non-`Unavailable` state after activity start with a ShadowLocationManager. |
| SE-P2-06 | **Moderate** | *(missing)* | **No property covers the pre-capture interference warning dialog (TSPEC `InterferenceWarningCaptureTest`, AT-E-04/05).** `InterferenceWarningCaptureTest` is a named TSPEC §10.3 test class with a concrete scenario: MODERATE interference state → warning dialog appears → "Save with warning" tap → `interference_flag = true` in DB. PROP-CAPTURE-02 covers only the use-case-level `interference_flag` derivation (a JVM unit test); it does not cover the dialog's appearance or dismissal path. The dialog path (AT-E-04/05) has no property in PROPERTIES v0.2. | Add a new property `PROP-CAPTURE-11` (or sub-case of PROP-CAPTURE-07) covering: when `InterferenceState ∈ {MODERATE, WARNING}` at capture button tap, the `InterferenceWarningDialogFragment` is shown before `BearingCaptureDialogFragment`; tapping "Save with warning" completes the capture; tapping "Cancel" leaves the DB record count unchanged. Test class: `InterferenceWarningCaptureTest`. |
| SE-P2-07 | **Moderate** | *(missing)* | **No property covers the no-GPS manual coordinate entry dialog (TSPEC `NoGpsDialogTest`, AT-D).** `NoGpsDialogTest` is a named TSPEC §10.1 test class (AT-D): when `LocationState.Unavailable` is emitted and the user taps True North, a manual coordinate entry dialog appears; the mode remains MAGNETIC until coordinates are entered and confirmed. There is no PROPERTIES entry for this dialog behavior. PROP-LOCATION-01 covers the priority chain as a unit test but not the UI dialog trigger. | Add a new property (e.g., `PROP-DECL-04`) covering: when `northType = TRUE` is requested and `LocationRepository.locationState == Unavailable`, the manual coordinate entry dialog is displayed; heading remains MAGNETIC until coordinates are confirmed; after confirmation, `CompassViewModel.locationState` transitions to `LocationState.Manual`. Test class: `NoGpsDialogTest`. |
| SE-P2-08 | **Low** | *(API notes)* | **API notes use `WmmModel` instead of the TSPEC canonical class name `Wmm2025Model`.** Lines 33–35 reference `WmmModel.declination()`, `WmmModel.isExpired()`, `WmmModel.getModelId()`. The TSPEC §3.1 canonical class is `Wmm2025Model`. A developer reading the API notes would not find `WmmModel` in the architecture. Additionally, line 38 uses `LocationCache` instead of `CachedLocation` (the TSPEC §3.4 data class name). | Replace `WmmModel` with `Wmm2025Model` in the API notes. Replace `LocationCache` with `CachedLocation` and `savedAtMs` with `timestampMs` (matching the TSPEC §3.4 `CachedLocation` field name). |
| SE-P2-09 | **Low** | *(API notes)* | **API notes give an incomplete expiry condition for `WmmModel.isExpired()`.** Line 34 states "expired when model epoch > 2030.0". The TSPEC §3.1 specifies `isExpired()` returns `true` when `epochYears < 2025.0 || epochYears >= 2030.0` — both the post-2030 and pre-2025 conditions cause expiry. A developer reading only the API notes would miss the pre-2025 lower bound and not write `Wmm2025ModelTest` assertions for the pre-validity range (which TSPEC §10.1 explicitly mandates: "returns `true` when 2024.9"). | Update the API note to: "expired when `epochYears < 2025.0` or `epochYears >= 2030.0` (both bounds per TSPEC §3.1)". |

---

## Detailed Verification Notes

### PROP-NORTH-03 (SE-P-01)

Vector is now `lat=40.0, lon=−105.0, altM=0.0, epochYear=2025.5` with an inline note explicitly calling out the TSPEC §10.1 canonical altitude. The NOAA reference value `+8.93°` and tolerance `±0.1°` are unchanged. **Resolved.**

### PROP-SCHEMA-01 (SE-P-02)

The schema table lists `id` as `String (UUID v4, TEXT primary key — NOT auto-increment Long)`. The Then clause asserts `id.returnType.classifier == String::class`. The explanatory note references FSPEC §6.1 and explains the UUID rationale. **Resolved.**

### PROP-INTERFERENCE-01/02 Method Names (SE-P-03)

PROP-INTERFERENCE-01 uses `getExpectedFieldMagnitude(latDeg, lonDeg, altM, epochYears)` with all four TSPEC §2.1 parameter names. PROP-INTERFERENCE-02 uses `getExpectedInclination(latDeg, lonDeg, altM, epochYears)` and explicitly notes the old name was incorrect. Both match TSPEC §2.1 exactly. **Resolved.**

### Test Class Alignment (SE-P-04)

All 37 summary-table Test Class entries now reference TSPEC §10 classes. The summary tables reference: `CompassViewModelTest`, `WmmDeclinationAccuracyTest`, `Wmm2025ModelTest`, `MagneticFieldModelProviderTest`, `TrueNorthToggleTest`, `GridNorthAbsenceTest`, `BearingCaptureFlowTest`, `BearingCaptureUseCaseTest`, `InterferenceBaselineUpgradeTest`, `LocationRepositoryTest`, `LocationCacheAgeLabelTest`, `LocationPermissionRationaleTest`, `PermissionDeniedCaptureTest`, `BearingEncryptionTest`, `BearingForceStopTest`, `LuopanDatabaseMigrationTest`, `CompassColdStartBenchmark`, `CompassWarmStartBenchmark` — all are listed in TSPEC §10. **Resolved.**

Note: Three TSPEC §10 test classes do not appear anywhere in PROPERTIES v0.2 summary tables or property bodies — `ClockEpochYearTest`, `InterferenceWarningCaptureTest`, and `NoGpsDialogTest`. The first (`ClockEpochYearTest`) tests a utility function (`Clock.toEpochYears()`) with no direct behavioral property at the PROPERTIES level; its absence is acceptable. The latter two represent behavioral coverage gaps addressed in SE-P2-06 and SE-P2-07 above.

### PROP-LOCATION-03 Boundary (SE-P-05)

Sub-case A (30 days exact → `true`), Sub-case B (30 days + 1 ms → `false`), Sub-case C (30 days + 1 s → `false`) are all present with clear rationale. The boundary semantics (`elapsed_ms < 30 * 86_400_000L`, strictly less than) are stated in the property. **Resolved.**

### PROP-INTERFERENCE-03 Hardcoded Constants (SE-P-06)

The `50.0f` and `45.0f` constants are gone. The property now asserts behavioral absence of WMM calls and presence of a positive-finite EMA-sourced value. A normative note explicitly states that specific EMA seed values are not asserted. **Resolved.**

### PROP-PERSIST-02 ProcessPhoenix (SE-P-07)

The When clause specifies `ProcessPhoenix.triggerRebirth(context)`. An inline note explicitly prohibits `Process.killProcess()` and explains the rationale (unreliable on CI emulators without root). **Resolved.**

---

## Summary Table of New Findings

| ID | Severity | Properties Affected | Finding |
|----|----------|--------------------|---------| 
| SE-P2-01 | High | PROP-NORTH-01 | `TrueNorthTransformer.apply()` — class not in TSPEC; test body would not compile |
| SE-P2-02 | High | PROP-DECL-01 | `NorthTypeToggle.toggle()` — class not in TSPEC; test body would not compile |
| SE-P2-03 | High | PROP-NORTH-04, PROP-DECL-03 | `DeclinationProvider` — class not in TSPEC; test bodies would not compile |
| SE-P2-04 | Moderate | PROP-LOCATION-01 | `LocationResolver.resolve()` — should be `LocationRepository.resolvedLocation()` |
| SE-P2-05 | Moderate | PROP-LOCATION-02 | `locationProvider.startLocationUpdates()` — should be `LocationRepository.onStart(scope)` |
| SE-P2-06 | Moderate | *(missing property)* | No property for `InterferenceWarningCaptureTest` AT-E-04/05 (warning dialog before capture) |
| SE-P2-07 | Moderate | *(missing property)* | No property for `NoGpsDialogTest` AT-D (manual coord dialog on no-GPS + True North toggle) |
| SE-P2-08 | Low | *(API notes)* | `WmmModel` should be `Wmm2025Model`; `LocationCache` should be `CachedLocation` |
| SE-P2-09 | Low | *(API notes)* | Expiry condition missing lower bound: `epochYears < 2025.0 || epochYears >= 2030.0` |

---

## Recommendation: Need Attention

Three High findings (SE-P2-01, SE-P2-02, SE-P2-03) introduce non-TSPEC class names into test body `When`/`Then` clauses. Code written verbatim from these properties would not compile against the TSPEC-specified architecture. These must be corrected before implementation.

The four Moderate findings (SE-P2-04 through SE-P2-07) should be addressed in the same revision: two are additional method-name mismatches and two are behavioral coverage gaps against named TSPEC test classes.

The two Low findings (SE-P2-08, SE-P2-09) are documentation-only corrections that can be swept in without a blocking re-review cycle.

Re-review recommended after SE-P2-01 through SE-P2-05 are addressed.
