# Cross-Review: software-engineer — REQ

**Reviewer:** software-engineer
**Document reviewed:** docs/luopan-compass/p4-bearing-history/REQ-luopan-p4-bearing-history.md
**Date:** 2026-04-27
**Iteration:** 1

---

## Findings

| ID | Severity | Finding | Section ref |
|----|----------|---------|------------|
| F-01 | High | **Drift detection references "calibration-time expected magnitude" — this field does not exist in CalibrationRecord.** REQ-CAL-05 defines systematic drift as "measured field magnitude at a stable, low-interference location differs from calibration-time expected magnitude by >10% for >60 consecutive seconds." `CalibrationRecord` (db entity) stores only hard-iron/soft-iron coefficients and quality — it does NOT store the expected field magnitude used at calibration time. REQ-CAL-04 (master REQ §6.2) lists this as a required persisted field but the current schema lacks it. Without this value the drift rule is unimplementable as written: the runtime has no calibration-time reference to compare against. A DB migration will be required (version 3). | REQ-CAL-05, §5.3, Scenario E |
| F-02 | High | **"Stable, low-interference location" prerequisite for drift detection is undefined and undetectable.** The drift trigger requires the device to be at "a stable, low-interference location." Neither "stable" (stationary?) nor "low-interference" (below which threshold?) is defined. There is no motion/acceleration detector that confirms stationarity in the current code. Without these preconditions the 60-second timer can fire when the user is walking or in an interference-laden environment, making the prompt useless or misleading. The acceptance criteria must define: (a) what constitutes "stable" (e.g., accelerometer variance < X over 5 s), and (b) what constitutes "low-interference" (e.g., `InterferenceState.CLEAR` for the full 60 s window). | REQ-CAL-05, §5.3, Scenario E |
| F-03 | High | **`BearingDao.getAll()` returns `List<BearingRecord>` (not a `Flow`), which is incompatible with reactive UI updates and will load the entire table into memory.** The history screen requires live updates after delete+undo and after new captures. The current DAO returns a plain `List`; the ViewModel would have to re-query manually on every change. For hundreds of records this is also an unbounded memory load. Engineering will need to add a `Flow<List<BearingRecord>>` or `PagingSource`-backed DAO method, plus a DB migration if a new index is required. The REQ should acknowledge this as a constraint. Risk P4-R2 correctly calls out the need for virtualization but does not connect it to the DAO contract gap. | REQ-CAPTURE-03, §5.1, Risk P4-R2 |
| F-04 | Medium | **Confidence downgrade for calibration age >30 days already implemented differently from the REQ's claim.** Scenario D states: "Confidence is capped at 'Moderate' until recalibration occurs (calibration age >30 days condition per master REQ §8.1)." However, `ConfidenceModel.scoreCalibrationAge` returns `ConfidenceScore.POOR` (not MODERATE) when `ageDays > CAL_AGE_POOR_DAYS` (30). This means an uncalibrated or 31-day-old calibration already produces `OverallConfidence.POOR` — not MODERATE. The acceptance test in Scenario D expects the user can still operate normally with a "Moderate" cap, but in practice confidence will already be POOR. Either the acceptance test or the existing `ConfidenceModel` constants must be reconciled before Phase 4 work begins; otherwise the Phase 4 banner test will contradict live behavior. | REQ-CAL-05, §7 Scenario D |
| F-05 | Medium | **"First launch after Phase 4 install" detection mechanism is unspecified.** REQ-SENSOR-07 (§5.4) requires `sensor_profile.json` to be written "on first launch after Phase 4 installs." There is currently no version-gated first-launch guard in the codebase. Tracking which app version last wrote the profile requires a stored version code in `SettingsRepository` or a separate flag. The trigger wording "after Phase 4 install" implies a version-code comparison (write if stored_version < Phase 4 versionCode) rather than a one-time boolean, because the profile should regenerate on upgrade. This mechanism must be specified and implemented explicitly; it cannot be inferred from the REQ as written. | REQ-SENSOR-07, §5.4, Scenario F |
| F-06 | Medium | **Search by name (REQ-CAPTURE-03) has no corresponding DAO query, no debounce requirement, and no empty-search-results state defined.** The existing `BearingDao` provides only `getAll()` with no `LIKE`-based query. A new DAO method is needed. The REQ does not specify: (a) whether search is substring, prefix, or full-word; (b) whether it is case-insensitive; (c) what the UI shows when zero results match (empty state); (d) whether search is debounced (minimum character count before triggering). All four are engineering implementation decisions that should be resolved at REQ level to avoid scope churn in FSPEC. | REQ-CAPTURE-03, §5.1 |
| F-07 | Medium | **Undo of swipe-to-delete requires the deleted record to be held in memory during the 5-second window, but no lifecycle contract is specified.** If the user swipes away a record, presses Home (background), and the process is killed, the undo intent is lost. The REQ does not specify whether undo survives backgrounding, nor does it specify what happens to the in-memory record if a new capture occurs (potentially changing DB row ordering) during the 5-second window. The undo position guarantee ("restores the record in its original position") is particularly fragile if concurrent inserts shift the sort order. The REQ must define behavior when: (a) the app is backgrounded during the undo window, and (b) the record's original sort position is occupied by a new insert. | REQ-CAPTURE-03, §7 Scenario C |
| F-08 | Medium | **Recalibration banner persistence behavior is unspecified.** The REQ states the banner is "dismissible" but does not define: (a) whether dismissal is remembered across app restarts (should the banner re-appear on next launch if the condition still holds?); (b) whether there is a re-show cooldown (e.g., show at most once per day); (c) what happens if both conditions (age >30 days AND drift) apply simultaneously (two banners? one merged message?). Without these constraints, engineers will make divergent choices that may conflict with product intent. | REQ-CAL-05, §7 Scenarios D & E |
| F-09 | Low | **Scenario F acceptance test contradicts internal app storage semantics.** Scenario F states the file "is accessible via Android file manager (internal/files path)." Internal app storage (`Context.getFilesDir()`) is NOT accessible via the standard Android file manager on non-rooted devices without granted `READ_EXTERNAL_STORAGE` (deprecated) or `MANAGE_EXTERNAL_STORAGE`. The REQ should either: (a) clarify this is developer/ADB access only, or (b) move the file to `Environment.getExternalStoragePublicDirectory()` or a `FileProvider` URI for end-user access. Shipping with a false testability claim in the acceptance test is a QA trap. | REQ-SENSOR-07, §7 Scenario F |
| F-10 | Low | **No non-functional requirements defined for Phase 4 features.** The REQ introduces three new subsystems (history screen, drift-detection coroutine, sensor profile writer) but provides no NFRs for: (a) history screen initial load time (how many ms for 100 records?), (b) drift detection CPU overhead (it runs on the sensor pipeline thread), (c) sensor profile write time or I/O failure handling. Risk P4-R2 acknowledges a performance concern but elevates it to an engineering implementation detail without a measurable target. A minimum requirement ("initial load of up to 500 records must complete within 500 ms on Dispatchers.IO") is needed. | §8, cross-ref master REQ §7 |
| F-11 | Low | **REQ-DETECT-05 interference flag requires `field_deviation_pct` and `inclination_deviation_deg` to be stored at capture time, but the units definition is inconsistent.** The REQ uses `field_deviation_pct` in the requirement ID and description. However, `BearingRecord` (already implemented) stores `field_deviation_pct: Float` as a fractional value (0.25 = 25%) based on `InterferenceMetrics.fieldDeviation`. The history screen full record detail in Scenario B says "field_deviation_pct and inclination_deviation_deg at time of capture." If the UI renders this as a percentage, it must multiply by 100 from the stored value. The REQ should clarify the displayed unit: "25%" or "0.25" — and confirm the stored value is fractional. | REQ-DETECT-05, §5.2, §7 Scenario B |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | The drift detection rule compares measured field magnitude to "calibration-time expected magnitude." Should this be the WMM-expected field at calibration-time location, or the actual fitted ellipsoid radius from the `CalibrationEngine`? The `CalibrationResult` already computes a sphere radius (`r` in `fitEllipsoid`), which is effectively the calibration-time field magnitude. If this is the intended reference, no new DB column is needed — only the sphere radius needs to be stored in `CalibrationRecord`. |
| Q-02 | For Scenario E (drift prompt), should the 60-second continuous window reset if the `InterferenceState` temporarily rises above CLEAR, or should it accumulate only CLEAR-state seconds? Clarifying this changes the timer implementation from a simple monotonic counter to a conditional accumulator. |
| Q-03 | When the user taps "Recalibrate" from the drift banner (Scenario E), does the banner launch `CalibrationWizardActivity` directly, or navigate to a settings screen? This affects whether the history screen needs to be able to pop back to itself after calibration. |
| Q-04 | Is the bearing history screen a new tab in the existing `TabLayout` (adding a third destination to `nav_graph.xml`), a bottom sheet, or a full-screen fragment launched from a menu? The current architecture has two tabs (Modern, Luopan); adding a third is a navigation architecture decision that impacts Phase 3 code. |
| Q-05 | Should swipe-to-delete support undo from the Luopan Fragment's history entry point (if history is accessible from multiple modes), or only from the dedicated history screen? |

---

## Positive Observations

- The existing `BearingRecord` schema already captures `interference_flag`, `field_deviation_pct`, and `inclination_deviation_deg`, making REQ-DETECT-05 implementable with UI changes only — no DB migration required for that specific requirement.
- The drift detection prompt wording in Risk P4-R1 is correctly differentiated from the calibration-age prompt, reducing user confusion risk.
- REQ-SENSOR-07 explicitly states the file is "never transmitted," which is consistent with the existing `NoInternetPermissionCheck` lint rule and does not require any permission model changes.
- The 5-second undo window in REQ-CAPTURE-03 is a well-scoped interaction pattern that aligns with Material Design's Snackbar undo convention already present in `ModernCompassFragment`.
- Deferring CSV export (REQ-CAPTURE-05) to Phase 5 is a sound scope decision that avoids premature API contract work.

---

## Recommendation

**Need Attention**

F-01 and F-02 are blocking: the drift detection rule (REQ-CAL-05 / Scenario E) cannot be implemented as written because the calibration-time reference value is not persisted and the preconditions for triggering the rule are undefined. F-03 is also blocking for the history screen's reactive update behavior.

**Mandatory changes before FSPEC authoring:**

1. **F-01:** Add `expected_field_ut: Float` to `CalibrationRecord` (requires DB MIGRATION_2_3) and update `CalibrationEngine`, `CalibrationRepository`, and `CalibrationWizardActivity`. Clarify Q-01 to confirm whether the sphere radius from `fitEllipsoid` serves as the reference value.
2. **F-02:** Define concrete preconditions for the 60-second drift timer: what constitutes "stable" and "low-interference" in measurable sensor terms.
3. **F-03:** Specify that the history DAO must expose a reactive `Flow<List<BearingRecord>>` (or a `PagingSource`) and add this as an engineering constraint in the REQ.
4. **F-04:** Reconcile the Scenario D "capped at Moderate" claim with `ConfidenceModel.scoreCalibrationAge` which already returns POOR at >30 days.
5. **F-05:** Define the first-launch detection mechanism (version-code gate) for sensor profile writing.
6. **F-08:** Define banner dismissal persistence, re-show cadence, and multi-condition behavior.
