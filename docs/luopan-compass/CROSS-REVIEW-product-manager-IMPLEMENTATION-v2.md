# Cross-Review: Product Manager — Implementation (v2)

**Feature:** luopan-p1-core-compass  
**Reviewer:** Product Manager  
**Date:** 2026-04-24  
**Iteration:** 2  

## Summary

The three High findings from v1 are now resolved: the CalibrationRecord field-name compilation error is fixed, the interference-detection baseline uses a proper EMA, and both `values-ja/` and `values-zh-rTW/` locale directories have been added. WakeLockManager is now properly wired (M-04, M-05 resolved). However, four Medium findings from v1 remain unaddressed (M-01, M-02, M-03, M-06), including the P0 heading-precision requirement and an incomplete confidence-model calibration-quality dimension. H-03 is also only partially resolved — Simplified Chinese (`values-zh`) is still missing several strings and no explicit `values-zh-rCN/` directory exists.

---

## Previous Findings Resolution

| ID | Finding | Status |
|----|---------|--------|
| H-01 | CalibrationRecord `saved_at_utc` / `recorded_at` mismatch | ✅ Resolved — field is now `recorded_at: Long`; entity matches repository |
| H-02 | Interference field-deviation baseline self-referential | ✅ Resolved — EMA `baselineFieldUt` implemented; `fieldDeviation = abs(live - baseline) / baseline` |
| H-03 | Missing `zh-rTW` and `ja` locales | ⚠️ Partially resolved — `values-ja/` and `values-zh-rTW/` added, but `values-zh/` (Simplified Chinese) still missing 9 strings (`compass_unavailable`, `no_magnetometer_explanation`, `sensor_error`, `sensor_not_responding`, `interference_explanation`, and the 4 calibration-dialog strings). No `values-zh-rCN/` directory. |
| M-04 | WakeLockManager never acquired | ✅ Resolved — `wakeLockManager by lazy` + `onStart()`/`onStop()` wired in `CompassActivity` |
| M-05 | No sensor lifecycle in `onStart()`/`onStop()` | ✅ Resolved — WakeLockManager lifecycle now managed via Activity start/stop |

---

## Remaining Findings (Unaddressed from v1)

| ID | Severity | Description | REQ Reference |
|----|----------|-------------|---------------|
| M-01 | Medium | **Heading readout is integer-only (REQ-DISPLAY-02 P0 requirement).** `formatHeading()` formats `"%03d°"` — no tenths digit. REQ-DISPLAY-02 explicitly requires 0.1° precision in the digital readout. | REQ-DISPLAY-02 |
| M-02 | Medium | **Calibration quality never read by `ConfidenceModel`.** `scoreCalibrationQuality()` checks only `ageDays < 0`; a device with `POOR` or `FAIR` quality returns `GOOD`. The actual `CalibrationRecord.quality` field is never fetched or passed in. REQ-CAL-03 requires quality to be one of the five confidence dimensions. | REQ-CAL-03 |
| M-03 | Medium | **Interference banner not dismissible.** `interferenceBanner` is a plain `TextView` with no click handler. REQ-DETECT-03 explicitly says the warning must be user-dismissible; auto-dismiss on field recovery is not sufficient. | REQ-DETECT-03 |
| M-06 | Medium | **Coverage formula does not match spec.** `CalibrationEngine.getCoverageScore()` computes `xRange / (xRange + yRange + zRange)` (X fraction of sum), not `axis_range / max(all axis ranges)` per REQ-CAL-01. `CalibrationWizardActivity` further proxies Y and Z by multiplying by 0.9 and 0.8. Thresholds (0.15 minimum, 0.80 target) are applied against incorrect values. | REQ-CAL-01 |

---

## New Findings

| ID | Severity | Description | REQ Reference |
|----|----------|-------------|---------------|
| N-01 | Low | **Confidence badge text is hardcoded English.** `CompassActivity.observeUiState()` sets badge text to `"High accuracy"`, `"Moderate accuracy"`, `"Poor accuracy"` as string literals — not string resources. These will not be translated. (L-04 from v1, reclassified from observation to Low finding.) | REQ-L10N-01 |
| N-02 | Low | `values-zh-rTW/strings.xml` contains a duplicate `calibration_cta_title` entry at lines 16 and 23. Android will use the last definition; no functional impact, but the file should be deduplicated. | — |

---

## Acceptance Criteria Delta (changes from v1)

| REQ ID | v1 Status | v2 Status |
|--------|-----------|-----------|
| REQ-DETECT-01 | ❌ H-02 broken | ✅ Resolved |
| REQ-CAL-04 | ❌ H-01 build error | ✅ Resolved (H-01); L-02 non-atomic still open |
| REQ-DISPLAY-10 | ❌ M-04 never acquired | ✅ Resolved |
| REQ-NFR-03 | ❌ M-05 sensors not released | ✅ Resolved |
| REQ-L10N-01 | ❌ H-03 missing locales | ⚠️ Partially resolved (2 of 4 locales now complete; zh-Hans incomplete) |
| REQ-DISPLAY-02 | ⚠️ M-01 still open | ❌ Still missing 0.1° precision |
| REQ-CAL-03 | ❌ M-02 still open | ❌ Still missing |
| REQ-DETECT-03 | ⚠️ M-03 still open | ❌ Still missing dismissibility |
| REQ-CAL-01 | ⚠️ M-06 still open | ❌ Coverage formula still incorrect |

---

## Recommendation

**Need Attention**

All three High findings are resolved and the app is now compilable and fundamentally sound. However, four Medium findings (M-01, M-02, M-03, M-06) remain unaddressed — including one P0 requirement (0.1° heading precision) and an incomplete confidence-model calibration dimension — and require resolution before the feature can be considered complete against the REQ acceptance criteria.
