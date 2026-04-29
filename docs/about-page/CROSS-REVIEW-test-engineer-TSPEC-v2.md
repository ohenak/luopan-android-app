# Cross-Review: test-engineer — TSPEC

**Reviewer:** test-engineer
**Document reviewed:** docs/about-page/TSPEC-about-page.md (v0.2)
**Date:** 2026-04-28
**Iteration:** 2

---

## Prior Finding Resolution

| Prior ID | Prior Severity | Status | Evidence |
|----------|---------------|--------|---------|
| F-01 | Medium | **Resolved** | §11.1 adds `noBrowser_showsSnackbar` using `FragmentScenario` / `launchFragmentInContainer`, injects `FakeUrlLauncher(result = NoBrowserFound)`, clicks `tv_about_website`, and asserts `withText(R.string.about_no_browser_error)` is displayed. The production branch in §5.3 is now exercised at the Fragment level. |
| F-02 | Low | **Resolved** | Test renamed to `websiteUrl_isYijiStudio`; asserts `AboutFragment.WEBSITE_URL == "https://yiji.studio"` against the extracted `companion object` constant. |
| F-03 | Low | **Resolved** | `tabSync_fromModern_tabUnchanged` and `tabSync_fromLuopan_tabUnchanged` added to §11.2. |
| F-04 | Low | **Resolved** | `launch_noBrowser_returnsNoBrowserFound` removed from §11.1. |
| F-05 | Low | **Resolved** | `nav_launchSingleTop_noStackDuplicate` added to §11.2. |

---

## Findings

| ID | Severity | Finding | Section ref |
|----|----------|---------|------------|
| F-01 | Medium | **`fragment-testing` dependency missing from the project — `noBrowser_showsSnackbar` will not compile.** `launchFragmentInContainer<AboutFragment>()` requires `androidx.fragment:fragment-testing` (artifact `androidx.fragment:fragment-testing`). This library is absent from `gradle/libs.versions.toml` and `app/build.gradle.kts` — neither a version entry nor a `testImplementation` declaration exists. All other Robolectric tests in the project (`LuopanViewTest`, `SettingsRepositoryTest`, `CalibrationRepositoryTest`) rely on `@RunWith(RobolectricTestRunner::class)` with `RuntimeEnvironment` or `ApplicationProvider`, none of which require `fragment-testing`. Without the missing `testImplementation(libs.androidx.fragment.testing)` entry, the test class will fail to compile. The TSPEC must declare this new dependency alongside the test description. | TSPEC §11.1, §2 (New Files) |
| F-02 | Low | **`urlLauncher` field access modifier not specified — testability gap.** §5.1 shows `private val urlLauncher: UrlLauncher` in the Fragment constructor and states "Tests override the launcher by … setting the field before `onViewCreated`." The `noBrowser_showsSnackbar` snippet does `fragment.urlLauncher = fake`, which requires the field to be a non-private `var`. The class-level snippet in §5.1 uses `private val`, which is contradictory. Without an explicit statement that the field is declared as `internal var urlLauncher: UrlLauncher` (or equivalent), an engineer writing the Fragment will likely declare it `private val` and produce an uncompilable test. | TSPEC §5.1, §11.1 |
| F-03 | Low | **`tabSync` assertion gives no retrieval pattern — insufficient detail for implementation.** Both `tabSync_fromModern_tabUnchanged` and `tabSync_fromLuopan_tabUnchanged` in §11.2 state the assertion as `tabLayout.selectedTabPosition == 0` / `== 1`. There is no existing instrumented test in the project that accesses `TabLayout.selectedTabPosition` directly; all tab interactions in `LuopanFragmentTest`, `ModeSwitcherTest`, and `NoModeSwitcherTest` use Espresso view matchers. The required pattern is `activityRule.scenario.onActivity { activity -> val tab = activity.findViewById<TabLayout>(R.id.tabLayout); assertEquals(0, tab.selectedTabPosition) }` — running inside `onActivity {}` on the main thread. Without this detail, an engineer reading only the TSPEC cannot write the assertion. | TSPEC §11.2 |
| F-04 | Low | **`systemUrlLauncher_parsesUri_correctly` misclassified as "JVM" in §11.3.** `Uri.parse()` is `android.net.Uri` — an Android framework class that returns null or throws `RuntimeException: Method not mocked` under the plain JVM runner. The test lives in `AboutFragmentLogicTest` which carries `@RunWith(RobolectricTestRunner::class)`, so the test will pass, but the §11.3 summary table classifies it as "JVM / Pure parse check, no Android framework" — which is incorrect. The classification should be "Robolectric" with the rationale "android.net.Uri requires Android runtime". This is a documentation-only error but misleads engineers about whether this test can be moved to a non-Robolectric class. | TSPEC §11.1, §11.3 |
| F-05 | Low | **`websiteLink_firesActionViewIntent` stub setup is underspecified.** The assert column references `Intents.intending(hasAction(ACTION_VIEW)).respondWith(okResult)` without defining `okResult`. The project pattern (see `LocationPermissionTest` line 225) is `Instrumentation.ActivityResult(0, null)`. An engineer unfamiliar with Espresso Intents may supply a wrong result code or omit the stub entirely, leaving the test launching a real browser intent. The table should replace `okResult` with the concrete expression `Instrumentation.ActivityResult(Activity.RESULT_OK, null)`. | TSPEC §11.2 |

---

## Questions

None.

---

## Positive Observations

- **All five prior findings from iteration 1 are addressed.** The resolution quality is high: `noBrowser_showsSnackbar` exercises the exact production path (§5.3 click handler → `if (result is NoBrowserFound) Snackbar.make(...)`), the URL constant extraction makes `websiteUrl_isYijiStudio` a proper regression guard, and the `launchSingleTop` test correctly uses one `pressBack()` to assert no duplicate destination was pushed.
- **`FakeUrlLauncher` design is correct.** Configurable `result` field, captures `lastUrl`, zero internal state leakage between tests. Protocol-based, follows the project's fake-over-mock convention consistently.
- **§11.3 test-level rationale table is thorough.** Every test is mapped to a level with a justification. The Robolectric vs. instrumented split is sound: `ActivityNotFoundException` injection genuinely cannot be done via `Intents.intending()`, so pushing to Robolectric is the right call.
- **REQ traceability in §12 is complete.** Every acceptance criterion in REQ-ABOUT-01, REQ-ABOUT-02, REQ-ABOUT-03, REQ-ABOUT-NFR-01, and REQ-ABOUT-NFR-02 maps to a named component. No requirement is silently dropped.
- **Back-navigation tests cover both originating screens.** `nav_backFromAbout_returnsToModern` and `nav_backFromAbout_returnsToLuopan` enumerate each primary-screen origin separately, one-for-one with the REQ-ABOUT-03 back-navigation AC.

---

## Recommendation

**Need Attention**

> F-01 (Medium): `androidx.fragment:fragment-testing` must be added to `gradle/libs.versions.toml` and declared as `testImplementation` in `app/build.gradle.kts`. The TSPEC §2 (New Files) or §11.1 must document this dependency so the implementor knows to add it before writing the test. Without it, `launchFragmentInContainer` does not resolve and the Robolectric test class will not compile.
