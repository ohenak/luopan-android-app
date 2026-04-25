# Cross-Review: SE — PROPERTIES-luopan-p2-true-north-capture (Iteration 3)

**Reviewer role:** Senior Software Engineer
**Document reviewed:** PROPERTIES-luopan-p2-true-north-capture.md v0.3
**Prior review:** CROSS-REVIEW-software-engineer-PROPERTIES-v2.md (iteration 2, "Need Attention")
**Date:** 2026-04-24

---

## Summary

All 9 findings from iteration 2 (SE-P2-01 through SE-P2-09) have been correctly resolved in v0.3. The three blocking High findings are gone: `TrueNorthTransformer`, `NorthTypeToggle`, and `DeclinationProvider` have been replaced with TSPEC-canonical equivalents throughout. The four Moderate findings are also resolved: `LocationResolver.resolve()` is now `LocationRepository.resolvedLocation()`, `locationProvider.startLocationUpdates()` is now `LocationRepository.onStart(scope)`, and the two missing properties (PROP-CAPTURE-11 and PROP-LOCATION-07) have been added with well-structured Given/When/Then bodies. The two Low findings are resolved: `WmmModel` is replaced with `Wmm2025Model` everywhere, `CachedLocation` and `timestampMs` field names are correct, and the expiry lower bound `epochYears < 2025.0` is now explicitly stated.

Three new Low findings are identified in v0.3. None are blocking. The document is approved with minor issues.

---

## Verification of Iteration 2 Findings

| SE Finding | Status | Evidence |
|-----------|--------|---------|
| SE-P2-01 (High): PROP-NORTH-01 `TrueNorthTransformer` | **Resolved** | When clause: `CompassViewModel.uiState.headingDeg` observed with `northType=TRUE`; no invented class name remains |
| SE-P2-02 (High): PROP-DECL-01 `NorthTypeToggle.toggle()` | **Resolved** | Property statement and When/Then clauses use `CompassViewModel.setNorthType(NorthType.TRUE)` and `CompassViewModel.setNorthType(NorthType.MAGNETIC)` exclusively |
| SE-P2-03 (High): PROP-NORTH-04, PROP-DECL-03 `DeclinationProvider` | **Resolved** | Both properties use `MagneticFieldModelProvider`; Given clause uses `MagneticFieldModelProvider(wmm=expiredModel, fallback=androidGeoFieldModel)`; `DeclinationProvider` is absent |
| SE-P2-04 (Moderate): PROP-LOCATION-01 `LocationResolver.resolve()` | **Resolved** | When clause: `LocationRepository.resolvedLocation()` called in each scenario; `LocationResolver` is absent |
| SE-P2-05 (Moderate): PROP-LOCATION-02 `startLocationUpdates()` | **Resolved** | Given and Then clauses reference `locationRepository.onStart(scope)` and `LocationRepository.onStart(scope)` with the correct `CoroutineScope` parameter |
| SE-P2-06 (Moderate): missing PROP-CAPTURE-11 | **Resolved** | PROP-CAPTURE-11 added with three sub-cases: MODERATE+confirm, WARNING+cancel, CLEAR+HIGH (no dialog); references `InterferenceWarningDialogFragment` and `BearingCaptureDialogFragment` by TSPEC name |
| SE-P2-07 (Moderate): missing PROP-LOCATION-07 | **Resolved** | PROP-LOCATION-07 added with two sub-cases: valid coordinates entry and dialog dismiss; references `LocationState.Manual` and `NoGpsDialogTest` |
| SE-P2-08 (Low): `WmmModel` and `LocationCache` | **Resolved** | API notes use `Wmm2025Model` throughout (lines 33–35); `CachedLocation(latDeg, lonDeg, altM, timestampMs)` is correct; no `WmmModel` or `LocationCache` references remain in body text |
| SE-P2-09 (Low): expiry lower bound missing | **Resolved** | API notes line 34: `"expired when epochYears < 2025.0 or epochYears >= 2030.0 (both bounds per TSPEC §3.1)"`; PROP-NORTH-04 note: `"If epochYears < 2025.0, isExpired() returns true and the fallback applies"` |

---

## New Findings in v0.3

| ID | Severity | Property | Finding | Recommendation |
|----|----------|---------|---------|----------------|
| SE-P3-01 | **Low** | PROP-NORTH-04, PROP-DECL-03 | **`MagneticFieldModelProvider.getModel()` is not the TSPEC canonical method name.** The TSPEC §3.3 public API declares `fun activeModel(): MagneticFieldModel` — there is no `getModel()` method on `MagneticFieldModelProvider`. PROP-NORTH-04 Property statement reads "When `MagneticFieldModelProvider.getModel()` is called"; PROP-NORTH-04 When clause reads "`MagneticFieldModelProvider.getModel()` is called (equivalently: `activeModel()` per TSPEC §3.3)" — the parenthetical acknowledgement partially mitigates the risk. PROP-DECL-03 Property statement reads "`MagneticFieldModelProvider.getModel().getDeclination()`" with no corresponding correction note. A developer writing a test against PROP-DECL-03 who is not also reading the TSPEC might call `provider.getModel()` and fail at compile time. | In PROP-NORTH-04 and PROP-DECL-03, replace `getModel()` with `activeModel()` to match the TSPEC §3.3 declaration exactly. The existing parenthetical in PROP-NORTH-04 confirms the author knows the canonical name; applying it consistently removes all ambiguity. |
| SE-P3-02 | **Low** | PROP-CAPTURE-11 | **Sub-case for `OverallConfidence.POOR` with `InterferenceState.CLEAR` is missing from PROP-CAPTURE-11.** The property statement and summary table description both state the dialog triggers when `InterferenceState ∈ {MODERATE, WARNING}` OR `OverallConfidence == POOR`, which is consistent with TSPEC §7.3. However, the three sub-cases test only MODERATE+confirm (A), WARNING+cancel (B), and CLEAR+HIGH (C, no dialog). No sub-case tests CLEAR+POOR, which must show the dialog per the stated condition. Sub-case C's Given specifies `OverallConfidence.HIGH` explicitly — a developer looking only at sub-case C would not write a test for the POOR-only trigger path. | Add Sub-case D: `InterferenceState.CLEAR, OverallConfidence.POOR` → `InterferenceWarningDialogFragment` is shown. This is the only uncovered branch of the trigger condition stated in the property. The test class (`BearingCaptureFlowTest` or `InterferenceWarningCaptureTest`) is already identified; a fourth sub-case adds one Espresso assertion. |
| SE-P3-03 | **Low** | PROP-LOCATION-07 | **Summary table Test Class column omits `NoGpsDialogTest`.** The PROP-LOCATION-07 summary table entry lists only `BearingCaptureFlowTest`, while the property body reference note correctly names both `BearingCaptureFlowTest` and `NoGpsDialogTest per TSPEC §10.1`. TSPEC §10.1 lists `NoGpsDialogTest` as the primary test class for AT-D; it is a Robolectric/Espresso test distinct from `BearingCaptureFlowTest`. The summary table is used for planning and CI tagging — omitting `NoGpsDialogTest` here means the AT-D acceptance criterion would not be tracked to this property in the summary view. | Add `NoGpsDialogTest` to the Test Class cell for PROP-LOCATION-07 in the summary table (e.g., `` `BearingCaptureFlowTest`, `NoGpsDialogTest` ``), consistent with the property body reference note and TSPEC §10.1. |

---

## Detailed Verification Notes

### SE-P2-01: PROP-NORTH-01 (TrueNorthTransformer → CompassViewModel)

The When clause now reads: "`CompassViewModel.uiState.headingDeg` is observed with `northType=TRUE` and a valid `WmmResult`". The Given clause injects `FakeMagneticFieldModel` and `FakeLocationRepository`, both TSPEC-defined test doubles (§2.1 and §10.1 respectively). No invented class names remain. **Resolved.**

### SE-P2-02: PROP-DECL-01 (NorthTypeToggle → CompassViewModel.setNorthType)

The Property statement now reads: "`CompassViewModel.setNorthType()` only accepts `NorthType.TRUE` or `NorthType.MAGNETIC`". Both When/Then pairs use `CompassViewModel.setNorthType(NorthType.TRUE)` and `CompassViewModel.setNorthType(NorthType.MAGNETIC)`. The `GridNorthAbsenceTest` reference class (TSPEC §10.3) is present. **Resolved.**

### SE-P2-03: PROP-NORTH-04, PROP-DECL-03 (DeclinationProvider → MagneticFieldModelProvider)

`DeclinationProvider` is completely absent from v0.3. PROP-NORTH-04 Given clause correctly constructs `MagneticFieldModelProvider(wmm=expiredModel, fallback=androidGeoFieldModel)`. PROP-DECL-03 Given clause uses `MagneticFieldModelProvider configured with a FakeMagneticFieldModel`. The offline-computation framing in PROP-DECL-03 is now accurate: the property asserts no network call is fired, which is testable without an HTTP interceptor (per the TSPEC §2.1 no-network contract). **Resolved.** (Residual `getModel()` naming issue is SE-P3-01 at Low severity.)

### SE-P2-04: PROP-LOCATION-01 (LocationResolver → LocationRepository.resolvedLocation)

`LocationResolver` is absent. All four scenarios call `LocationRepository.resolvedLocation()` in the When clause. The four `locationState` enum values (`GpsFresh`, `GpsCached`, `Manual`, `Unavailable`) match the TSPEC §3.4 sealed class definition exactly. **Resolved.**

### SE-P2-05: PROP-LOCATION-02 (startLocationUpdates → LocationRepository.onStart)

The Given clause now specifies `CompassActivity` configured to call `locationRepository.onStart(scope)` from `Activity.onStart()`. The Then clause asserts `LocationRepository.onStart(scope)` has been called exactly once. The `ShadowLocationManager` assertion checks `GPS_PROVIDER` with `minTimeMs=5000` and `minDistanceM=0.0f`, matching TSPEC §6.2. **Resolved.**

### SE-P2-06: PROP-CAPTURE-11 (missing pre-capture warning dialog)

PROP-CAPTURE-11 has been added with three sub-cases covering MODERATE+confirm, WARNING+cancel, and CLEAR+HIGH (no dialog). The property correctly names `InterferenceWarningDialogFragment` and `BearingCaptureDialogFragment` (TSPEC §7.3). The `interference_flag = true` assertion on confirm path is consistent with TSPEC §3.6 and PROP-CAPTURE-02. The `BearingRepository.getAll().size == 0` assertion on the cancel path is a precise and testable post-condition. **Resolved.** (The missing CLEAR+POOR sub-case is SE-P3-02 at Low severity.)

### SE-P2-07: PROP-LOCATION-07 (missing no-GPS dialog)

PROP-LOCATION-07 has been added. Sub-case A covers valid coordinate entry → `LocationState.Manual` transition → `uiState.northType == NorthType.TRUE` → `BearingRecord` with non-null `lat`/`lon`. Sub-case B covers dismiss → `uiState.northType == NorthType.MAGNETIC`. The `FakeLocationRepository` test double is named in TSPEC §10.1. The note correctly points to `NoGpsDialogTest` per TSPEC §10.1. **Resolved.** (Summary table Test Class omission is SE-P3-03 at Low severity.)

### SE-P2-08: WmmModel → Wmm2025Model

All occurrences of `WmmModel` have been replaced with `Wmm2025Model` in the API notes section (lines 33–35). `CachedLocation(latDeg, lonDeg, altM, timestampMs)` is correct per TSPEC §3.4. **Resolved.**

### SE-P2-09: Expiry lower bound

API notes line 34 states: "expired when `epochYears < 2025.0` or `epochYears >= 2030.0` (both bounds per TSPEC §3.1)". PROP-NORTH-04 note adds: "If `epochYears < 2025.0`, `isExpired()` returns `true` and the fallback applies — in addition to the post-2030 upper bound. Both conditions are tested in `Wmm2025ModelTest` per TSPEC §10.1." **Resolved.**

---

## Summary Table of New Findings

| ID | Severity | Properties Affected | Finding |
|----|----------|--------------------|---------| 
| SE-P3-01 | Low | PROP-NORTH-04, PROP-DECL-03 | `MagneticFieldModelProvider.getModel()` — non-TSPEC method name; TSPEC §3.3 declares `activeModel()` |
| SE-P3-02 | Low | PROP-CAPTURE-11 | No sub-case for `InterferenceState.CLEAR + OverallConfidence.POOR` → dialog shown (trigger condition stated in property but not tested) |
| SE-P3-03 | Low | PROP-LOCATION-07 | Summary table Test Class omits `NoGpsDialogTest` (present in body note and TSPEC §10.1, absent from summary row) |

---

## Recommendation: Approved with Minor Issues

All three High findings (SE-P2-01, SE-P2-02, SE-P2-03) and all four Moderate findings (SE-P2-04 through SE-P2-07) from iteration 2 are fully resolved. The document correctly uses TSPEC-canonical class names throughout. No invented class or method name remains in any Given/When/Then body that would cause a compilation failure if followed verbatim.

The three new findings are all Low severity. SE-P3-01 (`getModel()` vs `activeModel()`) is a naming inconsistency mitigated by an inline correction note in PROP-NORTH-04 and does not introduce a new class name. SE-P3-02 (PROP-CAPTURE-11 missing POOR-only sub-case) is a test coverage gap that a developer would catch during implementation when the trigger condition is coded. SE-P3-03 (PROP-LOCATION-07 summary table) is a bookkeeping gap that does not affect the behavioral correctness of the property body.

Implementation may proceed from this document. The three Low findings should be addressed in the next document revision (v0.4) but do not require a blocking re-review.
