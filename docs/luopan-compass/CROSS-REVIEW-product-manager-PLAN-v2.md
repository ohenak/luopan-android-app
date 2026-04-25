# Cross-Review: PM — PLAN-luopan-p2-true-north-capture (Iteration 2)

**Reviewer role:** Product Manager
**Document reviewed:** PLAN-luopan-p2-true-north-capture.md v0.2
**Prior review:** CROSS-REVIEW-product-manager-PLAN.md (Iteration 1)
**Prior recommendation:** Approved with Minor Issues (6 findings: PM-F-01 through PM-F-06)
**Date:** 2026-04-24

---

## Summary

v0.2 was revised specifically to address the six PM findings and ten TE findings raised in iteration 1. All six PM findings are resolved. The PLAN is ready for implementation.

---

## Finding Verification

### PM-F-01 — `interference_flag` derivation (High) — RESOLVED

**Prior finding:** P6.1 set `interference_flag = true` on `OverallConfidence.POOR` alone, contradicting FSPEC BR-10 and AT-E-10.

**Verification:** P6.1 success criteria now explicitly state: "`interference_flag = true` when `InterferenceState ∈ {MODERATE, WARNING}` at tap time; `interference_flag = false` when `InterferenceState == CLEAR`, even if `OverallConfidence == POOR` (FSPEC BR-10, AT-E-10). `OverallConfidence.POOR` alone does NOT set this flag." The `BearingCaptureUseCaseTest` cases explicitly call out the AT-E-10 scenario: `POOR+CLEAR→flag=false`. The same correct derivation is carried through P6.1 (Phase Table), P6.3 success criteria, P8.2, and P8.5. The `captured_at` clause in P6.1 also correctly documents the `snapshot.tapTimestampMs` semantics (TE-P-03 companion fix).

**Status: RESOLVED.**

---

### PM-F-02 — `WallClock` naming (High) — RESOLVED

**Prior finding:** P1.1 named the production `Clock` implementation `SystemClock`, colliding with `android.os.SystemClock` in contradiction of FSPEC §6.2 BR-06 / TSPEC §2.2.

**Verification:** P1.1 Phase Table description reads: "`WallClock` production impl wraps `System.currentTimeMillis()` (named `WallClock`, not `SystemClock`, to avoid collision with `android.os.SystemClock` — TSPEC §2.2)." P1.1 success criteria state: "production implementation is named `WallClock` (NOT `SystemClock` — avoids collision with `android.os.SystemClock`; TSPEC §2.2)." Key Implementation Note §5.8 adds a normative block explicitly prohibiting `SystemClock` and explaining why. All downstream phase references (P6.1, P8.2 test list, etc.) use `WallClock` or `FakeClock` consistently.

**Status: RESOLVED.**

---

### PM-F-03 — Pre-capture warning dialog completeness (High) — RESOLVED

**Prior finding:** P6.3 partially described the pre-capture warning dialog but missed the trigger condition for `OverallConfidence.POOR`-only captures and did not explicitly map AT-E-10.

**Verification:** P6.3 Phase Table now describes the full four-step tap sequence: (1) `tapTimestampMs` recorded before any dialog; (2) `InterferenceWarningDialogFragment` shown when `InterferenceState ∈ {MODERATE, WARNING}` OR `OverallConfidence == POOR` (FSPEC §2.5 step 3b) with "Cancel" abandoning capture entirely; (3) `BearingCaptureDialogFragment` with inline GPS privacy notice; (4) confirm saves and toasts. P6.3 success criteria explicitly cover: POOR+CLEAR pre-capture warning shown but `interference_flag=false` in the saved record (AT-E-10), and `InterferenceWarningCaptureTest` Espresso test covering MODERATE→warning→`interference_flag=true`. AT-E-10 is also explicitly listed in P8.2 and P8.5 test coverage.

**Status: RESOLVED.**

---

### PM-F-04 — GRID exclusion guard (Medium) — RESOLVED

**Prior finding:** No explicit GRID exclusion assertion or test task enforced that `NorthType.GRID` is never written to `BearingRecord` or shown in the UI (REQ-NORTH-04, FSPEC BR-03/BR-04, AT-G-08).

**Verification:** Multiple enforcement points added throughout v0.2:

- **P4.3 Phase Table:** "Toggle MUST be binary TRUE/MAGNETIC only; GRID label MUST NOT appear anywhere in the UI (AT-G-08)."
- **P4.3 success criteria:** "Toggle is binary TRUE/MAGNETIC only; GRID label does not appear anywhere in the UI or view hierarchy (`GridNorthAbsenceTest` — AT-G-08)."
- **P6.1 success criteria:** "throws `IllegalStateException` if `northType == GRID`"; `BearingCaptureUseCaseTest` includes the GRID-guard test case.
- **P6.3 success criteria:** "`north_type` assertion: `IllegalStateException` thrown before DB write if `north_type == GRID` (AT-G-08)."
- **P8.5 test list:** `GridNorthAbsenceTest` (AT-G-08) explicitly named as a required Espresso test class asserting no view with text "Grid" or "GRID" in main screen or capture dialog.

**Status: RESOLVED.**

---

### PM-F-05 — P6.4 batch placement and inline privacy notice (Medium) — RESOLVED

**Prior finding:** REQ-CAPTURE-06 (first-capture location privacy confirmation) was split across P6.3 (Batch 6) and P6.4 (Batch 7), creating a window where the capture save path could ship without the mandatory privacy toggle. FSPEC §2.5 step 4 and BR-15 require the notice to be inline in the capture dialog, not a preceding modal.

**Verification:** P6.4 Phase Table clarifies: "P6.4 must be implemented alongside P6.3 in the same batch — the capture dialog must never ship without the privacy toggle/notice." P6.4 success criteria add: "Implemented in the same batch as P6.3; capture dialog MUST NOT ship without privacy toggle." The Batch 6 row in the parallelization table now lists `P6.3, P6.4, P8.4, P8.6` together with the explicit note: "P6.4 (inline privacy notice) moves here alongside P6.3 — capture dialog must never ship without privacy toggle (PM-F-05)." The inline placement is confirmed: "GPS toggle and first-capture privacy notice ('Your location will be attached to this bearing...') are displayed INLINE inside `BearingCaptureDialogFragment` (not as a separate preceding modal — FSPEC §2.5 step 4, BR-15)." The dependency graph also shows P6.4 as a same-batch peer of P6.3 in Batch 6.

**Status: RESOLVED.**

---

### PM-F-06 — `"True N (cached location)"` invalid label (Low) — RESOLVED

**Prior finding:** P4.1 success criteria listed `"True N (cached location)"` as a valid `north_label`, which is not defined in FSPEC §2.2 FSPEC-TOGGLE or FSPEC §5.3.

**Verification:** P4.1 success criteria now read: "`north_label` has exactly three valid values (FSPEC §2.2 FSPEC-TOGGLE, §5.3): `"True N"` (for both GPS fresh and cached GPS location), `"True N (manual location)"` (for manual coordinate entry), and `"Magnetic N"`. `"True N (cached location)"` is NOT a valid label — cached-vs-fresh distinction is surfaced in the declination info panel source label, not the heading label." The invalid label is explicitly called out as prohibited, and the correct distinction mechanism (declination info panel, not the heading label) is noted.

**Status: RESOLVED.**

---

## New Scope Issues in v0.2

No new scope issues identified. The following checks were performed:

**Deferred features:** REQ-CAPTURE-03 (bearing history), REQ-CAPTURE-05 (share/export), REQ-DECL-03 (Grid north), and REQ-DISPLAY-04–06 (Luopan mode) remain absent from the PLAN. No Phase 3/4/5 features have been introduced.

**REQ-NORTH-04 label coverage:** The three valid label values in P4.1 match REQ-NORTH-04 exactly (`"True N"`, `"True N (manual location)"`, `"Magnetic N"`). Grid N deferred per REQ §6 and PLAN §5.7.

**REQ-CAPTURE-01 field completeness:** P1.2 success criteria enumerate all required fields from REQ-CAPTURE-01 (id, name, bearing_deg, north_type, confidence, captured_at, calibration_version, field_deviation_pct, inclination_deviation_deg, interference_flag, optional lat/lon/alt/notes/display_mode). No field omitted.

**Cold-start NFR coverage (REQ-NFR-08):** P8.6 retains both `StartupMode.COLD` (≤5 s) and `StartupMode.WARM` (≤3 s) thresholds, matching REQ-NFR-08 exactly.

**TE findings (co-applied in v0.2):** The TE-P findings applied in the same revision (TE-P-01 through TE-P-10) are consistent with PM concerns and do not introduce product scope drift. TE-P-03 (`captured_at` semantics), TE-P-06 (`WallClock` rename), and TE-P-02 (`interference_flag` fix) are the direct counterparts of PM-F-03, PM-F-02, and PM-F-01, respectively, and are confirmed resolved above.

**§5.2 migration gate:** The hard gate that `LuopanDatabaseMigrationTest` must pass before `BearingRepository` (P6.2) is implemented is confirmed present in both the implementation notes and the Batch 4 parallelization row.

**Dependency graph integrity:** P6.4 now correctly appears as a Batch 6 peer of P6.3 in the ASCII dependency graph (line: `P6.3, P6.4, P8.4, P8.6`). No orphaned phases detected.

---

## Recommendation: Approved

All six PM findings (PM-F-01 through PM-F-06) are fully resolved in v0.2. No new scope, traceability, or acceptance-criteria gaps were identified. The PLAN covers all REQ IDs and FSPEC acceptance tests, and the batching model is sound.

Implementation may proceed on Batch 0 immediately.
