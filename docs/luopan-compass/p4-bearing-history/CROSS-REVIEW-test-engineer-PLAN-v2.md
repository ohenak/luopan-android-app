# Cross-Review: test-engineer — PLAN

**Reviewer:** test-engineer
**Document reviewed:** docs/luopan-compass/p4-bearing-history/PLAN-luopan-p4-bearing-history.md (v0.2-draft)
**Date:** 2026-04-27
**Iteration:** 2

---

## Review Scope

Reviewed against:
- TSPEC-luopan-p4-bearing-history v0.2-draft
- Prior review: CROSS-REVIEW-test-engineer-PLAN.md (iteration 1)

Evaluation criteria: resolution of the four Medium findings from iteration 1 (F-01 through F-04); test coverage completeness; TDD sequencing correctness; FSPEC acceptance-test-to-PLAN-task mapping.

---

## Resolution Status: Iteration 1 Medium Findings

| Finding | Status | Notes |
|---------|--------|-------|
| F-01 — `IDriftDetector` has no red task before B-3 | **Resolved** | Task B-2a added: structural/contract test for `IDriftDetector` and `DriftEvent` enum before B-3. |
| F-02 — `dismissCalAgeBanner()` and `dismissDriftBanner()` have no dedicated unit test tasks | **Resolved** | Task D-1b added: explicit unit tests for both state transitions. DoD checklist updated to match. |
| F-03 — AT-CAL-02-D and AT-CAL-02-E absent from all Phase D tasks | **Resolved** | Task D-1c added: unit tests for cooldown suppression (AT-CAL-02-D) and cooldown re-arm (AT-CAL-02-E). Test File Inventory updated. |
| F-04 — No unit test for `updateListState()` four-branch logic | **Resolved** | Task E-2a added: unit test for all four visibility combinations. Extraction of `updateListState()` as an `internal` function specified. |

---

## Findings

| ID | Severity | Finding | Section ref |
|----|----------|---------|------------|
| F-01 | Medium | **B-2a forward-reference makes half its stated scope untestable at write time.** Task B-2a specifies two goals: (1) assert `DriftEvent.TRIGGERED` and `DriftEvent.RESET` enum constants exist; (2) assert `FakeDriftDetector` (once created in B-4) compiles as an `IDriftDetector`. Goal 2 cannot be written when B-2a is the red task, because `FakeDriftDetector` does not exist until B-4. An engineer following TDD strictly cannot write a failing test that references an unwritten class without a compile error — which is a different failure mode than a failing test. The B-2a task description parenthetically acknowledges this ("once created in B-4"), but does not resolve the sequencing contradiction. This means the `FakeDriftDetector implements IDriftDetector` assertion either belongs in B-4 (as a contract guard before the production code that uses `IDriftDetector`) or the task description needs to be narrowed to only the assertions that can be written red at B-2a time (enum constants and `onFrame()` signature). As written, an implementer either cannot compile the B-2a test suite or must split the test across phases without a clear task for the second part. | Phase B, task B-2a |
| F-02 | Low | **TSPEC §9.3b still shows `FakeDriftDetector` subclassing the concrete `DriftDetector` class, while the PLAN correctly specifies it implements `IDriftDetector`.** The TSPEC code snippet at line 1578 reads `class FakeDriftDetector(…) : DriftDetector(FakeClock(0L))`. The PLAN task B-4 specifies `FakeDriftDetector` implements `IDriftDetector`. These are structurally incompatible: a class cannot simultaneously be a subclass of the concrete `DriftDetector` and implement only the `IDriftDetector` interface (though it would work if `DriftDetector` itself implements `IDriftDetector`, which is correct per B-3 and B-6). The risk is that an engineer reading TSPEC §9.3b and skipping the PLAN B-4 description will implement the subclass approach, defeating the interface abstraction. The PLAN iteration 1 review flagged this as Low (F-05), and the PLAN v0.2 does not add any cross-reference note to alert engineers to the TSPEC inconsistency. The PLAN itself is correct; this is a documentation risk that should be called out explicitly (e.g., a note in B-4 stating "The TSPEC §9.3b snippet is superseded; use `IDriftDetector` not `DriftDetector` as the supertype"). | Phase B, task B-4 |
| F-03 | Low | **TSPEC §9.7 (AT-CAL-03-B2) contains a logically incorrect test body that would not verify the claimed boundary condition.** The TSPEC code for AT-CAL-03-B2 calls `clock.advance(61_001L)` and then calls `onFrame()` once. On that first call, the detector is in IDLE state — it transitions to COUNTING and sets `countingStartMs = clock.nowMs() = 61,001`. The elapsed time is then `61,001 − 61,001 = 0 ms` — below the 60-second threshold. The detector stays COUNTING and returns `null`. The test asserts `event != TRIGGERED`, which passes — but for the wrong reason: the detector hasn't yet reached the 60-second mark in this test, so the "exactly 10% boundary" condition was never evaluated. The correct structure requires an initial `onFrame()` at t=0 to start the COUNTING timer, then `clock.advance(61_001L)`, then a second `onFrame()`. The PLAN task B-5 inherits this gap because it references AT-CAL-03-B2 from the TSPEC and lists it in `DriftDetectorTest.kt`. An implementer following the TSPEC snippet will produce a test that passes trivially without actually verifying the boundary. This is a cross-document defect originating in the TSPEC; the PLAN does not replicate the test body, but it cites the test ID without flagging the TSPEC test structure issue. | Phase B, task B-5; TSPEC §9.7 |
| F-04 | Low | **D-1a places AT-CAL-01-C (calibration age floor-division test) in `CompassViewModelDriftTest.kt`, creating a semantic mismatch that may confuse implementers.** The test verifies `computeCalibrationAgeDays()` floor-division semantics — a calibration-age concern, not a drift concern. Placing it in a file named `CompassViewModelDriftTest.kt` violates naming convention (the file's name signals that all tests within it are drift-detection tests). An implementer searching for calibration-age tests will not find AT-CAL-01-C without knowing to look in a drift-named file. The PLAN resolves the coverage gap (correctly), but the file placement decision will be silently inherited and the misplacement will persist in the test suite. A better placement is a separate `CompassViewModelCalibrationTest.kt` or the existing `CompassViewModelTest.kt`. This is an organizational finding, not a coverage gap. | Phase D, task D-1a |

---

## FSPEC Acceptance Test → PLAN Task Mapping (Updated)

The following gaps identified in iteration 1 are now resolved. Updated mapping for affected rows:

| FSPEC AT | PLAN Task(s) | Coverage Level | Status |
|----------|-------------|---------------|--------|
| AT-CAL-01-C | D-1a | Unit | Resolved (was: Gap) |
| AT-CAL-02-D | D-1c | Unit | Resolved (was: Gap) |
| AT-CAL-02-E | D-1c | Unit | Resolved (was: Gap) |
| AT-CAL-02-A | D-1 (via AT-VM-DRIFT-01) | Unit | Unchanged |

All other mappings from iteration 1 remain valid. No new acceptance test gaps identified.

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | Task B-2a acknowledges the `FakeDriftDetector` forward reference parenthetically but does not resolve it. Is the intent that B-2a only asserts the enum constants and `onFrame()` signature, and a separate assertion of `FakeDriftDetector implements IDriftDetector` is added informally to B-4 without a new task number? If so, the B-2a task description should be narrowed to remove the `FakeDriftDetector` assertion from its stated scope. |
| Q-02 | The TSPEC §9.1 coverage matrix assigns AT-CAL-02-D and AT-CAL-02-E to the `BearingHistoryViewModel (drift banner)` row, but the PLAN correctly places these tests in `CompassViewModelDriftTest.kt`. Should the TSPEC coverage matrix be updated to reflect the correct test file? |

---

## Positive Observations

- All four Medium findings from iteration 1 (F-01 through F-04) are substantively resolved: new tasks B-2a, D-1a, D-1b, D-1c, and E-2a are present, correctly sequenced (each precedes its corresponding implementation task), and named in the Test File Inventory.
- The DoD checklist has been updated to explicitly require AT-CAL-01-C, AT-CAL-02-D, AT-CAL-02-E, `dismissCalAgeBanner()`/`dismissDriftBanner()` unit tests, `updateListState()` four-branch unit test, `IDriftDetectorContractTest` existence, and a signed-off manual test AT-HIST-03-D artifact. The checklist is now a genuine quality gate.
- The cross-review issues addressed table is complete and accurate — every iteration 1 finding (both TE and PM) is mapped to a resolution task. Traceability from review finding to PLAN task to DoD item is unbroken.
- The dependency graph is correctly updated with A-0 as a gate. The "parallelizable pairs" note at the bottom of the graph clarifies the B∥C, D-after-B, and F-after-C+E relationships in plain language.
- Task D-1d (onCalibrationCompleteFromHistory async re-flash mitigation) is well-specified: it names the exact race path (synchronous flag set before IO, conditional clear after IO), specifies `TestCoroutineScheduler` for control, and precedes D-4 in task order.

---

## Recommendation

**Approved with Minor Issues**

> The four Medium findings from iteration 1 are fully resolved. Three new findings are Low severity only (no Mediums or Highs). The B-2a forward-reference (F-01 above) is the highest-priority item to clarify before implementation, but it does not block implementation if engineers are aware of the sequencing constraint.

**Recommended changes before implementation starts:**

1. **F-01 (Medium):** Narrow the B-2a task description to remove the `FakeDriftDetector implements IDriftDetector` assertion from its scope (since it cannot be written as a compilable red test until B-4). Either add that assertion explicitly to B-4 as a contract guard, or note in B-2a that the fake-implements-interface check is deferred to B-4.

2. **F-02 (Low):** Add a note in PLAN task B-4 explicitly stating that the TSPEC §9.3b code snippet is superseded — `FakeDriftDetector` must implement `IDriftDetector`, not subclass `DriftDetector` directly.

3. **F-03 (Low):** Add a note in PLAN task B-5 that the TSPEC §9.7 AT-CAL-03-B2 test body has a sequencing error. The correct structure requires an initial `onFrame()` at t=0 to start the timer, followed by `clock.advance(61_001L)`, followed by a second `onFrame()` to evaluate the boundary. Engineers should use the corrected structure, not the TSPEC snippet.

4. **F-04 (Low):** Consider moving AT-CAL-01-C to `CompassViewModelTest.kt` (or a new `CompassViewModelCalibrationTest.kt`) rather than `CompassViewModelDriftTest.kt`.
