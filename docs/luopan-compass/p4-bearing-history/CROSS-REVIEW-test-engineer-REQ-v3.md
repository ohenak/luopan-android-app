# Cross-Review: test-engineer — REQ

**Reviewer:** test-engineer
**Document reviewed:** docs/luopan-compass/p4-bearing-history/REQ-luopan-p4-bearing-history.md
**Date:** 2026-04-27
**Iteration:** 3

---

## Summary of Iteration 2 Resolution

All 14 iteration-1 findings were confirmed resolved in v0.2-draft. Iteration 2 raised 8 findings (F-01 High, F-02–F-04 Medium, F-05–F-08 Low). The document submitted for this v3 review carries the version header `0.2-draft` — it is structurally identical to the document reviewed in iteration 2. Cross-referencing the text of each iteration-2 finding against the current document:

| Iter-2 ID | Severity | Status |
|-----------|----------|--------|
| F-01 — Scenario E2 uses undefined `InterferenceState.POOR` | High | **NOT RESOLVED** — line still reads "InterferenceState is POOR per REQ-DETECT-01" |
| F-02 — Timer reset rule on precondition oscillation | Medium | **NOT RESOLVED** — no hysteresis or minimum-violation-duration rule added |
| F-03 — Clock interface dependency for 10-minute cooldown | Medium | **NOT RESOLVED** — no Clock dependency requirement added |
| F-04 — Performance NFR test methodology unspecified | Medium | **NOT RESOLVED** — Macrobenchmark vs. manual profiler distinction still absent |
| F-05 — Scenario E lacks `expected_field_ut > 0.0` guard | Low | **NOT RESOLVED** — Scenario E and Condition B still read "is present" without the positive-value guard |
| F-06 — Debounce restart rule not stated | Low | **NOT RESOLVED** — no explicit statement that the 300 ms window restarts on each keystroke |
| F-07 — Scenario F asserts non-automatable file-manager inaccessibility | Low | **NOT RESOLVED** — assertion still present in Scenario F |
| F-08 — Day count N: floor vs. round not specified | Low | **NOT RESOLVED** — no integer floor/round statement added |

One new High finding (F-09) is raised for the first time in this iteration, identified by cross-checking the stated confidence-cap rule against the live `ConfidenceModel` implementation.

---

## Findings

| ID | Severity | Finding | Section ref |
|----|----------|---------|------------|
| F-01 | High | **Scenario E2 references a non-existent `InterferenceState` value ("POOR") — the acceptance test will not compile.** The `InterferenceState` enum defines three values: `CLEAR`, `MODERATE`, and `WARNING`. There is no `POOR` value. Scenario E2 Given states: "active interference from a proximate source has set confidence to Poor (InterferenceState is POOR per REQ-DETECT-01)." Any automated acceptance test that instantiates `InterferenceState.POOR` will fail to compile. The suppression precondition in §5.3 Condition B correctly uses "CLEAR or MODERATE (i.e., NOT WARNING)" — the scenario must match it. Required correction: "Active `InterferenceState` is `WARNING` (the state that maps to Poor confidence via the confidence model)." This finding was raised as High in iteration 2 and is unresolved. | §7 Scenario E2; §5.3 Condition B precondition 2 |
| F-09 | High | **Scenario D's stated confidence-cap rule contradicts the `ConfidenceModel` implementation — the scenario acceptance test will assert the wrong outcome.** §5.3 Condition A and Scenario D both state: "overall confidence is capped at MODERATE if all other dimensions are GOOD (min over all dimensions: POOR from cal_age → MODERATE cap from the scoring formula)." This is incorrect. `ConfidenceModel.normalCompute()` takes `minOf(scores)` across all five dimensions. When `cal_age > 30 days`, `scoreCalibrationAge` returns `ConfidenceScore.POOR` (numericValue = 1). `minScore = 1` maps to `OverallConfidence.POOR` — not MODERATE. The only mechanism that caps HIGH → MODERATE is the `!hasGyroscope` branch and the `extremeLatitudeActive` flag; neither applies here. A tester writing an assertion based on the REQ description ("capped at MODERATE") will write a test that passes only if the implementation is wrong. Either (a) the REQ must be corrected to read "overall confidence is POOR," or (b) if MODERATE is the intended product behavior, the `ConfidenceModel` scoring formula must be changed and the REQ must specify a different capping rule (e.g., a separate cal_age override branch). This mismatch must be resolved before FSPEC authoring. | §5.3 Condition A Confidence impact; §7 Scenario D; `ConfidenceModel.normalCompute()` lines 64–68; `ConfidenceScore` GOOD(3)/MODERATE(2)/POOR(1) |
| F-02 | Medium | **The drift detection timer reset rule on precondition oscillation is unspecified — a near-threshold regression test cannot be written.** §5.3 Condition B states the timer "resets to zero if any precondition is violated." At typical 50 Hz sensor update rates, `InterferenceState` and accelerometer variance are re-evaluated on every sensor event. If a precondition is violated for one or two frames (e.g., a brief accelerometer spike pushes variance above 0.01 during an otherwise stable 58-second window), two materially different behaviors are possible: (a) any single-frame violation resets the 60-second timer to zero; (b) violations shorter than some minimum window are ignored (hysteresis). These produce different test cases and different regression tests for near-threshold behavior. The REQ must explicitly state one of these semantics. This finding was raised as Medium in iteration 2 and is unresolved. | §5.3 Condition B timer; §7 Scenario E |
| F-03 | Medium | **The 10-minute drift cooldown is untestable without a `Clock` interface dependency, but the REQ does not require one.** §5.3 states the drift dismissal cooldown is 10 minutes "stored in ViewModel." A CI unit test cannot wait 10 real minutes. The codebase already provides a `Clock` interface (`com.luopan.compass.util.Clock`) and `FakeClock` (`com.luopan.compass.util.FakeClock`) used in `ConfidenceModel` and `CalibrationRepositoryTest`. The REQ must explicitly require that drift detection and cooldown logic accepts a `Clock` dependency — this is a testability requirement. Without it, FSPEC and TSPEC authors may use `System.currentTimeMillis()` directly, making the 10-minute cooldown regression permanently untestable in unit tests. This finding was raised as Medium in iteration 2 and is unresolved. | §5.3 Condition B dismissal; §7 Scenario E |
| F-04 | Medium | **Performance NFR for frame time has no specified test methodology — it cannot gate CI without knowing which harness applies.** §5.1 requires frame time ≤ 16 ms during fling scroll with 500 records on a mid-range device. The codebase has an existing macrobenchmark workflow (`.github/workflows/macrobenchmark.yml`) with `FrameTimingMetric`-capable tooling. The REQ must state whether this NFR is verified by: (a) a Macrobenchmark instrumented test with `FrameTimingMetric` gated in the macrobenchmark CI workflow, or (b) a manual Systrace/profiler verification that does not block automated CI. The same ambiguity applies to the initial-load NFR (500 ms for 500 records): the test harness — unit test with a fake Room DAO, or an instrumented Room test — is unspecified. Without this, engineering cannot determine whether a failing assertion should block a PR merge. This finding was raised as Medium in iteration 2 and is unresolved. | §5.1 Performance NFR; §8 Risk P4-R2 |
| F-05 | Low | **Scenario E does not require `expected_field_ut > 0.0`, creating a latent division-by-zero in test data.** §5.3 states MIGRATION_2_3 sets `expected_field_ut = 0.0` for existing records, which "disables Condition B triggering." The drift formula is `|measured − expected| / expected`. With `expected_field_ut = 0.0`, this produces division by zero. Scenario E's Given reads "A CalibrationRecord with `expected_field_ut` is present" without a positive-value guard. Both the Scenario E Given and the Condition B preconditions in §5.3 must read "A CalibrationRecord with `expected_field_ut > 0.0` is present." This is also a precondition for the formula to be evaluable in test code. This finding was raised as Low in iteration 2 and is unresolved. | §5.3 Condition B trigger formula; §7 Scenario E |
| F-06 | Low | **Search debounce restart rule is implied but not stated — the debounce unit test cannot distinguish correct from incorrect implementations.** §5.1 specifies "trigger search after 300 ms of no input." Standard debounce semantics restart the 300 ms window on each keystroke (i.e., typing at 100 ms intervals never fires a search until 300 ms of silence). An alternative interpretation — timer starts on first keystroke and fires exactly once — would also satisfy the literal spec text. The codebase uses `Flow.debounce()` in similar contexts, which has restart-on-emission semantics, but the REQ must state this explicitly. An automated test that types characters at 100 ms intervals and asserts exactly one search emission requires knowing which semantic applies. This finding was raised as Low in iteration 2 and is unresolved. | §5.1 Search behaviour |
| F-07 | Low | **Scenario F asserts "NOT accessible via the standard Android file manager on non-rooted devices" — this cannot be automated and should be removed from the acceptance test.** The REQ correctly designates the network non-transmission guarantee as a design-level property enforced by a lint rule rather than a test assertion. The same treatment must apply to file manager accessibility: no automated instrumented test can assert that a file at `Context.getFilesDir()` is invisible to the stock Files app — this is an Android platform sandboxing property, not an application behavior. The assertion should be removed from Scenario F and replaced with: "The file is written to `Context.getFilesDir()` (internal app storage)." The inaccessibility guarantee is implicit in Android's storage model. This finding was raised as Low in iteration 2 and is unresolved. | §7 Scenario F |
| F-08 | Low | **Banner day-count N computation rule (floor vs. round) is not specified — boundary unit tests cannot determine expected output.** §5.3 Condition A specifies the banner as "Your calibration is [N] days old — consider recalibrating" with N computed dynamically. Scenario D uses N = 31 as a concrete example. The REQ does not state whether N is computed by integer floor division (e.g., 31 days 23 hours → "31") or by rounding (→ "32"). These produce different expected outputs at the ±1-day boundary and different assertions in the banner-text unit test. The REQ must specify: "N is computed as integer floor division of elapsed milliseconds by 86,400,000." This finding was raised as Low in iteration 2 and is unresolved. | §5.3 Condition A; §7 Scenario D |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | For Condition B timer: if `InterferenceState` oscillates between `CLEAR` and `WARNING` for a few frames during an otherwise stable drift window, does any single-frame `WARNING` reset the timer to zero, or is there a minimum violation duration that must be sustained before the timer resets? |
| Q-02 | For the 10-minute drift cooldown: will the drift detection component accept a `Clock` interface dependency (consistent with the existing `FakeClock` pattern) to enable unit testing without real-time delays? |
| Q-03 | For the confidence-cap rule in Scenario D: the current `ConfidenceModel` implementation produces `OverallConfidence.POOR` (not MODERATE) when `cal_age > 30 days` and all other dimensions are GOOD. Is the intended product behavior POOR or MODERATE? If MODERATE is intended, does the scoring formula need to change? |
| Q-04 | For the banner day count N: is N computed as integer floor division (31 days 23 hours → "31") or rounded (→ "32")? |
| Q-05 | For the performance NFR (frame time ≤ 16 ms): is this gated via a Macrobenchmark `FrameTimingMetric` in the existing macrobenchmark CI workflow, or is it a manual profiler check that does not block automated CI? |

---

## Positive Observations

- The swipe-to-delete state machine (Scenarios C, C2, C3) remains exemplary: commit-on-swipe semantics, single-active-undo, and process-kill behavior are all concrete and map to isolated state machine unit tests.
- The search behavior block in §5.1 remains fully specified for the resolved dimensions: case-insensitive substring, 300 ms debounce trigger, zero-match message text, and empty-query restoration are all concrete values.
- `sensor_profile.json` schema definition in §5.4 is precise: field names, types, value sources, units, and pretty-print format are all named. A JSON schema validation test can be written directly from this spec without additional clarification.
- Scenario D2 (exactly 30-day-old calibration shows no banner) correctly tests the strict `> 30` boundary and remains unambiguous.
- The REQ-DETECT-05 prohibitive language ("Badge MUST NOT appear on records with `interference_flag=false`") correctly maps to a direct negative test assertion and has not regressed.
- The two-banner coexistence rule (age-based and drift banners may show simultaneously as separate banners) is explicit and enables an integration test with both conditions active simultaneously.
- The `sensor_profile_written_for_version` version-code comparison mechanism is concrete and maps cleanly to a `SettingsRepository` unit test with injected version codes.

---

## Recommendation

**Need Attention**

> Two High findings are present (F-01 and F-09). Both must be corrected before FSPEC authoring begins.
>
> 1. **F-01 (mandatory — compile blocker):** Correct Scenario E2 Given to use `InterferenceState.WARNING` instead of the non-existent "POOR" value. §5.3 Condition B already uses the correct framing ("CLEAR or MODERATE, i.e., NOT WARNING") — the scenario must match it.
>
> 2. **F-09 (mandatory — spec-vs-implementation contradiction):** Resolve the conflict between the stated confidence-cap rule ("capped at MODERATE") and the `ConfidenceModel` implementation (produces POOR when `cal_age` scores POOR via the min-score formula). Either update the REQ to reflect the actual behavior (POOR), or change the `ConfidenceModel` and update the REQ with the corrected rule. This must be settled before FSPEC authoring so that acceptance tests are written against the correct expected value.
>
> 3. **F-02 and F-03 (recommended before FSPEC):** Specify the timer oscillation/reset rule for Condition B, and require a `Clock` interface dependency for the cooldown logic.
>
> 4. **F-04 (recommended before TSPEC):** Specify whether the 16 ms frame-time NFR is gated in automated CI via the existing macrobenchmark workflow or is a manual verification only.
>
> 5. **F-05 through F-08 (Low — must resolve before TSPEC):** Add `expected_field_ut > 0.0` guard to Scenario E and Condition B; state debounce restart semantics; remove the non-automatable file-manager assertion from Scenario F; specify integer floor division for N in the banner day-count.
