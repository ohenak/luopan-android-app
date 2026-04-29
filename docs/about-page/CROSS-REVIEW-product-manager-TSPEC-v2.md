# Cross-Review: product-manager — TSPEC

**Reviewer:** product-manager
**Document reviewed:** docs/about-page/TSPEC-about-page.md (v0.2)
**Date:** 2026-04-28
**Iteration:** 2

---

## Prior Finding Resolution

| Prior ID | Prior Severity | Status | Evidence |
|----------|---------------|--------|---------|
| F-01 | Medium | **Resolved** | §11.1 now contains `noBrowser_showsSnackbar`: launches `AboutFragment` via `FragmentScenario`, injects `FakeUrlLauncher(result = NoBrowserFound)`, clicks `tv_about_website`, and asserts `onView(withText(R.string.about_no_browser_error)).check(matches(isDisplayed()))`. The REQ-ABOUT-02 no-browser AC is now verified end-to-end at the Fragment level. |
| F-02 | Low | **Acknowledged / unchanged** | `launchSingleTop` behavior in §7 remains beyond REQ scope; still Low and still acceptable as a defensive implementation detail. |

---

## Findings

| ID | Severity | Finding | Requirement ref |
|----|----------|---------|----------------|
| F-01 | Low | **BearingHistoryFragment concurrency risk not surfaced in TSPEC.** REQ §6 Assumptions explicitly flags: *"BearingHistoryFragment (Phase 4) is out of scope for this feature's navigation tests; if it ships concurrently, its screen must be added to the acceptance criteria for REQ-ABOUT-03."* The TSPEC does not carry this forward-risk note anywhere — not in §6.3 (tab-sync exclusion), §7 (navigation graph), or §12 (traceability). If Phase 4 ships concurrently, the Activity's tab-sync `else -> return` branch and the navigation test matrix must also cover `dest_bearing_history`, but an implementor reading only the TSPEC has no signal to check. A single inline comment in §6.3 or §12 cross-referencing the REQ assumption would eliminate the risk of this being missed. No change is required to current AC coverage; this is a documentation-only gap. | REQ-ABOUT-03 |

---

## Questions

None.

---

## Positive Observations

- **F-01 (v1 Medium) fully resolved.** The Robolectric `noBrowser_showsSnackbar` test in §11.1 directly wires `FakeUrlLauncher` into the Fragment and asserts the Snackbar text — exactly what the prior review required. The test code snippet is correct and complete.
- **All P0 requirements covered by named technical components.** §12 traceability matrix maps every REQ-ABOUT-01, REQ-ABOUT-02, REQ-ABOUT-03, REQ-ABOUT-NFR-01, and REQ-ABOUT-NFR-02 row to a specific file, class, or method. No requirement is silently dropped.
- **Acceptance criteria fidelity is high across all requirements.** The `about_studio_description` string in §9 exactly matches REQ copy (including `&amp;` encoding). The intent data URI in the instrumented test assertion (`"https://yiji.studio"`) exactly matches the REQ-ABOUT-02 AC. Back-navigation tests enumerate both originating screens (Modern, Luopan) separately, matching REQ-ABOUT-03 one-for-one.
- **Tab-sync tests are thorough.** `tabSync_fromModern_tabUnchanged` and `tabSync_fromLuopan_tabUnchanged` verify the REQ-ABOUT-03 exclusion requirement directly — the selected tab position is unchanged after navigating to About from each primary screen.
- **No new scope creep** beyond the already-noted `launchSingleTop` defensive case.
- **No-browser NFR (REQ-ABOUT-NFR-02) has three layers of coverage:** `try/catch` in `SystemUrlLauncher`, the sealed `Result` type that makes the branch exhaustively handleable, and the Robolectric Snackbar test. The no-crash guarantee is structurally enforced, not just tested.

---

## Recommendation

**Approved with Minor Issues**

> F-01 (Low): Add a brief inline note in §6.3 or §12 cross-referencing the REQ §6 assumption that BearingHistoryFragment (if shipped concurrently) must be added to tab-sync exclusion logic and navigation test coverage. No AC changes, no implementation changes — documentation only.
