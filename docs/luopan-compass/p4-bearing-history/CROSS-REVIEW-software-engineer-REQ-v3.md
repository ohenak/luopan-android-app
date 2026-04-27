# Cross-Review: software-engineer — REQ

**Reviewer:** software-engineer
**Document reviewed:** docs/luopan-compass/p4-bearing-history/REQ-luopan-p4-bearing-history.md
**Date:** 2026-04-27
**Iteration:** 3

---

## Summary of Iteration 2 Resolution

The v0.3-draft addresses the two mandatory High findings and one mandatory Medium from iteration 2.
Specifically:

- **v2-F-01 (High):** The REQ now states `CalibrationResult` must gain a new `sphereRadius_uT`
  field (by implication — §5.3 says `expected_field_ut` is "derived from the sphere radius `r`
  computed by `CalibrationEngine.fitEllipsoid`"). However, `CalibrationResult` still does NOT
  expose `r` as a named field. See F-01 below — this finding is partially resolved at the REQ
  intent level but the required schema change to `CalibrationResult` is not stated explicitly.
- **v2-F-02 (High):** `InterferenceState.POOR` references in Condition B precondition 2 and
  Scenario E2 have been replaced with the correct enum values. Condition B now reads "CLEAR or
  MODERATE (i.e., not WARNING)." Scenario E2 now correctly references `InterferenceState.WARNING`.
  **Resolved.**
- **v2-F-03 (Medium):** The REQ does not explicitly name the owning component for the drift timer,
  but the note in Risk P4-R4 acknowledges the architecture is unresolved. See F-02 below for
  remaining gap.
- **v2-F-04 (Medium):** `CalibrationWizardActivity` navigation contract for the history screen
  context remains unresolved. Risk P4-R4 is still listed as open. See F-02 below.
- **v2-F-05 (Medium):** The accelerometer variance ambiguity ("3-axis variance" — per-axis or
  combined magnitude?) is not resolved in v0.3. See F-03 below.
- **v2-F-06 (Medium):** I/O failure contract for `sensor_profile.json` write is not resolved.
  See F-04 below.
- **v2-F-07 (Low):** Scenario E's `expected_field_ut = 0.0` guard is still not explicit in the
  scenario pre-condition. See F-05 below.
- **v2-F-08 (Low):** The 10-minute drift cooldown process-death scope question is still open.
  See F-06 below.
- **v2-F-09 (Low):** `sensor_profile_written_for_version` key placement note resolved — the REQ
  now explicitly names the key and compares against `BuildConfig.VERSION_CODE`. The REQ does not
  prescribe the `const val` placement in `SettingsRepository`, which is acceptable at REQ level.
  **Resolved.**

---

## Findings

| ID | Severity | Finding | Section ref |
|----|----------|---------|------------|
| F-01 | High | **`CalibrationResult` still does not expose sphere radius `r` — the three-file schema change is not specified in the REQ.** §5.3 correctly states `expected_field_ut` is "derived from the sphere radius `r` computed by `CalibrationEngine.fitEllipsoid`" and that Risk P4-R3 names `CalibrationEngine`, `CalibrationRepository`, and migration scripts. However, the REQ never states that `CalibrationResult` itself must gain a new field. In the current code `r` is a local variable in `fitEllipsoid` (line 118 of `CalibrationEngine.kt`); `CalibrationResult` exposes only `residualMicroTesla` (the mean absolute deviation from `r`, not `r` itself), `coverageScore`, `quality`, `hardIron`, and `softIron`. `CalibrationRepository.toRecord()` therefore has no value to write into `expected_field_ut`. The FSPEC and PLAN must include an explicit `CalibrationResult` schema change (add `sphereRadius_uT: Float`) as a first-class task — without it the migration column will always be populated with a placeholder or incorrect value. The REQ must name this contract change to prevent it being missed during FSPEC authoring. | §5.3 database constraint, Risk P4-R3 |
| F-02 | Medium | **Risk P4-R4 (navigation architecture) is still listed as an open risk, but it must be resolved before FSPEC authoring.** §5.3 Conditions A and B both say "Tapping the banner opens `CalibrationWizardActivity` directly." The current `CalibrationWizardActivity.saveCalibration()` calls `setResult(RESULT_OK) + finish()`, returning to whichever caller started it via `startActivityForResult` / `ActivityResultLauncher`. `CompassActivity` currently handles the calibration result via `CompassViewModel.onCalibrationComplete()`. If the history screen is a separate Fragment (or a new Activity), it needs its own `ActivityResultLauncher` and must call an equivalent hook to refresh `calibrationAgeDays` on return. The REQ does not resolve: (a) what screen hosts the recalibration banners (compass screen only, or history screen too?), (b) whether the history screen registers its own `ActivityResultLauncher`, and (c) what happens to banner state after calibration completes — is the age-based banner suppressed immediately on return, or only after the next `loadCalibrationAge()` completes asynchronously? Without these answers, FSPEC cannot prescribe the state machine. | §5.3 Conditions A & B, §7 Scenarios D & E, Risk P4-R4 |
| F-03 | Medium | **"3-axis variance < 0.01 (m/s²)²" in Condition B precondition 1 is still ambiguous between per-axis and combined-magnitude interpretations.** v0.3 retains the phrase "accelerometer 3-axis variance < 0.01 (m/s²)² over a rolling 5-second window" unchanged from v0.2. The variance of the 3-axis magnitude (`sqrt(ax²+ay²+az²)`) and the per-axis variance (all three axes independently < 0.01) produce materially different stationary detection sensitivity: on a typical stationary device the magnitude variance is ~0.001–0.003 (m/s²)² while per-axis variances can differ by a factor of 2–5x depending on device orientation relative to gravity. Whichever form is intended, the FSPEC will implement differently: a per-axis check needs three variance accumulators; a magnitude check needs one. The REQ must explicitly state which form is required. | §5.3 Condition B precondition 1, §7 Scenario E |
| F-04 | Medium | **No I/O failure handling contract specified for `sensor_profile.json` write.** §5.4 specifies writing to `Context.getFilesDir()` but defines no behavior when the write fails (e.g., storage full). Specifically, it does not define whether `sensor_profile_written_for_version` should be written on failure. If the key IS written on failure, the profile will never be retried (silent permanent data loss). If the key is NOT written, the write will be retried on every subsequent launch (correct retry behavior, but could cause repeated I/O on low-storage devices). This is a P2 requirement with a write-once guarantee; the failure contract must be explicit. Scenario F has no corresponding negative scenario for write failure. | §5.4 REQ-SENSOR-07, §7 Scenario F |
| F-05 | Low | **Scenario E's pre-condition "A CalibrationRecord with `expected_field_ut` is present" does not exclude the migration zero-value case, creating a latent division-by-zero risk.** §5.3 states MIGRATION_2_3 sets `expected_field_ut = 0.0` for existing records and says this "disables Condition B triggering." But the drift formula `|measured − expected| / expected` divides by `expected_field_ut`; if `expected_field_ut = 0.0`, this is a division by zero. The code-level guard (presumably `if (expected_field_ut > 0f)`) is implied but not stated in the REQ. Scenario E's Given should explicitly read "A CalibrationRecord with `expected_field_ut > 0.0` is present" so acceptance testers and FSPEC authors know the zero-value case must be excluded. | §5.3 Condition B trigger formula, §7 Scenario E |
| F-06 | Low | **Drift prompt 10-minute cooldown process-death behavior is unspecified.** §5.3 states the 10-minute cooldown is "stored in ViewModel." A ViewModel survives configuration changes but NOT process death. If the user dismisses the banner, then force-kills and immediately relaunches the app, the ViewModel is reconstructed and the cooldown is gone — the banner will re-appear after 60 s of continuous drift, potentially within seconds of the previous dismissal. This contrasts with the age-based banner which explicitly re-appears on every launch (which is consistent and documented). The REQ should state whether the 10-minute drift cooldown is intended to be purely session-scoped (acceptable; re-show on relaunch is OK) or must survive process death (requires a SharedPreferences timestamp). This affects implementation cost: SharedPreferences requires an additional key in `SettingsRepository`. | §5.3 Condition B dismissal cooldown |
| F-07 | Low | **The version header in the REQ document itself still reads "0.2-draft" despite being presented as v0.3-draft.** The metadata table at the top of the document shows `Version: 0.2-draft`. If this document is the v0.3-draft, the version field should read `0.3-draft` to prevent confusion with the prior draft that the v2 review was based on. | REQ §1 metadata table |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | Should `CalibrationResult` gain a field named `sphereRadius_uT: Float` (populated from the local `r` variable in `fitEllipsoid`)? This is the only path by which `CalibrationRepository.toRecord()` can populate `expected_field_ut` correctly. Confirming this in the REQ eliminates ambiguity about whether to derive the value from `residualMicroTesla` or another source. |
| Q-02 | Is the "3-axis variance" check in Condition B precondition 1 the variance of the combined magnitude `sqrt(ax²+ay²+az²)`, or must all three per-axis variances independently be below 0.01 (m/s²)²? |
| Q-03 | If `sensor_profile.json` write fails due to storage-full, should `sensor_profile_written_for_version` be (a) not written (retry next launch), or (b) written anyway (accept the loss and suppress future retries)? |
| Q-04 | Is the 10-minute drift cooldown purely session-scoped (ViewModel field, lost on process death), or must it survive process death (SharedPreferences timestamp)? |
| Q-05 | Which screen owns the recalibration banners — the compass screen only, or both the compass screen and the bearing history screen? If both, does the history screen require its own `ActivityResultLauncher` for `CalibrationWizardActivity`, and what callback refreshes `calibrationAgeDays` on return? |

---

## Positive Observations

- **v2-F-02 fully resolved:** All references to the non-existent `InterferenceState.POOR` have been
  corrected to `InterferenceState.WARNING`. Condition B precondition 2 and Scenario E2 are now
  internally consistent and automatable.
- **v2-F-07 (Low) resolved:** Scenario E's Given language for `expected_field_ut` presence is
  improved; the database constraint section clearly states the migration default is 0.0 and links
  it to Condition B being disabled. Only the missing explicit `> 0.0` guard in Scenario E itself
  remains (F-05 above, Low severity).
- **Swipe-to-delete contract (§5.1 and Scenarios C, C2, C3)** is fully implementable as written;
  commit-on-swipe semantics, single-active-undo constraint, second-swipe dismissal, and
  process-kill behavior are all specified with no remaining ambiguity.
- **Search specification (§5.1)** is complete and directly implementable: case-insensitive substring
  match, 300 ms debounce, empty-query restore, zero-match empty-state message. The existing
  `BearingDao.getAll()` gap is acknowledged and resolved by the REQ mandating a reactive
  `Flow<List<BearingRecord>>` DAO method.
- **Sensor profile schema (§5.4)** is precise and testable: every field has a named source
  (`Build.MODEL`, `Sensor.getName()`, etc.), type, and format. The version-gate detection
  mechanism (`sensor_profile_written_for_version` key comparison) is correctly specified.
- **Confidence model alignment (Scenario D)** correctly describes the `min()` behavior across
  `ConfidenceScore` dimensions. The scenario no longer claims a fixed "MODERATE cap" — it
  accurately reflects that overall confidence is POOR when any other dimension is also degraded,
  consistent with `ConfidenceModel.normalCompute()`.
- **REQ-DETECT-05 interference flag (§5.2)** is fully implementable without schema changes;
  `BearingRecord` already stores `interference_flag`, `field_deviation_pct`, and
  `inclination_deviation_deg`. The `field_deviation_pct` display conversion (stored fractional ×
  100 → "25%") is explicitly specified, eliminating the unit ambiguity from iteration 1.

---

## Recommendation

**Need Attention**

F-01 remains a High finding: the `CalibrationResult` schema change required to populate
`expected_field_ut` is not explicitly specified in the REQ, and without it the FSPEC and PLAN
cannot prescribe the correct three-file change to `CalibrationEngine`, `CalibrationResult`, and
`CalibrationRepository`.

**Mandatory before FSPEC authoring:**

1. **F-01 (High — mandatory):** Add an explicit statement to §5.3 (or Risk P4-R3) that
   `CalibrationResult` must gain a new field (e.g., `sphereRadius_uT: Float`) populated from
   the local variable `r` in `fitEllipsoid`, and that `CalibrationRepository.toRecord()` maps
   this field to `expected_field_ut` in `CalibrationRecord`. This is a three-file change:
   `CalibrationEngine.kt`, `CalibrationResult` (inline data class in `CalibrationEngine.kt`),
   and `CalibrationRepository.kt`.

2. **F-02 (Medium — mandatory):** Resolve Risk P4-R4 (navigation architecture) before FSPEC
   authoring. Specifically: state which screen(s) host the recalibration banners, and define the
   `ActivityResultLauncher` and post-calibration refresh contract for the history screen context.

3. **F-03 (Medium — recommended):** Clarify whether "3-axis variance < 0.01 (m/s²)²" means
   per-axis (three independent accumulators) or combined-magnitude variance (one accumulator).
