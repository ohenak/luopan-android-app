# Cross-Review: product-manager — PLAN

**Reviewer:** product-manager
**Document reviewed:** `docs/luopan-compass/p4-bearing-history/PLAN-luopan-p4-bearing-history.md`
**Date:** 2026-04-27
**Iteration:** 1

---

## Findings

| ID | Severity | Finding | Requirement ref |
|----|----------|---------|----------------|
| F-01 | Low | Phase A-0 (FSPEC backport) is listed as a pre-implementation gate but has no blocking dependency arc in the dependency graph. The graph shows Phase A depending on nothing and Phase A-0 as a separate node above it, but the textual dependency for all phases reading "Must complete before implementation starts" is not enforced structurally in the graph. A casual reader of the graph could interpret Phase A as parallelisable with A-0. The gate must be made unambiguous — A should depend on A-0 in the graph, not just in prose. | REQ-CAL-05 (FSPEC-CAL-03 is source of truth for DriftDetector behaviour) |
| F-02 | Low | Phase E does not include a task for search-bar visibility toggling per the State A / State B / normal list state machine in FSPEC-HIST-04 (search bar is GONE in State A). Task E-7 creates the layout and E-3/E-4 cover the undo ViewModel, but no task explicitly assigns ownership of the four-state `updateListState()` visibility logic to Phase E. It appears in Phase F (F-3 mentions `updateListState()` helper), which creates a risk of the Fragment being wired before the state machine is validated. Product impact: if State A hides the search bar incorrectly, the "No bearings yet" empty state misbehaves on first use. | REQ-CAPTURE-03 (FSPEC-HIST-04 State A — search bar GONE) |
| F-03 | Low | The acceptance test coverage table for Phase F omits AT-SENSOR-01 from the instrumented test list for Phase F-2 (CompassActivity wiring). Task F-2 adds `SensorCapabilityLogger` dispatch to `CompassActivity.onCreate()`, but no Phase F task writes or runs the instrumented tests for this wiring — those are assigned to Phase C (C-4, C-5). The dependency graph correctly notes that Phase F can start only after Phase C is done, but it is not made explicit that the instrumented sensor tests (C-4/C-5) must also be green before F-13's final all-tests-pass gate. This is a traceability gap for REQ-SENSOR-07's Scenario F acceptance test. | REQ-SENSOR-07 (Scenario F, F2, F3, F4) |
| F-04 | Low | AT-CAL-01-C (N uses floor division: 31d 23h → 31) is listed in the FSPEC/REQ as a required acceptance test but does not appear explicitly in any PLAN task. The unit test for `computeCalibrationAgeDays()` is implied by Phase D (D-1/D-3 add calibration age logic to `CompassViewModel`), but no task explicitly names AT-CAL-01-C. If the engineer implementing Phase D does not read the FSPEC acceptance tests exhaustively, this unit test will be missed. | REQ-CAL-05 (Scenario D — N = floor division; AT-CAL-01-C) |
| F-05 | Low | The "Snackbar body text" correction (from "Deleted" to "Bearing deleted") is captured correctly in the Definition of Done checklist and in Phase E task E-8 string resources (`bearing_deleted`). However, Phase F task F-3 describes the Snackbar as `setDuration(5000)` without referencing the message string name. This is minor but means a reviewer tracing F-3 to the requirement cannot confirm the text without cross-referencing E-8. The requirement text in REQ-CAPTURE-03 reads "5-second undo toast" with no prescribed wording, so this is purely a traceability nit — not a product-fidelity issue. | REQ-CAPTURE-03 (swipe-to-delete behaviour) |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | Phase B includes `FakeAccelerometerVarianceTracker` (B-4) but the dependency graph shows Phase D depending on Phase B. Does Phase D's `CompassViewModel` constructor accept `AccelerometerVarianceTracker` directly (the concrete class) or behind an interface? The PLAN says `IDriftDetector` is behind an interface for testability, but it is silent on whether `AccelerometerVarianceTracker` is also interface-backed. If it is not, Phase D's AT-VM-DRIFT-01 can only stub drift detection via `FakeDriftDetector` — the variance tracker remains real in ViewModel tests. This is consistent with what the PLAN says, but worth confirming the product-level coverage of Scenario E's "device is stationary" precondition is adequately exercised. |
| Q-02 | Phase G task G-4 is the only place AT-HIST-03-D (manual process-death test) appears. Is there a plan to record the result of this manual test in a way that is visible to QA sign-off? The PLAN says "Record result in task tracking" but the tracking tool is not identified. For a P1 requirement (REQ-CAPTURE-03 Scenario C3), this should be confirmed before Phase 4 is declared done. |

---

## Positive Observations

- Every P0 and P1 requirement from the REQ maps to at least one phase and at least one task. REQ-CAPTURE-03 (bearing history, search, swipe-to-delete, interference flag), REQ-CAL-05 (both recalibration prompt conditions), REQ-DETECT-05 (interference badge), and REQ-SENSOR-07 (sensor profile) are each fully covered across Phases A through G.
- The phasing order is correctly aligned with product priorities: foundational data layer (A) before domain logic (B, C) before ViewModel wiring (D) before UI (E, F) before performance validation (G). P0 infrastructure is never deferred behind P1 UI work.
- The plan correctly treats REQ-CAPTURE-05 (CSV export) as deferred to Phase 5 and introduces no scope creep related to it.
- All acceptance tests from the FSPEC and REQ are traced to specific task IDs and test files. The FSPEC open-questions resolution table is directly mirrored in the "Cross-Review Issues Addressed" section of the PLAN, creating clean bidirectional traceability.
- Phase A-0 as an explicit pre-implementation FSPEC-update gate is good product hygiene — it ensures FSPEC-CAL-03 is the authoritative source of the "COUNTING past 60 s" rule before any engineer implements `DriftDetector`.
- The Definition of Done checklist is specific and product-observable (text strings, Snackbar duration, badge visibility) rather than merely "tests pass," which protects acceptance criteria from being satisfied in test doubles only.
- The manual test (AT-HIST-03-D) is explicitly called out as non-automatable with a clear justification referencing FSPEC-HIST-03, rather than being silently omitted.
- Parallelisable phases (B and C) are clearly identified and justified, reducing delivery risk without compromising correctness.

---

## Recommendation

**Approved with Minor Issues**

All P0 and P1 requirements are covered. No out-of-scope behaviour is introduced. Acceptance criteria from REQ and FSPEC are faithfully represented in the task list. The four Low findings are traceability nits and do not block delivery, but F-01 (ambiguous A-0 gate in dependency graph) and F-04 (AT-CAL-01-C not explicitly named in any task) should be addressed before Phase A/D implementation begins to avoid rework.

**Recommended actions before implementation:**

1. **F-01:** Add an explicit arrow from A-0 to Phase A in the dependency graph, or annotate Phase A with "Requires A-0 complete."
2. **F-04:** Add AT-CAL-01-C to the Phase D task list (e.g., as part of D-3's test coverage or a new D-3a sub-task).

The remaining findings (F-02, F-03, F-05) can be addressed during Phase F review if desired but are unlikely to cause product defects given the engineer's reference to the FSPEC and the Definition of Done checklist.
