# Cross-Review: product-manager — PROPERTIES

**Reviewer:** product-manager
**Document reviewed:** docs/about-page/PROPERTIES-about-page.md
**Date:** 2026-04-28
**Iteration:** 1

---

## Findings

| ID | Severity | Finding | Requirement ref |
|----|----------|---------|----------------|
| F-01 | Low | **P-15 introduces behavior not stated in the REQ.** The `launchSingleTop` no-duplicate-destination property tests that tapping "About" while already on the About screen does not push a second entry onto the back stack. REQ-ABOUT-03 specifies only that the About screen opens from Modern/Luopan Mode and that back-press returns to the originating screen. It does not address the re-tap-from-About scenario. This behavior originates in TSPEC §7 as a UX polish decision. It is harmless and aligns with good UX practice, but the product acceptance criteria do not require it. The TE should note the TSPEC as the authoritative source for this property, not the REQ. | REQ-ABOUT-03 |
| F-02 | Low | **P-16 tests an internal implementation constraint, not a product requirement.** The property asserts `AboutFragment` must NOT register a `MenuProvider`. This is an architectural enforcement from TSPEC §5.4, not a user-visible acceptance criterion in the REQ. The REQ states the menu item is Activity-level (REQ-ABOUT-03), but does not specify what the Fragment must not do. This is a valid test engineering guardrail; however, it does not map to any REQ acceptance criterion and its source should be cited as TSPEC rather than REQ-ABOUT-03. | REQ-ABOUT-03 |
| F-03 | Low | **P-19 and P-20 (test double contract properties) have no REQ traceability.** Section 5 is correctly labeled "Test Double Contract Properties" and explicitly describes them as test infrastructure verification. From a product lens, these properties assert nothing about user-visible behavior and introduce no scope creep. The coverage matrix rows for these (row "Test infrastructure fidelity | P-19, P-20") correctly reflect this. No product risk, but both properties should be omitted from any product-facing traceability claims. | — |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | P-15 tests that back-press after a re-tap from `dest_about` returns to Modern Mode (using `compassRose` as the anchor view). Should this also cover the case where the user navigated to About from Luopan Mode — i.e., re-tapping About from About returns to Luopan after back-press? The current property only validates the Modern-entry path for the single-top scenario. |

---

## Positive Observations

- All three P0 functional requirements (REQ-ABOUT-01, REQ-ABOUT-02, REQ-ABOUT-03) and both P0 non-functional requirements (REQ-ABOUT-NFR-01, REQ-ABOUT-NFR-02) have direct, clearly mapped properties. No P0 requirement is uncovered.
- Acceptance criteria from the REQ are reflected with high fidelity. The happy-path intent AC (P-06), the no-browser Snackbar AC (P-07, P-18), and both navigation and back-navigation ACs (P-09 through P-12) are each directly traceable to their REQ acceptance criteria without narrowing or reinterpretation.
- The coverage matrix in §6 is accurate and explicit about what each row covers; the "Code review only" annotation for REQ-ABOUT-NFR-01 is an honest and appropriate acknowledgment.
- The positive/negative test pair for the Snackbar branch (P-07 and P-08) is sound product logic: P-08 ensures the Snackbar is not shown spuriously on success, which is implied by the REQ's two-state error model even if not stated as an explicit acceptance criterion.
- The tab-sync exclusion properties (P-13, P-14) are a precise translation of the REQ-ABOUT-03 requirement that `dest_about` "must be explicitly excluded from the CompassActivity tab-sync logic."
- The scope boundary is respected: no properties exist for service listings, contact links, booking CTAs, or bilingual variants beyond the single bilingual name string — all correctly excluded per REQ §5 Out of Scope.

---

## Recommendation

**Approved with Minor Issues**

> All findings are Low. No P0 or P1 requirement is uncovered, no product acceptance criterion is contradicted or dropped, and scope is well-controlled. The three Low findings are documentation clarifications (cite TSPEC as the authoritative source for P-15 and P-16; exclude P-19/P-20 from REQ traceability claims) rather than property changes. Q-01 is a coverage question for the TE to assess. The PROPERTIES document is ready to proceed to implementation.
