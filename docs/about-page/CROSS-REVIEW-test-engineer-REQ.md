# Cross-Review: test-engineer — REQ

**Reviewer:** test-engineer
**Document reviewed:** docs/about-page/REQ-about-page.md
**Date:** 2026-04-28
**Iteration:** 1

---

## Findings

| ID | Severity | Finding | Section ref |
|----|----------|---------|------------|
| F-01 | Medium | **REQ-ABOUT-02 acceptance criterion is not directly automatable as written.** "https://yiji.studio opens in the device's default browser" describes OS-level behavior that Espresso cannot assert directly. The testable property is the *intent* fired: `ACTION_VIEW` with data URI `https://yiji.studio`. Additionally, "the app remains open in the background" is a natural consequence of `startActivity()` without `finish()` — it is not a separately verifiable property at the Espresso level and should be removed or restated as "the About screen remains in the back stack." The criterion must be rewritten to reference the intent so a test can use `Intents.intended(hasData("https://yiji.studio"))` with the `espresso-intents` library. | REQ-ABOUT-02 |
| F-02 | Medium | **REQ-ABOUT-03 "from any primary screen" is not enumerated.** The existing codebase has two primary screens (ModernCompassFragment, LuopanFragment). Without enumerating them, a test engineer cannot write exhaustive navigation tests and cannot detect regressions when new screens are added. The acceptance criterion must either list the specific originating screens or define "primary screen" with a reference to the nav graph destinations. Back-press behavior must also be tested from each named originating screen. | REQ-ABOUT-03 |
| F-03 | Low | **REQ-ABOUT-01 description content unspecified — test can only assert visibility, not correctness.** "A brief description are visible" gives no expected string. A test can only check `isDisplayed()` on a view, not that the correct description text is present. This allows a blank or wrong description to pass. The acceptance criterion should specify either the exact string resource name or a meaningful substring that must be present. | REQ-ABOUT-01 |
| F-04 | Low | **Overflow menu item label not specified — test interactions are fragile without it.** Espresso tests that open the overflow menu and select "About" need either the exact menu item text string or a string resource ID. The REQ does not name either. Without this, tests must hardcode a string that may not match the implementation. | REQ-ABOUT-03 |
| F-05 | Low | **No negative property for the browser intent failure path.** What must happen when no browser is installed and the user taps the website link is undefined. The absence of a required behavior here means no negative test can be written. At minimum, the REQ should state whether a Toast, Snackbar, or silent no-op is acceptable. | REQ-ABOUT-02 |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | Should the website link test use `Intents.intending()` to stub the browser, or is it acceptable to let the intent fire and use `Intents.intended()` to verify after the fact? Stubbing is safer in CI (no real browser needed) and should be the default. |
| Q-02 | Is BearingHistoryFragment (Phase 4) considered a "primary screen" for REQ-ABOUT-03? If it ships before or concurrently with the About screen, it must be included in navigation tests. |

---

## Positive Observations

- Three requirements are sufficiently small and independent that each maps cleanly to a separate Espresso test class.
- REQ-ABOUT-02 "device's default browser" rules out WebView, keeping test scope simple — no in-app web content lifecycle to test.
- The existing `ModeSwitcherTest` and `DeclinationInfoPanelTest` instrumented tests establish Espresso patterns (overflow menu interaction, intent verification, navigation back-stack) that the About screen tests can follow directly.
- Static content means no async loading states, no ViewModel, no fake/stub infrastructure beyond intent stubbing for the URL.

---

## Recommendation

**Need Attention**

> F-01 (Medium): REQ-ABOUT-02 acceptance criterion must be rewritten as a verifiable intent property rather than observed OS behavior.
> F-02 (Medium): REQ-ABOUT-03 must enumerate the specific originating screens covered by "any primary screen."
