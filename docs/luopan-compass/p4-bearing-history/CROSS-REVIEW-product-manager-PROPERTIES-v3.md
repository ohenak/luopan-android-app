# Cross-Review: product-manager — PROPERTIES

**Reviewer:** product-manager
**Document reviewed:** docs/luopan-compass/p4-bearing-history/PROPERTIES-luopan-p4-bearing-history.md (v0.3-draft)
**Date:** 2026-04-27
**Iteration:** 3

---

## Iteration 1 and 2 Finding Resolution Status

This review focuses on whether the five genuine PM findings from iteration 1 (confirmed unresolved in iteration 2) have been addressed in v0.3-draft. The `Cross-reviews addressed` header in v0.3 explicitly lists PM PROPERTIES-v1 F-01 through F-05 as addressed, and §6.1 documents each resolution. Verification is below.

| Iteration 1 Finding | Severity | v0.3 Status | Verdict |
|--------------------|----------|-------------|---------|
| F-01: "Bearing deleted" not anchored in FSPEC | Medium | Partially addressed | **Open — residual defect** |
| F-02: Snackbar duration pass condition relies on reflection | Low | Resolved | Closed |
| F-03: PROP-CAL-002 lacks millisecond-precision boundary | Low | Resolved | Closed |
| F-04: No E2E/integration property for Scenario E2 user-visible outcome | Low | Resolved | Closed |
| F-05: PROP-NAV-010 encodes an implementation detail | Low | Resolved | Closed |

---

## Findings

| ID | Severity | Finding | Requirement ref |
|----|----------|---------|----------------|
| F-01 | Low | **FSPEC-HIST-03 step 3 still reads "Deleted" — the source defect has not been corrected.** PROP-HIST-041 in v0.3 acknowledges this gap and anchors its pass condition to TSPEC §7.5 "pending that FSPEC update." The resolution note in §6.1 confirms: "FSPEC-HIST-03 §Behavioral Flow step 3 must be updated before implementation." The FSPEC itself has not been changed — step 3 of FSPEC-HIST-03 continues to specify `"Deleted"` as the Snackbar message. The PROPERTIES document has done the right thing: it flags the discrepancy, notes the mandatory FSPEC correction, and anchors the property to TSPEC §7.5 as an interim measure. This is a sound workaround, and the property itself is correct. However, the FSPEC-HIST-03 defect remains open and must be corrected before PLAN task F-9 implementation begins. Severity is downgraded from Medium to Low relative to iteration 2 because v0.3 has correctly mitigated the anchoring risk — the property no longer claims the FSPEC is authoritative for "Bearing deleted" while the FSPEC contradicts it. The residual action is an FSPEC edit, not a PROPERTIES change. | REQ-CAPTURE-03; FSPEC-HIST-03 §Behavioral Flow step 3 |

---

## Closed Findings (confirmed resolved in v0.3)

| ID | Original Severity | Resolution in v0.3 |
|----|------------------|--------------------|
| F-02 | Low | PROP-HIST-042 pass condition rewritten to assert user-observable behavior: Snackbar is no longer visible after `SystemClock.sleep(5100)` ms with no user interaction. Reflection removed. Aligned to REQ Scenario C "exactly 5 seconds." |
| F-03 | Low | PROP-CAL-002 pass condition now specifies exact millisecond boundary values: elapsed == `30 * 86_400_000L` ms → banner `GONE`; elapsed == `30 * 86_400_000L + 1` ms → banner `VISIBLE`. Both boundary values must be tested. Aligned to REQ-CAL-05 Condition A floor-division specification. |
| F-04 | Low | New property PROP-CAL-019b added. It covers the integration-level Scenario E2 user-visible outcome: when `interferenceState = WARNING` AND drift conditions are met, `driftBannerState` remains `HIDDEN` and the drift banner view has `visibility == GONE` in the instrumented scenario. Classified as Integration/Integration and mapped to Scenario E2 in §5.2. Complements the existing unit-level PROP-CAL-031. |
| F-05 | Low | PROP-NAV-010 rewritten to assert product behavior (correct side effects per banner path) with TSPEC §6.2 cited as the architectural source for the two-launcher decision. The property no longer mandates the specific names `calWizardLauncherAge` / `calWizardLauncherDrift` as a product contract. |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | F-01 has now been open across three PM review iterations. The PROPERTIES document correctly handles it as a prerequisite note. Will FSPEC-HIST-03 step 3 be updated to "Bearing deleted" before PLAN task F-9 begins, or will this be tracked as a standalone FSPEC erratum with a separate commit? Either path is acceptable, but the FSPEC and PROPERTIES should not remain contradictory through implementation. |

---

## Positive Observations

- **F-02 resolution is product-faithful.** Replacing the reflection-based Snackbar duration check with a 5100 ms behavioral timing assertion correctly represents the user-visible "Snackbar visible for exactly 5 seconds" requirement from REQ Scenario C. The test now exercises what a user experiences, not an internal Android field.

- **F-03 resolution locks down the boundary precisely.** Specifying both `30 * 86_400_000L` ms (no banner) and `30 * 86_400_000L + 1` ms (banner shown) as required test values directly closes the off-by-one risk identified in iteration 1. This is exactly the right level of specificity for a floor-division boundary condition.

- **F-04 resolution (PROP-CAL-019b) correctly layers the coverage.** The new property adds integration-level coverage for the user-observable Scenario E2 outcome — complementing, not replacing, the existing unit-level PROP-CAL-031. The two properties together span the full testing pyramid from DriftDetector unit behavior to the instrumented banner-visibility assertion. PROP-CAL-019b is also correctly mapped in §5.2 (Scenario E2 → PROP-CAL-019b) and §6.2 (gap closed).

- **F-05 resolution correctly separates product from implementation.** PROP-NAV-010 now asserts observable outcomes (correct side effects per banner tap path) and cites TSPEC §6.2 as the architectural source for the implementation choice. The property is now appropriately product-scoped.

- **No new scope creep or regression.** v0.3 addresses all five iteration 1 findings without introducing any out-of-scope behaviors. The coverage matrix remains complete, and all 14 REQ acceptance scenarios remain mapped to at least one property.

- **PROP-HIST-041 mitigation is well-structured.** The prerequisite note in §6.1 and within PROP-HIST-041 itself creates a clear, auditable contract: FSPEC update is a mandatory gate before F-9 implementation. This prevents the "Deleted" vs. "Bearing deleted" conflict from surviving silently into the implementation phase.

---

## Recommendation

**Approved with Minor Issues**

> F-01 (Low, residual from iteration 1): The FSPEC-HIST-03 step 3 text must be corrected from "Deleted" to "Bearing deleted" before PLAN task F-9 implementation begins. This is an FSPEC edit, not a PROPERTIES change — the v0.3 PROPERTIES document handles the gap correctly as a mandatory prerequisite. The residual action item is on the PM to update FSPEC-HIST-03.
>
> F-02 through F-05 are confirmed resolved. No further PROPERTIES changes are required for those findings.
>
> This document may proceed to implementation review pending the FSPEC-HIST-03 correction.
