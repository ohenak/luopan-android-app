# Cross-Review: product-manager — PLAN

**Reviewer:** product-manager
**Document reviewed:** `docs/luopan-compass/p4-bearing-history/PLAN-luopan-p4-bearing-history.md` (v0.2-draft)
**Date:** 2026-04-27
**Iteration:** 2

---

## Iteration 1 Findings: Resolution Status

| ID | Severity (v1) | Status | Evidence |
|----|--------------|--------|---------|
| F-01 | Low | **Resolved** | Dependency graph updated: A-0 is now labeled `← GATE: no implementation phase may start until A-0 is complete`; Phase A entry shows `[requires A-0]` with a direct structural arrow from A-0. Phase A body header also reads "Dependency: Phase A-0 (FSPEC backport must complete before any implementation begins)." The gate is unambiguous in both the graph and the prose. |
| F-04 | Low | **Resolved** | New task D-1a added to Phase D: "Write failing unit test AT-CAL-01-C for `computeCalibrationAgeDays()` floor division: inject `FakeClock` with timestamp representing 31d 23h elapsed; assert return value equals 31 (not 32)." AT-CAL-01-C is also listed in the Test File Inventory under `CompassViewModelDriftTest.kt` and in the Definition of Done checklist. |

---

## Findings

| ID | Severity | Finding | Requirement ref |
|----|----------|---------|----------------|
| F-01 | Low | **FSPEC-HIST-03 Snackbar body text not updated to match PLAN.** The PLAN (tasks E-8 and DoD checklist) specifies the Snackbar body text as "Bearing deleted" and cites `PM TSPEC-v1 F-03` as the authority. However, FSPEC-HIST-03 §Behavioral Flow step 3 still reads: *"A Snackbar appears with message 'Deleted' and an 'Undo' action button."* The PM TSPEC-v1 F-03 finding raised "Deleted" vs. richer alternatives as a question for the PM to decide; PM TSPEC-v2 records it as resolved via a TSPEC string change. But the FSPEC — the authoritative behavioral source of truth for engineers — was not updated. An engineer reading FSPEC-HIST-03 sees "Deleted"; the PLAN says "Bearing deleted." These are in conflict. The correct resolution is to update FSPEC-HIST-03 to reflect the approved "Bearing deleted" text. Until then, an engineer implementing from the FSPEC alone will use the wrong string. | REQ-CAPTURE-03, FSPEC-HIST-03 |
| F-02 | Low | **AT-CAL-01-C may be implied twice in the test inventory.** The Test File Inventory lists `RecalibrationBannerTest.kt` as covering "AT-CAL-01-A through G" (instrumented). AT-CAL-01-C is a unit test (FSPEC: "*Who:* Unit test (FakeClock)") and is correctly assigned to `CompassViewModelDriftTest.kt` via task D-1a. The "through G" shorthand in `RecalibrationBannerTest.kt` creates ambiguity: it implies AT-CAL-01-C is also expected in the instrumented test file, which it is not (and should not be — it tests a pure function, not Fragment UI). A reader auditing test coverage could conclude that either the instrumented test is missing a case (C) or the unit test is redundant. This is a documentation clarity nit; it does not affect product coverage, which is correct. | REQ-CAL-05 (AT-CAL-01-C — floor division, Scenario D) |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | The DoD entry for AT-HIST-03-D now reads "sign-off record linked in task tracking." The task tracking tool was not identified in iteration 1 (Q-02). Is there an agreed artifact location (e.g., a comment in the PR, a JIRA ticket, a doc annotation) where the sign-off record will be stored? Clarifying this before Phase G avoids the sign-off being recorded in an inaccessible location at QA close-out. |
| Q-02 | The `RecalibrationBannerTest.kt` inventory row shows "AT-CAL-01-A through G." Confirming: AT-CAL-01-C is covered exclusively by the unit test in `CompassViewModelDriftTest.kt` (D-1a), and the instrumented `RecalibrationBannerTest.kt` will cover AT-CAL-01-A, -B, -D, -E, -F, -G only. Should the inventory row be updated to "AT-CAL-01-A, B, D, E, F, G" to eliminate the ambiguity? |

---

## Positive Observations

- **F-01 (A-0 gate) fully resolved.** The dependency graph now has explicit structural and textual enforcement of the gate in three locations: the ASCII graph annotation, the `[requires A-0]` label on Phase A, and the Phase A dependency prose. The ambiguity identified in iteration 1 is gone.
- **F-04 (AT-CAL-01-C) fully resolved.** Task D-1a is explicit, correctly placed before the implementation task (D-2), references both PM PLAN-v1 F-04 and TE PLAN-v1 F-06, and the test is correctly filed in the unit test inventory under `CompassViewModelDriftTest.kt`. The DoD checklist entry is specific and verifiable.
- **All TE PLAN-v1 findings addressed.** The Cross-Review Issues table accounts for all nine TE PLAN-v1 findings (F-01 through F-09), each with a named resolution task. The resolution is traceable: new tasks B-2a, D-1a, D-1b, D-1c, D-1d, E-2a, and an update to G-1 are all visible in the PLAN.
- **Performance test isolation.** G-1 now uses a dedicated `BearingHistoryPerfTest.kt`, correctly separating the 500-record seeding overhead from the functional test suite. The note explaining the rationale (slow data-seeding bleed) is good engineering hygiene.
- **DoD checklist is comprehensive and product-observable.** The updated checklist now includes 18 checkable items, all of them concrete (specific test IDs, specific string values, specific method names). This protects against acceptance criteria being satisfied only in test doubles.
- **No scope creep introduced.** The v0.2 additions (D-1a through D-1d, E-2a, B-2a) are all test tasks — no new production behavior, no new user-visible features, and no deferred requirements pulled forward. REQ-CAPTURE-05 (CSV export) remains correctly deferred to Phase 5.
- **P0 and P1 requirements remain fully covered.** REQ-CAPTURE-03 (history, search, swipe-to-delete), REQ-CAL-05 (both prompt conditions), REQ-DETECT-05 (interference badge), and REQ-SENSOR-07 (sensor profile) are each traced to at least one task and at least one acceptance test. No P0 or P1 requirement was dropped or narrowed between v0.1 and v0.2.

---

## Recommendation

**Approved with Minor Issues**

Both High-priority iteration 1 findings (F-01 and F-04) are cleanly resolved. The two new findings are Low severity:

- **F-01** (FSPEC-HIST-03 text not updated) should be corrected before Phase E implementation begins. The fix is a one-line update to FSPEC-HIST-03 step 3: change `"Deleted"` to `"Bearing deleted"`. Without this fix, the FSPEC and PLAN are in conflict, and an engineer following the FSPEC will implement the wrong string.
- **F-02** (inventory notation) is a documentation clarity nit with no product-fidelity impact; it can be addressed opportunistically.

**Required action before Phase E implementation:**

1. **F-01:** Update FSPEC-HIST-03 Behavioral Flow step 3 to read: *"A Snackbar appears with message 'Bearing deleted' and an 'Undo' action button."* This makes the FSPEC consistent with the PM decision recorded in PM TSPEC-v1 F-03 and the PLAN task E-8.

**Optional (recommended) action:**

2. **F-02:** Update the `RecalibrationBannerTest.kt` row in the Test File Inventory from "AT-CAL-01-A through G" to "AT-CAL-01-A, B, D, E, F, G" to avoid implying AT-CAL-01-C is an instrumented test.
