# Cross-Review: test-engineer — FSPEC

**Reviewer:** test-engineer
**Document reviewed:** docs/luopan-compass/p4-bearing-history/FSPEC-luopan-p4-bearing-history.md
**Date:** 2026-04-27
**Iteration:** 2

---

## Resolution Status of v1 Findings

| v1 ID | Severity | Status | Notes |
|-------|----------|--------|-------|
| F-01 | High | **Resolved** | SQL `LIKE` strategy committed in FSPEC-HIST-02; AT-HIST-02-A/B rewritten to assert UI-observable list content |
| F-02 | High | **Resolved** | AT-HIST-03-D reclassified as manual with documented rationale; AT-HIST-03-E added as automatable `ActivityScenario` approximation |
| F-03 | High | **Resolved** | FSPEC-CAL-INT-01 and AT-CAL-INT-01 added as required integration test covering the full wiring path |
| F-04 | Medium | **Resolved** | AT-CAL-01-D specifies `TabLayout.selectTab()` mechanism; lifecycle implication documented |
| F-05 | Medium | **Resolved** | AT-CAL-01-E specifies `ActivityScenario` close-and-relaunch pattern; new ViewModel instance stated explicitly |
| F-06 | Medium | **Resolved** | AT-HIST-01-A now asserts row-0 text content includes "Newest" (strategy-agnostic, content-based assertion) |
| F-07 | Medium | **Resolved** | AT-HIST-02-C added: keystroke at 250 ms suppresses 300 ms timer from t=0, fires at t=550 ms |
| F-08 | Medium | **Resolved** | AT-CAL-03-F added: second TRIGGERED requires new full 60-second window after first trigger |
| F-09 | Low | **Resolved** | AT-HIST-01-B and AT-HIST-01-C specify `VISIBLE`/`GONE` on the detail panel within the ViewHolder |
| F-10 | Low | **Resolved** | AT-HIST-05-C now specifies `RecyclerViewActions.actionOnItemAtPosition(0, click())` as the expand trigger |
| F-11 | Low | **Resolved** | AT-HIST-03-C specifies the DB assertion is made immediately after Snackbar B appears, before any timer elapses |
| F-12 | Low | **Resolved** | AT-SENSOR-01-E defines injectable `SensorFileWriter` interface; constructor injection; stub throws `IOException` |
| F-13 | Low | **Resolved** | AT-CAL-03-A now uses direct enum value `interferenceState = InterferenceState.WARNING`; "mock" language removed |
| F-14 | Low | **Resolved** | Edge case explicitly annotated "No acceptance test required — implementation courtesy behavior only" |
| F-15 | Low | **Resolved** | AT-CAL-02-F specifies `doesNotExist()` assertion from `ModernCompassFragment`'s perspective; mechanism documented |

All 15 High/Medium/Low findings from iteration 1 are addressed. The review below evaluates new content added in v0.2 and any residual gaps.

---

## Findings

| ID | Severity | Finding | Section ref |
|----|----------|---------|------------|
| F-01 | Medium | **AT-HIST-02-B asserts `searchFlow("nor")` is called exactly once, but a fake DAO that counts invocations is not defined.** The test specifies "Unit test (TestCoroutineDispatcher + fake DAO)" and asserts both that no call is issued within 200 ms and that `searchFlow("nor")` is called exactly once after idle. Counting invocations requires a recording fake (a stub that tracks call count and arguments). The FSPEC defines `BearingDao.searchFlow()` as a DAO interface method but does not define the corresponding `FakeBearingDao` test double. Without specifying whether this fake is a spy, a hand-written recording stub, or a MockK mock, two engineers will implement incompatible test doubles. The test must either (a) specify that a hand-written `FakeBearingDao` records all `searchFlow` calls and their arguments, consistent with the project's protocol-based fake pattern (as seen in `FakeBearingCaptureUseCase`), or (b) replace the "called exactly once" assertion with a ViewModel-state assertion (e.g., "list StateFlow emits exactly one update containing records matching 'nor'"), which is strategy-agnostic and does not require a recording fake. | FSPEC-HIST-02 AT-HIST-02-B, AT-HIST-02-C, AT-HIST-02-D |
| F-02 | Medium | **AT-CAL-INT-01 specifies "sensor frames injected into `CompassViewModel.startSensorCollection()`" but does not define the injection mechanism.** The test requires real `DriftDetector`, real `AccelerometerVarianceTracker`, and `FakeClock` wired into a real `CompassViewModel`. The critical missing detail is how sensor frames are injected into `CompassViewModel` without a real Android sensor stack. The existing `CompassViewModelTest` avoids this by testing pure logic components only. An integration test that drives `startSensorCollection()` would need either: (a) a `SensorEventSource` abstraction that the ViewModel accepts as a constructor parameter (allowing a fake frame emitter in tests), or (b) a test that calls `DriftDetector.onFrame()` directly (bypassing the ViewModel's sensor loop, which tests wiring only partially). The FSPEC must specify which injection point is used — the difference is architecturally significant and will determine whether additional production-code changes are required to support the test. | FSPEC-CAL-INT-01 AT-CAL-INT-01 |
| F-03 | Medium | **AT-HIST-01-A does not specify how "interference badge is present on exactly 5 rows" is asserted across all visible RecyclerView items.** Asserting badge presence on a specific position is straightforward (`RecyclerViewActions.actionOnItemAtPosition`), but asserting badge presence/absence across all 10 rows requires either iterating the adapter or using a custom `RecyclerViewMatcher`. The existing test infrastructure (e.g., `BearingCaptureFlowTest`) uses standard Espresso matchers but does not show a pattern for asserting per-item properties across multiple rows. The test must specify the assertion mechanism: e.g., "iterate positions 0–9; for each, check that the badge view matches the expected visibility given the seeded `interference_flag` value" or "use a custom `RecyclerViewMatcher` that counts views with a given ID and expected visibility." Without this, an engineer asserting "badge exists somewhere" rather than "on exactly 5 rows" would not be caught by the test. | FSPEC-HIST-01 AT-HIST-01-A |
| F-04 | Medium | **AT-CAL-01-F specifies "spy on CompassViewModel" but the project's test infrastructure uses protocol-based fakes, not spies.** The test is classified "Instrumented test (spy on CompassViewModel)" and asserts "`loadCalibrationAge()` was called." Spying on a real `CompassViewModel` in an instrumented test requires MockK's `spyk()` (or equivalent), which wraps the real object. There is no evidence of MockK spy usage in the existing test suite — `CompassViewModelTest` and `BearingCaptureFlowTest` use hand-written fakes and real objects. Using `spyk()` on an Android `ViewModel` in an instrumented test is fragile due to ViewModel lifecycle management. A more robust alternative: verify the side-effect of `loadCalibrationAge()` (i.e., `CompassUiState.calibration_age_days` is updated to a fresh value after RESULT_OK) rather than asserting the call count. The test must either specify that MockK is an accepted dependency for instrumented tests in this project, or replace the spy assertion with a state-observable assertion. | FSPEC-CAL-01 AT-CAL-01-F; FSPEC-NAV-01 AT-NAV-01-C |
| F-05 | Low | **AT-HIST-02-D asserts "`getAllFlow()` emission is received immediately" but does not define what "immediately" means in a test with coroutines.** In a `TestCoroutineDispatcher` / `runTest` context, "immediately" means within the same dispatch cycle — no `advanceTimeBy()` call is needed. But the test must specify whether it uses `TestScope.advanceUntilIdle()` or inspects the first emitted value directly. Without this, an engineer might add an unnecessary `advanceTimeBy(300)` (treating the clear as if it still debounces), producing a test that passes but doesn't actually verify the immediate-restore property. The test should explicitly state: "the list StateFlow update is received without advancing the test clock." | FSPEC-HIST-02 AT-HIST-02-D |
| F-06 | Low | **FSPEC-CAL-03 does not specify an acceptance test for the `COUNTING → TRIGGERED` transition when the deviation is exactly 10% (the boundary case of the `> 0.10` threshold).** AT-CAL-03-B tests 12% deviation (clearly above threshold). AT-CAL-03-C tests 9.9% (clearly below). The exact-boundary case — `|measured − expected| / expected == 0.10` — is not covered. At exactly 10%, the condition `> 0.10` is false; the detector must NOT trigger. This is a common off-by-one bug source and should be an explicit test case. | FSPEC-CAL-03 Drift Threshold Formula; AT-CAL-03-B, AT-CAL-03-C |
| F-07 | Low | **AT-SENSOR-01-B asserts "the `written_at_iso8601` timestamp is the same as prior launch" but does not specify how the prior-launch timestamp is captured for comparison.** In an instrumented test, the test cannot read the file written during a previous test run unless it explicitly saves the value. The test must specify: "Read `written_at_iso8601` from the file after the first launch. Run the app a second time (simulate via a second `SensorCapabilityLogger` invocation with the same `sensorProfileWrittenForVersion = VERSION_CODE`). Read the file again. Assert that `written_at_iso8601` is unchanged." The current phrasing "timestamp same as prior launch" implies cross-run state that an automated test cannot access unless it is stored within the same test method. | FSPEC-SENSOR-01 AT-SENSOR-01-B |
| F-08 | Low | **The `SearchView` visibility rule for State A (zero records) is specified in FSPEC-HIST-04 but has no corresponding acceptance test.** FSPEC-HIST-04 Business Rules states "Search bar in State A: `View.GONE` — hidden entirely." AT-HIST-04-A asserts the empty-state illustration and message are visible and RecyclerView is `GONE`, but does not assert that the search bar is also `GONE`. This is a testable property (a single `onView(withId(R.id.search_bar)).check(matches(not(isDisplayed())))` assertion) and should be included in AT-HIST-04-A or added as a separate AT. | FSPEC-HIST-04 Business Rules "Search bar in State A"; AT-HIST-04-A |
| F-09 | Low | **AT-CAL-02-C does not specify the assertion mechanism for "drift detector timer = 0."** The test asserts three outcomes: `driftBannerState = HIDDEN`, drift detector timer = 0, and `driftCooldownTimestampMs = 0L`. Asserting `driftBannerState` via the StateFlow is straightforward. Asserting `driftCooldownTimestampMs` via `SettingsRepository` is concrete. But asserting "drift detector timer = 0" requires either (a) a `DriftDetector.timerMs` property exposed for testing, or (b) a behavioral assertion (e.g., confirm that a new 60-second window causes TRIGGERED again — but this is tested separately in AT-CAL-03-F). The test must specify the observable — either expose an `@VisibleForTesting` timer property or replace the assertion with a behavioral proxy that does not require internal state inspection. | FSPEC-CAL-02 AT-CAL-02-C |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | For AT-HIST-02-B and AT-HIST-02-C: does the project intend to introduce MockK (or another spy/recording framework) for these tests, or should all `searchFlow()` call-count assertions be replaced with ViewModel-state observable assertions? The existing test suite (seen in `CompassViewModelTest`, `BearingCaptureFlowTest`) uses only hand-written fakes and real objects — no spy framework is in use. |
| Q-02 | For AT-CAL-INT-01: is a `SensorEventSource` abstraction or equivalent injection point planned for `CompassViewModel.startSensorCollection()`, or should the integration test drive `DriftDetector.onFrame()` directly (bypassing the full sensor loop)? The answer determines whether the test is a true integration test of the wiring or a partial wiring test. |
| Q-03 | For AT-CAL-01-F and AT-NAV-01-C: is MockK or any spy framework an accepted dependency for instrumented tests in this project? If not, the "spy on CompassViewModel" mechanism must be replaced with a side-effect-observable assertion. |
| Q-04 | For AT-CAL-02-C: will `DriftDetector` expose an `@VisibleForTesting` timer property, or should the timer-reset assertion be replaced by a behavioral proxy test (verifying that a new 60-second window is required before re-triggering)? |

---

## Assessment of New Content Added in v0.2

### FSPEC-CAL-INT-01 (new integration test section)

This is the most important addition in v0.2. It correctly identifies the `AccelerometerVarianceTracker → DriftDetector.onFrame() → CompassViewModel → driftBannerState` wiring boundary. The test components are correctly specified (real `DriftDetector`, real `AccelerometerVarianceTracker`, `FakeClock`, fake `SettingsRepository`). The unresolved gap is the sensor frame injection mechanism (F-02 above).

### AT-HIST-03-D / AT-HIST-03-E split

The reclassification of AT-HIST-03-D as manual with a documented reason is correct. The manual procedure is clear and repeatable. AT-HIST-03-E as the automated approximation using `ActivityScenario` close-and-reopen is appropriately scoped — it tests the persistent DB state (the true invariant) without requiring process-kill tooling.

### AT-CAL-03-F (post-trigger IDLE reset)

Well-specified. The test uses FakeClock to drive time deterministically, distinguishes between "timer at 0 after trigger" and "59 additional seconds still insufficient," and specifies the exact boundary (t ≥ 121 s). This is a correct and automatable test.

### AT-HIST-02-C (debounce timer restart)

The new test correctly targets the "timer restarts on keystroke" property by specifying an interleaved keystroke at t=250 ms. The assertion that no `searchFlow()` fires at t=300 ms and that it fires at t=550 ms is precise. The residual gap (F-01) is the fake DAO specification needed to make the invocation assertion concrete.

### AT-CAL-01-D / AT-CAL-01-E (lifecycle mechanism)

Both are now precisely specified. AT-CAL-01-D uses `TabLayout.selectTab()` — the correct mechanism to simulate Fragment `onStop`/`onStart` without Activity or ViewModel destruction. AT-CAL-01-E uses separate `ActivityScenario` instances to simulate new ViewModel creation. Both are automatable without non-standard tooling.

### FSPEC-SENSOR-01 — Injectable `SensorFileWriter`

The injectable interface pattern for AT-SENSOR-01-E is a good design decision. It follows the same constructor-injection pattern used for `Clock` in `DriftDetector`. The interface definition (`interface SensorFileWriter { fun write(content: String) }`) is simple and sufficient.

### AT-CAL-01-G (both banners simultaneously)

Correctly classified as instrumented and correctly specifies the structural assertion ("distinct, non-merged UI elements"). This is the right level of specificity — it prevents an implementation that renders a single merged banner from passing.

---

## Positive Observations

- **All 15 v1 findings are addressed.** The resolution table in the FSPEC's Open Questions section maps every finding to a concrete change. This is the correct format for tracking cross-review closure.
- **The SQL `LIKE` strategy commitment in FSPEC-HIST-02 resolves the highest-impact v1 finding cleanly.** Committing to one filtering path (SQL, not in-memory) plus specifying the exact DAO query signature and `Dispatchers.IO` execution context makes the search behavior fully testable at the unit level.
- **FSPEC-CAL-INT-01 is appropriately scoped.** Rather than trying to cover the full UI stack, it tests exactly the boundary that unit tests of each component in isolation cannot cover: `DriftDetector` → `CompassViewModel` wiring. The component list (real `DriftDetector`, real `AccelerometerVarianceTracker`, `FakeClock`, fake `SettingsRepository`) is precise.
- **The `AccelerometerVarianceTracker` / `NoiseVarianceTracker` distinction is explicitly documented.** The FSPEC calls out "They must not be conflated" — a critical note that prevents engineers from reusing the wrong tracker. This kind of explicit disambiguation is high-value in a spec.
- **AT-CAL-01-C (floor division unit test) is a strong testability pattern.** Isolating the `computeCalibrationAgeDays(elapsedMs)` pure function into its own unit test (rather than embedding the boundary check in a larger fragment test) follows the same pattern used in `CacheAgeLabelFormatter.format()` tests in `CompassViewModelTest`.
- **The drift threshold formula specification is unambiguous:** `|measured − expected| / expected > 0.10`, evaluated only after the 60-second timer elapses, with the division-by-zero guard explicitly tied to precondition 3 (`expectedFieldUt > 0.0`). AT-CAL-03-D verifies the guard; AT-CAL-03-B verifies the trigger.
- **AT-HIST-05-D (negative inclination deviation) targets the correct edge case.** Truncation toward zero (not floor) for negative values (`-2.3 → "-2°"`) is the right behavior and differs from simple floor (`-3`). Specifying a unit test for this single-value transformation prevents a common sign-handling bug.
- **The `DriftBannerState.HIDDEN` initial value is explicitly stated.** This prevents a race condition where the banner flickers visible on Fragment start before the ViewModel emits its first value.

---

## Recommendation

**Approved with Minor Issues**

> All High and Medium findings from iteration 1 are resolved. Four new Medium findings (F-01 through F-04) are present; none block authoring of the majority of the test suite, but F-01 (fake DAO specification) and F-02 (sensor frame injection mechanism) should be resolved before TSPEC authoring to avoid rework.
>
> Specific items to address before TSPEC:
>
> 1. **F-01 (Medium):** Specify the `FakeBearingDao` test double pattern for AT-HIST-02-B, AT-HIST-02-C, and AT-HIST-02-D. Either define a hand-written recording stub consistent with the project's fake pattern, or replace invocation-count assertions with ViewModel-state observable assertions.
>
> 2. **F-02 (Medium):** Specify the sensor frame injection mechanism for AT-CAL-INT-01. If `startSensorCollection()` cannot accept injected frames without a `SensorEventSource` abstraction, define that abstraction as a required code change; if the integration test drives `DriftDetector.onFrame()` directly, state this explicitly and document what wiring is and is not covered.
>
> 3. **F-03 (Medium):** Specify the assertion mechanism for "badge present on exactly 5 rows" in AT-HIST-01-A — either a custom RecyclerView matcher or an explicit per-position loop.
>
> 4. **F-04 (Medium):** Replace "spy on CompassViewModel" in AT-CAL-01-F and AT-NAV-01-C with a state-observable assertion (e.g., `CompassUiState.calibration_age_days` updated to a fresh value) unless MockK spy is confirmed as an accepted instrumented-test dependency.
>
> 5. **F-05 through F-09 (Low):** Address before test implementation begins to prevent implementation ambiguity in AT-HIST-02-D, AT-CAL-03 boundary coverage, AT-SENSOR-01-B cross-run state, AT-HIST-04-A search-bar visibility, and AT-CAL-02-C timer-reset observability.
