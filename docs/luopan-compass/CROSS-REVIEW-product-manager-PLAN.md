# Cross-Review: PM — PLAN-luopan-p2-true-north-capture

**Reviewer role:** Product Manager  
**Document reviewed:** PLAN-luopan-p2-true-north-capture.md  
**Date:** 2026-04-25  

---

## Summary

The PLAN covers the Phase 2 feature set solidly at the structural level and the parallelization model is sound. All eight core REQ IDs (REQ-NORTH-01 through REQ-NFR-08) map to at least one PLAN phase. The critical path is logical and the test phase (P8) includes all four required test types.

However, six findings require attention before implementation begins. The most significant are: (1) a `interference_flag` derivation rule in P6.1 that contradicts FSPEC BR-10 (sets the flag on `OverallConfidence.POOR` alone, not on `InterferenceState`), (2) a missing scope guard for the GRID deferred feature — the PLAN has no explicit assertion or test task that GRID is never written or shown, (3) the first-capture location privacy consent flow (REQ-CAPTURE-06) is split across two phases in a way that leaves the privacy notice logic unimplemented until after the save path is already wired, and (4) the `SystemClock` name in P1.1 collides with `android.os.SystemClock` in contradiction to the FSPEC naming mandate. Two minor findings round out the review.

Overall the PLAN is implementable but needs targeted corrections before the first batch begins.

---

## Findings

| ID | Severity | Phase | Finding | Recommendation |
|----|----------|-------|---------|----------------|
| PM-F-01 | **High** | P6.1 | `interference_flag` derivation is wrong. The P6.1 success criterion states "`interference_flag = true` when `confidence == POOR`." This directly contradicts FSPEC BR-10 and AT-E-10, which specify that `interference_flag` is set only when `InterferenceState` is `MODERATE` or `WARNING` at tap time — `OverallConfidence.POOR` alone must NOT set the flag. If implemented as written, saved records will carry incorrect `interference_flag=true` values for poor-calibration captures where no magnetic interference exists, corrupting Phase 4 history display. | Replace the P6.1 success criterion with: "`interference_flag = true` when `InterferenceState ∈ {MODERATE, WARNING}` at the instant of the capture button tap; `OverallConfidence.POOR` alone does NOT set this flag (FSPEC BR-10, AT-E-10)." Add a `BearingCaptureUseCaseTest` case: POOR confidence + CLEAR interference → `interference_flag = false`. |
| PM-F-02 | **High** | P1.1 | Production `Clock` implementation named `SystemClock` conflicts with `android.os.SystemClock`. The P1.1 description says "`SystemClock` production impl wraps `System.currentTimeMillis()`." FSPEC §6.2 BR-06 explicitly mandates the name `WallClock` to avoid this collision ("The production implementation is named `WallClock` (not `SystemClock`) to avoid a naming collision with `android.os.SystemClock`"). | Rename the production implementation to `WallClock` throughout the PLAN (P1.1 description, success criterion, and all references in P3.1, P5.1, P6.1, P8.2). |
| PM-F-03 | **High** | P6.3 / P6.4 | The pre-capture interference/confidence warning dialog (FSPEC §2.5 step 3b) is partially missing. P6.3 mentions "show warning before confirm when `interference_flag = true`" but the toast-before-dialog sequence described in the FSPEC (warning dialog first, then name/notes dialog) and the separate `OverallConfidence.POOR` warning trigger are not explicitly called out. Furthermore, the AT-E-04/E-05/E-06/E-10 acceptance tests require this dialog to appear for Poor-confidence captures even when `interference_flag` ends up false — a nuance P6.3 currently misses. | Expand P6.3 description to explicitly model: (a) snapshot at tap time; (b) warning dialog fires when `InterferenceState ∈ {MODERATE, WARNING}` OR `OverallConfidence == POOR`; (c) warning dialog precedes the name/notes dialog; (d) "Cancel" abandons the capture entirely; (e) warning dialog text from FSPEC §2.5 step 3b. Add AT-E-10 coverage to the P6.3 test targets. |
| PM-F-04 | **Medium** | P6.3 / missing | No explicit GRID exclusion guard or test task. REQ §6, FSPEC BR-03/BR-04, and AT-G-08 all require that GRID is never shown in the toggle and never written to `BearingRecord.north_type`. The PLAN has no phase, success criterion, or test target dedicated to enforcing this invariant. The FSPEC requires an assertion (`assert north_type != GRID` before DB write) and an Espresso UI check. | Add to P6.3 success criteria: "`north_type` assertion throws `IllegalStateException` if the value is ever `GRID`." Add AT-G-08 (no GRID text in view hierarchy) as an explicit test target in P8.5. |
| PM-F-05 | **Medium** | P6.4 | REQ-CAPTURE-06 first-capture consent is split across P6.3 and P6.4. The key concern is timing: P6.3 wires the save path and is in Batch 6, while P6.4 (privacy dialog) is in Batch 7. This means the save path ships temporarily without the mandatory first-capture privacy confirmation, which could be deployed in an intermediate state. FSPEC §2.5 step 4 and BR-15 require the GPS toggle and privacy notice to be part of the capture dialog, not a separate preceding dialog. The FSPEC describes a single capture dialog containing the GPS toggle and first-capture notice inline — not a separate consent dialog that precedes the capture dialog. | (a) Clarify in P6.4 that the GPS privacy notice is inline inside the capture dialog (not a separate modal that appears before it), consistent with FSPEC §2.5 step 4. (b) Move P6.4 into Batch 6 alongside P6.3 so the capture dialog is never shipped without the privacy toggle/notice. Update the dependency graph accordingly. |
| PM-F-06 | **Low** | P4.1 | P4.1 success criteria list `"True N (cached location)"` as a valid `north_label`. FSPEC §2.2 step 6 (FSPEC-TOGGLE) and FSPEC §5.3 define only three valid label values: `"True N"`, `"True N (manual location)"`, and `"Magnetic N"`. A cached GPS fix still shows `"True N"` (not `"True N (cached location)"`) — the cached-location distinction is surfaced in the declination info panel source label, not in the main heading label. | Remove `"True N (cached location)"` from P4.1 success criteria. The valid label set is `"True N"` (GPS fix or cached location), `"True N (manual location)"` (manual entry), and `"Magnetic N"`. Add a note directing engineers to the declination info panel for source-label display of the cached-vs-fresh distinction. |

---

## REQ Coverage Matrix

| REQ ID | Title | PLAN Phase(s) | Coverage |
|--------|-------|---------------|----------|
| REQ-NORTH-01 | Bundled WMM2025 coefficients | P2.1, P2.2 | Covered |
| REQ-NORTH-02 | Android GeomagneticField fallback | P2.3, P2.4 | Covered |
| REQ-NORTH-03 | GPS location handling | P3.1, P3.2, P7.2 | Covered |
| REQ-NORTH-04 | North reference label on all headings | P4.1, P4.3 | Covered |
| REQ-DECL-01 | North type toggle | P4.1, P4.3 | Covered |
| REQ-DECL-02 | Declination info panel | P5.1, P5.2 | Covered |
| REQ-CAPTURE-01 | Bearing record schema | P1.2 | Covered |
| REQ-CAPTURE-02 | Capture flow UX (≤3 taps) | P6.3 | Covered |
| REQ-CAPTURE-04 | Data persistence (encrypted) | P1.2, P1.3, P6.2, P8.4 | Covered |
| REQ-CAPTURE-06 | Location privacy confirmation | P6.4 | Covered (see PM-F-05) |
| REQ-NFR-08 | Cold-start time | P8.6 | Covered |
| WMM interference upgrade (REQ §5.1 note) | Baseline upgrade from GeomagneticField to WMM2025 | P4.2 | Covered |
| §11.4 Extreme latitude advisory | Polar advisory + confidence cap | P7.1 | Covered |
| §11.8 GPS unavailable | Fallback chain + manual entry | P3.1, P7.2 | Covered |

---

## FSPEC Acceptance Test Coverage Matrix

| AT ID | Description | PLAN Phase(s) | Coverage |
|-------|-------------|---------------|----------|
| AT-A (A-01 to A-04) | True north switching, 200 ms | P4.1, P4.3, P8.5 | Covered |
| AT-B (B-01 to B-03) | Offline declination with GPS | P2.2, P2.4, P8.1 | Covered |
| AT-C (C-01 to C-03) | No GPS, cached location | P3.1, P5.1, P8.2 | Covered |
| AT-D (D-01 to D-04) | No GPS, no cache, manual entry | P3.1, P7.2, P8.2 | Covered |
| AT-E (E-01 to E-09) | Bearing capture happy/interference paths | P6.1, P6.3, P6.4, P8.5 | Partially covered — AT-E-10 not explicitly listed (see PM-F-01, PM-F-03) |
| AT-E-10 | Poor confidence + CLEAR interference → interference_flag=false | P6.1, P6.3 | Not explicitly mapped (see PM-F-01, PM-F-03) |
| AT-F (F-01 to F-04) | WMM interference baseline upgrade | P4.2, P8.3 | Covered |
| AT-G (G-01 to G-09) | Extreme latitude, cache expiry, GRID exclusion, permission denial | P7.1, P7.2, P8.5 | AT-G-08 (GRID UI check) not mapped to any phase or test target (see PM-F-04) |
| AT-PERSIST (PERSIST-01, PERSIST-02) | Encryption check, force-stop resilience | P6.2, P8.4 | Covered |
| AT-NFR-01 (NFR-01, NFR-02) | Cold/warm start ≤5 s / ≤3 s | P8.6 | Covered |

---

## Deferred Feature Scope Check

The following features are deferred in REQ §6. None appear in the PLAN:

| Deferred REQ | Deferred to | Present in PLAN? |
|-------------|-------------|-----------------|
| REQ-CAPTURE-03 (bearing history screen) | Phase 4 | No — correct |
| REQ-CAPTURE-05 (share/export) | Phase 5 | No — correct |
| REQ-DECL-03 (Grid north) | Phase 5 | No — correct (GRID exclusion guard needed, see PM-F-04) |
| REQ-DISPLAY-04–06 (Luopan mode) | Phase 3 | No — correct |

Scope is clean. No Phase 3/4/5 features have been pulled forward.

---

## Test Phase Assessment (P8)

| Test Type | PLAN Phase | Assessment |
|-----------|-----------|------------|
| Unit tests (pure JVM/Robolectric) | P8.1, P8.2, P8.3 | Covered. Coverage targets (≥90% wmm package, ≥85% location/capture) are explicit and appropriate. |
| Integration tests (instrumented, DB migration, encryption) | P8.4 | Covered. Migration test and encryption test both specified. |
| UI / Espresso tests | P8.5 | Covered. Three Espresso test classes named. AT-G-08 GRID exclusion check missing (see PM-F-04). |
| Macrobenchmark (cold/warm startup) | P8.6 | Covered. Both `StartupMode.COLD` and `StartupMode.WARM` with correct thresholds (≤5 s / ≤3 s) and ≥5 iterations. |

All four required test types are present.

---

## Critical Path Assessment

The sequential critical path is: Batch 0 → Batch 1 → Batch 2 → Batch 3 (P4.1) → Batch 4 → Batch 5 (P6.2) → Batch 6 (P6.3) → Batch 7 (P8.5). This is reasonable. P4.1 (`CompassViewModel` true north integration) is correctly identified as the integration hub, and all downstream UI, capture, and test phases gate on it. The decision to keep P8.1 (WMM unit tests) in Batch 3 alongside P4.1 is efficient — WMM layer tests do not block UI work.

One concern: P6.4 in Batch 7 creates a window where the capture save path (P6.3, Batch 6) is implemented without the first-capture privacy notice. This is addressed in PM-F-05.

---

## Recommendation: Approved with Minor Issues

The PLAN is structurally sound and covers all REQ IDs and FSPEC acceptance tests with two gaps. Before the first batch begins, the following changes are required:

1. **PM-F-01 (High):** Fix `interference_flag` derivation in P6.1 to use `InterferenceState`, not `OverallConfidence.POOR`.
2. **PM-F-02 (High):** Rename `SystemClock` → `WallClock` throughout.
3. **PM-F-03 (High):** Expand P6.3 to fully model the pre-capture warning dialog sequence and add AT-E-10 coverage.
4. **PM-F-04 (Medium):** Add GRID exclusion assertion to P6.3 success criteria and AT-G-08 to P8.5 targets.
5. **PM-F-05 (Medium):** Clarify that the GPS privacy notice is inline in the capture dialog (not a preceding modal) and move P6.4 into Batch 6.
6. **PM-F-06 (Low):** Remove `"True N (cached location)"` from P4.1 label set; replace with `"True N"`.

None of these findings require architectural changes. All are correctible with targeted edits to the PLAN document. Implementation may begin on Batch 0 (P1.1, P1.2, P1.4, P2.1) after PM-F-02 is resolved in P1.1, as the remaining findings do not affect those foundation tasks.
