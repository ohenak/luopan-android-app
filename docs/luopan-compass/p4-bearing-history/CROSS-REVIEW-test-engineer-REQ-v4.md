# Cross-Review: test-engineer — REQ

**Reviewer:** test-engineer
**Document reviewed:** docs/luopan-compass/p4-bearing-history/REQ-luopan-p4-bearing-history.md
**Date:** 2026-04-27
**Iteration:** 4

---

## Summary of Iteration 3 Resolution

Iteration 3 raised 9 findings against a v0.2-draft base (2 High, 3 Medium, 4 Low). All 9 are resolved in v0.4-draft.

| Iter-3 ID | Severity | Status |
|-----------|----------|--------|
| F-01 — Scenario E2 used non-existent `InterferenceState.POOR` | High | **Resolved** — Scenario E2 now reads "has set `InterferenceState` to `WARNING` (the state that maps to Poor confidence via the confidence model, per REQ-DETECT-01)" |
| F-09 — Confidence cap stated as MODERATE but ConfidenceModel produces POOR | High | **Resolved** — §5.3 Condition A and Scenario D now both correctly state POOR, with the min-score formula quoted inline |
| F-02 — Timer reset rule on precondition oscillation unspecified | Medium | **Resolved** — §5.3 Condition B now explicitly states "any single violation of any precondition immediately resets the timer to zero, with no hysteresis window" |
| F-03 — Clock interface dependency not required | Medium | **Resolved** — §5.3 Condition B now requires `DriftDetector` to accept a `Clock` interface dependency (consistent with the existing `FakeClock` pattern) |
| F-04 — Performance NFR test methodology unspecified | Medium | **Resolved** — §5.1 Performance NFR test methodology now specifies: 500 ms threshold via instrumented Room in-memory test; 16 ms threshold via Macrobenchmark `FrameTimingMetric`; CI treatment (non-blocking regression alert, not a build failure gate) |
| F-05 — Scenario E lacked `expected_field_ut > 0.0` guard | Low | **Resolved** — Scenario E Given now reads "A CalibrationRecord with `expected_field_ut > 0.0` is present"; Condition B precondition 3 matches |
| F-06 — Debounce restart rule not stated | Low | **Resolved** — §5.1 Search behaviour now states "the 300 ms window restarts on each keystroke (standard debounce — each new character resets the timer)" |
| F-07 — Scenario F asserted non-automatable file-manager inaccessibility | Low | **Resolved** — §5.4 now states inaccessibility "is an implicit guarantee from Android's storage sandboxing model — it is not asserted in automated acceptance tests"; Scenario F only asserts write to `Context.getFilesDir()` |
| F-08 — Day count N: floor vs. round not specified | Low | **Resolved** — §5.3 Condition A now states "computed as integer floor division of elapsed milliseconds (e.g., 31 days and 23 hours → N = 31; 32 days exactly → N = 32). N is never rounded up." |

---

## Findings

| ID | Severity | Finding | Section ref |
|----|----------|---------|------------|
| F-01 | Medium | **Banner dismiss mechanism is unspecified — the triggering condition for the 10-minute cooldown (Condition B) cannot be asserted in a test.** Both banners are described as "dismissible" (§2, §5.3 header) and Scenario D says "User can dismiss the banner." However, the only banner tap action specified in §5.3 is tapping the banner body, which *navigates to CalibrationWizardActivity*. There is no separate dismiss affordance described (e.g., an X button, a swipe-to-dismiss gesture, or a standalone close action). This creates an untestable gap: (a) if the only dismissal path is CalibrationWizard RESULT_OK, then a user who presses Back from CalibrationWizard cannot dismiss the banner — but Scenario D says dismissal leaves the app "fully functional," implying a lighter dismiss path exists; (b) for Condition B, the 10-minute cooldown start is triggered "per dismissal" — without knowing which UI action constitutes a dismissal, an automated test cannot assert the correct cooldown start timestamp. The dismiss mechanism must be specified as a concrete UI affordance (e.g., "a close/X button in the banner triggers dismissal without opening CalibrationWizard; tapping the banner body opens CalibrationWizardActivity"). | §5.3 Condition A, Condition B dismissal; §7 Scenario D, Scenario E |
| F-02 | Medium | **DriftDetector "reset" semantics on RESULT_OK are undefined — the post-calibration state machine cannot be unit-tested.** §5.3 Condition B states: "on `RESULT_OK`, banner is dismissed and drift detector is reset." The word "reset" is not defined. Two distinct behaviors are possible: (a) reset clears only the 60-second drift timer (the cooldown timestamp in `SettingsRepository` is preserved); (b) reset clears both the 60-second timer and the 10-minute cooldown timestamp. These produce materially different observable states after calibration. With behavior (a), if the drift condition continues and the 10-minute cooldown has not yet elapsed, the banner will not re-appear until the cooldown expires. With behavior (b), the banner could re-appear after another 60-second drift window immediately after calibration. A unit test asserting the `SettingsRepository` cooldown key state after RESULT_OK requires knowing which interpretation applies. | §5.3 Condition B dismissal; §7 Scenario E |
| F-03 | Low | **`inclination_deviation_deg` display format is unspecified — exact string assertions cannot be written.** §5.1 and REQ-DETECT-05 specify `field_deviation_pct` with a complete display format: stored fractional × 100, displayed as "25%". `inclination_deviation_deg` is listed alongside it but has no display format: no unit suffix ("°"? "deg"?), no decimal precision (integer? one decimal place?), no label text. Scenario B only asserts the field is "shown" — not the exact rendered string. An instrumented test asserting the expanded interference record detail view cannot write a precise string assertion without this specification. | §5.1 Expanded record fields; §5.2 REQ-DETECT-05; §7 Scenario B |
| F-04 | Low | **No scenario for in-session banner suppression after dismissal — the per-session persistence property is untested.** §5.3 Condition A specifies that the age-based banner dismissal is "per-session (stored in ViewModel only)" and that the banner "re-appears on the next app launch." There is a scenario for re-appearance on next launch (Scenario D: "Banner re-appears on next launch if calibration is still >30 days old"). However, there is no scenario asserting that after dismissal within a session, the banner does NOT re-appear in that same session — for example, when the user navigates away from the history tab and returns. Without this negative within-session case, an implementation that suppresses the banner only once and then re-shows it on every tab re-entry would pass Scenario D but violate §5.3. | §5.3 Condition A dismissal; §7 Scenario D |
| F-05 | Low | **Scenario F4's logcat assertion cannot be automated — it should be replaced with an assertable state check.** Scenario F4 asserts: "The failure from the previous attempt is present in logcat at `Log.e` level." Logcat output from a *previous* app launch is not accessible to the current instrumented test session. This assertion is inherently manual. The automatable proxy is: assert that `sensor_profile_written_for_version` in `SettingsRepository` remains at its pre-launch value (i.e., was not updated) after the failed write. The REQ correctly specifies the key is not updated on failure (§5.4 I/O failure contract), so the state is assertable — but the Scenario F4 Then clause should be corrected to assert the repository key state rather than logcat content. | §5.4 I/O failure contract; §7 Scenario F4 |
| F-06 | Low | **Which screen/fragment hosts the recalibration banners is not stated explicitly, creating ambiguity in test setup.** Navigation architecture in §5.1 describes `CalibrationWizardActivity` as "launched from the history screen via `ActivityResultLauncher` registered in `BearingHistoryFragment`." However, Scenarios D and E use "when the app is opened" as the When condition — not "when the user is on the Bearing History tab." A test engineer writing Scenario D cannot determine: must the instrumented test navigate to the History tab first before asserting the age banner, or is the banner shown on the compass screen as well? The REQ should explicitly state which screen(s) host each banner. If banners only appear in `BearingHistoryFragment`, the Scenario When clauses should read "when user opens Bearing History." | §5.1 Navigation architecture; §7 Scenario D, Scenario E |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | For both banners: what is the UI affordance for dismissing the banner *without* opening CalibrationWizard? Is there an X/close button, a swipe gesture, or is the only dismissal path tapping the banner body (which opens CalibrationWizard)? This determines whether dismissal and navigation are the same action or two separate code paths. |
| Q-02 | For the drift detector reset on `RESULT_OK` (Condition B): does "reset" clear only the 60-second drift timer, or does it also clear the 10-minute cooldown timestamp in `SettingsRepository`? |
| Q-03 | For `inclination_deviation_deg` in the expanded interference record: what is the display format? Unit suffix, decimal precision, and label text are needed for instrumented test assertions. |
| Q-04 | Do the recalibration banners (Condition A and Condition B) appear only on the Bearing History tab (`BearingHistoryFragment`), or also on the Modern and Luopan compass tabs? |

---

## Positive Observations

- All 9 iteration-3 findings are resolved — this is a clean and thorough response pass. The v0.4 REQ is significantly more testable than v0.3.
- The `DriftDetector` Clock dependency requirement (§5.3 Condition B timer ownership) is now expressed as a first-class testability requirement with explicit reference to the existing `FakeClock` pattern — this directly unblocks TSPEC authoring for the 60-second timer and 10-minute cooldown without real-time delays.
- The no-hysteresis timer reset rule ("any single violation of any precondition immediately resets the timer to zero") is now a precise, unambiguous, directly testable contract. A unit test can inject a single-frame precondition violation and assert timer = 0.
- The Performance NFR test methodology (§5.1) is now the most precisely specified NFR in any phase REQ: two distinct test types, distinct harnesses, and a clear CI treatment (non-blocking regression alert) are all named. This is exemplary.
- The integer floor division specification for N in the age-based banner (with examples for 31d 23h and 32d exactly) removes all boundary ambiguity — three exact unit test cases follow directly from the spec text.
- The `expected_field_ut > 0.0` guard in Scenario E Given and Condition B precondition 3 is consistent with the migration handling (MIGRATION_2_3 sets 0.0, disabling Condition B) and prevents division-by-zero in test data construction.
- Scenario E2 is now precisely correct: `InterferenceState.WARNING` matches the enum value, the confidence mapping is named inline ("the state that maps to Poor confidence via the confidence model"), and the timer behavior ("does not count while `InterferenceState` is `WARNING`") is explicitly asserted — this maps to a direct unit test on `DriftDetector` with a mocked `InterferenceState`.

---

## Recommendation

**Approved with Minor Issues**

All prior High and Medium findings are resolved. Two new Medium findings remain (F-01, F-02), but their scope is limited to the banner dismiss mechanism and the drift detector reset semantics — neither blocks understanding the core behavior, and both are resolvable with targeted prose additions in §5.3 without structural REQ changes.

> **To reach full Approved status before FSPEC authoring, the following should be addressed:**
>
> 1. **F-01 (Medium — recommended before FSPEC):** Add a concrete dismiss affordance to §5.3 Condition A and Condition B (e.g., "a close button in the banner dismisses it without opening CalibrationWizard"). Clarify whether "tapping the banner" and "dismissing the banner" are the same action or two separate affordances. Specify which action starts the 10-minute cooldown for Condition B.
>
> 2. **F-02 (Medium — recommended before FSPEC):** Define "reset" in the Condition B RESULT_OK path: state explicitly whether the 10-minute cooldown timestamp in `SettingsRepository` is cleared on successful recalibration, or only the 60-second drift timer.
>
> 3. **F-03 through F-06 (Low — may resolve in FSPEC):** Specify `inclination_deviation_deg` display format; add an in-session banner suppression scenario; correct Scenario F4 Then clause to assert `sensor_profile_written_for_version` key state instead of logcat; state explicitly which screen(s) host the recalibration banners.
