# PLAN-about-page: Execution Plan

## About Screen — YiJi Studio

| Field | Value |
|-------|-------|
| **Date** | 2026-04-28 |
| **Branch** | feat-about-page |
| **Input TSPEC** | docs/about-page/TSPEC-about-page.md v0.4 |
| **Input REQ** | docs/about-page/REQ-about-page.md v0.2 |

---

## Summary

Implement a static About screen (`dest_about`) accessible from a persistent Activity-level overflow menu on all primary screens. The screen shows the studio name, description, and a tappable website link (`https://yiji.studio`). A `UrlLauncher` interface with `SystemUrlLauncher` and `FakeUrlLauncher` implementations provides the injectable seam for the no-browser Snackbar branch. All work follows strict TDD: test written first (Red), then production code (Green), then clean-up (Refactored).

---

## Status Key

| Symbol | Meaning |
|--------|---------|
| ⬚ | Not Started |
| 🔴 | Red — test written, failing |
| 🟢 | Green — test passing |
| 🔵 | Refactored |
| ✅ | Done |

---

## Phase 0 — Build Setup

Must complete before any test in Phase 3 can compile.

| # | Task | Test File | Source File | Status |
|---|------|-----------|-------------|--------|
| 0.1 | Add `fragment = "1.6.1"` version to `[versions]` in `libs.versions.toml` | — | `gradle/libs.versions.toml` | ⬚ |
| 0.2 | Add `fragment-testing = { group = "androidx.fragment", name = "fragment-testing", version.ref = "fragment" }` to `[libraries]` in `libs.versions.toml` | — | `gradle/libs.versions.toml` | ⬚ |
| 0.3 | Add `testImplementation(libs.fragment.testing)` to `app/build.gradle.kts` | — | `app/build.gradle.kts` | ⬚ |
| 0.4 | Add `testImplementation(libs.espresso.core)` to `app/build.gradle.kts` — required by `onView`/`withText`/`isDisplayed`/`doesNotExist` in Robolectric tests (`espresso-core` is currently `androidTestImplementation` only) | — | `app/build.gradle.kts` | ⬚ |

**Dependency:** Tasks 0.1 → 0.2 → 0.3 → 0.4 (sequential within phase).

---

## Phase 1 — UrlLauncher Protocol

Create the injectable seam and its test double. No TDD cycle needed for the interface/fake themselves — they are test infrastructure, not production logic.

| # | Task | Test File | Source File | Status |
|---|------|-----------|-------------|--------|
| 1.1 | Create `UrlLauncher` interface with sealed `Result` (Launched, NoBrowserFound) | — | `app/src/main/java/com/luopan/compass/ui/UrlLauncher.kt` | ⬚ |
| 1.2 | Implement `SystemUrlLauncher(context: Context)` — `startActivity(ACTION_VIEW)`, catch `ActivityNotFoundException` → return `NoBrowserFound` | — | `app/src/main/java/com/luopan/compass/ui/UrlLauncher.kt` | ⬚ |
| 1.3 | Create `FakeUrlLauncher` as `internal class` — `var result = Launched`, `var lastUrl: String?`, captures `lastUrl` on `launch()`. Lives in `src/test` (not `src/main`) so it is never compiled into the production APK | — | `app/src/test/java/com/luopan/compass/ui/FakeUrlLauncher.kt` | ⬚ |

**Dependency:** Phase 0 complete (build sync needed before IDE compilation).

---

## Phase 2 — Resources (parallelisable)

String resources, layouts, and the menu file are pure XML with no runtime dependencies between them — all three tasks can be dispatched in parallel.

| # | Task | Test File | Source File | Status |
|---|------|-----------|-------------|--------|
| 2.1 | Add 5 string resources to `strings.xml`: `menu_about`, `about_studio_name`, `about_studio_description`, `about_website_label`, `about_no_browser_error` | — | `app/src/main/res/values/strings.xml` | ⬚ |
| 2.2 | Create `fragment_about.xml`: `ScrollView` → `LinearLayout` (vertical, 24dp padding) → `tv_about_studio_name` (Headline5), 8dp Space, `tv_about_description` (Body1), 24dp Space, `tv_about_website` (Body1, textColorPrimary, clickable, focusable) | — | `app/src/main/res/layout/fragment_about.xml` | ⬚ |
| 2.3 | Create `menu_about.xml`: single item `action_about`, title `@string/menu_about`, `showAsAction="never"` | — | `app/src/main/res/menu/menu_about.xml` | ⬚ |
| 2.4 | Add `MaterialToolbar` (id=`toolbar`, `match_parent` × `?attr/actionBarSize`, `colorSurface` bg) as first child of `activity_compass.xml` root `LinearLayout`, above `NavHostFragment` | — | `app/src/main/res/layout/activity_compass.xml` | ⬚ |

**Dependency:** Phase 1 complete (not technically required for XML, but avoids merge conflicts). Tasks 2.1–2.4 can run in parallel.

---

## Phase 3 — AboutFragment (TDD)

Write the Robolectric unit tests first (Red), then implement `AboutFragment` to make them pass (Green).

> **Red-state clarification:** In Tasks 3.1, 3.3, and 3.5 the expected Red state is a **compilation failure**, not a runtime test failure. `AboutFragment` does not exist at 3.1; the click handler does not exist at 3.5. Do not stub-create `AboutFragment` before Task 3.2 — the compilation error itself is the Red state that drives implementation.

| # | Task | Test File | Source File | Status |
|---|------|-----------|-------------|--------|
| 3.1 | **Red** — Write `AboutFragmentLogicTest` with `websiteUrl_isYijiStudio`: assert `AboutFragment.WEBSITE_URL == "https://yiji.studio"`. Annotate `@RunWith(RobolectricTestRunner::class)`. Test fails: `AboutFragment` does not exist yet. | `app/src/test/java/com/luopan/compass/ui/AboutFragmentLogicTest.kt` | — | ⬚ |
| 3.2 | **Green** — Create `AboutFragment` skeleton: `internal var urlLauncher: UrlLauncher? = null`, `companion object { const val WEBSITE_URL = "https://yiji.studio" }`, `onAttach` assigns `SystemUrlLauncher(context)` if null. Test 3.1 passes. | `app/src/test/java/com/luopan/compass/ui/AboutFragmentLogicTest.kt` | `app/src/main/java/com/luopan/compass/ui/AboutFragment.kt` | ⬚ |
| 3.3 | **Red** — Add `systemUrlLauncher_parsesUri_correctly` to `AboutFragmentLogicTest`: `Uri.parse(AboutFragment.WEBSITE_URL)` scheme == "https", host == "yiji.studio". Test fails until `Uri` is available under Robolectric (already wired by `@RunWith`). | `app/src/test/java/com/luopan/compass/ui/AboutFragmentLogicTest.kt` | — | ⬚ |
| 3.4 | **Green** — No production code change needed; Robolectric stubs `android.net.Uri`. Verify test 3.3 passes. | `app/src/test/java/com/luopan/compass/ui/AboutFragmentLogicTest.kt` | — | ⬚ |
| 3.5 | **Red** — Add `noBrowser_showsSnackbar` to `AboutFragmentLogicTest`: `val scenario = launchFragmentInContainer<AboutFragment>()`, then `scenario.onFragment { it.urlLauncher = FakeUrlLauncher().apply { result = NoBrowserFound } }` (injection must happen *after* `launchFragmentInContainer` returns and *before* `perform(click())`), then click `tv_about_website`, assert `onView(withText(R.string.about_no_browser_error)).check(matches(isDisplayed()))`. Test fails: `onViewCreated` click handler not yet implemented. | `app/src/test/java/com/luopan/compass/ui/AboutFragmentLogicTest.kt` | — | ⬚ |
| 3.6 | **Green** — Implement `AboutFragment.onCreateView` (inflate `fragment_about.xml`) and `onViewCreated` click handler: `urlLauncher.launch(WEBSITE_URL)`; if `NoBrowserFound` → `Snackbar.make(requireView(), R.string.about_no_browser_error, LENGTH_LONG).show()`. Test 3.5 passes. | `app/src/test/java/com/luopan/compass/ui/AboutFragmentLogicTest.kt` | `app/src/main/java/com/luopan/compass/ui/AboutFragment.kt` | ⬚ |
| 3.7 | **Refactor** — Review `AboutFragment`: clean up any nullability handling on `urlLauncher`, ensure `onAttach` guard is tight. Re-run all four Robolectric tests; all green. | `app/src/test/java/com/luopan/compass/ui/AboutFragmentLogicTest.kt` | `app/src/main/java/com/luopan/compass/ui/AboutFragment.kt` | ⬚ |
| 3.8 | **Red** — Add `launched_doesNotShowSnackbar` to `AboutFragmentLogicTest`: inject `FakeUrlLauncher(result = Launched)`, click `tv_about_website`, assert `onView(withText(R.string.about_no_browser_error)).check(doesNotExist())`. Test is expected to fail until the click handler is confirmed not to show the Snackbar on success. | `app/src/test/java/com/luopan/compass/ui/AboutFragmentLogicTest.kt` | — | ⬚ |
| 3.9 | **Green** — No production code change needed; the click handler added in 3.6 only calls `Snackbar.make` in the `NoBrowserFound` branch. Verify test 3.8 passes after 3.6 implementation is in place. | `app/src/test/java/com/luopan/compass/ui/AboutFragmentLogicTest.kt` | — | ⬚ |

**Dependency:** Phase 2 complete (fragment_about.xml and strings must exist for `onCreateView` to compile). Phase 1 complete (UrlLauncher must exist).

---

## Phase 4 — Navigation + Activity Integration

Wire `dest_about` into the NavController and configure `CompassActivity`.

| # | Task | Test File | Source File | Status |
|---|------|-----------|-------------|--------|
| 4.1 | Add `dest_about` fragment destination to `nav_graph.xml`: `android:name="com.luopan.compass.ui.AboutFragment"`, `android:label="About"` | — | `app/src/main/res/navigation/nav_graph.xml` | ⬚ |
| 4.2 | Update `CompassActivity.onCreate`: `val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)` → `setSupportActionBar(toolbar)` | — | `app/src/main/java/com/luopan/compass/ui/CompassActivity.kt` | ⬚ |
| 4.3 | Add Activity-level `MenuProvider` to `CompassActivity.onCreate` (after `wireTabNavigation()`): inflate `menu_about.xml`; on `action_about` → `navController.navigate(R.id.dest_about, null, NavOptions.Builder().setLaunchSingleTop(true).build())` | — | `app/src/main/java/com/luopan/compass/ui/CompassActivity.kt` | ⬚ |
| 4.4 | Add inline comment in `wireTabNavigation()` destination listener `else -> return` branch: `// dest_about and any future non-tab destinations fall here — no tab selection change` | — | `app/src/main/java/com/luopan/compass/ui/CompassActivity.kt` | ⬚ |

**Dependency:** Phase 3 complete (AboutFragment must exist before it can be added to nav_graph). Tasks 4.1–4.4 are sequential (4.1 must precede 4.2–4.4 to avoid R.id.dest_about unresolved). Phase 4 has no dedicated test tasks — all Phase 4 behaviour is verified by the Phase 5 instrumented tests (`nav_fromModern_aboutScreenShown`, `tabSync_*`, `nav_launchSingleTop_noStackDuplicate`).

---

## Phase 5 — Instrumented Tests

Write and verify `AboutScreenTest` against the running app. These run on a device/emulator.

| # | Task | Test File | Source File | Status |
|---|------|-----------|-------------|--------|
| 5.1 | Create `AboutScreenTest` with `@Before` (`Intents.init()` only — do NOT navigate to About here; each test navigates as part of its own Given) and `@After` (`Intents.release()`), following `LocationPermissionTest` pattern. Add 3 content visibility tests: `content_studioNameVisible`, `content_descriptionVisible`, `content_websiteLabelVisible` — each opens overflow and taps `menu_about` before asserting. | `app/src/androidTest/java/com/luopan/compass/ui/AboutScreenTest.kt` | — | ⬚ |
| 5.2 | Add `websiteLink_firesActionViewIntent`: navigate to About first, stub `Intents.intending(hasAction(ACTION_VIEW)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))`, tap `tv_about_website`, assert `Intents.intended(allOf(hasAction(ACTION_VIEW), hasData("https://yiji.studio")))` | `app/src/androidTest/java/com/luopan/compass/ui/AboutScreenTest.kt` | — | ⬚ |
| 5.3 | Add 2 navigation-from tests: `nav_fromModern_aboutScreenShown`, `nav_fromLuopan_aboutScreenShown` (open overflow → tap `menu_about` → assert `tv_about_studio_name` displayed) | `app/src/androidTest/java/com/luopan/compass/ui/AboutScreenTest.kt` | — | ⬚ |
| 5.4 | Add 2 back-navigation tests: `nav_backFromAbout_returnsToModern` (`pressBack()` → `onView(withId(R.id.compassRose)).check(matches(isDisplayed()))`), `nav_backFromAbout_returnsToLuopan` (`pressBack()` → `onView(withId(R.id.luopanView)).check(matches(isDisplayed()))`) | `app/src/androidTest/java/com/luopan/compass/ui/AboutScreenTest.kt` | — | ⬚ |
| 5.5 | Add 2 tab-sync tests: `tabSync_fromModern_tabUnchanged` (open About → `activityRule.scenario.onActivity { assertEquals(0, it.findViewById<TabLayout>(R.id.tabLayout).selectedTabPosition) }`), `tabSync_fromLuopan_tabUnchanged` (switch to Luopan tab, open About → same assertion with `1`) | `app/src/androidTest/java/com/luopan/compass/ui/AboutScreenTest.kt` | — | ⬚ |
| 5.6 | Add `nav_launchSingleTop_noStackDuplicate`: navigate to About, open overflow and tap About again, `pressBack()`, assert Modern screen shown | `app/src/androidTest/java/com/luopan/compass/ui/AboutScreenTest.kt` | — | ⬚ |

**Dependency:** Phase 4 complete (Activity + nav_graph must be wired before any instrumented test can open About).

---

## Integration Points

| System | Touched by | Risk |
|--------|-----------|------|
| `CompassActivity` toolbar + menu | Phase 4 | Toolbar height shifts existing layout; `NavHostFragment` must retain `android:layout_weight="1"` to fill remaining space. Verified implicitly by Phase 5.3 nav-from tests — if content is not visible, those tests fail. |
| NavController back-stack | Phase 4 | `launchSingleTop` must be set on the navigate call, not in nav_graph XML (XML `launchSingleTop` only applies to action elements) |
| Tab-sync listener | Phase 4.4 | `else -> return` already handles `dest_about`; comment only, no behaviour change |
| `LuopanFragment` MenuProvider | Phase 4 | Activity-level menu registered first → "About" appears above "Show/hide rings" in overflow. Acceptable per TSPEC §6.5. |
| Robolectric + `fragment-testing` | Phase 0 + 3 | `fragment-testing` is a new `testImplementation` dependency; must add version `1.6.1` manually since no existing `fragment` version key exists in `libs.versions.toml` |

---

## Definition of Done

- [ ] All Robolectric tests in `AboutFragmentLogicTest` pass: `./gradlew :app:test --tests "*.AboutFragmentLogicTest"`
- [ ] No-browser Snackbar branch covered by `noBrowser_showsSnackbar` (Robolectric)
- [ ] Successful-launch branch covered by `launched_doesNotShowSnackbar` (Robolectric — negative property)
- [ ] All instrumented tests in `AboutScreenTest` pass on a connected device/emulator: `./gradlew :app:connectedAndroidTest`
- [ ] `nav_launchSingleTop_noStackDuplicate` passes — back from About returns to previous screen, not a duplicate About
- [ ] `./gradlew :app:assembleDebug` succeeds with no warnings for new files
- [ ] No regressions in existing test suites: `./gradlew :app:test` and `./gradlew :app:connectedAndroidTest`
- [ ] REQ traceability: every AC in REQ-ABOUT-01/02/03 has a corresponding passing test
- [ ] REQ-ABOUT-NFR-01 (no network calls) verified by code review: `AboutFragment` contains no Retrofit/OkHttp/URLConnection calls; all content from string resources
