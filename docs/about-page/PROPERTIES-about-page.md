# PROPERTIES-about-page: Testable System Properties

## About Screen — YiJi Studio

| Field | Value |
|-------|-------|
| **Date** | 2026-04-28 |
| **Branch** | feat-about-page |
| **Input REQ** | docs/about-page/REQ-about-page.md v0.2 |
| **Input TSPEC** | docs/about-page/TSPEC-about-page.md v0.4 |
| **Input PLAN** | docs/about-page/PLAN-about-page.md |

---

## Test Level Key

| Level | Runner | When to use |
|-------|--------|-------------|
| **Unit-Robolectric** | RobolectricTestRunner | Android classes (`Uri`, Fragment lifecycle, Snackbar) required but no device |
| **Instrumented** | AndroidJUnitRunner (device/emulator) | Real NavController, TabLayout, Activity lifecycle, intent dispatching |
| **Code Review** | Manual | Properties that are verified by absence of code constructs |

> **Note:** All tests in `AboutFragmentLogicTest` run under `@RunWith(RobolectricTestRunner::class)`. There are no plain-JVM tests — even the `WEBSITE_URL` constant assertion runs in the Robolectric runner because the file-level `@RunWith` annotation applies to all methods.

## Test Dependencies

The Robolectric tests in `AboutFragmentLogicTest` (P-04, P-05, P-07, P-08, P-18–P-20) require two `testImplementation` dependencies that are not yet in the project:

| Dependency | Required by | Task |
|------------|-------------|------|
| `testImplementation(libs.fragment.testing)` | `launchFragmentInContainer` in P-07, P-08 | PLAN Task 0.3 |
| `testImplementation(libs.espresso.core)` | `onView`, `withText`, `matches`, `isDisplayed`, `doesNotExist` in P-07, P-08 | PLAN Task 0.4 |

`espresso-core` is currently `androidTestImplementation` only. Without a `testImplementation` declaration, the Robolectric test class will not compile.

---

## 1. Content Properties (REQ-ABOUT-01)

> **Navigation precondition for §1 and P-06:** Each instrumented test navigates to About in its own body (`openActionBarOverflowOrOptionsMenu(…); onView(withText(R.string.menu_about)).perform(click())`). The `@Before` method calls `Intents.init()` only and does not pre-navigate.

| ID | Property | Category | Level | Test Method | Assertion |
|----|----------|----------|-------|-------------|-----------|
| P-01 | `AboutFragment` **must** display the text from `R.string.about_studio_name` ("易機閣 / YiJi Studio") | Functional | Instrumented | `content_studioNameVisible` | Navigate to About; `onView(withText(R.string.about_studio_name)).check(matches(isDisplayed()))` |
| P-02 | `AboutFragment` **must** display the text from `R.string.about_studio_description` | Functional | Instrumented | `content_descriptionVisible` | Navigate to About; `onView(withText(R.string.about_studio_description)).check(matches(isDisplayed()))` |
| P-03 | `AboutFragment` **must** display the text from `R.string.about_website_label` ("yiji.studio") | Functional | Instrumented | `content_websiteLabelVisible` | Navigate to About; `onView(withText(R.string.about_website_label)).check(matches(isDisplayed()))` |

---

## 2. URL / Intent Properties (REQ-ABOUT-02)

| ID | Property | Category | Level | Test Method | Assertion |
|----|----------|----------|-------|-------------|-----------|
| P-04 | `AboutFragment.WEBSITE_URL` **must** equal `"https://yiji.studio"` | Contract | Unit-Robolectric | `websiteUrl_isYijiStudio` | `assertEquals("https://yiji.studio", AboutFragment.WEBSITE_URL)` |
| P-05 | `Uri.parse(AboutFragment.WEBSITE_URL)` **must** produce a URI with scheme `"https"` and host `"yiji.studio"` | Contract | Unit-Robolectric | `systemUrlLauncher_parsesUri_correctly` | `assertEquals("https", uri.scheme); assertEquals("yiji.studio", uri.host)` |
| P-06 | Tapping `tv_about_website` **must** fire an `ACTION_VIEW` intent with data URI exactly `https://yiji.studio` | Functional | Instrumented | `websiteLink_firesActionViewIntent` | Navigate to About; stub `Intents.intending(hasAction(ACTION_VIEW)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))`; tap `tv_about_website`; `Intents.intended(allOf(hasAction(Intent.ACTION_VIEW), hasData("https://yiji.studio")))` |
| P-07 | When `UrlLauncher.launch()` returns `NoBrowserFound`, tapping `tv_about_website` **must** show a Snackbar with text from `R.string.about_no_browser_error` | Error Handling | Unit-Robolectric | `noBrowser_showsSnackbar` | `onView(withText(R.string.about_no_browser_error)).check(matches(isDisplayed()))` after injecting `FakeUrlLauncher(result = NoBrowserFound)` via `FragmentScenario.onFragment { }` |
| P-08 (negative) | When `UrlLauncher.launch()` returns `Launched`, tapping `tv_about_website` **must NOT** show a Snackbar | Error Handling | Unit-Robolectric | `launched_doesNotShowSnackbar` | `onView(withText(R.string.about_no_browser_error)).check(doesNotExist())` after injecting `FakeUrlLauncher(result = Launched)` |

---

## 3. Navigation Properties (REQ-ABOUT-03)

| ID | Property | Category | Level | Test Method | Assertion |
|----|----------|----------|-------|-------------|-----------|
| P-09 | Opening overflow and tapping `menu_about` **from Modern Mode** **must** navigate to `AboutFragment` | Functional | Instrumented | `nav_fromModern_aboutScreenShown` | `openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext); onView(withText(R.string.menu_about)).perform(click()); onView(withId(R.id.tv_about_studio_name)).check(matches(isDisplayed()))` |
| P-10 | Opening overflow and tapping `menu_about` **from Luopan Mode** **must** navigate to `AboutFragment` | Functional | Instrumented | `nav_fromLuopan_aboutScreenShown` | Switch to Luopan tab; `openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext); onView(withText(R.string.menu_about)).perform(click()); onView(withId(R.id.tv_about_studio_name)).check(matches(isDisplayed()))` |
| P-11 | Pressing back from About (entered from Modern) **must** return to Modern Mode (`R.id.compassRose` visible) | Functional | Instrumented | `nav_backFromAbout_returnsToModern` | Navigate to About from Modern; `pressBack(); onView(withId(R.id.compassRose)).check(matches(isDisplayed()))` |
| P-12 | Pressing back from About (entered from Luopan) **must** return to Luopan Mode (`R.id.luopanView` visible) | Functional | Instrumented | `nav_backFromAbout_returnsToLuopan` | Navigate to About from Luopan; `pressBack(); onView(withId(R.id.luopanView)).check(matches(isDisplayed()))` |
| P-13 | Navigating to About from Modern Mode **must NOT** change the active tab (TabLayout position remains `0`) | Functional | Instrumented | `tabSync_fromModern_tabUnchanged` | `openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext); onView(withText(R.string.menu_about)).perform(click()); activityRule.scenario.onActivity { assertEquals(0, it.findViewById<TabLayout>(R.id.tabLayout).selectedTabPosition) }` |
| P-14 | Navigating to About from Luopan Mode **must NOT** change the active tab (TabLayout position remains `1`) | Functional | Instrumented | `tabSync_fromLuopan_tabUnchanged` | Switch to Luopan tab; `openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext); onView(withText(R.string.menu_about)).perform(click()); activityRule.scenario.onActivity { assertEquals(1, it.findViewById<TabLayout>(R.id.tabLayout).selectedTabPosition) }` |
| P-15 | Tapping "About" while already on the About screen **must NOT** push a duplicate `dest_about` onto the back stack; pressing back **must** return to the originating primary screen (TSPEC §7) | Functional | Instrumented | `nav_launchSingleTop_noStackDuplicate` | Navigate to About from Modern; `openActionBarOverflowOrOptionsMenu(...); onView(withText(R.string.menu_about)).perform(click()); pressBack(); onView(withId(R.id.compassRose)).check(matches(isDisplayed()))`. Luopan entry path is mechanically identical (same `launchSingleTop` flag) — one test is sufficient. |
| P-16 (negative) | `AboutFragment` **must NOT** register a `MenuProvider`; the About menu item is provided exclusively by the Activity-level `MenuProvider` (TSPEC §5.4) | Contract | Code Review | — | Verify `AboutFragment.kt` contains no call to `addMenuProvider(...)` or `requireActivity().addMenuProvider(...)` |

---

## 4. Non-Functional Properties

| ID | Property | Category | Level | Test Method | Assertion |
|----|----------|----------|-------|-------------|-----------|
| P-17 | `AboutFragment` **must NOT** make any network calls; all displayed text comes from bundled string resources | Contract | Code Review | — | Verify `AboutFragment.kt` contains no imports of `okhttp3`, `retrofit2`, `java.net.URL`, `java.net.HttpURLConnection`, or coroutine network dispatchers |
| P-18 | Tapping `tv_about_website` when no browser is installed **must not** crash the app (`ActivityNotFoundException` is caught) | Error Handling | Unit-Robolectric | `noBrowser_showsSnackbar` (same as P-07) | Test completes without `ActivityNotFoundException` propagating; Snackbar shown instead |

---

## 5. Test Double Contract Properties

These verify that `FakeUrlLauncher` correctly models the `UrlLauncher` protocol — needed as infrastructure for P-07 and P-08.

> **Placement note:** `FakeUrlLauncher` must live in `app/src/test/java/com/luopan/compass/ui/` (not `src/main`). It is declared `internal` so Robolectric tests in the same module can access it, but it is never compiled into the production APK. See TSPEC §2 for the updated file listing.

| ID | Property | Category | Level | Test Method | Assertion |
|----|----------|----------|-------|-------------|-----------|
| P-19 | `FakeUrlLauncher.launch(url)` **must** capture the URL in `lastUrl` | Contract | Unit-Robolectric | (add `assertEquals(AboutFragment.WEBSITE_URL, fake.lastUrl)` at end of `noBrowser_showsSnackbar`) | `assertEquals(AboutFragment.WEBSITE_URL, fake.lastUrl)` asserted after `perform(click())` in `noBrowser_showsSnackbar` |
| P-20 | `FakeUrlLauncher.launch(url)` **must** return the configured `result` value | Contract | Unit-Robolectric | (validated implicitly by P-07 and P-08) | `fake.result = NoBrowserFound → launch(url) returns NoBrowserFound` |

---

## 6. Coverage Matrix

| Requirement | Properties | Gap? |
|-------------|-----------|------|
| REQ-ABOUT-01 (studio name + description visible) | P-01, P-02 | None |
| REQ-ABOUT-01 (website label visible) | P-03 | None |
| REQ-ABOUT-02 (ACTION_VIEW intent fired) | P-04, P-05, P-06 | None |
| REQ-ABOUT-02 (no-browser Snackbar) | P-07, P-18 | None |
| REQ-ABOUT-02 (no Snackbar on success) | P-08 | None |
| REQ-ABOUT-03 (navigation from Modern) | P-09 | None |
| REQ-ABOUT-03 (navigation from Luopan) | P-10 | None |
| REQ-ABOUT-03 (back to Modern) | P-11 | None |
| REQ-ABOUT-03 (back to Luopan) | P-12 | None |
| REQ-ABOUT-03 (tab-sync exclusion) | P-13, P-14 | None |
| REQ-ABOUT-03 (no duplicate destination) | P-15 | None |
| REQ-ABOUT-NFR-01 (no network calls) | P-17 | Code review only — acceptable for static screen |
| REQ-ABOUT-NFR-02 (no crash) | P-18 | `SystemUrlLauncher`'s `try/catch ActivityNotFoundException` is not directly exercised by any property — `FakeUrlLauncher` is used instead. The catch clause is verified by code review of `SystemUrlLauncher.launch()`. Accepted gap: adding a `ShadowPackageManager` Robolectric test is deferred. |
| Test infrastructure fidelity | P-19, P-20 | None |

---

## 7. Test File Mapping

| Test File | Properties covered |
|-----------|--------------------|
| `app/src/test/java/com/luopan/compass/ui/AboutFragmentLogicTest.kt` | P-04, P-05, P-07, P-08, P-18, P-19, P-20 |
| `app/src/androidTest/java/com/luopan/compass/ui/AboutScreenTest.kt` | P-01, P-02, P-03, P-06, P-09, P-10, P-11, P-12, P-13, P-14, P-15 |
| Code Review | P-16, P-17 |
