# Cross-Review: test-engineer — REQ

**Reviewer:** test-engineer
**Document reviewed:** docs/luopan-compass/p4-bearing-history/REQ-luopan-p4-bearing-history.md
**Date:** 2026-04-27
**Iteration:** 2

---

## Summary of Iteration 1 Resolution

All 14 iteration-1 findings are resolved in v0.2-draft. Key improvements that directly unblock test authoring:

- **F-01 (High):** Drift precondition now quantified — accelerometer variance < 0.01 (m/s²)² over 5 s; interference guard requires InterferenceState CLEAR or MODERATE. Fully resolved.
- **F-02 (High):** Swipe-to-delete state machine is now complete — commit-on-swipe, single-active-undo, second-swipe cancels first, process-kill behavior. Scenarios C, C2, C3 cover all branches.
- **F-03 (High):** First-launch detection mechanism is now a concrete version-code comparison via `sensor_profile_written_for_version` in `SettingsRepository`. Resolved.
- **F-04 (High):** Confidence-cap rule for cal_age > 30 days is now reproduced inline using the min-score formula. Scenario D updated to reference POOR from cal_age → overall capped at MODERATE. Resolved.
- **F-05 (Medium):** Scenario E2 added — explicit negative that drift prompt must NOT fire when InterferenceState is active. Resolved in intent; see new finding F-01 below for a precision issue in the scenario wording.
- **F-06 (Medium):** Search behavior fully specified: case-insensitive substring, 300ms debounce, empty-query restores full list, zero-match shows "No bearings match your search". Resolved.
- **F-07 (Medium):** NFR now measurable — 500ms initial load for 500 records on `Dispatchers.IO`; 16ms frame time during fling scroll. Resolved.
- **F-08 (Medium):** Dismissal persistence specified — age-based banner is per-session, reappears on next launch; drift banner has 10-minute per-dismissal cooldown. Resolved.
- **F-09 (Medium):** Negative badge scenario now explicit — REQ-DETECT-05 contains "Badge MUST NOT appear on records with interference_flag=false"; Scenario B includes the paired non-flagged record. Resolved.
- **F-10 (Medium):** Expanded record fields now enumerated in §5.1; interference-only fields (field_deviation_pct, inclination_deviation_deg) are gated on interference_flag=true in §5.2. Resolved.
- **F-11 (Low):** Scenario D2 added with exactly 30-day-old calibration as a negative boundary test. Resolved.
- **F-12 (Low):** sensor_profile.json schema is fully specified with field names, types, sources, units, and pretty-print format. Resolved.
- **F-13 (Low):** Network non-transmission now correctly declared as a design-level guarantee enforced by a lint rule, not an automated test assertion. Resolved.
- **F-14 (Low):** Scenario A2 added for empty history state; §5.1 empty history state is specified with exact message text. Resolved.

---

## Findings

| ID | Severity | Finding | Section ref |
|----|----------|---------|------------|
| F-01 | High | **Scenario E2 references a non-existent InterferenceState value ("POOR") — the acceptance test is untestable as written.** Scenario E2's Given states "InterferenceState is POOR per REQ-DETECT-01." The `InterferenceState` enum defines `CLEAR`, `MODERATE`, and `WARNING` — there is no `POOR` value. The drift suppression precondition in §5.3 Condition B correctly uses `CLEAR` or `MODERATE` (implying NOT `WARNING`), but Scenario E2 uses the undefined alias "POOR." An automated acceptance test that instantiates `InterferenceState.POOR` will fail to compile. The scenario Given must be corrected to: "Active InterferenceState is WARNING (the state that maps to Poor confidence via the confidence model)." | §7 Scenario E2, §5.3 Condition B precondition 2 |
| F-02 | Medium | **The drift detection timer reset rule on precondition oscillation is unspecified — a key edge-case test cannot be written.** §5.3 Condition B states the timer "resets to zero if any precondition is violated." However, at a 50 Hz sensor update rate, the InterferenceState and accelerometer variance values are re-evaluated on every sensor event. The REQ does not define behavior when a precondition is violated transiently (e.g., InterferenceState flips to WARNING for one or two frames then back to CLEAR during an otherwise stable 55-second window). Two distinct behaviors are possible: (a) any single-frame violation resets the timer to zero; (b) violations below a hysteresis threshold are ignored. These produce materially different test cases. Without the rule, the state machine for the 60-second timer has an unspecified branch that prevents writing a regression test for near-threshold oscillation scenarios. | §5.3 Condition B timer, §7 Scenario E |
| F-03 | Medium | **The 10-minute drift cooldown requires a controllable clock dependency for automated testing, but none is called out in the REQ.** §5.3 states the drift dismissal cooldown is 10 minutes "stored in ViewModel." A unit test for this cooldown cannot rely on a real 10-minute wait in CI. The existing codebase already provides a `FakeClock` and `Clock` interface pattern (used in `ConfidenceModel` and `CalibrationRepositoryTest`). The REQ should explicitly require that the drift detection and cooldown logic accepts a `Clock` dependency — this is a testability requirement, not an implementation preference. Without it, the FSPEC and TSPEC authors may use `System.currentTimeMillis()` directly, making the cooldown regression untestable in a unit test. | §5.3 Condition B dismissal, §7 Scenario E |
| F-04 | Medium | **Performance NFR for frame time has no specified test methodology — "16ms during fling scroll on a mid-range device" cannot be evaluated in automated CI without a defined instrumentation harness.** §5.1 requires frame time ≤ 16ms during fling scroll with 500 records on a mid-range device. The codebase already includes a macrobenchmark workflow (see `.github/workflows`). The REQ should specify whether this NFR is to be verified by: (a) a Macrobenchmark instrumented test with a `FrameTimingMetric`, or (b) a manual Systrace/profiler verification only. Without this, engineering cannot determine whether a failing frame-time assertion should block a CI gate or be treated as a periodic manual check. The initial load NFR (500ms) has the same ambiguity — it is measurable but the test harness (unit test with a fake Room DAO, or instrumented test with a real database?) is unspecified. | §5.1 Performance NFR, §8 Risk P4-R2 |
| F-05 | Low | **Scenario E does not exclude the `expected_field_ut = 0.0` migration case, creating a latent division-by-zero in test data.** §5.3 states that MIGRATION_2_3 sets `expected_field_ut = 0.0` for existing records and that this "disables Condition B triggering." The drift formula is `|measured − expected| / expected`. If a test is constructed with a CalibrationRecord where `expected_field_ut = 0.0`, the formula produces a division by zero. Scenario E's Given currently reads "A CalibrationRecord with `expected_field_ut` is present" — it does not specify that `expected_field_ut > 0.0` is required for the condition to be testable. Both the Scenario E Given and the Condition B precondition in §5.3 should read "A CalibrationRecord with `expected_field_ut > 0.0` is present." | §5.3 Condition B trigger formula, §7 Scenario E |
| F-06 | Low | **Search debounce behavior under mid-input keystrokes is unspecified — the debounce restart rule is implied but not stated.** §5.1 specifies a 300ms debounce: "trigger search after 300ms of no input." Standard debounce semantics restart the 300ms window on each keystroke. However, the REQ does not explicitly state this, making it ambiguous whether: (a) the timer restarts on each character (standard debounce), or (b) the timer starts only on the first character of a new query and fires once after 300ms regardless of subsequent input. An automated test that types characters at 100ms intervals and asserts exactly one search call requires knowing which semantic applies. Given the codebase already uses `Flow.debounce()` in similar contexts, the intended behavior is almost certainly (a) — but it must be stated explicitly to be testable. | §5.1 Search behaviour |
| F-07 | Low | **Scenario F assertion "NOT accessible via the standard Android file manager" is inherently a manual verification — it should be removed from the automated acceptance test scenario.** The REQ correctly designates the network non-transmission guarantee as design-level (enforced by `NoInternetPermissionCheck` lint rule). The same treatment should apply to file manager accessibility. No automated instrumented test can assert that a file at `Context.getFilesDir()` is inaccessible via the stock Files app on a non-rooted device — this is a platform-level sandboxing property. The scenario should remove this assertion and replace it with: "The file is written to `Context.getFilesDir()` (internal app storage)." The inaccessibility guarantee is implicit from Android's storage sandboxing model and needs no automated test assertion. | §7 Scenario F |
| F-08 | Low | **Scenario D's Snackbar/banner exact string format is testable for the static portion but not for the dynamic day count.** Scenario D asserts: banner text "Your calibration is 31 days old — consider recalibrating." This is testable for a Given that injects a 31-day-old timestamp. However, §5.3 Condition A specifies the banner as "Your calibration is [N] days old — consider recalibrating" with N computed dynamically. No scenario tests a boundary where N = 31 vs N = 32 (to catch an off-by-one in the day calculation rounding). A unit test for the banner text generator needs to verify: (a) N = 31 days → "31 days old"; (b) N = 30 days → no banner; (c) N = 31 days and 23 hours → "31 days old" (integer truncation, not rounding). The REQ should state whether N is computed by integer floor division of the elapsed duration or by rounding. | §5.3 Condition A, §7 Scenario D |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | For Condition B timer: if InterferenceState oscillates between CLEAR and WARNING for a few frames (e.g., due to a sensor spike) during an otherwise 55-second stable drift window, does the timer reset to zero on any single WARNING frame, or is there a minimum violation duration required to reset? |
| Q-02 | For the 10-minute drift cooldown: will the drift detection component accept a `Clock` interface dependency (consistent with the existing `FakeClock` pattern) to enable unit testing of the cooldown without real-time delays? |
| Q-03 | For the banner day count N: is N computed as integer floor division (e.g., 31 days and 23 hours → "31"), or rounded (→ "32")? This determines the boundary test values for the banner text assertion. |
| Q-04 | For the performance NFR: is frame time (16ms with 500 records) intended to be gated in CI via a Macrobenchmark `FrameTimingMetric` test, or is it a manual profiler check that does not block automated CI? |

---

## Positive Observations

- The v0.2-draft resolves all 14 iteration-1 findings. The iteration-2 review is significantly narrower in scope — this is a strong improvement pass.
- The swipe-to-delete state machine is now exemplary for testability: commit-on-swipe semantics, single-active-undo, process-kill behavior, and all three Scenarios (C, C2, C3) map cleanly to distinct state machine tests.
- The search behavior block in §5.1 is fully test-ready: case-insensitive substring, debounce duration, zero-match message text, and empty-query behavior are all concrete values that enable exact assertion in unit and instrumented tests.
- The sensor_profile.json schema in §5.4 is the most precise schema definition in any phase REQ to date — field names, types, sources, and units are all named. A JSON schema validation assertion can be written directly from this spec.
- Scenario D2 (exactly 30-day-old calibration shows no banner) is a correct boundary negative test; this strict-greater-than boundary is now unambiguous.
- The interference flag negative assertion in REQ-DETECT-05 ("MUST NOT appear on records with interference_flag=false") uses the correct prohibitive language that maps to a direct negative test assertion.
- The two-banner coexistence rule ("drift prompt and age-based prompt may both be shown simultaneously as separate banners; they are not merged") is an explicit positive statement that enables an integration test with both conditions active simultaneously.

---

## Recommendation

**Need Attention**

> F-01 is a High finding: Scenario E2 references `InterferenceState.POOR` which does not exist — the acceptance test will not compile. This must be corrected before FSPEC authoring.
>
> F-02 and F-03 are Medium findings that affect unit-test design decisions in the TSPEC:
>
> 1. **F-01 (mandatory):** Correct Scenario E2 Given to reference `InterferenceState.WARNING` (not "POOR"). Condition B precondition 2 in §5.3 already uses the correct framing ("CLEAR or MODERATE, i.e., not WARNING") — the scenario must match it.
>
> 2. **F-02 (recommended before FSPEC):** Specify the timer oscillation/reset rule for Condition B. State explicitly whether any single-frame precondition violation resets the timer, or whether there is a minimum violation window (hysteresis).
>
> 3. **F-03 (recommended before TSPEC):** Require that drift detection and cooldown logic accept a `Clock` interface dependency, consistent with the existing `FakeClock` pattern used in `ConfidenceModel` and calibration tests. This is a testability prerequisite for the TSPEC.
