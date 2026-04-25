# Cross-Review: Test Engineer — Implementation (v4)

**Feature:** luopan-p1-core-compass  
**Reviewer:** Test Engineer  
**Date:** 2026-04-24  
**Iteration:** 4  

---

## Summary

Commit `64e2ca3` resolves all three actionable findings raised in the v3 review. PROP-DISPLAY-05 has been corrected in the properties spec to reflect the actual (and now accepted) user-dismissible behavior that aligns with REQ-DETECT-03. Five new unit tests in `PropertiesTest.kt` close the M-09 (`scoreCalibrationQuality`) and M-10 (`getPerAxisCoverage`) gaps. L-08 was already resolved in a prior commit (strings were present in `values-zh`); it is confirmed closed.

No new High or Medium findings are identified in this iteration. The pre-existing deferred items (PROP-FUSION-06, PROP-SENSOR-04, all Espresso/Instrumented stubs) remain open by design as Phase 2 scope and are not re-flagged. One pre-existing Low finding (L-10: heading format `%05.1f°` not unit-tested) and one pre-existing Low finding (L-11: deprecated `WakeLockManager.SCREEN_DIM_WAKE_LOCK`) remain open but are non-blocking.

The implementation is gatable for Phase 1.

---

## v3 Findings Resolution

| ID | Finding | Status |
|----|---------|--------|
| H-05 | PROP-DISPLAY-05 conflict — spec said "non-dismissible" but REQ-DETECT-03 says "dismissible" | **Resolved** — PROP-DISPLAY-05 heading now reads "Interference warning is user-dismissible" and the property body describes the tap-dismiss-and-reset lifecycle, fully aligning with REQ-DETECT-03 |
| M-09 | `scoreCalibrationQuality()` had no dedicated unit tests | **Resolved** — three new tests added: `FAIR` → `MODERATE`, `POOR` → `POOR`, and uncalibrated (`ageDays = -1`) overrides quality to `POOR` even when quality enum is `GOOD` |
| M-10 | `getPerAxisCoverage()` had no unit tests; PROP-CAL-02 gap | **Resolved** — two new tests added: equal ranges all return `1.0f`; dominant-axis-only input returns `cx=1.0f`, `cy=0.0f`, `cz=0.0f` |
| L-08 | `values-zh/strings.xml` missing `interference_explanation`, `sensor_error`, `sensor_not_responding` | **Confirmed closed** (strings were present; L-08 was stale in v3) |

---

## Detailed Verification

### H-05: PROP-DISPLAY-05 wording

`PROPERTIES-luopan-p1-core-compass.md` line 706 now reads:

> **PROP-DISPLAY-05: Interference warning is user-dismissible**

The property body describes the full lifecycle: tap hides the banner immediately; banner does not reappear until interference clears and re-triggers; once CLEAR, the dismissal flag resets for the next event. This is consistent with REQ-DETECT-03 and with the `CompassActivity` implementation (`setOnClickListener` + `interferenceBannerDismissed` flag + reset on CLEAR). The matrix row in line 1188 likewise reads "Interference warning user-dismissible." Finding H-05 is fully resolved.

### M-09: `scoreCalibrationQuality()` tests

Three tests verified in `PropertiesTest.kt` (lines 299–342):

1. `scoreCalibrationQuality FAIR calibration maps to MODERATE confidence` — calls `compute()` with `calibrationQuality = CalibrationQuality.FAIR`, all other dimensions GOOD/CLEAR, asserts `OverallConfidence.MODERATE`.
2. `scoreCalibrationQuality POOR calibration maps to POOR confidence` — same setup with `CalibrationQuality.POOR`, asserts `OverallConfidence.POOR`.
3. `scoreCalibrationQuality uncalibrated always returns POOR regardless of quality param` — passes `calibrationAgeDays = -1L` with `CalibrationQuality.GOOD`, asserts `OverallConfidence.POOR`, confirming the uncalibrated override takes precedence over the quality enum.

All three cases are correctly scoped as JVM unit tests using Robolectric, exercise real production code paths, and assert the minimum-dimension semantics documented in the spec. Coverage is complete for the three missing cases identified in M-09.

### M-10: `getPerAxisCoverage()` tests

Two tests verified in `PropertiesTest.kt` (lines 344–368):

1. `getPerAxisCoverage equal ranges return all ones` — adds 10 samples with `(i, i, i)`, destructures the returned `Triple`, asserts all three coverage values equal `1.0f ± 0.01`. Exercises the equal-range path where all axes share the maximum range.
2. `getPerAxisCoverage zero range axis returns zero coverage` — adds 11 samples with varying X (`0..10`) and constant Y/Z (`5.0f`), asserts `cx=1.0f`, `cy=0.0f`, `cz=0.0f`. Exercises the compressed-axis path where subordinate axes have zero range and thus zero coverage.

Both tests directly verify the contract of `getPerAxisCoverage()` documented in PROP-CAL-02 and close the gap previously filed as M-10/M-01.

---

## New Findings

None. No new issues were identified in commit `64e2ca3` beyond the pre-existing deferred items.

---

## Open Pre-existing Items (non-blocking, deferred by design)

| ID | Item | Status |
|----|------|--------|
| H-01 (v1) | PROP-FUSION-06 — synthetic `known_heading_30s.json` sequence; ±0.5° CI gate unverified | Open — Phase 2 scope |
| H-03 (v1) | PROP-SENSOR-04 — zero-mag immediate STUCK path not tested | Open — Phase 2 scope |
| L-10 (v3) | `%05.1f°` heading format not unit-tested | Open — Low risk, non-blocking |
| L-11 (v3) | `WakeLockManager.SCREEN_DIM_WAKE_LOCK` deprecated API 26+ | Open — cleanup, non-blocking |
| — | All DISPLAY/CAL-UX/SENSOR/PERSIST Espresso stubs | Open — Phase 2 scope |

---

## Recommendation

**Approved**

All v3 findings are resolved. PROP-DISPLAY-05 is now consistent across spec and implementation. The five new unit tests for `scoreCalibrationQuality()` and `getPerAxisCoverage()` are correctly written, exercise the right code paths, and assert the expected behavior. No new issues are introduced by commit `64e2ca3`. The implementation satisfies Phase 1 gate criteria within the accepted scope of deferred Phase 2 items.
