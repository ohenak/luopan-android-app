# Cross-Review: test-engineer — Implementation

**Reviewer:** test-engineer
**Document reviewed:** app/src/test/java/com/luopan/compass/ui/AboutFragmentLogicTest.kt, app/src/androidTest/java/com/luopan/compass/ui/AboutScreenTest.kt, app/src/test/java/com/luopan/compass/ui/FakeUrlLauncher.kt, app/src/main/java/com/luopan/compass/ui/AboutFragment.kt, app/src/main/java/com/luopan/compass/ui/UrlLauncher.kt, app/src/main/res/layout/fragment_about.xml, app/src/main/res/values/strings.xml, app/src/main/java/com/luopan/compass/ui/CompassActivity.kt, app/src/main/res/navigation/nav_graph.xml
**Date:** 2026-04-28
**Iteration:** 1

---

## Findings

| ID | Severity | Finding | Section ref |
|----|----------|---------|------------|
| F-01 | Medium | **Latent injection-timing fragility in `noBrowser_showsSnackbar` (P-07/P-18/P-19).** `launchFragmentInContainer` drives the full `onAttach → onViewCreated` lifecycle before returning. `onAttach` assigns `urlLauncher = SystemUrlLauncher(context)`. The test then replaces it via `scenario.onFragment { fragment.urlLauncher = fake }`. This is safe today because `onViewCreated` captures `urlLauncher` at click-invocation time (the listener calls `urlLauncher!!.launch(...)` dynamically). However, if the implementation were refactored to eagerly capture the launcher at `onViewCreated` time — e.g., `val launcher = urlLauncher!!; tv.setOnClickListener { launcher.launch(...) }` — the test would silently call `SystemUrlLauncher` instead of `FakeUrlLauncher`, either crashing (no real browser in Robolectric) or passing incorrectly. No comment in the test explains why post-launch injection is safe, leaving this as an undocumented fragility. Mitigation: add a code comment in `noBrowser_showsSnackbar` and `launched_doesNotShowSnackbar` noting that injection is safe because the click listener captures `urlLauncher` dynamically (at invocation, not at `onViewCreated`). | P-07, P-08, P-18, P-19 |
| F-02 | Low | **P-19 URL-capture assertion is missing from `launched_doesNotShowSnackbar` (P-08/P-20).** `noBrowser_showsSnackbar` correctly asserts `assertEquals(AboutFragment.WEBSITE_URL, fake.lastUrl)` after the click, covering P-19 for the `NoBrowserFound` path. `launched_doesNotShowSnackbar` does not include the same assertion. If a future bug caused the `Launched` branch to skip calling `urlLauncher.launch()` entirely (e.g., an accidental early return), `launched_doesNotShowSnackbar` would still pass because `onView(withText(R.string.about_no_browser_error)).check(doesNotExist())` passes trivially when the launch was never called. Adding `assertEquals(AboutFragment.WEBSITE_URL, fake.lastUrl)` to `launched_doesNotShowSnackbar` closes this gap and gives full coverage of P-20. | P-08, P-20 |
| F-03 | Low | **`nav_launchSingleTop_noStackDuplicate` (P-15) relies on an implicit assumption that the overflow menu is accessible from the About screen.** The test navigates to About, then calls `openActionBarOverflowOrOptionsMenu(...)` a second time from within About to trigger the single-top behavior. The Activity-level `MenuProvider` is always registered (lifecycle owner = Activity), so this works. However, no comment in the test notes this assumption. If the Activity were ever restructured to hide the toolbar on `dest_about`, the second `openActionBarOverflowOrOptionsMenu` call would throw `PerformException` and the test would fail with a confusing error rather than a meaningful failure message about single-top behavior. A one-line comment explaining the precondition would prevent future confusion. | P-15 |
| F-04 | Low | **REQ/string mismatch is not surfaced by any automated assertion.** `R.string.about_studio_description` ends with a trailing period ("…& I Ching.") while the REQ-ABOUT-01 acceptance criterion specifies the string as "Chinese metaphysics consultations — Feng Shui, Purple Star Astrology & I Ching" (no period). `content_descriptionVisible` correctly asserts `withText(R.string.about_studio_description)` (resource reference), so it passes regardless of the period. No test asserts the canonical literal string, meaning the REQ mismatch is invisible to CI. This was also flagged as F-01 (Low) in the PM implementation review. Resolution: either update the REQ acceptance criterion to include the trailing period, or remove the period from the string resource. The test itself does not need to change. | REQ-ABOUT-01, P-02 |

---

## Questions

| ID | Question |
|----|---------|
| Q-01 | F-01: Is the `urlLauncher` capture pattern (dynamic at invocation rather than at `onViewCreated`) an intentional design decision worth documenting in the TSPEC injection pattern section, or should the implementation be hardened with a guard comment in `AboutFragment.onViewCreated`? |
| Q-02 | F-04: Confirmed duplicate of PM implementation review F-01. Is there a decision on whether the trailing period stays (update REQ) or is removed (update string resource)? Resolving this unblocks closing both reviews. |

---

## Positive Observations

- **All 20 PROPERTIES are covered.** P-01 through P-20 map to exactly the test methods specified in the PROPERTIES §7 file mapping table. No property is untested and no test is orphaned.
- **Test levels are correctly assigned.** Robolectric is used for `FakeUrlLauncher`-based branch tests (P-07, P-08) and constant/URI-contract tests (P-04, P-05, P-19, P-20). Instrumented tests are used where real NavController, TabLayout, Activity lifecycle, and intent dispatching are required (P-01 through P-03, P-06, P-09 through P-15). This matches the PROPERTIES level key exactly.
- **`FakeUrlLauncher` is correctly designed.** It is `internal`, lives in `src/test` (not `src/main`), implements the `UrlLauncher` interface directly (protocol-based fake, not a mock framework stub), captures `lastUrl` for assertion, and exposes a configurable `result`. This is a textbook test double for the sealed-Result pattern.
- **`noBrowser_showsSnackbar` correctly exercises P-07, P-18, and P-19 together.** Single test, three properties, correct assertion order: click → Snackbar visible → `fake.lastUrl` equals `WEBSITE_URL`.
- **Tab-sync exclusion (P-13, P-14) is verified at the right level.** `activityRule.scenario.onActivity { assertEquals(...) }` runs on the main thread with the real `TabLayout` instance, giving a reliable assertion for tab position without race conditions.
- **`nav_launchSingleTop_noStackDuplicate` (P-15) correctly validates the single-top NavOptions.** Navigating to About twice and pressing back once to land on Modern is the minimal but sufficient proof that no duplicate back-stack entry was created.
- **P-16 (no `MenuProvider` in `AboutFragment`) and P-17 (no network calls):** Confirmed by code review. `AboutFragment.kt` contains no `addMenuProvider`, `requireActivity().addMenuProvider`, or any network-related import.
- **Build dependencies are correct.** `app/build.gradle.kts` declares `testImplementation(libs.fragment.testing)`, `debugImplementation(libs.fragment.testing.manifest)`, and `testImplementation(libs.espresso.core)` — all three dependencies called out in PROPERTIES §Test Dependencies are present and correctly scoped.
- **`SystemUrlLauncher` correctly uses `FLAG_ACTIVITY_NEW_TASK`** when launching the browser intent from a non-Activity context, preventing `android.util.AndroidRuntimeException` on API 26+.
- **`AboutScreenTest` `@Before`/`@After` pattern is correct.** `Intents.init()` in `@Before` and `Intents.release()` in `@After` with no pre-navigation in `@Before` — each test navigates independently, consistent with the PROPERTIES §1 navigation precondition note and the existing `LocationPermissionTest` pattern.

---

## Property Coverage Matrix

| Property | Test method | Result |
|----------|-------------|--------|
| P-01 | `content_studioNameVisible` | Covered |
| P-02 | `content_descriptionVisible` | Covered |
| P-03 | `content_websiteLabelVisible` | Covered |
| P-04 | `websiteUrl_isYijiStudio` | Covered |
| P-05 | `systemUrlLauncher_parsesUri_correctly` | Covered |
| P-06 | `websiteLink_firesActionViewIntent` | Covered |
| P-07 | `noBrowser_showsSnackbar` | Covered |
| P-08 | `launched_doesNotShowSnackbar` | Covered — see F-02 for missing `lastUrl` guard |
| P-09 | `nav_fromModern_aboutScreenShown` | Covered |
| P-10 | `nav_fromLuopan_aboutScreenShown` | Covered |
| P-11 | `nav_backFromAbout_returnsToModern` | Covered |
| P-12 | `nav_backFromAbout_returnsToLuopan` | Covered |
| P-13 | `tabSync_fromModern_tabUnchanged` | Covered |
| P-14 | `tabSync_fromLuopan_tabUnchanged` | Covered |
| P-15 | `nav_launchSingleTop_noStackDuplicate` | Covered — see F-03 for comment gap |
| P-16 | Code review | Covered — no `addMenuProvider` in `AboutFragment.kt` |
| P-17 | Code review | Covered — no network imports in `AboutFragment.kt` |
| P-18 | `noBrowser_showsSnackbar` (same as P-07) | Covered |
| P-19 | `assertEquals(WEBSITE_URL, fake.lastUrl)` in `noBrowser_showsSnackbar` | Covered on `NoBrowserFound` path only — see F-02 |
| P-20 | Implicit via P-07 and P-08 | Covered |

---

## Recommendation

**Approved with Minor Issues**

> F-01 (Medium): The post-launch injection pattern in `noBrowser_showsSnackbar` and `launched_doesNotShowSnackbar` is safe with the current implementation but is undocumented. The risk is that a future refactor of `AboutFragment.onViewCreated` could silently break the test injection without causing an obvious test failure. Add a one-line code comment in both test methods and/or in `AboutFragment.onViewCreated` explaining that the click listener must capture `urlLauncher` dynamically (not eagerly at `onViewCreated` time) for the Robolectric injection pattern to remain valid.
>
> F-02 (Low): Add `assertEquals(AboutFragment.WEBSITE_URL, fake.lastUrl)` to `launched_doesNotShowSnackbar` to fully validate P-20 and prevent a silent false-positive if the click handler were accidentally bypassed on the `Launched` path.
>
> F-03 (Low) and F-04 (Low) are documentation and copy gaps with no functional test impact. F-04 is a duplicate of PM review F-01 and should be resolved at the REQ/string level rather than the test level.
