# Cross-Review: product-manager — TSPEC

**Reviewer:** product-manager
**Document reviewed:** docs/about-page/TSPEC-about-page.md (v0.1)
**Date:** 2026-04-28
**Iteration:** 1

---

## Findings

| ID | Severity | Finding | Requirement ref |
|----|----------|---------|----------------|
| F-01 | Medium | **No-browser Snackbar AC is unverified.** REQ-ABOUT-02 acceptance criterion states: *"A Snackbar appears with the text from `R.string.about_no_browser_error`; no crash occurs."* TSPEC §11.1 claims this is "covered by the unit test (`FakeUrlLauncher.result = NoBrowserFound` → Snackbar shown)" — but the three unit tests listed exercise the `UrlLauncher` protocol in isolation, not the Fragment's response. `launch_noBrowser_returnsNoBrowserFound` tests that `FakeUrlLauncher` returns `NoBrowserFound`; it does not inject that result into `AboutFragment` and assert that a Snackbar with `R.string.about_no_browser_error` is shown. The acceptance criterion "A Snackbar appears" is unverified by any test in the plan. A test must be added that wires `FakeUrlLauncher` (returning `NoBrowserFound`) into the Fragment and verifies the Snackbar text. | REQ-ABOUT-02 (no-browser AC) |
| F-02 | Low | **`launchSingleTop` behavior is beyond REQ scope.** §7 adds `launchSingleTop = true` to prevent duplicate About screen destinations when the user taps "About" while already on the About screen. The REQ does not specify this edge case. The behavior is defensive and correct — noting it so it is documented in the REQ assumptions or out-of-scope section if a future reviewer questions it. No change required. | REQ-ABOUT-03 |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | Will the Snackbar for the no-browser case be verified at unit level (using Robolectric or a Fragment test with injected `FakeUrlLauncher`) or at instrumented level (using a custom `IntentLauncher` stub that throws `ActivityNotFoundException`)? Either approach satisfies F-01 as long as the Snackbar text assertion is included. |

---

## Positive Observations

- §12 traceability matrix is complete — all 9 requirement rows (including both NFRs) map to named technical components. No requirement is silently dropped.
- REQ-ABOUT-01 both ACs (studio name, description) each have a named instrumented test — AC coverage is 1:1.
- REQ-ABOUT-03 back-navigation tests enumerate both originating screens (Modern, Luopan) separately, exactly matching the REQ acceptance criteria.
- No scope creep detected beyond the `launchSingleTop` defensive case (F-02 Low).
- The `about_studio_description` string in §9 exactly matches the copy specified in REQ-ABOUT-01, including the `&amp;` encoding — no silent rewrite.

---

## Recommendation

**Need Attention**

> F-01 (Medium): A test must be added that injects `FakeUrlLauncher` returning `NoBrowserFound` into `AboutFragment` and asserts the Snackbar with `R.string.about_no_browser_error` appears. The current unit test plan verifies the protocol only, leaving the no-browser acceptance criterion unverified.
