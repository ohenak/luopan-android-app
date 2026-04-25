# Cross-Review: Test Engineer — Implementation (v2)

**Feature:** luopan-p1-core-compass
**Reviewer:** Test Engineer
**Date:** 2026-04-24
**Iteration:** 2

---

## Summary

The optimizer commit c4ea139 addressed three of the five High/Medium findings from v1 (CalibrationRecord field rename, EMA-based interference baseline, WakeLockManager lifecycle wiring, and two new locale files). However, the changes introduced one new compile-break regression: `CalibrationRecordTest.kt` still references the 23-field `CalibrationRecord` schema that was simplified to 15 fields, and will not compile against the updated production class. The two High-severity findings from v1 (PROP-FUSION-06 synthetic sequence, PROP-SENSOR-04 zero-mag STUCK path) remain unresolved. Additionally, a spec-compliance gap is now visible in the WakeLockManager implementation: PROP-DISPLAY-02/03 requires `FLAG_KEEP_SCREEN_ON` (WindowManager flag), but the optimizer wired `PowerManager.SCREEN_DIM_WAKE_LOCK` instead.

---

## Previous High Findings Resolution

| ID | Finding | Status |
|----|---------|--------|
| H-01 | PROP-FUSION-06 synthetic sequence — `known_heading_30s.json` is a synthetic placeholder; ±0.5° CI gate not verified | **Still present** — file unchanged; no executing test added |
| H-03 | PROP-SENSOR-04 zero-mag immediate STUCK not implemented — `SensorStateMonitor` only uses time-gap detection; no all-zero-magnetometer path | **Still present** — `SensorStateMonitor.kt` and its test are unchanged; PropertiesTest still does not cover this path |

---

## Impact on Test Coverage

### CalibrationRecord simplification (15 fields)

The optimizer removed 8 fields from `CalibrationRecord` (`device_model`, `sensor_fingerprint`, `residual_rms`, `saved_at_utc`, `wmm_expected_field_ut`, `wmm_source`, `coverage_x`, `coverage_y`, `coverage_z`, `calibration_schema_version`) and renamed `saved_at_utc` → `recorded_at`. The production class and `CalibrationRepository.toRecord()` correctly use `recorded_at`. However, `CalibrationRecordTest.kt` was not updated and still constructs `CalibrationRecord` with the full 23-field schema (including `saved_at_utc`, `device_model`, `residual_rms`, etc.). This is a **compile-break**: the test file will fail to compile against the current production data class. No existing PropertiesTest covers `CalibrationRecord` construction directly, so this regression has no compensating coverage.

### CompassViewModel — EMA interference baseline and lowRateStartNs

The optimizer introduced `baselineFieldUt: Float = -1f` (first-frame seed, then 0.99/0.01 EMA) and `lowRateStartNs: Long = -1L` (2-second power-saving timer). Neither the ViewModel coroutine path nor these two state variables have JVM unit test coverage. `CompassViewModelTest` continues to test only `CompassUiState.INITIAL` defaults. PROP-SENSOR-06 (power-saving advisory after ≥ 2 s at < 15 Hz) and the EMA-derived `fieldDeviation` input to `InterferenceDetector` remain untested at the unit level. This does not worsen the v1 gap — both were already flagged (L-04, L-07) — but the optimizer's new logic is equally unverified.

### WakeLockManager wiring

`CompassActivity.onStart()` now calls `wakeLockManager.acquire()` and `onStop()` calls `release()`. The implementation uses `PowerManager.SCREEN_DIM_WAKE_LOCK or ON_AFTER_RELEASE` with a 10-minute timed acquire. PROP-DISPLAY-02 specifies that `activity.window.attributes.flags and FLAG_KEEP_SCREEN_ON != 0` after `onStart()` — a `WindowManager` flag, not a `PowerManager` wake lock. No call to `window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)` exists in the codebase, meaning the spec assertion would fail even if an Instrumented test were written. No new tests were added for PROP-DISPLAY-02/03.

### New locale files (values-zh-rTW, values-ja)

Both files provide translations for strings actively referenced in `CompassActivity`: `sensor_not_responding`, `sensor_error`, `interference_explanation`, `stabilizing`, `no_gyroscope_advisory`, `power_saving_advisory`, `calibrate_now`. The `values-ja/strings.xml` file is complete relative to these runtime keys. The `values-zh-rTW/strings.xml` file contains a **duplicate key** (`calibration_cta_title` appears at both line 16 and line 23); Android's AAPT2 treats duplicate string keys as a warning or error depending on the build configuration — this should be resolved.

The pre-existing `values-zh/strings.xml` (Simplified Chinese) is **still missing** three runtime-referenced keys: `interference_explanation`, `sensor_error`, and `sensor_not_responding`. These strings will fall back to English on `zh` (generic Simplified Chinese) devices for sensor error and interference states. This was a pre-existing gap but was not closed by the optimizer despite the locale work in c4ea139.

The pre-existing duplicate `CalibrationResult` class definition (one in `CalibrationResult.kt` with `Vector3`/`Matrix3x3` fields; one in `CalibrationEngine.kt` with `FloatArray` fields — both in the same package) was not resolved by the optimizer. This was noted as M-04 in v1 and remains a compile-ambiguity risk. `CalibrationDataClassesTest` tests the stale `Vector3`-based shape; `PropertiesTest` and `CalibrationRepositoryTest` correctly use the `FloatArray`-based shape from `CalibrationEngine.kt`.

---

## New Findings

### High

- **[H-04] CalibrationRecordTest compile-break from optimizer schema simplification**: `CalibrationRecordTest.kt` instantiates `CalibrationRecord` with 23 fields (`device_model`, `sensor_fingerprint`, `residual_rms`, `saved_at_utc`, `wmm_expected_field_ut`, `wmm_source`, `coverage_x/y/z`, `calibration_schema_version`). The optimizer simplified `CalibrationRecord` to 15 fields and removed all of these. The test file was not updated. This will produce a Kotlin compilation error for the entire `calibration` test module. The fix is to either update `CalibrationRecordTest` to match the 15-field schema or — if the 23-field schema is intentional — revert the production class simplification.

### Medium

- **[M-07] PROP-DISPLAY-02/03 implementation uses wrong screen-on mechanism**: PROP-DISPLAY-02 specifies `FLAG_KEEP_SCREEN_ON` (verified as `activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0`). The optimizer's `WakeLockManager` uses `PowerManager.SCREEN_DIM_WAKE_LOCK`, which dims but does not set the window flag. An Instrumented test for PROP-DISPLAY-02 written against the current implementation would fail the spec assertion. The implementation should add `window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)` in `onStart()` and clear it with `clearFlags` in `onStop()` (in addition to or instead of the WakeLock).

- **[M-08] values-zh-rTW/strings.xml has a duplicate key**: `calibration_cta_title` is defined twice (lines 16 and 23). AAPT2 may silently use the last definition, or may produce a build error with `--strict-namespaces`. The duplicate should be removed.

### Low

- **[L-08] values-zh locale missing three runtime-referenced keys**: `interference_explanation`, `sensor_error`, and `sensor_not_responding` are absent from `values-zh/strings.xml`. These strings are referenced in `CompassActivity.kt` and will display English text on generic Simplified Chinese devices for those states. The optimizer closed this gap for `values-zh-rTW` and `values-ja` but not for `values-zh`.

- **[L-09] EMA baseline and power-saving timer in CompassViewModel have no unit coverage**: The `baselineFieldUt` EMA initialisation and the `lowRateStartNs` 2-second gate are new logic paths added by the optimizer. No JVM test exercises the ViewModel coroutine path. This continues the L-07 gap from v1 and should be addressed before the release gate.

---

## Properties Coverage Matrix Changes from v1

| Property ID | v1 Status | v2 Status | Change |
|---|---|---|---|
| PROP-FUSION-06 | ❌ synthetic placeholder | ❌ synthetic placeholder | Unchanged |
| PROP-SENSOR-04 | ❌ zero-mag path not tested | ❌ zero-mag path not tested | Unchanged |
| PROP-DISPLAY-02 | ❌ not tested | ❌ not tested; implementation uses wrong mechanism | Regressed (spec-impl gap now visible) |
| PROP-DISPLAY-03 | ❌ not tested | ❌ not tested; implementation uses wrong mechanism | Regressed (spec-impl gap now visible) |
| PROP-L10N-01 | ❌ not tested | ❌ not tested; ja + zh-rTW files now exist | Marginally improved (files exist) |
| PROP-L10N-03 | ❌ not tested | ❌ not tested; zh-rTW file now exists | Marginally improved (file exists) |

All other properties from v1 are unchanged in status.

---

## Recommendation

**Need Attention**

The optimizer's changes introduced a compile-break regression (`CalibrationRecordTest` referencing the removed 23-field `CalibrationRecord` schema) and exposed a spec-implementation mismatch for PROP-DISPLAY-02/03 (wrong screen-on mechanism). These must be resolved before re-gating. The two pre-existing High findings (H-01, H-03) remain open and continue to require hardware data collection and a zero-mag STUCK implementation respectively.
