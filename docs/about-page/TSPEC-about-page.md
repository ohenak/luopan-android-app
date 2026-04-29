# TSPEC-about-page: Technical Specification
## About Screen — YiJi Studio

| Field | Value |
|-------|-------|
| **Version** | 0.4 |
| **Date** | 2026-04-28 |
| **Status** | Draft |
| **Author** | Engineering |
| **Feature branch** | feat-about-page |
| **Input REQ** | docs/about-page/REQ-about-page.md v0.2 |

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [New Files](#2-new-files)
3. [Modified Files](#3-modified-files)
4. [UrlLauncher Protocol](#4-urllauncher-protocol)
5. [AboutFragment](#5-aboutfragment)
6. [Activity Toolbar and Menu](#6-activity-toolbar-and-menu)
7. [Navigation Graph](#7-navigation-graph)
8. [Layouts](#8-layouts)
9. [String Resources](#9-string-resources)
10. [Error Handling](#10-error-handling)
11. [Test Strategy](#11-test-strategy)
12. [Requirements Traceability](#12-requirements-traceability)

---

## 1. Architecture Overview

The About screen is a pure-display Fragment with no ViewModel, no database, and no sensor dependency. The only runtime behaviour is dispatching an `ACTION_VIEW` intent when the user taps the website link.

### Dependency Graph

```
CompassActivity
  └── (toolbar) menu_about.xml ──► navigate(dest_about)
                                        │
                                   AboutFragment
                                        ├── fragment_about.xml (layout)
                                        └── UrlLauncher (interface)
                                               └── SystemUrlLauncher (impl)
                                                     ├── startActivity(ACTION_VIEW)
                                                     └── catch ActivityNotFoundException
                                                               └── Snackbar.make(root, …)
```

`UrlLauncher` is the only injectable seam. It is constructor-injected into `AboutFragment` via a factory argument; tests supply `FakeUrlLauncher`.

---

## 2. New Files

| File | Purpose |
|------|---------|
| `app/src/main/java/com/luopan/compass/ui/AboutFragment.kt` | Full-screen Fragment; displays studio identity, fires URL intent |
| `app/src/main/java/com/luopan/compass/ui/UrlLauncher.kt` | Protocol interface + `SystemUrlLauncher` implementation |
| `app/src/test/java/com/luopan/compass/ui/FakeUrlLauncher.kt` | Test double — `internal` class; lives in `src/test` so it is never compiled into the production APK |
| `app/src/main/res/layout/fragment_about.xml` | About screen layout |
| `app/src/main/res/menu/menu_about.xml` | Activity-level "About" overflow menu item |
| `app/src/test/java/com/luopan/compass/ui/AboutFragmentLogicTest.kt` | Unit tests — UrlLauncher dispatch logic |
| `app/src/androidTest/java/com/luopan/compass/ui/AboutScreenTest.kt` | Instrumented tests — content, navigation, intent, Snackbar |

---

## 3. Modified Files

| File | Change |
|------|--------|
| `app/src/main/res/layout/activity_compass.xml` | Add `MaterialToolbar` above `NavHostFragment` |
| `app/src/main/res/navigation/nav_graph.xml` | Add `dest_about` Fragment destination |
| `app/src/main/java/com/luopan/compass/ui/CompassActivity.kt` | `setSupportActionBar()`, add About `MenuProvider`, handle `dest_about` in tab-sync |
| `app/src/main/res/values/strings.xml` | Add `menu_about`, `about_studio_name`, `about_studio_description`, `about_website_label`, `about_no_browser_error` |
| `gradle/libs.versions.toml` | Add `fragment-testing = { group = "androidx.fragment", name = "fragment-testing", version.ref = "fragment" }` to `[libraries]` |
| `app/build.gradle.kts` | Add `testImplementation(libs.fragment.testing)` (required by `launchFragmentInContainer`) and `testImplementation(libs.espresso.core)` (required by `onView`/`withText`/`isDisplayed`/`doesNotExist` in Robolectric tests) |

---

## 4. UrlLauncher Protocol

```kotlin
// app/src/main/java/com/luopan/compass/ui/UrlLauncher.kt

interface UrlLauncher {
    sealed class Result {
        object Launched : Result()
        object NoBrowserFound : Result()
    }
    fun launch(url: String): Result
}

class SystemUrlLauncher(private val context: Context) : UrlLauncher {
    override fun launch(url: String): UrlLauncher.Result {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
            UrlLauncher.Result.Launched
        } catch (e: ActivityNotFoundException) {
            UrlLauncher.Result.NoBrowserFound
        }
    }
}

// Test double — lives in src/test (never compiled into production APK)
// app/src/test/java/com/luopan/compass/ui/FakeUrlLauncher.kt
internal class FakeUrlLauncher : UrlLauncher {
    var result: UrlLauncher.Result = UrlLauncher.Result.Launched
    var lastUrl: String? = null
    override fun launch(url: String): UrlLauncher.Result {
        lastUrl = url
        return result
    }
}
```

`UrlLauncher` is the only mock seam. `SystemUrlLauncher` takes `Context` (not `Activity`) so it can be constructed from any Android context. The sealed `Result` type makes the no-browser branch exhaustively handleable.

---

## 5. AboutFragment

### 5.1 Constructor and Injection

`AboutFragment` receives its `UrlLauncher` via a companion-object factory:

```kotlin
class AboutFragment : Fragment() {

    internal var urlLauncher: UrlLauncher? = null  // set in onAttach; overridable by tests

    companion object {
        const val WEBSITE_URL = "https://yiji.studio"
    }
}
```

`WEBSITE_URL` is a `companion object` constant so it can be asserted in pure JVM unit tests without instantiating the Fragment. `urlLauncher` is `internal var` (not `private val`) so Robolectric tests in the same module can assign `fragment.urlLauncher = FakeUrlLauncher()` inside `scenario.onFragment { }` without reflection.

Because `Fragment` requires a no-arg constructor for the system, `UrlLauncher` is supplied lazily in `onAttach`. The simpler approach (and the one used here) is:

- The fragment holds an `internal var urlLauncher: UrlLauncher?` overridable by tests.
- In `onAttach(context)`, if `urlLauncher` is not already set, assign `SystemUrlLauncher(context)`.
- Tests override the launcher by subclassing or by setting the field before `onViewCreated`.

For instrumented tests, use `Intents.intending()` to stub the browser — no `FakeUrlLauncher` needed at the instrumented level.

### 5.2 View Binding

`AboutFragment` uses direct `findViewById` (consistent with all existing fragments; the project does not use ViewBinding or DataBinding).

| View ID | Type | Content |
|---------|------|---------|
| `tv_about_studio_name` | `TextView` | `R.string.about_studio_name` — "易機閣 / YiJi Studio" |
| `tv_about_description` | `TextView` | `R.string.about_studio_description` |
| `tv_about_website` | `TextView` | `R.string.about_website_label` — "yiji.studio" |

### 5.3 Click Handler

```kotlin
tvAboutWebsite.setOnClickListener {
    val result = urlLauncher.launch(WEBSITE_URL)
    if (result is UrlLauncher.Result.NoBrowserFound) {
        Snackbar.make(requireView(), R.string.about_no_browser_error, Snackbar.LENGTH_LONG).show()
    }
}
```

`requireView()` is the Snackbar anchor, consistent with `ModernCompassFragment.showLocationPermissionDeniedSnackbar()`.

### 5.4 No Menu Registration

`AboutFragment` does NOT register a `MenuProvider`. The Activity-level menu (§6) provides the About item on all primary screens. When `dest_about` is the current destination, the Activity's About item remains visible but selecting it is a no-op (the NavController will navigate to `dest_about` which is already current; the default `launchSingleTop` flag prevents duplicate back-stack entries — see §7).

---

## 6. Activity Toolbar and Menu

### 6.1 Layout Change (`activity_compass.xml`)

Add `MaterialToolbar` as the first child of the root `LinearLayout`, above `NavHostFragment`:

```xml
<com.google.android.material.appbar.MaterialToolbar
    android:id="@+id/toolbar"
    android:layout_width="match_parent"
    android:layout_height="?attr/actionBarSize"
    android:background="?attr/colorSurface"
    app:titleTextColor="?attr/colorOnSurface" />
```

The toolbar sits above the `NavHostFragment` (weight=1) and above the `TabLayout`. Visual order top-to-bottom: Toolbar → NavHostFragment → TabLayout.

### 6.2 CompassActivity Changes

```kotlin
// In onCreate(), after setContentView():
val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
setSupportActionBar(toolbar)

// After wireTabNavigation():
addMenuProvider(object : MenuProvider {
    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_about, menu)
    }
    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_about -> {
                navController.navigate(R.id.dest_about)
                true
            }
            else -> false
        }
    }
}, this)  // lifecycle owner = Activity (menu persists for Activity lifetime)
```

Note: `this` (the Activity) is the lifecycle owner so the About menu item is always present, regardless of which Fragment is active.

### 6.3 Tab-Sync Exclusion

The existing `addOnDestinationChangedListener` already handles `dest_about` safely:

```kotlin
navController.addOnDestinationChangedListener { _, destination, _ ->
    val targetPosition = when (destination.id) {
        R.id.dest_modern -> TAB_MODERN
        R.id.dest_luopan -> TAB_LUOPAN
        else -> return@addOnDestinationChangedListener  // dest_about: no tab change
    }
    …
}
```

No change needed here — the `else -> return` branch is already correct. Document this explicitly in code with a comment referencing `dest_about`. Note: if a future screen (e.g., `dest_bearing_history`) is added to the nav graph, it must also be covered by the `else -> return` branch and by the tab-sync exclusion test matrix.

### 6.4 Menu Resource (`menu_about.xml`)

```xml
<menu xmlns:android="http://schemas.android.com/apk/res/android">
    <item
        android:id="@+id/action_about"
        android:title="@string/menu_about"
        android:showAsAction="never" />
</menu>
```

`showAsAction="never"` ensures it always appears in the overflow dropdown, never as a toolbar icon.

### 6.5 Menu Item Ordering

When the user is on LuopanFragment, both the Activity's `menu_about.xml` (About) and `LuopanFragment`'s `menu_luopan.xml` (Show/hide rings) are inflated. Android merges menus in registration order: Activity menu first, then fragment MenuProviders. Result: "About" appears above "Show/hide rings" in the overflow. This is acceptable.

---

## 7. Navigation Graph

Add to `nav_graph.xml`:

```xml
<fragment
    android:id="@+id/dest_about"
    android:name="com.luopan.compass.ui.AboutFragment"
    android:label="About" />
```

No `action` element is required — `navController.navigate(R.id.dest_about)` navigates directly by destination ID. The default `NavOptions` push `dest_about` onto the back stack; pressing back returns to the previous destination (Modern or Luopan), satisfying REQ-ABOUT-03.

To prevent duplicate About screens when the user taps "About" from `dest_about` itself, add `launchSingleTop = true` to the navigate call:

```kotlin
navController.navigate(
    R.id.dest_about,
    null,
    NavOptions.Builder().setLaunchSingleTop(true).build()
)
```

---

## 8. Layouts

### 8.1 `fragment_about.xml`

```
ScrollView (match_parent / match_parent)
  └── LinearLayout (vertical, padding 24dp)
        ├── TextView id=tv_about_studio_name   (textAppearanceHeadline5)
        ├── Space (8dp)
        └── TextView id=tv_about_description   (textAppearanceBody1)
        ├── Space (24dp)
        └── TextView id=tv_about_website       (textAppearanceBody1, textColorPrimary, clickable=true, focusable=true)
```

`tv_about_website` uses `textColorPrimary` and an underline span to signal it is a link. No `<a href>` or `Linkify` — the click handler in code drives navigation (§5.3).

---

## 9. String Resources

Add to `app/src/main/res/values/strings.xml`:

```xml
<!-- About screen -->
<string name="menu_about">About</string>
<string name="about_studio_name">易機閣 / YiJi Studio</string>
<string name="about_studio_description">Chinese metaphysics consultations — Feng Shui, Purple Star Astrology &amp; I Ching.</string>
<string name="about_website_label">yiji.studio</string>
<string name="about_no_browser_error">No browser found to open link</string>
```

Note: `&amp;` is the XML-escaped form of `&` required inside string resources.

---

## 10. Error Handling

| Scenario | Handler | User-visible outcome |
|----------|---------|---------------------|
| `ActivityNotFoundException` on website tap | Caught in `SystemUrlLauncher.launch()`, returns `NoBrowserFound` | Snackbar: `R.string.about_no_browser_error` |
| User taps "About" while already on About screen | `launchSingleTop = true` in navigate call | No-op — no duplicate destination pushed |
| Back-press from About | NavController default back-stack pop | Returns to originating screen (Modern or Luopan) |

---

## 11. Test Strategy

### 11.1 Unit / Robolectric Tests (`AboutFragmentLogicTest`)

`AboutFragmentLogicTest` uses `@RunWith(RobolectricTestRunner::class)`. Pure-JVM assertions (no Fragment instantiation) run fine under Robolectric.

| Test | Level | Assertion |
|------|-------|-----------|
| `websiteUrl_isYijiStudio` | Robolectric | `AboutFragment.WEBSITE_URL == "https://yiji.studio"` — guards against accidental URL change |
| `systemUrlLauncher_parsesUri_correctly` | Robolectric | `Uri.parse(AboutFragment.WEBSITE_URL)` scheme == "https", host == "yiji.studio" (`android.net.Uri` requires Robolectric runtime) |
| `noBrowser_showsSnackbar` | Robolectric | Launch `AboutFragment` via `FragmentScenario`, set `fragment.urlLauncher = FakeUrlLauncher(result = NoBrowserFound)`, click `tv_about_website`, assert `onView(withText(R.string.about_no_browser_error)).check(matches(isDisplayed()))` |
| `launched_doesNotShowSnackbar` | Robolectric | Launch `AboutFragment` via `FragmentScenario`, set `fragment.urlLauncher = FakeUrlLauncher(result = Launched)`, click `tv_about_website`, assert `onView(withText(R.string.about_no_browser_error)).check(doesNotExist())` |

**Robolectric injection pattern for `noBrowser_showsSnackbar`:**

```kotlin
@Test
fun noBrowser_showsSnackbar() {
    val fake = FakeUrlLauncher().apply { result = UrlLauncher.Result.NoBrowserFound }
    val scenario = launchFragmentInContainer<AboutFragment>()
    scenario.onFragment { fragment -> fragment.urlLauncher = fake }
    onView(withId(R.id.tv_about_website)).perform(click())
    onView(withText(R.string.about_no_browser_error)).check(matches(isDisplayed()))
    assertEquals(AboutFragment.WEBSITE_URL, fake.lastUrl)  // P-19: launcher was called with the correct URL
}
```

`launchFragmentInContainer` is from `androidx.fragment:fragment-testing`; Robolectric inflates the layout and drives the full `onAttach → onViewCreated` lifecycle in the JVM test runner. No emulator or device required.

### 11.2 Instrumented Tests (`AboutScreenTest`)

Uses `ActivityScenarioRule(CompassActivity::class.java)` with `Intents.init()` / `Intents.release()` via `@Before` / `@After`, following the `LocationPermissionTest` pattern.

| Test | Given | Action | Assert |
|------|-------|--------|--------|
| `content_studioNameVisible` | About screen open | — | `onView(withText(R.string.about_studio_name)).check(matches(isDisplayed()))` |
| `content_descriptionVisible` | About screen open | — | `onView(withText(R.string.about_studio_description)).check(matches(isDisplayed()))` |
| `content_websiteLabelVisible` | About screen open | — | `onView(withText(R.string.about_website_label)).check(matches(isDisplayed()))` |
| `websiteLink_firesActionViewIntent` | About screen open, `val okResult = Instrumentation.ActivityResult(Activity.RESULT_OK, null)` / `Intents.intending(hasAction(ACTION_VIEW)).respondWith(okResult)` | Tap `tv_about_website` | `Intents.intended(allOf(hasAction(ACTION_VIEW), hasData("https://yiji.studio")))` |
| `nav_fromModern_aboutScreenShown` | Modern Mode active | Open overflow, tap `R.string.menu_about` | `onView(withId(R.id.tv_about_studio_name)).check(matches(isDisplayed()))` |
| `nav_fromLuopan_aboutScreenShown` | Luopan Mode active | Open overflow, tap `R.string.menu_about` | `onView(withId(R.id.tv_about_studio_name)).check(matches(isDisplayed()))` |
| `nav_backFromAbout_returnsToModern` | Navigated to About from Modern | `pressBack()` | `onView(withId(R.id.compassRose)).check(matches(isDisplayed()))` |
| `nav_backFromAbout_returnsToLuopan` | Navigated to About from Luopan | `pressBack()` | `onView(withId(R.id.luopanView)).check(matches(isDisplayed()))` |
| `tabSync_fromModern_tabUnchanged` | Modern tab active (position 0) | Open overflow, tap `R.string.menu_about` | `activityRule.scenario.onActivity { assertEquals(0, it.findViewById<TabLayout>(R.id.tabLayout).selectedTabPosition) }` |
| `tabSync_fromLuopan_tabUnchanged` | Luopan tab active (position 1) | Open overflow, tap `R.string.menu_about` | `activityRule.scenario.onActivity { assertEquals(1, it.findViewById<TabLayout>(R.id.tabLayout).selectedTabPosition) }` |
| `nav_launchSingleTop_noStackDuplicate` | About screen open | Open overflow, tap `R.string.menu_about` again; `pressBack()` | `onView(withId(R.id.compassRose)).check(matches(isDisplayed()))` — back returns to Modern, not a second About |

### 11.3 Test Level Summary

| Property | Level | Rationale |
|----------|-------|-----------|
| `WEBSITE_URL` constant value | Robolectric | File-level `@RunWith(RobolectricTestRunner::class)` applies to all methods including pure string assertions |
| `Uri.parse` correctness | Robolectric | `android.net.Uri` is a stubbed Android class — requires Robolectric runtime even for a parse check |
| No-browser Snackbar branch | Robolectric | `ActivityNotFoundException` not injectable via `Intents.intending()`; `FakeUrlLauncher` + `FragmentScenario` exercises the real Fragment path in the JVM |
| No Snackbar on successful launch | Robolectric | Negative guard — `FakeUrlLauncher(result = Launched)` + `doesNotExist()` ensures the Snackbar is not shown on the happy path |
| Content visible on screen | Instrumented | Requires inflated layout |
| Intent fired with correct URI | Instrumented | Requires real Activity + Intents library |
| Navigation from Modern/Luopan | Instrumented | Requires real NavController |
| Back-press returns to origin | Instrumented | Requires real back-stack |
| Tab selection unchanged after About | Instrumented | Requires real TabLayout + NavController |
| `launchSingleTop` prevents duplicate destination | Instrumented | Requires real back-stack |

---

## 12. Requirements Traceability

| Requirement | Technical component |
|-------------|-------------------|
| REQ-ABOUT-01 (studio name + description visible) | `fragment_about.xml` views; `R.string.about_studio_name`, `R.string.about_studio_description` |
| REQ-ABOUT-02 (ACTION_VIEW intent fired) | `UrlLauncher.launch()` in `AboutFragment.onViewCreated()` click handler |
| REQ-ABOUT-02 (no-browser Snackbar) | `SystemUrlLauncher` catches `ActivityNotFoundException`, returns `NoBrowserFound`; fragment shows Snackbar |
| REQ-ABOUT-03 (Activity-level overflow menu) | `menu_about.xml` inflated by Activity `MenuProvider` |
| REQ-ABOUT-03 (dest_about in NavController) | `nav_graph.xml` `dest_about` destination |
| REQ-ABOUT-03 (tab-sync exclusion) | `else -> return` branch in `wireTabNavigation()` destination listener |
| REQ-ABOUT-03 (back returns to origin) | Default NavController back-stack pop |
| REQ-ABOUT-NFR-01 (static content) | No network calls in `AboutFragment`; all text from string resources |
| REQ-ABOUT-NFR-02 (no crash on no browser) | `try/catch ActivityNotFoundException` in `SystemUrlLauncher` |
