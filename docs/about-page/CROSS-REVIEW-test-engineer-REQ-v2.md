# Cross-Review: test-engineer — REQ

**Reviewer:** test-engineer
**Document reviewed:** docs/about-page/REQ-about-page.md (v0.2)
**Date:** 2026-04-28
**Iteration:** 2

---

## Prior Findings Resolution Check

| Prior ID | Finding | Status |
|----------|---------|--------|
| TE-F-01 | REQ-ABOUT-02 AC not automatable | ✅ Resolved — criterion now asserts `ACTION_VIEW` intent with data URI `https://yiji.studio`; directly testable via `Intents.intended()` (already used in `LocationPermissionTest`) |
| TE-F-02 | "Any primary screen" unenumerated | ✅ Resolved — REQ-ABOUT-03 now names ModernCompassFragment and LuopanFragment explicitly |
| TE-F-03 | Description string unspecified | ✅ Resolved — `R.string.about_studio_description` named with exact copy |
| TE-F-04 | Menu item label unspecified | ✅ Resolved — `R.string.menu_about` named |
| TE-F-05 | No-browser failure behavior undefined | ✅ Resolved — Snackbar with `R.string.about_no_browser_error` specified |

---

## Findings

| ID | Severity | Finding | Section ref |
|----|----------|---------|------------|
| F-01 | Low | **REQ-ABOUT-NFR-01 has no measurable acceptance criterion.** "No network request is required to render the screen" is correct but untestable in an automated test without StrictMode instrumentation or a custom network interceptor. For a purely static screen this is verifiable by code review (no Retrofit/OkHttp/URLConnection calls in the implementation), but the NFR should either note "verified by code review" or be promoted to an Assumption rather than an NFR. Not blocking. | REQ-ABOUT-NFR-01 |
| F-02 | Low | **No-browser Snackbar test requires architectural note for TSPEC.** `Intents.intending()` stubs intent launch results but does not simulate `ActivityNotFoundException`. Testing the no-browser path will require either (a) extracting the intent dispatch to an injectable `UrlLauncher` interface that can be faked, or (b) unit-testing the catch-and-Snackbar branch in isolation. The REQ correctly specifies the behavior; this is noted so the TSPEC author plans the abstraction proactively rather than discovering it during implementation. | REQ-ABOUT-02 (no-browser AC) |

---

## Questions

None.

---

## Positive Observations

- Intent-based AC for REQ-ABOUT-02 is precisely automatable. The `espresso-intents` library is already a project dependency (`LocationPermissionTest` uses `Intents.init()` / `Intents.intending()` / `Intents.intended()`); no new test infrastructure needed.
- `R.string.about_studio_description` with exact copy means the test can use `onView(withText(R.string.about_studio_description)).check(matches(isDisplayed()))` — no hardcoded strings, locale-safe.
- Back-navigation ACs enumerate both originating screens (Modern, Luopan) — two discrete test cases, no ambiguity.
- `R.string.menu_about` allows Espresso to select the menu item via `onView(withText(R.string.menu_about)).perform(click())` — consistent with existing menu test patterns in `LuopanFragmentTest`.
- Static screen means no async state to manage in tests — no `IdlingResource`, no coroutine test dispatchers.

---

## Recommendation

**Approved with Minor Issues**

> No Medium or High findings remain. F-01 and F-02 are Low — both deferred to TSPEC without blocking REQ approval.
