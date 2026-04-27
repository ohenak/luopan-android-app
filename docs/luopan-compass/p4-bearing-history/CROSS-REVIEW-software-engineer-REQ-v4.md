# Cross-Review: software-engineer — REQ

**Reviewer:** software-engineer
**Document reviewed:** docs/luopan-compass/p4-bearing-history/REQ-luopan-p4-bearing-history.md
**Date:** 2026-04-27
**Iteration:** 4

---

## Summary of Iteration 3 Resolution

The v0.4-draft resolves all remaining High and Medium findings from iteration 3.

- **v3-F-01 (High):** `CalibrationResult` schema change now explicitly named. §5.3 states `CalibrationResult` must gain a `sphereRadius_uT: Float` field populated from local variable `r` in `fitEllipsoid`, and that `CalibrationRepository.toRecord()` maps it to `expected_field_ut`. Risk P4-R3 cross-references this as an explicit three-file change. **Resolved.**
- **v3-F-02 (Medium) / v2-F-04 (Medium):** Risk P4-R4 marked as resolved. Navigation architecture is fully specified in §5.1: `BearingHistoryFragment` as third tab, `ActivityResultLauncher` registered there, `RESULT_OK` triggers `viewModel.loadCalibrationAge()`, cancel/back returns to history screen. See new finding F-01 below for a residual precision gap.
- **v3-F-03 (Medium):** Combined 3-axis magnitude variance is now explicit in Condition B precondition 1: "A single variance accumulator over the magnitude series is used — not three independent per-axis accumulators." **Resolved.**
- **v3-F-04 (Medium):** I/O failure contract for `sensor_profile.json` is now fully specified: `Log.e` on failure, `sensor_profile_written_for_version` key NOT updated on failure (retry on next launch). Scenario F4 added. **Resolved.**
- **v3-F-05 (Low):** Scenario E's pre-condition now explicitly reads "A CalibrationRecord with `expected_field_ut > 0.0` is present." **Resolved.**
- **v3-F-06 (Low):** 10-minute drift cooldown now stored in `SettingsRepository` as a Unix epoch millisecond value, explicitly surviving process death. **Resolved.**
- **v3-F-07 (Low):** Version header now reads `0.4-draft`. **Resolved.**

---

## Findings

| ID | Severity | Finding | Section ref |
|----|----------|---------|------------|
| F-01 | Medium | **`viewModel.loadCalibrationAge()` is `private` in `CompassViewModel`, but the REQ instructs `BearingHistoryFragment` to call it.** §5.1 Navigation architecture states: "On `RESULT_OK`, the fragment calls `viewModel.loadCalibrationAge()` to update the banner state." Similarly, §5.3 Condition A states "on `RESULT_OK`, banner is dismissed and `viewModel.loadCalibrationAge()` is called." In the current codebase `loadCalibrationAge()` is declared `private fun loadCalibrationAge()` in `CompassViewModel` (line 423). The REQ does not specify which ViewModel type `BearingHistoryFragment` uses. If the history screen uses a new `BearingHistoryViewModel`, it cannot call this private method. If it shares `CompassViewModel`, the method must be promoted to `internal` or `public`. Either path is a non-trivial implementation decision that the FSPEC must resolve: (a) promote `loadCalibrationAge()` to `internal` in `CompassViewModel` and share the instance with `BearingHistoryFragment` via Activity-scoped ViewModel, or (b) define an equivalent refresh entry point in the new ViewModel and wire it to the calibration repository. Without this clarified, FSPEC authors will make divergent choices. | §5.1 Navigation architecture, §5.3 Condition A |
| F-02 | Low | **The REQ states FakeClock is used "in `ConfidenceModel` and calibration tests," but the actual codebase uses `FakeClock` in `CompassViewModelTest` and WMM model tests — not in `ConfidenceModelTest` or calibration-specific tests.** §5.3 Timer ownership: "`DriftDetector` accepts a `Clock` interface dependency (consistent with the `FakeClock` pattern used in `ConfidenceModel` and calibration tests)." `FakeClock` does not appear in `ConfidenceModelTest.kt` or any file under `calibration/` in the test tree. The precedent is valid (`FakeClock` and `Clock` interface exist and are used in `CompassViewModelTest`), but the stated reference tests are incorrect. This is a documentation inaccuracy that could send FSPEC authors to the wrong test files when establishing the testing pattern for `DriftDetector`. The REQ should reference `CompassViewModelTest` as the precedent. | §5.3 Condition B timer ownership |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | Which ViewModel does `BearingHistoryFragment` use for the recalibration banner state — a new `BearingHistoryViewModel`, or the shared Activity-scoped `CompassViewModel`? If the latter, should `loadCalibrationAge()` be promoted from `private` to `internal`? This decision determines the ViewModel ownership boundary and must be settled in the FSPEC. |

---

## Positive Observations

- **v3-F-01 fully resolved:** §5.3 now names `sphereRadius_uT: Float` as the new `CalibrationResult` field and Risk P4-R3 explicitly calls out `CalibrationEngine`, `CalibrationResult`, and `CalibrationRepository` as the three-file change. FSPEC and PLAN authors have a clear, unambiguous implementation target.
- **Navigation architecture (Risk P4-R4) is resolved:** The third-tab `BearingHistoryFragment` model, `ActivityResultLauncher` registration, and back-navigation behavior are all precisely specified in §5.1. The previous three-iteration open question is closed.
- **Combined-magnitude variance formula:** Condition B precondition 1 now explicitly calls out a single accumulator over `sqrt(ax²+ay²+az²)`, and Scenario E's Given uses the same formula verbatim. No ambiguity remains between per-axis and magnitude-based interpretations.
- **I/O failure contract (REQ-SENSOR-07):** The failure path is now complete: `Log.e`, no version key update, and a retry-on-next-launch guarantee. Scenario F4 provides a directly automatable acceptance test for this path.
- **Drift cooldown durability:** Storing the cooldown timestamp in `SettingsRepository` as a Unix epoch millisecond value is the correct implementation choice and is now explicit in both §5.3 and Scenario E. The contrast with the age-based banner's session-only dismissal is clear.
- **`expected_field_ut > 0.0` guard:** The division-by-zero risk from the MIGRATION_2_3 zero-value default is now explicitly guarded in both the formula definition and Scenario E's pre-condition.
- **`DriftDetector` component boundary:** Specifying `DriftDetector` as a separately injected component (not an inline ViewModel field) with `Clock` dependency is a sound testability decision that directly enables unit testing of the 60-second threshold and 10-minute cooldown without real-time delays.

---

## Recommendation

**Approved with Minor Issues**

F-01 is Medium severity but does not block FSPEC authoring if the FSPEC explicitly resolves the ViewModel ownership question (Q-01) before prescribing the `ActivityResultLauncher` callback. F-02 is a documentation inaccuracy only and does not affect implementability.

**Recommended actions before FSPEC authoring:**

1. **F-01 (Medium — recommended):** Add one sentence to §5.1 Navigation architecture stating which ViewModel type `BearingHistoryFragment` will use for banner state and calibration-age refresh (e.g., "the shared Activity-scoped `CompassViewModel`" or "a new `BearingHistoryViewModel`"), and whether `loadCalibrationAge()` must be promoted to `internal`. This prevents the FSPEC from introducing an unresolved ViewModel boundary.

2. **F-02 (Low — optional):** Replace "ConfidenceModel and calibration tests" with "CompassViewModelTest" in the `DriftDetector` timer ownership paragraph so FSPEC authors reference the correct precedent test file.
