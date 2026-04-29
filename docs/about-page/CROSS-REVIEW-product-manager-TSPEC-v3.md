# Cross-Review: product-manager — TSPEC

**Reviewer:** product-manager
**Document reviewed:** docs/about-page/TSPEC-about-page.md (v0.3)
**Date:** 2026-04-28
**Iteration:** 3

---

## Prior Finding Resolution

| Prior ID | Prior Severity | Status | Evidence |
|----------|---------------|--------|---------|
| v1 F-01 | Medium → Resolved in v0.2 | **Confirmed resolved** | `noBrowser_showsSnackbar` in §11.1 continues to wire `FakeUrlLauncher(result = NoBrowserFound)` into the Fragment and assert Snackbar text. No regression in v0.3. |
| v1 F-02 | Low → Acknowledged | **Unchanged / acceptable** | `launchSingleTop` defensive behavior in §7 remains beyond REQ scope; still Low and still acceptable. |
| v2 F-01 | Low | **Still open** | The v0.3 change set does not include any inline note in §6.3 or §12 cross-referencing the REQ §6 assumption about BearingHistoryFragment. Neither section carries the forward-risk note. Carried forward below as F-01. |

---

## Findings

| ID | Severity | Finding | Requirement ref |
|----|----------|---------|----------------|
| F-01 | Low | **BearingHistoryFragment concurrency risk note still absent from TSPEC.** REQ §6 Assumptions states: *"BearingHistoryFragment (Phase 4) is out of scope for this feature's navigation tests; if it ships concurrently, its screen must be added to the acceptance criteria for REQ-ABOUT-03."* This forward-risk note has not been added to §6.3 (tab-sync exclusion), §7 (navigation graph), or §12 (traceability) across two revision cycles. An implementor reading only the TSPEC continues to have no signal that `dest_bearing_history` must be added to the `else -> return` exclusion branch and the navigation test matrix if Phase 4 ships concurrently. One sentence cross-referencing the REQ assumption in §6.3 or §12 would close this. No AC changes and no implementation changes are required. | REQ-ABOUT-03 |

---

## v0.3 Change Assessment

| Change | Product impact | Assessment |
|--------|---------------|------------|
| `fragment-testing` dependency added to §3 Modified Files | None — test infrastructure only | Correct. Completes the declared dependency for `launchFragmentInContainer` used in `noBrowser_showsSnackbar`; no product scope change. |
| `urlLauncher` changed to `internal var` in §5.1 | None — visibility change for test injection | Correct. No user-visible behavior affected; enables Robolectric injection pattern without reflection. |
| Concrete `TabLayout.selectedTabPosition` retrieval in tabSync tests | Strengthens REQ-ABOUT-03 tab-sync AC verification | Positive. The explicit `assertEquals(0/1, selectedTabPosition)` assertion directly verifies that navigating to About does not alter the active tab — exactly what the REQ-ABOUT-03 tab-sync exclusion requirement demands. |
| `okResult` defined as `Instrumentation.ActivityResult(Activity.RESULT_OK, null)` in §11.2 | Completes REQ-ABOUT-02 happy-path test scaffolding | Correct. The `okResult` stub prevents the real browser from launching during the instrumented intent test, allowing `Intents.intended()` to verify the URI. No AC narrowing. |
| `Uri.parse` test reclassified from JVM to Robolectric in §11.3 | None — test runner reclassification only | Correct. The assertion (`scheme == "https"`, `host == "yiji.studio"`) still executes and still guards the REQ-ABOUT-02 URI AC. No coverage drop. |

---

## Questions

None.

---

## Positive Observations

- **All P0 requirements remain fully covered.** REQ-ABOUT-01 (studio identity), REQ-ABOUT-02 (ACTION_VIEW intent + no-browser Snackbar), REQ-ABOUT-03 (overflow navigation, back-press from both origins, tab-sync exclusion), REQ-ABOUT-NFR-01 (static content), and REQ-ABOUT-NFR-02 (no-crash fallback) each map to named technical components in §12. No requirement is silently dropped in v0.3.
- **Acceptance criteria fidelity is maintained.** The `about_studio_description` string in §9 continues to match REQ copy. The intent data URI assertion (`"https://yiji.studio"`) exactly matches REQ-ABOUT-02's happy-path AC. Back-navigation tests enumerate both originating screens (Modern, Luopan) individually, matching REQ-ABOUT-03 one-for-one.
- **Tab-sync verification is now more precise.** The v0.3 addition of `TabLayout.selectedTabPosition` retrieval makes the tabSync tests directly observable — the concrete `assertEquals(0, …)` / `assertEquals(1, …)` assertions leave no ambiguity about what "tab unchanged" means in product terms.
- **No new scope creep.** All five v0.3 changes are infrastructure or test-precision improvements. No new user-visible behavior is introduced beyond what the REQ specifies.
- **No-browser coverage remains three-layered.** `try/catch ActivityNotFoundException` in `SystemUrlLauncher`, sealed `Result` type for exhaustive handling, and the Robolectric `noBrowser_showsSnackbar` test — all intact in v0.3.

---

## Recommendation

**Approved with Minor Issues**

> F-01 (Low — carried from v2): Add a single inline note in §6.3 or §12 cross-referencing the REQ §6 assumption that BearingHistoryFragment (Phase 4), if shipped concurrently, must be added to the `else -> return` tab-sync exclusion branch and the navigation test matrix. No AC changes, no implementation changes, no code changes required — documentation only.
