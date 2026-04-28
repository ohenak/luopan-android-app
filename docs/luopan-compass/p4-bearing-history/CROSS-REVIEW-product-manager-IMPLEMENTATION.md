# Cross-Review: Product Manager — Implementation
Feature: p4-bearing-history
Date: 2026-04-28
Reviewer Role: Product Manager
Document Reviewed: Implementation (codebase)
Reference: REQ-luopan-p4-bearing-history.md

## Summary

All P0 and P1 requirements from REQ-luopan-p4-bearing-history.md are implemented and the core product behaviors are present. Two medium-severity gaps were identified — a missing empty-state illustration (REQ-CAPTURE-03) and the unused `banner_recalibration.xml` layout file — along with several low-severity observations around minor spec deviations.

## Findings

### High

None.

### Medium

| ID | Severity | Finding | Requirement ref |
|----|----------|---------|----------------|
| F-01 | Medium | **Empty-state illustration missing.** REQ-CAPTURE-03 and Scenario A2 specify "an illustration and message" for the zero-bearings empty state. The `fragment_bearing_history.xml` shows only a `TextView` (`@string/empty_no_bearings`) with no illustration/image view. The string text is correct, but the visual illustration element is absent. While not strictly a functional blocker, it is an explicit acceptance-test criterion: "An empty-state illustration … are displayed." | REQ-CAPTURE-03 §5.1; Scenario A2 |
| F-02 | Medium | **`banner_recalibration.xml` is a dead layout file.** The layout exists in `app/src/main/res/layout/` and is included in the resource merger, but it is not `<include>`d or inflated anywhere in the codebase. The actual banners in `fragment_bearing_history.xml` are inline `ConstraintLayout` blocks with their own IDs. The dead file creates a misleading artifact — a future developer may expect it to be the canonical banner layout and add code against IDs (`@+id/banner_root`, `@+id/banner_text`, `@+id/banner_close`) that are not wired to any Fragment. It should either be deleted or adopted as the shared include for both banners. | REQ-CAL-05 §5.3; §2 Capability table |

### Low

| ID | Severity | Finding | Requirement ref |
|----|----------|---------|----------------|
| F-03 | Low | **Age-based banner re-appearance relies on `onResume` + sensor frame, not a dedicated state.** The banner visibility is gated by `compassViewModel.calAgeBannerDismissed` (a `var` on the ViewModel) checked in `updateAgeBanner()`. On cold start the flag is `false`, so the banner will appear. On process restart after dismissal the flag resets correctly because it is explicitly not persisted. This matches the "per-session dismissible; banner re-appears on next launch" spec. However, the banner state is driven from `uiState.calibration_age_days` which is populated by `buildUiState()` in the sensor loop. If the sensor is not running (e.g. sensor freeze, STUCK state), `calibration_age_days` reflects the last-known value and the banner will still appear correctly because `loadCalibrationAge()` is called in `init` and in `onResume`. No functional gap, but the indirect dependency on the sensor loop for banner visibility may be surprising if the sensor pipeline is paused. | REQ-CAL-05; Scenario D |
| F-04 | Low | **Drift detection timer does not re-arm after 60 s with no deviation.** Per REQ-CAL-05 Condition B: after 60 s elapse, if the deviation is ≤10% the `DriftDetector` stays in `COUNTING` state from the original `countingStartMs` and re-evaluates on every subsequent frame. This means the 10% threshold is tested on every frame after 60 s — there is no "timer reset to zero on pass." This is consistent with the comment in the code ("stay COUNTING from current start"). The REQ says "timer: starts counting when all preconditions hold; resets to zero if any precondition is violated" — implying reset only on violation, not on a passed drift check. The behavior is consistent with the REQ letter, but the "stays COUNTING indefinitely" path could trigger `TRIGGERED` as soon as deviation subsequently exceeds 10% without restarting the 60 s window. This is a potential unexpected UX: a user who briefly returns to the calibration environment and then moves away again would trigger the banner without a fresh 60 s window. Low risk given Risk P4-R1 is accepted. | REQ-CAL-05 §5.3; Scenario E |
| F-05 | Low | **`RecyclerView.NO_ID.toInt()` used instead of `RecyclerView.NO_POSITION` for invalid-position guard.** In `BearingHistoryFragment.onSwiped`, the guard is `if (position == RecyclerView.NO_ID.toInt()) return`. `RecyclerView.NO_ID` is `-1L` and `RecyclerView.NO_POSITION` is also `-1`, so they evaluate identically at runtime. No functional bug, but using the wrong constant name makes the intent unclear to a reader and may confuse future maintainers. | REQ-CAPTURE-03 §5.1 (swipe-to-delete) |
| F-06 | Low | **`banner_cal_age` string format argument type.** `strings.xml` defines `banner_cal_age` as `%1$d` (integer), and `BearingHistoryFragment.updateAgeBanner()` passes `ageDays` (a `Long`). On Android, `getString(R.string.banner_cal_age, ageDays)` with a `Long` where `%d` is expected is not guaranteed to render correctly on all API levels (some versions truncate or throw). The REQ specifies N is computed as floor division — which correctly produces a `Long` — but the string resource should use `%1$d` consistently with a `toLong()` cast or the call site should cast to `Int`. Functionally produces the right text on tested devices, but is a latent compatibility concern. | REQ-CAL-05; Scenario D |

## Positive Observations

- All user-facing string literals match the REQ exactly: banner texts, empty-state messages, interference badge, undo/delete labels, and search hint are all correctly specified.
- The `Flow<List<BearingRecord>>` reactive DAO requirement (REQ-CAPTURE-03 performance NFR) is satisfied by `getAllFlow()` and `searchFlow()` in `BearingDao`.
- Debounce behavior (300 ms on non-empty query, 0 ms on empty query) is correctly implemented in `BearingHistoryViewModel`, matching the REQ search spec including the "empty query restores full list immediately" requirement.
- Swipe-to-delete with single active undo is correctly implemented: `currentSnackbar?.dismiss()` is called before `deleteRecord()`, committing the prior deletion before starting a new undo window.
- Interference badge (`⚠ Captured under interference`) is correctly visible only when `record.interference_flag == true` and absent otherwise, satisfying Scenario B.
- Expanded record shows `field_deviation_pct` as a percentage (stored fraction × 100) and `inclination_deviation_deg` only when `interference_flag == true` — matching REQ-DETECT-05 and Scenario B.
- DB migration (MIGRATION_2_3) correctly adds `expected_field_ut REAL NOT NULL DEFAULT 0.0` to `calibration_records`, preserving the existing-record guard (disables Condition B until user recalibrates).
- `CalibrationEngine.fitEllipsoid()` populates `sphereRadius_uT`, `CalibrationResult` exposes it, and `CalibrationRepository.toRecord()` maps it to `expected_field_ut` — the three-file change is complete.
- `DriftDetector` is correctly implemented as a separate component (not inlined in the ViewModel sensor loop), accepts a `Clock` interface, and is injected via the Factory — matching the REQ testability requirement.
- `AccelerometerVarianceTracker` correctly computes the combined 3-axis magnitude variance over a rolling time-based 5-second window (not three independent per-axis accumulators).
- `InterferenceState.WARNING` correctly suppresses Condition B (drift timer doesn't count), satisfying Scenario E2.
- Drift banner dismissal persists `driftCooldownTimestampMs` to `SettingsRepository` (SharedPreferences) — survives process death, satisfying Scenario E cooldown spec.
- `SensorCapabilityLogger` is called on `Dispatchers.IO` from `CompassActivity.onCreate()`, satisfying REQ-SENSOR-07 first-launch detection.
- `sensor_profile_written_for_version` key is correctly defined as a `const val` in `SettingsRepository` and updated only on successful write — retry-on-failure contract is met.
- `buildJsonFromSensors()` produces all required JSON fields with 2-space indent, and `reporting_mode` is mapped to a string name.
- `BearingHistoryFragment` is registered as a third tab in `nav_graph.xml` and `activity_compass.xml`, leaving the existing Modern and Luopan tabs structurally unchanged.
- Both `ActivityResultLauncher` instances (age path, drift path) are registered in `onCreate()` per Jetpack best practice, correctly handling `RESULT_OK` for each path independently.
- `computeCalibrationAgeDays()` uses `TimeUnit.MILLISECONDS.toDays()` which performs floor division — correctly implementing the "N is never rounded up" spec (Scenario D: 31 days 23 hours → N = 31).

## Recommendation

Approved with Minor Issues

> F-01 (missing empty-state illustration) and F-02 (dead `banner_recalibration.xml`) are Medium findings. F-01 is the more user-facing gap — the acceptance test criterion for Scenario A2 explicitly calls for an illustration. This should be addressed before release but does not block the core feature functionality. F-02 is a code hygiene issue that should be resolved to avoid future confusion. All P0 and P1 requirements are otherwise correctly implemented.
