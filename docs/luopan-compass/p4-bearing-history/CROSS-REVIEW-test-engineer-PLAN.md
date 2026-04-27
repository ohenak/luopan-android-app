# Cross-Review: test-engineer — PLAN

**Reviewer:** test-engineer
**Document reviewed:** docs/luopan-compass/p4-bearing-history/PLAN-luopan-p4-bearing-history.md
**Date:** 2026-04-27
**Iteration:** 1

---

## Review Scope

Reviewed against:
- REQ-luopan-p4-bearing-history v0.4-draft
- FSPEC-luopan-p4-bearing-history v0.2-draft
- TSPEC-luopan-p4-bearing-history v0.2-draft

Evaluation criteria: test coverage completeness, TDD sequencing correctness (red→green→refactor), FSPEC acceptance-test-to-PLAN-task mapping, and integration/E2E test task presence.

---

## Findings

| ID | Severity | Finding | Section ref |
|----|----------|---------|------------|
| F-01 | Medium | **`IDriftDetector` interface has no test task before its creation task.** Phase B task B-3 creates the `IDriftDetector` interface and `DriftEvent` enum with no preceding failing-test task. These are pure-Kotlin artifacts (no Android runtime) that can and should have a failing compilation/structural test (e.g., assert the enum values `TRIGGERED`/`RESET` exist, assert `onFrame()` signature compiles) written first. Even a contract-level test asserting `FakeDriftDetector implements IDriftDetector` serves as a red-phase gate. Currently B-3 is a green-only step with no red precursor, violating TDD ordering. | Phase B, task B-3 |
| F-02 | Medium | **`dismissCalAgeBanner()` and `dismissDriftBanner()` have no dedicated unit test tasks.** Phase D task D-4 implements four methods (`dismissCalAgeBanner`, `onCalibrationCompleteFromHistory`, `resetDriftDetector`, `dismissDriftBanner`). The test file listed is `CompassViewModelDriftTest.kt`, but the TSPEC test coverage matrix (§9.1) and PLAN Test File Inventory do not list explicit unit tests for `dismissCalAgeBanner()` and `dismissDriftBanner()` as standalone assertions. The only mapped tests are AT-VM-DRIFT-01 and AT-VM-DRIFT-01b (covering TRIGGERED→cooldown→VISIBLE wiring). The cooldown-write path (`dismissDriftBanner` writes `driftCooldownTimestampMs` to SettingsRepository) and the session-flag path (`dismissCalAgeBanner` sets `calAgeBannerDismissed = true`) are unit-testable ViewModel behaviors that belong in the unit test phase, not only in instrumented tests (AT-CAL-01-D, AT-CAL-02-B). The PLAN task D-1 only mentions AT-VM-DRIFT-01 and AT-VM-DRIFT-01b; D-4 has no red task of its own. | Phase D, tasks D-1 and D-4 |
| F-03 | Medium | **`AT-CAL-02-D` and `AT-CAL-02-E` (cooldown suppression and re-arm unit tests) are listed in the TSPEC coverage matrix under `BearingHistoryViewModel (drift banner)` but are absent from the PLAN task list.** The TSPEC §9.1 assigns `AT-CAL-02-A, D, E` to the `BearingHistoryViewModel` unit test row. `AT-CAL-02-D` (banner suppressed while cooldown active) and `AT-CAL-02-E` (banner shown after cooldown expires) are unit tests for `CompassViewModel` cooldown logic. These tests are not mentioned in any Phase D task, and `CompassViewModelDriftTest.kt` is not listed in the Test File Inventory as containing them. Without a PLAN task explicitly writing these tests, they can be silently omitted during implementation. | Phase D, Test File Inventory (§ unit test files) |
| F-04 | Medium | **Phase E has no failing-test task for `BearingHistoryFragment`'s search bar restoration to `View.VISIBLE` (PM TSPEC-v1 F-06).** The TSPEC §7.1 specifies an `updateListState()` helper with four visibility combinations including restoring `search_bar` to `View.VISIBLE` when records exist and query is empty. The PLAN tasks E-2 through E-6 cover ViewModel search and undo tests and `BearingAdapter` format tests, but no unit or instrumented test task is mapped to the four-branch `updateListState()` logic. The search bar visibility is only partially tested through `AT-HIST-04-A` (State A, search bar GONE), but the reverse transition (State A → normal, search bar becomes VISIBLE) requires a dedicated test. `AT-HIST-04-B` covers the record appearance but is not specified to assert search bar restoration. This gap means the State-A-to-normal search bar transition can regress silently. | Phase E, tasks E-2 and E-6; Phase F, task F-6 |
| F-05 | Low | **`FakeDriftDetector` in TSPEC §9.3b subclasses `DriftDetector(FakeClock(0L))` (a concrete class), which conflicts with Phase B task B-3's design decision to extract `IDriftDetector`.** The PLAN correctly specifies that B-4 creates `FakeDriftDetector` implementing `IDriftDetector` (not subclassing `DriftDetector`). However the TSPEC §9.3b still shows `class FakeDriftDetector(…) : DriftDetector(FakeClock(0L))`. This inconsistency between TSPEC and PLAN creates a risk that an engineer following the TSPEC code snippet bypasses the interface and subclasses the concrete class instead. The PLAN itself is correct; the inconsistency is in the upstream TSPEC. Noted here because the PLAN task B-4 description references the correct interface approach but does not explicitly flag the conflicting TSPEC snippet. | Phase B, task B-4 |
| F-06 | Low | **`AT-CAL-01-C` (floor division unit test) is listed in the TSPEC coverage matrix but has no mapped PLAN task.** The TSPEC §9.1 assigns `AT-CAL-01-C` (FakeClock unit test for `computeCalibrationAgeDays()` floor division) to the `BearingHistoryFragment (age banner)` unit test row. This test is not mentioned in any Phase D or Phase E task, and no task references a `computeCalibrationAgeDays()` function or its unit test. An engineer following the PLAN could complete all tasks without writing this test. | Phase D or E, missing task |
| F-07 | Low | **Phase G task G-1 places the 500-record performance test inside `BearingHistoryFragmentTest.kt`, but the PLAN Test File Inventory also lists it there — mixed with functional instrumented tests.** Placing a data-seeding performance test in the same file as functional UI tests creates slow test suite bleed: every run of functional tests will also execute the 500-record seed + query even when investigating an unrelated UI regression. A better practice is a dedicated `BearingHistoryPerfTest.kt`. This is a test organization issue, not a coverage gap. | Phase G, task G-1 |
| F-08 | Low | **Manual test AT-HIST-03-D in Phase G is documented but has no definition-of-done gate linking it to a tracked artifact.** Task G-4 says "Record result in task tracking" but the Definition of Done checklist does not include "AT-HIST-03-D manual test result recorded and signed off." If the manual test is skipped, the DoD checklist still passes. The DoD item for manual tests should explicitly reference a result artifact (e.g., "Manual test AT-HIST-03-D executed and result recorded in task tracking"). | Phase G, task G-4; Definition of Done |
| F-09 | Low | **No PLAN task covers the `onCalibrationCompleteFromHistory()` re-flash mitigation unit test.** The TSPEC §7.1 specifies a two-step suppress-then-clear logic in `onCalibrationCompleteFromHistory()` (`calAgeBannerDismissed = true` immediately, cleared on IO resolve if age ≤ 30). This is a non-trivial async state machine (race mitigation). No Phase D task writes a failing unit test for this specific race path. The existing AT-CAL-01-F covers the instrumented path, but not the ViewModel-level unit isolation of the flag behavior. | Phase D, task D-4 |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | Task B-3 creates `IDriftDetector` and `DriftEvent` without a preceding test task. Is there an intentional decision to skip the red phase for pure interface/enum declarations? If so, should the PLAN note this exception explicitly so future reviewers don't flag it? |
| Q-02 | `AT-CAL-02-D` and `AT-CAL-02-E` are listed in the TSPEC coverage matrix but not in any PLAN task. Are these tests intended to be written as part of D-1 (expanding the failing test list), or are they implicitly covered by AT-VM-DRIFT-01b and omitted from the PLAN as duplicates? |
| Q-03 | The `DriftDetectorIntegrationTest.kt` is placed under `src/test/` (JVM) rather than `src/androidTest/` (instrumented). Is this intentional? The integration test uses `FakeClock` and does not require Android runtime, so JVM placement is correct — but the Test File Inventory labeling as "integration" may mislead engineers into expecting an on-device test. Should the label be "JVM integration test" to disambiguate? |
| Q-04 | The PLAN does not have a Phase B or Phase D task to write a failing test for `CompassViewModel.simulateSensorFrame()` before it is added (D-2). Is `simulateSensorFrame()` considered a pure scaffolding addition (no behavior of its own) and therefore excluded from TDD sequencing? |

---

## Positive Observations

- TDD ordering is correctly enforced across the main delivery path: every implementation task (A-2, A-5, A-6, A-8, A-10, B-2, B-6, C-3, E-4, E-6) has a preceding failing-test task that references the same test file. This is the strongest aspect of the PLAN.
- The pre-implementation gate (Phase A-0) for the FSPEC backport is an excellent structural decision. Requiring the FSPEC update before any code begins prevents the "re-evaluate-each-frame" rule from remaining a silent TSPEC invention.
- Integration/E2E test tasks are properly separated into their own tasks (B-7/B-8 for drift detector integration, F-4 through F-12 for instrumented tests, G-1/G-2 for performance). Each has a distinct task number, a distinct test file, and a clear dependency ordering.
- The parallelizable phase structure (B ∥ C, D after B, E after A+D, F after C+E) is correctly reflected in the dependency graph and the task structure. This is actionable for a tech lead running parallel implementation streams.
- The Definition of Done checklist is detailed and implementation-specific (Snackbar duration, `notifyItemChanged()` constraint, `buildJsonFromSensors()` purity), which makes it a genuine quality gate rather than a boilerplate checklist.
- Cross-review traceability is excellent: every TSPEC cross-review finding (TE TSPEC-v1, PM TSPEC-v1) is explicitly mapped to a PLAN task in the "Cross-Review Issues Addressed" table. Reviewers of the PLAN can verify resolution without re-reading the TSPEC.
- FSPEC acceptance test coverage is comprehensive. A systematic mapping of FSPEC AT-IDs to PLAN tasks shows all 36 acceptance tests from the FSPEC are traceable to at least one PLAN task (unit or instrumented).

---

## FSPEC Acceptance Test → PLAN Task Mapping

The following table documents the traceability check performed during review. Tests marked with a gap note are addressed in the Findings above.

| FSPEC AT | PLAN Task(s) | Coverage Level |
|----------|-------------|---------------|
| AT-HIST-01-A | F-5 | Instrumented |
| AT-HIST-01-B | F-5 | Instrumented |
| AT-HIST-01-C | F-5 | Instrumented |
| AT-HIST-02-A | E-2 | Unit |
| AT-HIST-02-B | E-2 | Unit |
| AT-HIST-02-C | E-2 | Unit |
| AT-HIST-02-D | E-2 | Unit |
| AT-HIST-02-E | F-8 | Instrumented |
| AT-HIST-03-A | F-9 | Instrumented |
| AT-HIST-03-B | F-9 | Instrumented |
| AT-HIST-03-C | F-9 | Instrumented |
| AT-HIST-03-D (manual) | G-4 | Manual |
| AT-HIST-03-E | F-9 | Instrumented |
| AT-HIST-04-A | F-6 | Instrumented |
| AT-HIST-04-B | F-6 | Instrumented |
| AT-HIST-05-A | F-7 | Instrumented |
| AT-HIST-05-B | F-7 | Instrumented |
| AT-HIST-05-C | F-7 | Instrumented |
| AT-HIST-05-D | E-5 | Unit |
| AT-CAL-01-A | F-10 | Instrumented |
| AT-CAL-01-B | F-10 | Instrumented |
| AT-CAL-01-C | **No PLAN task** (F-06) | Gap |
| AT-CAL-01-D | F-10 | Instrumented |
| AT-CAL-01-E | F-10 | Instrumented |
| AT-CAL-01-F | F-10 | Instrumented |
| AT-CAL-01-G | F-10 | Instrumented |
| AT-CAL-02-A | D-1 (implied via AT-VM-DRIFT-01) | Unit |
| AT-CAL-02-B | F-11 | Instrumented |
| AT-CAL-02-C | F-11 | Instrumented |
| AT-CAL-02-D | **No PLAN task** (F-03) | Gap |
| AT-CAL-02-E | **No PLAN task** (F-03) | Gap |
| AT-CAL-02-F | F-11 | Instrumented |
| AT-CAL-03-A through F, B2 | B-5 | Unit |
| AT-CAL-INT-01 | B-7 | JVM Integration |
| AT-SENSOR-01-A through D | C-4 | Instrumented |
| AT-SENSOR-01-E through G | C-1 | Unit |
| AT-NAV-01-A | F-4 | Instrumented |
| AT-NAV-01-B | F-4 | Instrumented |
| AT-NAV-01-C | F-12 | Instrumented |
| AT-VAR-01 | B-1 | Unit |
| AT-VM-DRIFT-01, 01b | D-1 | Unit |
| AT-UNDO-VM-01 through 03 | E-3 | Unit |

---

## Recommendation

**Need Attention**

> Four Medium findings (F-01 through F-04) require attention before implementation begins.

**Required changes:**

1. **F-01 (Medium):** Add a failing-test task before B-3 that asserts the `IDriftDetector` interface contract and `DriftEvent` enum values. This can be as simple as a compilation test or a `FakeDriftDetector`-implements-`IDriftDetector` structural assertion.

2. **F-02 (Medium):** Add failing unit test cases for `dismissCalAgeBanner()` (asserts `calAgeBannerDismissed = true`) and `dismissDriftBanner()` (asserts `driftCooldownTimestampMs` written to `SettingsRepository` and `driftBannerState = HIDDEN`) to the D-1 task or a new D-1a task preceding D-4.

3. **F-03 (Medium):** Add `AT-CAL-02-D` and `AT-CAL-02-E` explicitly to Phase D task D-1's test list, or create a new task D-1b. Update the Test File Inventory entry for `CompassViewModelDriftTest.kt` to include these tests.

4. **F-04 (Medium):** Add an instrumented test assertion in F-6 (or a new task F-6a) that verifies the search bar becomes `View.VISIBLE` when `getAllFlow()` emits a non-empty list from State A. Alternatively, add a unit test for the `updateListState()` four-branch logic using a testable extraction of that helper.
