# Cross-Review: Test Engineer — PLAN-luopan-p3-luopan-mode

| Field | Value |
|-------|-------|
| Reviewer | Test Engineer |
| Document | PLAN-luopan-p3-luopan-mode.md |
| Date | 2026-04-25 |
| Recommendation | Approved with Minor Issues |

---

## Summary

The PLAN is well-structured and demonstrates strong test-first discipline. Every task specifies test files alongside implementation files, and the acceptance criteria in most tasks are precise and directly traceable to TSPEC unit tests or FSPEC acceptance criteria. The batch/dependency graph is logical, and the Open Issue Notice (V3-F01) is honestly disclosed and acted upon.

Three findings were identified: one High and two Medium. The High finding concerns a dependency ordering problem — Task 4.3 specifies a test (`v3f01_tick_mark_uses_display_bearing`) that requires `LuopanView.setLockState()` to exist, yet Task 4.3 lists `Task 4.1` as a dependency. This is correct in the dependency table but the task narrative for Task 4.3 says "depends on Task 3.2 (LuopanFragment shell), Task 4.1 (LuopanView)" — Task 3.2 only provides the shell without `setLockState()` being callable, so the dependency is correct but the acceptance test for V3-F01 relies on an API specified in Task 4.1. Because Tasks 4.1, 4.2, and 4.3 are marked as parallelizable, an engineer starting Task 4.3 in parallel with Task 4.1 cannot write or run `v3f01_tick_mark_uses_display_bearing` until Task 4.1 is complete. The PLAN implies these can run in parallel, but Task 4.3's critical V3-F01 acceptance test is blocked on Task 4.1. This needs explicit sequencing documentation.

The two Medium findings are: (1) a gap in the `CompassViewModelSessionStateTest` acceptance criteria — the test for `lockXiang_under_magnetic_north_stores_true_north_bearing` does not assert `displayXiangBearing` after the combined `lock()` + `rederive()` call (carry-over from TSPEC V3-F02), and (2) the `NoModeSwitcherTest` modification acceptance lacks a testable criterion for what "TabLayout IS present" means as an automated assertion.

---

## Findings

### High Priority

| ID | Severity | Finding | Section Ref |
|----|----------|---------|-------------|
| P-F01 | High | **Task 4.3 is declared parallelizable with Tasks 4.1 and 4.2, but its critical acceptance test `v3f01_tick_mark_uses_display_bearing` depends on `LuopanView.setLockState(active, displayXiangBearing)` which is only implemented in Task 4.1. An engineer starting Task 4.3 concurrently with Task 4.1 cannot write or execute this test until 4.1 is complete. The dependency graph in the batch summary correctly lists `Task 4.1` as a prerequisite of `Task 4.3`, but the batch 4 summary says "Tasks 4.1, 4.2, and 4.3 can run in parallel after Batch 3 completes." This is a contradiction: 4.3 cannot proceed to its final acceptance test while 4.1 is in progress. The PLAN must either (a) make Task 4.3 strictly sequential after Task 4.1, or (b) split Task 4.3 into an overlay-text sub-task (parallelizable with 4.1) and a tick-mark-test sub-task (strictly after 4.1). As written, a parallel execution will result in the V3-F01 acceptance test being deferred silently or left unwritten.** | Task 4.3 acceptance criteria (`v3f01_tick_mark_uses_display_bearing`); Batch 4 summary; Dependency graph |

### Medium Priority

| ID | Severity | Finding | Section Ref |
|----|----------|---------|-------------|
| P-F02 | Medium | **Task 2.3 acceptance criterion `lockXiang_under_magnetic_north_stores_true_north_bearing` asserts that `xiangBearing ≈ 45.0f` is stored, but does not assert that `displayXiangBearing` is also set correctly. Per TSPEC v1.2 §4.4, `lockXiang()` calls `lock()` followed immediately by `rederive()`. An implementer who omits the `rederive()` call after `lock()` in `lockXiang()` would still pass the current assertion (xiangBearing == 38.0f per the TSPEC v3 review math; or 45.0f per this task's scenario). The missing assertion on `displayXiangBearing` means the `rederive()` post-`lock()` call in `lockXiang()` is not verified. This is carry-over from TSPEC V3-F02.** | Task 2.3 acceptance criteria (4th bullet: `lockXiang_under_magnetic_north_stores_true_north_bearing`); TSPEC v1.2 §4.4, §5.3 (`lockXiang()`) |
| P-F03 | Medium | **Task 3.1 acceptance criteria include "all existing instrumented tests in `ui/` still pass" and the `NoModeSwitcherTest` update to "assert TabLayout IS present in Phase 3", but neither criterion specifies a testable assertion. The existing `NoModeSwitcherTest` presumably asserts absence of the mode-switcher widget; the replacement criterion must specify exactly what is being checked (e.g., `onView(withId(R.id.tabLayout)).check(matches(isDisplayed()))`). Without this, an engineer could simply delete the test rather than update it, and the acceptance criterion would be satisfied by absence. The PLAN should specify the updated assertion explicitly.** | Task 3.1 acceptance criteria (last bullet: `NoModeSwitcherTest` update) |

### Low Priority

| ID | Severity | Finding | Section Ref |
|----|----------|---------|-------------|
| P-F04 | Low | **Task 4.3 acceptance test `ac23_overlay_displays_magnetic_bearing_correctly` is listed in the task detail but is NOT listed in Task 4.3's Acceptance table. It appears in the TSPEC §12.5 (as `ac23_northSwitch_overlayDisplaysConvertedBearing`) and in Task 4.3's description, but the task's formal Acceptance bullets only reference `ac07_`, `ac08_`, `ac10_`, `ac11_`, `ac23_overlay_displays_magnetic_bearing_correctly`, and `v3f01_tick_mark_uses_display_bearing`. On closer inspection it is present as the fifth bullet. No gap — but the test name in the acceptance table (`ac23_overlay_displays_magnetic_bearing_correctly`) differs from the name in TSPEC §12.5 (`ac23_northSwitch_overlayDisplaysConvertedBearing`). Mismatched names make traceability audits harder and increase the chance that two engineers implement the same test under different names.** | Task 4.3 acceptance (fifth bullet); TSPEC v1.2 §12.5 |
| P-F05 | Low | **Task 7.1 acceptance criterion `mode_switch_luopan_under_300ms` says "measured via `SystemClock.elapsedRealtime()` before/after navigate call" but does not specify the assertion form (e.g., `assertTrue(elapsed < 300L)`). The 300 ms budget is defined in REQ-NFR-LUOPAN-04 and TSPEC §9.4. The measurement methodology in the PLAN is more specific than the TSPEC's "visual check" note, which is an improvement — but it still does not state what constitutes test failure, leaving the threshold implicit. An implementer might log the value without asserting it.** | Task 7.1 acceptance (first bullet); TSPEC §9.4 |
| P-F06 | Low | **The Definition of Done includes "Default ring labels render in Traditional Chinese regardless of system locale (en/ja tested)" but does not reference a specific automated test. AC-12 in the FSPEC maps to `ac12_default_language_english_locale` which is listed under Task 6.1 acceptance. The DoD item is testable only through Task 6.1's instrumented test. The DoD should cite `ac12_default_language_english_locale` (Task 6.1) explicitly so reviewers can trace DoD items to test artifacts.** | Definition of Done (last item); Task 6.1 acceptance (first bullet) |
| P-F07 | Low | **Batch 6 says "Task 6.1 can start after Batch 2 (Task 2.2 for SettingsRepository keys, Task 2.3 for ViewModel setters) ... and can proceed in parallel with Batches 3–4 if resources allow." However, the `ac13_show_romanization_toggle` acceptance test for Task 6.1 is an instrumented test that requires `LuopanFragment` to be inflated (Task 3.2) and the readout panel to display text (Task 4.2). Task 6.1 can produce and test the ViewModel/Mapper unit behavior in parallel with Batches 3–4, but its instrumented acceptance tests are blocked on Tasks 3.2 and 4.2. The parallelization claim is overstated for the instrumented tests. The PLAN should clarify that the ViewModel/mapper unit behavior (carry-over from Task 2.1's unit test for `mapper_show_my_language_uses_english`) can proceed in parallel, but the instrumented UI tests for Task 6.1 cannot run until Batch 4 is complete.** | Batch 6 summary; Task 6.1 acceptance (`ac13_show_romanization_toggle`, `ac12_default_language_english_locale`) |

---

## Positive Observations

- Every task specifies both production files and test files together. The test files are named, located in the correct source sets (`src/test` vs `src/androidTest`), and the split between unit, Robolectric, and instrumented tests is consistently correct across all tasks.
- The TSPEC unit test names from §12.1.1–12.2.2 are reproduced verbatim in the PLAN acceptance criteria for Tasks 1.1–2.3, providing direct traceability from PLAN task to TSPEC test specification without ambiguity.
- The Open Issue Notice (V3-F01) is disclosed at the top of the PLAN, cross-referenced in Task 3.2, Task 4.1, and Task 4.3, and tracked in the Definition of Done. This is the correct handling for a known open issue — it does not block the PLAN but is required to be resolved before DoD is met.
- Task 2.3 correctly includes the Magnetic-to-True-North conversion for `lockXiang()` as an explicit acceptance criterion (`lockXiang_under_magnetic_north_stores_true_north_bearing`). This was a High finding in the TSPEC v2 review (N-F02) and is now a first-class acceptance criterion in the PLAN — the right outcome.
- The dependency graph is explicit in two forms (prose table and ASCII directed graph), making it easy to verify that no task claims an API that hasn't been implemented in a prior task.
- Task 1.3 explicitly resolves the TE-N-F01 contradiction in the ZuoXiangLock `rederive()` contract by requiring the test to be rewritten. This kind of forward reference to a prior review finding is well-managed.

---

## Recommendation

**Approved with Minor Issues**

P-F01 is the only finding that could cause a concrete implementation problem: if Task 4.3 is executed concurrently with Task 4.1, the `v3f01_tick_mark_uses_display_bearing` test cannot be written or run. This is a sequencing defect in the PLAN. It can be resolved by adding an explicit note that Task 4.3's acceptance tests are split: overlay text tests can run in parallel with Task 4.1, but `v3f01_tick_mark_uses_display_bearing` is blocked on Task 4.1 completion. No structural redesign of the task breakdown is needed.

P-F02 and P-F03 are medium issues that require a one-line addition each to the relevant acceptance criteria. They do not block implementation but do leave test gaps that could allow defects to pass undetected.

P-F04 through P-F07 are low-priority traceability and documentation gaps. They should be addressed before the PLAN is handed to an implementation engineer but do not require a formal re-review.
