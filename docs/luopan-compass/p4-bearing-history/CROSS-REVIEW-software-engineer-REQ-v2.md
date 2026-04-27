# Cross-Review: software-engineer — REQ

**Reviewer:** software-engineer
**Document reviewed:** docs/luopan-compass/p4-bearing-history/REQ-luopan-p4-bearing-history.md
**Date:** 2026-04-27
**Iteration:** 2

---

## Summary of Iteration 1 Resolution

The v0.2-draft addresses all six mandatory changes from iteration 1. Specifically:

- **F-01 (High):** `expected_field_ut` column added to `CalibrationRecord`; MIGRATION_2_3 specified; sphere radius `r` nominated as source. Partially resolved — see new finding F-01 below.
- **F-02 (High):** Stationary precondition now quantified (accelerometer variance < 0.01 (m/s²)² over 5 s); interference guard added (Condition B only fires when state is CLEAR or MODERATE). Resolved.
- **F-03 (High):** Reactive `Flow<List<BearingRecord>>` or `PagingSource` DAO requirement now explicit in REQ-CAPTURE-03. Resolved.
- **F-04 (Medium):** Confidence impact of cal_age > 30 days now explained inline via the min-score formula; Scenario D updated to reflect actual POOR→cap-at-MODERATE behavior. Resolved.
- **F-05 (Medium):** First-launch detection now specified via `sensor_profile_written_for_version` version-code comparison in `SettingsRepository`. Resolved.
- **F-08 (Medium):** Banner dismissal persistence, re-show behavior, and 10-minute drift cooldown now fully specified. Resolved.

---

## Findings

| ID | Severity | Finding | Section ref |
|----|----------|---------|------------|
| F-01 | High | **`CalibrationResult` does not expose the sphere radius `r` — the `expected_field_ut` source value is inaccessible from `CalibrationRepository`.** The REQ (§5.3 database constraint) correctly states that `expected_field_ut` is "derived from the sphere radius `r` computed by `CalibrationEngine.fitEllipsoid`." However, `r` is a local variable inside `fitEllipsoid` (line 118 of `CalibrationEngine.kt`) and is NOT a field in `CalibrationResult`. `CalibrationResult` only exposes `residualMicroTesla`, which is the mean absolute deviation from `r` (not `r` itself). `CalibrationRepository.toRecord()` therefore has no value to write into `expected_field_ut`. To implement the REQ as written, `CalibrationResult` must gain a new field (e.g., `sphereRadius_uT: Float`), `fitEllipsoid` must populate it, and `toRecord()` must map it to `expected_field_ut`. The FSPEC and PLAN must include this `CalibrationResult` schema change explicitly — it is a three-file change (`CalibrationEngine`, `CalibrationResult`, `CalibrationRepository`). | §5.3 database constraint, Risk P4-R3 |
| F-02 | High | **`InterferenceState` enum uses `WARNING` (not `POOR`) for the state that maps to Poor confidence, but Condition B and Scenario E2 reference "Poor" interchangeably as both a confidence level and an interference state.** `InterferenceState` has three values: `CLEAR`, `MODERATE`, `WARNING`. There is no `InterferenceState.POOR`. Condition B's precondition 2 says "Active `InterferenceState` from REQ-DETECT-01 is `CLEAR` or `MODERATE`" — this is implementable. But Scenario E2's Given says "active interference from a proximate source has set confidence to Poor (InterferenceState is POOR per REQ-DETECT-01)" which references a non-existent enum value. The REQ must consistently use `InterferenceState.WARNING` (the state that drives Poor confidence via `ConfidenceModel.scoreInterference`) rather than the undefined alias "POOR." This is a precision issue that will cause confusion in FSPEC and during implementation, and will make the Scenario E2 acceptance test literally un-automatable as written. | §5.3 Condition B precondition 2, §7 Scenario E2 |
| F-03 | Medium | **Drift detection timer state machine is unspecified: where does it live, on which thread, and how does it handle `InterferenceState` oscillation during the 60-second window?** The REQ specifies the preconditions and timer threshold but says nothing about the component that owns the timer. The sensor pipeline runs on `Dispatchers.Default` inside `CompassViewModel.startSensorCollection()`. Adding a stateful 60-second timer to the ViewModel's sensor loop is possible but the REQ must clarify: (a) does the timer live in `CompassViewModel` as a new mutable field (side-effecting the 200 Hz sensor loop), or in a dedicated `DriftDetector` component injected into the ViewModel? (b) is the timer reset when any one precondition is momentarily violated (e.g., `InterferenceState` flips to `WARNING` for one frame then back to `CLEAR`), or is there hysteresis? Without this, engineering teams will make divergent choices that the FSPEC cannot reconcile. | §5.3 Condition B timer, §7 Scenario E |
| F-04 | Medium | **`CalibrationWizardActivity` does not currently support a "return to caller" navigation contract, but the REQ requires banners to launch it and implies the user returns to the history screen afterward.** `CalibrationWizardActivity.saveCalibration()` calls `setResult(RESULT_OK, intent)` + `finish()` — it returns to whichever Activity launched it via `startActivityForResult()`. The REQ states "tapping the banner opens `CalibrationWizardActivity` directly" (§5.3 Condition A and B) but does not specify: (a) which Activity or Fragment is responsible for launching it (`CompassActivity`? a new `BearingHistoryActivity`?); (b) what happens when the user completes or cancels calibration — do they return to the compass screen, the history screen, or wherever the banner appeared? The `onCalibrationComplete()` hook in `CompassViewModel` handles the compass screen case, but if the banner appears on a new history screen, a parallel callback mechanism is needed. Risk P4-R4 raises the navigation architecture question as open but the REQ must resolve it before FSPEC authoring. | §5.3 Conditions A & B, §7 Scenarios D & E, Risk P4-R4 |
| F-05 | Medium | **The accelerometer variance formula for the "stationary" precondition (< 0.01 (m/s²)² over 5 s) is ambiguous: is this per-axis or the 3-axis combined magnitude variance?** Condition B precondition 1 specifies "accelerometer 3-axis variance < 0.01 (m/s²)² over a rolling 5-second window." The phrase "3-axis variance" could mean: (a) variance of each individual axis independently (all three must be < 0.01), or (b) variance of the combined magnitude `sqrt(ax²+ay²+az²)`. At 50 Hz over 5 s, a 250-sample window for a magnitude variance is straightforward; per-axis requires three independent variance accumulators. These produce materially different sensitivity to device motion. The REQ must specify which form is intended. Gravity (~9.8 m/s²) affects the mean on each axis but not variance when the device is truly stationary, so both forms are feasible — but they are not equivalent. | §5.3 Condition B precondition 1, §7 Scenario E |
| F-06 | Medium | **No I/O failure handling specified for `sensor_profile.json` write.** REQ-SENSOR-07 (§5.4) specifies writing to `Context.getFilesDir()` on first launch. Internal app storage write can fail when storage is full or permissions are unexpectedly revoked (rare but possible). The REQ does not define: (a) whether a write failure is silently swallowed or reported (e.g., logged only); (b) whether the `sensor_profile_written_for_version` key is updated if the write fails (if it is, the file will never be retried; if it is not, the write will be retried on every subsequent launch until it succeeds). A P2 requirement with a write-once guarantee needs a defined failure contract to avoid silent data loss. | §5.4 REQ-SENSOR-07, §7 Scenario F |
| F-07 | Low | **Scenario E's Given pre-condition "A CalibrationRecord with `expected_field_ut` is present" is ambiguous about the zero-value migration case.** §5.3 specifies that MIGRATION_2_3 sets `expected_field_ut = 0.0` for existing records. If the drift formula is `|measured − expected| / expected`, a stored `expected_field_ut = 0.0` causes a division-by-zero at runtime. The REQ (§5.3 database constraint) says "which disables Condition B triggering" but does not state the exact guard: is it `expected_field_ut > 0.0` as a precondition check in code, or is there a sentinel value? Scenario E's Given should explicitly exclude the `expected_field_ut = 0.0` migration case, or the precondition should read "A CalibrationRecord with `expected_field_ut > 0.0` is present." | §5.3 Condition B trigger formula, §7 Scenario E |
| F-08 | Low | **The "10-minute cooldown" for the drift prompt is specified as stored in ViewModel only (session-scoped), but this is inconsistent with the stated behavior.** §5.3 says the drift prompt dismissal has a "10-minute cooldown (stored in ViewModel)." A ViewModel survives configuration changes but NOT process death. If the user dismisses the drift banner and then force-kills and relaunches within 10 minutes, the ViewModel is reconstructed and the cooldown is lost — the banner will re-appear immediately. For a banner that fires after 60 continuous seconds of drift, this means a relaunched app could show the drift banner again within seconds of the previous dismissal. Contrast with the age-based banner which explicitly re-appears on every launch. The REQ should clarify whether 10-minute cooldown must survive process death (requiring SharedPreferences/timestamp) or whether re-show on relaunch is acceptable. | §5.3 Condition B dismissal, §2 Usable State table |
| F-09 | Low | **`SettingsRepository` has no existing key for `sensor_profile_written_for_version` — this is a new Phase 4 addition that must be coordinated with the implementation of REQ-SENSOR-07.** The current `SettingsRepository` (Phase 1–3 additions only) has no `sensor_profile_written_for_version` key. The REQ correctly specifies the key name and comparison logic. However, the REQ should call out that this key must be added as a new `const val` and `var` pair in `SettingsRepository`, parallel to the existing Phase 3 additions block — not as an ad-hoc `prefs.getInt()` call at the use site. This is a minor coordination note to prevent scattered SharedPreferences access. | §5.4 REQ-SENSOR-07, first-launch detection mechanism |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | `CalibrationResult` currently only exposes `residualMicroTesla` (mean absolute deviation from sphere radius `r`). Should `r` be added as a new field `sphereRadius_uT: Float` to `CalibrationResult`, or should `expected_field_ut` in `CalibrationRecord` be derived differently (e.g., from the WMM-expected field at calibration-time location, if GPS was available)? |
| Q-02 | For Condition B precondition 1, is the accelerometer variance check per-axis (all three axes independently < 0.01 (m/s²)²), or is it the variance of the combined 3-axis magnitude? |
| Q-03 | When the drift banner is tapped from a hypothetical `BearingHistoryFragment` and `CalibrationWizardActivity` completes, should the user return to the history screen or the compass screen? Does the history screen need to register an `ActivityResultLauncher` to receive `RESULT_OK` and call `loadCalibrationAge()`? |
| Q-04 | If the `sensor_profile.json` write fails due to storage full, should the `sensor_profile_written_for_version` key be written anyway (accept the loss) or left at its prior value (retry next launch)? |
| Q-05 | Is the "10-minute drift cooldown" intended to survive process death (requires a timestamp in SharedPreferences), or is it purely session-scoped (ViewModel field)? |

---

## Positive Observations

- The complete REQ-CAL-05 specification in §5.3 is a substantial improvement over iteration 1 — Condition A and Condition B are cleanly separated with distinct trigger, banner text, dismissal, and cooldown rules. This is directly FSPEC-ready.
- The v0.2 sensor_profile.json schema (§5.4) is precise and complete: all field names, types, sources, and units are named, with a clear pretty-print format note. Testable without ambiguity.
- The search behavior block in §5.1 fully resolves the iteration 1 gap: match type (case-insensitive substring), debounce (300 ms), empty-query behavior, and zero-match state are all specified.
- The swipe-to-delete contract (§5.1) now covers all edge cases identified in iteration 1: commit-on-swipe semantics, single-active-undo constraint, second-swipe dismisses first Snackbar, process-kill behavior. Scenarios C, C2, and C3 are complete.
- The confidence-cap explanation in Scenario D correctly uses the min-score model from master REQ §8.3 rather than asserting a fixed "MODERATE cap" — this is consistent with `ConfidenceModel.normalCompute()`.
- REQ-DETECT-05 badge negative case (absent for `interference_flag=false`) and Scenario B now includes the paired non-flagged record — fully addresses the TE review finding.
- Deferring the navigation architecture decision to Risk P4-R4 is acceptable for a REQ, but that risk must be resolved before FSPEC authoring (see F-04).

---

## Recommendation

**Need Attention**

F-01 and F-02 are blocking for implementation correctness:

1. **F-01 (mandatory before FSPEC):** Add `sphereRadius_uT: Float` (or equivalent) to `CalibrationResult` so that `CalibrationRepository.toRecord()` can populate `expected_field_ut`. The FSPEC and PLAN must include this as an explicit change to `CalibrationEngine`, `CalibrationResult`, and `CalibrationRepository`.

2. **F-02 (mandatory before FSPEC):** Replace all references to "InterferenceState is POOR" in the REQ (§5.3 Condition B and Scenario E2) with the correct enum value `InterferenceState.WARNING`. Verify Condition B precondition 2 reads: "Active `InterferenceState` is `CLEAR` or `MODERATE` (i.e., not `WARNING`)."

3. **F-03 (strongly recommended before FSPEC):** Specify the component that owns the drift timer (ViewModel field vs. dedicated `DriftDetector`) and the precondition-violation reset behavior, so the FSPEC can prescribe the state machine without ambiguity.

4. **F-04 (must resolve before FSPEC):** Resolve Risk P4-R4 (navigation architecture) and define the back-navigation contract for `CalibrationWizardActivity` when launched from a recalibration banner on the history screen.
