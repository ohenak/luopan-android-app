# Cross-Review: product-manager — PROPERTIES

**Reviewer:** product-manager
**Document reviewed:** docs/luopan-compass/p4-bearing-history/PROPERTIES-luopan-p4-bearing-history.md
**Date:** 2026-04-27
**Iteration:** 1

---

## Findings

| ID | Severity | Finding | Requirement ref |
|----|----------|---------|----------------|
| F-01 | Medium | PROP-HIST-041 specifies the Snackbar text as "Bearing deleted", and §6.1 explicitly acknowledges this supersedes FSPEC-HIST-03 which still reads "Deleted". The REQ §5.1 and the FSPEC-HIST-03 behavioral flow (step 3) both say the Snackbar message should simply be "Deleted"; only the PLAN document says "Bearing deleted". The PROPERTIES document has elevated a PLAN-level assertion into a binding property without an authoritative REQ or FSPEC change. This is a product decision (copy/UX) that must be resolved by updating the FSPEC, not by a PROPERTIES workaround. Until FSPEC-HIST-03 is updated, the pass condition for PROP-HIST-041 is not anchored to an approved PM artifact. | REQ-CAPTURE-03; FSPEC-HIST-03 §Behavioral Flow step 3 |
| F-02 | Low | PROP-HIST-042 requires the Snackbar duration to be verified via "reflection or wrapper" against a 5000 ms field. The REQ specifies "5-second Undo Snackbar" as a product requirement (Scenario C). This property correctly captures that requirement. However, the pass condition relies on implementation internals (Snackbar duration field via reflection), which is fragile. A product-faithful pass condition would assert user-observable behavior: the Snackbar disappears without undo action after 5 seconds. The current pass condition could pass even if the Snackbar shows for the wrong duration due to test-timing tolerances. | REQ-CAPTURE-03 Scenario C |
| F-03 | Low | Scenario D2 (negative boundary: calibration exactly 30 days old shows no banner) maps cleanly to PROP-CAL-002, which correctly specifies `calibration_age_days <= 30` → banner `GONE`. However, PROP-CAL-002's pass condition says "for a calibration that is exactly 30 days old" without specifying the exact elapsed millisecond value used. The REQ defines the boundary condition precisely: `(today − calibration_date) > 30 days`, and Scenario D states "31 days and 23 hours → N=31". An elapsed value of exactly `30 * 86_400_000L` milliseconds should show no banner; `30 * 86_400_000L + 1` milliseconds should trigger it. The pass condition should lock in the millisecond-precision boundary to prevent a one-day-off implementation from passing the test. | REQ-CAPTURE-03 Scenario D2; REQ-CAL-05 Condition A |
| F-04 | Low | REQ Scenario E2 (drift prompt suppressed during active WARNING interference) is covered by PROP-CAL-031, which correctly states that `DriftDetector` must not count time while `interferenceState == WARNING`. The pass condition tests at the `DriftDetector` unit level only. There is no E2E or integration-level property that asserts the end-user-observable behavior from Scenario E2: when both the WARNING interference indicator AND the drift threshold are simultaneously met, only the REQ-DETECT-01 WARNING indicator is shown and the drift banner does not appear. This user-visible outcome is a P1 acceptance criterion and should have a complementary integration or E2E property. | REQ-CAL-05 Condition B; Scenario E2 |
| F-05 | Low | PROP-NAV-010 specifies that `BearingHistoryFragment` must register **two separate** `ActivityResultLauncher` instances (one for age, one for drift). This is a TSPEC/FSPEC architectural decision not explicitly stated in the REQ or FSPEC. The REQ (§5.1 navigation architecture) requires the `ActivityResultLauncher` pattern, but says nothing about one launcher vs. two. The FSPEC-NAV-01 also does not specify this split. Encoding two launchers as a mandatory contract in PROPERTIES goes beyond approved product behavior and crosses into implementation detail. The product-faithful requirement is simply that tapping each banner independently opens CalibrationWizardActivity and the correct result handling fires. | REQ-CAPTURE-03 §5.1 Navigation architecture; FSPEC-NAV-01 |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | PROP-HIST-041 locks in "Bearing deleted" as the Snackbar text. Will FSPEC-HIST-03 be updated to reflect this wording before implementation begins, so engineers have a single authoritative source? Or should PROP-HIST-041 revert its pass condition to match the FSPEC wording ("Deleted") and the FSPEC be updated separately? |
| Q-02 | PROP-CAL-044 specifies that `onCalibrationCompleteFromHistory()` sets `calAgeBannerDismissed = true` synchronously before an IO coroutine launches, then conditionally clears it afterward. This race-mitigation pattern is a TSPEC §6.1.3 design detail. From the product side, the acceptance criterion is that the age banner is dismissed immediately on `RESULT_OK` and only reappears if calibration is still stale. Is the synchronous-flag-set behavior critical to user experience, or is this purely an implementation-level concern that should remain in the TSPEC rather than a PROPERTIES assertion? |
| Q-03 | Scenario F (sensor profile) is fully covered. However, Scenario F4 (write failure → log at `Log.e` → retry on next launch) has PROP-SENSOR-023 covering the `Log.e` call, but the "retry on next launch" is covered by PROP-SENSOR-024 at unit level only. Is it acceptable to verify the retry guarantee purely via a second in-process `maybeWrite()` call (as PROP-SENSOR-024 does), or does product require an instrumented test that simulates actual process restart? |

---

## Positive Observations

- **Comprehensive REQ scenario traceability.** All 14 REQ acceptance scenarios (A, A2, B, C, C2, C3, D, D2, E, E2, F, F2, F3, F4) map to at least one property. The coverage matrix in §5.1 and §5.2 makes traceability explicit and auditable.

- **Scope discipline.** The PROPERTIES document stays tightly within the four behavioral domains defined in the REQ: bearing history, recalibration lifecycle, sensor logging, and navigation. No out-of-scope behaviors are introduced. REQ-CAPTURE-05 (CSV export) is correctly listed as out-of-scope in §6.3.

- **Product-faithful edge case coverage.** The interference badge properties (PROP-HIST-050 through PROP-HIST-055) precisely match the REQ and FSPEC intent: `GONE` (not `INVISIBLE`) for false-flagged records, correct truncation semantics for percentage and degree formatting, and explicit rejection of the badge on MODERATE/CLEAR records. These nuances are directly drawn from Scenario B.

- **Cross-review findings correctly promoted to properties.** The PM PLAN-v2 F-01 Snackbar text discrepancy and the TE PLAN-v2 findings (F-01 forward-reference, F-02 interface contract, F-03 sequencing bug) are all captured as binding properties (PROP-HIST-041, PROP-CAL-036, PROP-CAL-037, PROP-CAL-016) with clear explanations in §6.1. This prevents specification defects from silently propagating into implementation.

- **Empty state specification is complete.** PROP-HIST-060 through PROP-HIST-064 cover both empty-state variants (no records vs. no search matches), their distinct string resources, RecyclerView visibility, search bar visibility, and the reactive transition from empty to populated list. All map correctly to Scenarios A2 and the REQ §5.1 empty history state.

- **Performance NFR properties are well-anchored.** PROP-HIST-070 and PROP-HIST-071 reproduce the exact numeric thresholds from REQ §5.1 Performance NFR (500 ms / 500 records / Dispatchers.IO; 16 ms frame time) with the correct test methodology (in-memory Room for load; Macrobenchmark for frame time). PROP-HIST-071 correctly designates the benchmark as a non-blocking CI gate, matching the REQ's stated intent.

- **Cooldown persistence properties are product-faithful.** PROP-CAL-040 through PROP-CAL-043 precisely implement the REQ §5.3 Condition B dismiss mechanic: 10-minute cooldown stored as Unix epoch ms, surviving process death, cleared to 0L on `RESULT_OK`, and distinguishable from the "cooldown not set" state. The distinction between X-dismiss (starts cooldown) and `RESULT_OK` (clears cooldown) matches the FSPEC dismiss mechanics table exactly.

- **Sensor logging version-gate and failure-retry properties are exact.** PROP-SENSOR-010 through PROP-SENSOR-024 faithfully reflect the REQ's write-failure contract ("failure must be observable in logcat", "silent data loss not acceptable", "write retried on next launch") with the addition of SecurityException handling from TSPEC — a sensible gap-fill that doesn't contradict any product requirement.

---

## Recommendation

**Approved with Minor Issues**

> F-01 (Medium): The Snackbar text wording ("Bearing deleted" vs. "Deleted") is a product copy decision currently anchored only in the PLAN, not in an approved FSPEC. FSPEC-HIST-03 must be updated to reflect "Bearing deleted" so PROP-HIST-041 has an authoritative PM-approved source. This should be resolved before implementation of the swipe-to-delete feature (PLAN task F-9).
>
> F-02 through F-05 are Low severity and do not block implementation. They are flagged for engineering awareness:
> - F-02: Consider a user-observable pass condition for Snackbar duration rather than internal field inspection via reflection.
> - F-03: Tighten PROP-CAL-002's pass condition to specify the exact millisecond boundary (e.g., `30 * 86_400_000L` ms → no banner; `30 * 86_400_000L + 1` ms → banner).
> - F-04: Add an E2E or integration property covering Scenario E2's user-visible outcome (only WARNING indicator shown; drift banner absent when both conditions are simultaneously true).
> - F-05: Reconsider whether the two-launcher requirement in PROP-NAV-010 is a product constraint or an implementation choice; if the latter, it belongs in the TSPEC, not PROPERTIES.
