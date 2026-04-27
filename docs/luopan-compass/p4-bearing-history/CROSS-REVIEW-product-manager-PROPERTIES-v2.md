# Cross-Review: product-manager — PROPERTIES

**Reviewer:** product-manager
**Document reviewed:** docs/luopan-compass/p4-bearing-history/PROPERTIES-luopan-p4-bearing-history.md
**Date:** 2026-04-27
**Iteration:** 2

---

## Iteration 1 Finding Resolution Status

This review focuses on whether the five findings from the iteration 1 PM PROPERTIES review (F-01 through F-05) have been resolved. The document's `Cross-reviews addressed` field lists only PLAN-review findings (PM PLAN-v2 F-01; TE PLAN-v2 F-01 through F-03). No PROPERTIES-review findings are listed, confirming that none of the iteration 1 PM PROPERTIES findings have been formally addressed in this version of the document.

| Iteration 1 Finding | Severity | Status | Notes |
|--------------------|----------|--------|-------|
| F-01: "Bearing deleted" not anchored in FSPEC | Medium | **Unresolved** | FSPEC-HIST-03 step 3 still reads "Deleted". PROP-HIST-041 still supersedes FSPEC without an approved PM artifact change. |
| F-02: Snackbar duration pass condition relies on reflection | Low | **Unresolved** | PROP-HIST-042 pass condition unchanged — still specifies "via reflection or wrapper". |
| F-03: PROP-CAL-002 lacks millisecond-precision boundary | Low | **Unresolved** | Pass condition still says "exactly 30 days old" without specifying `30 * 86_400_000L` ms boundary value. |
| F-04: No E2E/integration property for Scenario E2 user-visible outcome | Low | **Unresolved** | PROP-CAL-031 remains unit-level only; no complementary E2E property added for the WARNING+drift simultaneous condition. |
| F-05: PROP-NAV-010 two-launcher split is an implementation detail in PROPERTIES | Low | **Unresolved** | PROP-NAV-010 still mandates two named `ActivityResultLauncher` instances, a TSPEC-level detail not anchored in any approved PM artifact. |

---

## Findings

| ID | Severity | Finding | Requirement ref |
|----|----------|---------|----------------|
| F-01 | Medium | **PROP-HIST-041 Snackbar text still unanchored in an approved PM artifact.** PROP-HIST-041 specifies the Snackbar body text as "Bearing deleted" and §6.2 notes this requirement, but FSPEC-HIST-03 Behavioral Flow step 3 still reads `"Deleted"`. The PROPERTIES document explicitly states it supersedes the FSPEC for this property. This remains a product copy decision (UX text) that must be owned by a PM artifact. Until FSPEC-HIST-03 is updated to specify "Bearing deleted", PROP-HIST-041's pass condition is not anchored to an approved source of truth. The risk is that an implementation team following the FSPEC produces "Deleted" and passes FSPEC compliance while failing PROP-HIST-041 — an irreconcilable conflict. | REQ-CAPTURE-03; FSPEC-HIST-03 §Behavioral Flow step 3 |
| F-02 | Low | **PROP-HIST-042 pass condition continues to rely on implementation internals.** The REQ specifies "exactly 5 seconds" for the Undo Snackbar (Scenario C). PROP-HIST-042's pass condition verifies duration by inspecting the Snackbar's internal duration field via reflection, which couples the property to a non-public Android API surface and is fragile if the Snackbar implementation changes. A user-observable pass condition — asserting the Snackbar is no longer displayed 5.5 s after swipe without tapping Undo — would more faithfully represent the product requirement and be more durable. | REQ-CAPTURE-03 Scenario C |
| F-03 | Low | **PROP-CAL-002 pass condition omits millisecond-precision boundary.** The REQ defines the age-banner trigger as strictly `> 30 days` using floor division of elapsed milliseconds. PROP-CAL-002's pass condition states "Banner visibility == GONE for a calibration that is exactly 30 days old" without specifying the exact elapsed millisecond value. An implementation using a rounding rule rather than floor division could produce the correct result for the narrative description "exactly 30 days old" while failing the boundary at `30 * 86_400_000L + 1` ms. The pass condition should explicitly state: (A) elapsed == `30 * 86_400_000L` ms → banner GONE; (B) elapsed == `30 * 86_400_000L + 1` ms → banner VISIBLE. PROP-CAL-003 (which covers N computation via floor division) does not fill this gap because it tests the numeric value of N, not banner visibility. | REQ-CAL-05 Condition A; Scenario D2 |
| F-04 | Low | **Scenario E2 user-visible outcome has no E2E or integration-level property.** PROP-CAL-031 covers the unit-level `DriftDetector` behavior (timer does not advance when `interferenceState == WARNING`), which is correct. However, Scenario E2 from the REQ is a P1 acceptance criterion stating that when both WARNING interference AND the drift threshold are simultaneously met, "only the WARNING-interference indicator from REQ-DETECT-01 is shown" and "the drift banner does not appear." No property in the document asserts this end-user-visible outcome at E2E or integration level. A new property is needed: given `interferenceState == WARNING` AND drift condition continuously met for >60 s, the drift banner must be `GONE` and no `DriftBannerState.VISIBLE` emission occurs. | REQ-CAL-05 Condition B; Scenario E2 |
| F-05 | Low | **PROP-NAV-010 continues to encode an implementation choice as a product contract.** PROP-NAV-010 mandates two separate, named `ActivityResultLauncher` instances (`calWizardLauncherAge`, `calWizardLauncherDrift`). The REQ §5.1 navigation architecture specifies `ActivityResultLauncher` registration in `BearingHistoryFragment` and the correct result-handling per banner, but is silent on whether one launcher or two are used. The observable product requirement is: tapping the age banner opens CalibrationWizardActivity and on RESULT_OK triggers age-banner dismissal; tapping the drift banner opens CalibrationWizardActivity and on RESULT_OK triggers drift detector reset. Whether this is achieved via one launcher with a flag or two launchers is an engineering choice. PROP-NAV-010 should be rewritten to assert the user-observable outcome, not the implementation structure. | REQ-CAPTURE-03 §5.1 Navigation architecture |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | F-01 has been open since iteration 1 and is blocking a clean anchor for PROP-HIST-041. Will FSPEC-HIST-03 be updated to reflect "Bearing deleted" before implementation of swipe-to-delete (PLAN task F-9)? If the FSPEC update is out-of-band with this review cycle, can the PROPERTIES document note that PROP-HIST-041 is contingent on a pending FSPEC update with a tracking reference? |
| Q-02 | For F-04: should the new E2E property (drift banner absent when WARNING is active) be added to the existing `RecalibrationBannerTest.kt` test file alongside PROP-CAL-031, or would it map to a new integration test file that involves both `InterferenceDetector` and `DriftDetector`? The answer affects whether this is a minor addendum to an existing property or a new property requiring its own PLAN task. |

---

## Positive Observations

- **No new scope creep introduced.** The document continues to restrict itself precisely to the four domains established in the REQ (bearing history, recalibration lifecycle, sensor logging, navigation). No out-of-scope behaviors have been added between the iteration 1 review and this re-review.

- **All iteration 1 unresolved findings remain narrow in scope.** None of the five open findings represent a missing P0 or P1 requirement. The core functional coverage — all 14 REQ acceptance scenarios — remains fully mapped and intact.

- **PLAN cross-review findings correctly reflected.** The `Cross-reviews addressed` header field correctly documents that PM PLAN-v2 F-01 (Snackbar text) and TE PLAN-v2 F-01 through F-03 (B-2a forward ref, TSPEC snippet conflict, AT-CAL-03-B2 sequencing bug) are captured as properties. §6.1 explains each resolution with clear supersession rationale.

- **Coverage matrix remains complete and auditable.** §5.1 (Requirements → Properties) and §5.2 (FSPEC Acceptance Tests → Properties) provide full traceability from the 6 in-scope requirements and all named FSPEC acceptance tests to at least one property each.

- **P1 recalibration lifecycle properties remain product-faithful.** The iteration 1 review praised the completeness of PROP-CAL-001 through PROP-CAL-044 and PROP-NAV-030 through PROP-NAV-033. This assessment is unchanged — the recalibration, drift detection, and database migration properties accurately reflect the REQ §5.3 full specification.

---

## Recommendation

**Need Attention**

> F-01 (Medium, unresolved from iteration 1): FSPEC-HIST-03 must be updated to specify "Bearing deleted" as the Snackbar body text. This is a product copy decision and cannot remain anchored only in PROPERTIES. Update FSPEC-HIST-03 Behavioral Flow step 3 and re-issue before PLAN task F-9 implementation begins.
>
> F-02 through F-05 (Low, unresolved from iteration 1) are not blocking but should be addressed in the next PROPERTIES revision:
> - F-02: Replace the reflection-based Snackbar duration pass condition in PROP-HIST-042 with a user-observable timing assertion.
> - F-03: Add explicit millisecond boundary values to PROP-CAL-002's pass condition (`30 * 86_400_000L` ms and `30 * 86_400_000L + 1` ms).
> - F-04: Add a new E2E or integration property asserting that the drift banner is absent when `interferenceState == WARNING` (Scenario E2 user-visible outcome).
> - F-05: Rewrite PROP-NAV-010 to assert the observable result-handling behavior per banner rather than mandating two named launcher instances.
