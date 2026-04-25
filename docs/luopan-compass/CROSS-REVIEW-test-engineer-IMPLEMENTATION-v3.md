# Cross-Review: Test Engineer — Implementation (v3)

**Feature:** luopan-p1-core-compass
**Reviewer:** Test Engineer
**Date:** 2026-04-24
**Iteration:** 3

---

## Summary

Commit `73ecb71` successfully resolves all three findings raised in the v2 review (H-04 compile-break, M-07 wrong screen-on mechanism, M-08 duplicate string). `CalibrationRecordTest.kt` now compiles cleanly against the 15-field schema. `CompassActivity` correctly uses `window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)` / `clearFlags` alongside the retained `WakeLockManager`. The duplicate `calibration_cta_title` key is gone from `values-zh-rTW/strings.xml`.

Two new implementation behaviors introduced in this commit require evaluation: `getCoverageScore()` / `getPerAxisCoverage()` in `CalibrationEngine`, and the `calibrationQuality` parameter wired into `ConfidenceModel.compute()`. One new spec-violation finding is raised (H-05: interference banner is now tap-dismissible, violating PROP-DISPLAY-05). The two pre-existing High findings from v1 (H-01, H-03) remain open as known items.

---

## Previous Findings Resolution

| ID | Finding | Status |
|----|---------|--------|
| H-04 | `CalibrationRecordTest.kt` compile-break — referenced removed 23-field schema | **Resolved** — rewritten to 15-field constructor; matches production `CalibrationRecord` exactly |
| M-07 | Wrong screen-on mechanism — `SCREEN_DIM_WAKE_LOCK` instead of `FLAG_KEEP_SCREEN_ON` | **Resolved** — `CompassActivity.onStart()` now calls `window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)` and `onStop()` calls `window.clearFlags(...)`. `WakeLockManager` (SCREEN_DIM_WAKE_LOCK) is retained in parallel, which is acceptable as belt-and-suspenders. |
| M-08 | Duplicate `calibration_cta_title` key in `values-zh-rTW/strings.xml` | **Resolved** — file now has a single entry for that key |
| H-01 (v1) | PROP-FUSION-06 — synthetic sensor sequence; ±0.5° CI gate unverified | **Still open** — `known_heading_30s.json` unchanged; no executing test added |
| H-03 (v1) | PROP-SENSOR-04 — zero-mag immediate STUCK path not tested or implemented | **Still open** — `SensorStateMonitor` and its test unchanged |

---

## Property Test Coverage for New Changes

### `getCoverageScore()` and `getPerAxisCoverage()` (CalibrationEngine)

`getCoverageScore()` returns `min(xRange, yRange, zRange) / max(xRange, yRange, zRange)` — the normalized worst-axis fraction. `getPerAxisCoverage()` returns a `Triple` of `(xRange/maxRange, yRange/maxRange, zRange/maxRange)`.

**Test coverage assessment:**

- `PropertiesTest.PROP-CAL-01` calls `getCoverageScore()` with all-identical samples and asserts `0f` — this path (total range = 0) is covered.
- No test calls `getPerAxisCoverage()` at all. PROP-CAL-02 requires verifying that the dominant-axis fraction equals `1.0f` and subordinate axes produce the correct proportional values (e.g. `coverage_x = 1.0f`, `coverage_y = 0.2f`, `coverage_z = 0.1f` from a defined distribution). This is **still unimplemented** (M-01 from v1, unresolved).
- `CalibrationWizardActivity` now uses `getPerAxisCoverage()` live during collection (per-axis UI labels and `MIN_COVERAGE` gate per axis). This code path is entirely untested at the JVM level — no unit test exercises the per-axis collection gate logic.
- The semantic contract of `getCoverageScore()` has changed subtly: it now returns the minimum axis fraction rather than a traditional spherical coverage score. The `classifyQuality` thresholds (`>= 0.6` for GOOD, `>= 0.3` for FAIR) were calibrated against the old formula. No test verifies that a spherical sample set (200 points from `addSphereSamples()`) produces a `getCoverageScore()` that actually satisfies the GOOD threshold — `CalibrationEngineTest` only passes explicit floats to `classifyQuality`.

### `calibrationQuality` parameter in `ConfidenceModel`

`CompassViewModel` now loads `calibrationQuality` from the DB record and passes it to `confidenceModel.compute()`. `ConfidenceModel.compute()` now accepts `calibrationQuality: CalibrationQuality = CalibrationQuality.GOOD` and routes it through `scoreCalibrationQuality()`.

**Test coverage assessment:**

- `ConfidenceModelTest` contains no test for `scoreCalibrationQuality()` and no call to `compute()` that overrides the default `calibrationQuality` parameter. All existing `ConfidenceModelTest` calls use the 7-argument signature without passing `calibrationQuality`, so the default `GOOD` is always exercised.
- No test verifies that `FAIR` quality caps overall confidence at `MODERATE`, or that `POOR` quality produces `POOR` confidence even when all other dimensions are `GOOD`. These are directly addressable with two JVM test cases.
- The ViewModel-level wiring (`calibrationQuality` loaded in `loadCalibrationAge()` and passed to `compute()`) remains untested — this is a continuation of the L-07/L-09 gap.

### Heading precision format `%05.1f°`

`CompassViewModel.formatHeading()` now formats with `%05.1f°` (leading zeros, one decimal). `CompassActivity` independently uses `"%05.1f°".format(frozenHeading)` for the STUCK-state freeze path. No unit test exercises this format string against a known heading value (e.g. `0.0` → `"000.0°"`, `359.9` → `"359.9°"`). The only tests for `heading_formatted` assert the `"---"` sentinel for the INITIAL/STABILIZING state — the formatted number path is untested.

---

## New Findings

### High

- **[H-05] PROP-DISPLAY-05 violated: interference banner is now tap-dismissible**
  `CompassActivity` wires `interferenceBanner.setOnClickListener { interferenceBannerDismissed = true; interferenceBanner.visibility = View.GONE }`. PROP-DISPLAY-05 specifies: "When the interference warning banner is visible, a user tap on it does not dismiss it. The warning persists until interference clears naturally." The current implementation contradicts this spec. Once the user taps the banner, `interferenceBannerDismissed` is `true` and subsequent `MODERATE` / `WARNING` state updates do not re-show the banner (both branches guard on `!interferenceBannerDismissed`). The banner can only re-appear after interference clears to `CLEAR` (which resets `interferenceBannerDismissed = false`) and then returns to a warning state. This is a direct spec violation that an Espresso test for PROP-DISPLAY-05 would catch. The fix is to remove the `setOnClickListener` on `interferenceBanner` (or make it a no-op).

### Medium

- **[M-09] `scoreCalibrationQuality()` has no dedicated tests**: The new sixth scoring dimension in `ConfidenceModel` — `scoreCalibrationQuality(ageDays, quality)` — is not covered by any test. Three cases are missing: `FAIR` quality → `MODERATE`; `POOR` quality → `POOR`; and uncalibrated (`ageDays < 0`) bypasses the quality enum and returns `POOR` directly. These are pure JVM unit tests requiring no Android context.

- **[M-10] `getPerAxisCoverage()` has no test — PROP-CAL-02 remains unimplemented**: This was previously filed as M-01 (v1). The new implementation now exposes `getPerAxisCoverage()` publicly and uses it in the wizard, but no test exercises it. PROP-CAL-02 explicitly requires verifying that the dominant-axis fraction is `1.0f` and subordinate axes return the correct proportional values. This is a straightforward JVM test.

### Low

- **[L-10] `%05.1f°` heading format not unit-tested**: The format string in `CompassViewModel.formatHeading()` and the parallel usage in `CompassActivity` for frozen-heading STUCK state are untested. Edge cases include: `heading = 0.0` → `"000.0°"`, `heading = 359.95` → rounding to `"360.0°"` (potential PROP-FUSION-01 boundary interaction), and values above 100°. A single `CompassViewModelTest` or `CompassUiStateTest` case exercising `buildUiState()` with a real heading value would close this gap.

- **[L-11] `WakeLockManager.SCREEN_DIM_WAKE_LOCK` is deprecated in API 26+**: `PowerManager.SCREEN_DIM_WAKE_LOCK` has been deprecated since Android API 26 (Oreo). Since `FLAG_KEEP_SCREEN_ON` is now the primary mechanism (correctly resolving M-07), `WakeLockManager` can be simplified to remove the deprecated constant, or the `WakeLockManager` can be removed entirely in favor of the window flag alone. This is a cleanup observation, not a spec issue.

- **[L-08] (persisting from v2) `values-zh/strings.xml` missing three runtime keys**: `interference_explanation`, `sensor_error`, and `sensor_not_responding` remain absent from `values-zh/strings.xml`. These strings will fall back to English on generic Simplified Chinese (`zh`) devices for interference and sensor-error states. This was not addressed by commit `73ecb71`.

---

## Properties Coverage Matrix — Changes from v2

| Property ID | v2 Status | v3 Status | Change |
|---|---|---|---|
| PROP-DISPLAY-02 | ❌ wrong mechanism (SCREEN_DIM_WAKE_LOCK) | ✅ Implementation now correct (FLAG_KEEP_SCREEN_ON) | Improved |
| PROP-DISPLAY-03 | ❌ wrong mechanism | ✅ Implementation now correct | Improved |
| PROP-DISPLAY-05 | ❌ not tested | ❌ Implementation violates spec (tap-dismissible) | Regressed |
| PROP-CAL-01 | ⚠️ getCoverageScore() only, per-axis not tested | ⚠️ Unchanged | Unchanged |
| PROP-CAL-02 | ❌ not tested | ❌ not tested; getPerAxisCoverage() added but no test | Unchanged |
| PROP-FUSION-06 | ❌ synthetic placeholder | ❌ synthetic placeholder | Unchanged |
| PROP-SENSOR-04 | ❌ zero-mag path not tested | ❌ zero-mag path not tested | Unchanged |

All other properties from v2 are unchanged in status.

---

## Recommendation

**Need Attention**

One new High-severity finding (H-05: PROP-DISPLAY-05 spec violation — interference banner is tap-dismissible) must be resolved before the next gate. The implementation directly contradicts the spec's "non-dismissible" property. Two Medium findings (M-09: `scoreCalibrationQuality` untested; M-10: `getPerAxisCoverage` untested / PROP-CAL-02 unimplemented) are straightforward JVM additions with no architectural risk. The two pre-existing High findings (H-01 synthetic sequence, H-03 zero-mag STUCK path) remain deferred as known items.

Once H-05 is fixed and M-09/M-10 test cases are added, the implementation is gatable for Phase 1 within the accepted scope of deferred Espresso/Instrumented test stubs.
