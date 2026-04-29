# Cross-Review: product-manager — PROPERTIES v2

**Reviewer:** product-manager
**Document reviewed:** docs/about-page/PROPERTIES-about-page.md
**Date:** 2026-04-28
**Iteration:** 2
**Prior review:** docs/about-page/CROSS-REVIEW-product-manager-PROPERTIES.md

---

## Resolution of Prior Findings

| Prior ID | Severity | Resolution |
|----------|----------|-----------|
| F-01 | Low | **Resolved.** P-15 now cites TSPEC §7 as the authoritative source for `launchSingleTop` behavior. The note also records that the Luopan entry path is mechanically identical and covered by the same flag, addressing Q-01 without adding a redundant second property. ✅ |
| F-02 | Low | **Resolved.** P-16 now cites TSPEC §5.4 as the source for the no-`MenuProvider` constraint. The property is correctly framed as an architectural guardrail, not a REQ acceptance criterion. ✅ |
| F-03 | Low | **Resolved.** Section 5 now carries an explicit placement note stating that `FakeUrlLauncher` is test infrastructure (lives in `src/test`, never compiled into the production APK, declared `internal`). The coverage matrix row "Test infrastructure fidelity | P-19, P-20" accurately reflects that these properties make no product-facing traceability claim. ✅ |
| Q-01 | — | **Answered.** P-15 explains that the Luopan entry path is mechanically identical (same `launchSingleTop` flag) and that one test is sufficient. This is a sound and well-reasoned response. ✅ |

---

## New Findings

No new findings. A full pass against the REQ (v0.2, approved) confirms:

- All P0 functional requirements (REQ-ABOUT-01, REQ-ABOUT-02, REQ-ABOUT-03) and both P0 non-functional requirements (REQ-ABOUT-NFR-01, REQ-ABOUT-NFR-02) remain directly and fully covered.
- No acceptance criterion has been dropped, narrowed, or reinterpreted between v1 and v2 of the PROPERTIES document.
- The changes between iterations are confined to: adding TSPEC source citations to P-15 and P-16, and adding the placement/infrastructure note to Section 5. No property logic, test method, or assertion was altered.
- Scope remains tight: no properties exist for out-of-scope items (service listings, contact links, booking CTA, bilingual variants beyond the single bilingual name string).

---

## Positive Observations

All observations from the prior review remain valid and are not repeated here. The following are specific to v2:

- The P-15 note is concise and technically precise: it correctly identifies that `launchSingleTop` is a NavController flag, not a per-entry-point behavior, and that testing one entry path exercises the common mechanism. This is an appropriate application of mechanical equivalence reasoning.
- The Section 5 placement note ("lives in `src/test` ... never compiled into the production APK") is exactly the right framing to prevent future reviewers from treating P-19/P-20 as product-facing scope. Referencing TSPEC §2 for the updated file listing is a useful cross-document pointer.
- P-16's addition of the TSPEC §5.4 citation makes the chain of authority explicit: REQ-ABOUT-03 → TSPEC §5.4 → P-16. A future maintainer can trace why this Code Review property exists without guessing.

---

## Recommendation

**Approved**

> All prior findings are resolved. No new findings. Requirements coverage is complete, acceptance criteria fidelity is high, and scope is well-controlled. The PROPERTIES document is ready to proceed to implementation.
