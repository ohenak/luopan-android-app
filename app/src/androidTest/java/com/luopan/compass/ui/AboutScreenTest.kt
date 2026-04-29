package com.luopan.compass.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.google.android.material.tabs.TabLayout
import com.luopan.compass.R
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented UI tests for the About screen (dest_about / AboutFragment).
 *
 * Covers TSPEC §6.5 acceptance criteria:
 *  - Content visibility (5.1): studio name, description, and website label are displayed.
 *  - Intent stub (5.2): tapping the website link fires ACTION_VIEW with https://yiji.studio.
 *  - Navigation from Modern / Luopan (5.3): About screen is reachable from both modes.
 *  - Back navigation (5.4): pressing Back returns to the originating mode screen.
 *  - Tab sync (5.5): the selected tab index is unchanged after opening and closing About.
 *  - Single-top (5.6): launching About twice and pressing Back returns to the main screen,
 *    not a duplicate About instance.
 *
 * Pattern:
 *  - @Before calls Intents.init() ONLY — no navigation happens here.
 *  - @After calls Intents.release().
 *  - Each test navigates to About as part of its own body using the overflow menu.
 *
 * Tab switching uses the same text-based click idiom as ModeSwitcherTest:
 *   onView(withText(R.string.tab_luopan)).perform(click())
 * This avoids a dependency on espresso-contrib (which is not in androidTestImplementation
 * for this module).
 *
 * Device-only: requires a running emulator or physical device (API 26+).
 * Compile-check only with: ./gradlew :app:assembleDebugAndroidTest
 * Full run with:           ./gradlew :app:connectedAndroidTest
 *
 * TSPEC §6.5, PLAN Phase 5.
 */
@RunWith(AndroidJUnit4::class)
class AboutScreenTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(CompassActivity::class.java)

    @Before
    fun setUp() {
        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    // -------------------------------------------------------------------------
    // Task 5.1 — Content visibility
    // -------------------------------------------------------------------------

    /**
     * The studio name string is visible on the About screen.
     * TSPEC §6.5: "tv_about_studio_name: text = about_studio_name."
     */
    @Test
    fun content_studioNameVisible() {
        openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)
        onView(withText(R.string.menu_about)).perform(click())
        onView(withText(R.string.about_studio_name)).check(matches(isDisplayed()))
    }

    /**
     * The studio description string is visible on the About screen.
     * TSPEC §6.5: "tv_about_description: text = about_studio_description."
     */
    @Test
    fun content_descriptionVisible() {
        openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)
        onView(withText(R.string.menu_about)).perform(click())
        onView(withText(R.string.about_studio_description)).check(matches(isDisplayed()))
    }

    /**
     * The website label string is visible on the About screen.
     * TSPEC §6.5: "tv_about_website: text = about_website_label."
     */
    @Test
    fun content_websiteLabelVisible() {
        openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)
        onView(withText(R.string.menu_about)).perform(click())
        onView(withText(R.string.about_website_label)).check(matches(isDisplayed()))
    }

    // -------------------------------------------------------------------------
    // Task 5.2 — Intent stub test
    // -------------------------------------------------------------------------

    /**
     * Tapping the website TextView fires an ACTION_VIEW intent for https://yiji.studio.
     * The intent is stubbed so the test does not leave the app.
     * TSPEC §6.5: "on click → urlLauncher.launch(WEBSITE_URL)."
     */
    @Test
    fun websiteLink_firesActionViewIntent() {
        openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)
        onView(withText(R.string.menu_about)).perform(click())
        Intents.intending(hasAction(Intent.ACTION_VIEW))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
        onView(withId(R.id.tv_about_website)).perform(click())
        Intents.intended(allOf(hasAction(Intent.ACTION_VIEW), hasData("https://yiji.studio")))
    }

    // -------------------------------------------------------------------------
    // Task 5.3 — Navigation from each mode
    // -------------------------------------------------------------------------

    /**
     * The About screen is reachable from Modern mode (the default start destination).
     * TSPEC §6.5: overflow "About" item is visible on all primary screens.
     */
    @Test
    fun nav_fromModern_aboutScreenShown() {
        openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)
        onView(withText(R.string.menu_about)).perform(click())
        onView(withId(R.id.tv_about_studio_name)).check(matches(isDisplayed()))
    }

    /**
     * The About screen is reachable from Luopan mode.
     * Switches to Luopan tab using the same text-click idiom as ModeSwitcherTest.
     * TSPEC §6.5: overflow "About" item is visible on all primary screens.
     */
    @Test
    fun nav_fromLuopan_aboutScreenShown() {
        onView(withText(R.string.tab_luopan)).perform(click())
        openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)
        onView(withText(R.string.menu_about)).perform(click())
        onView(withId(R.id.tv_about_studio_name)).check(matches(isDisplayed()))
    }

    // -------------------------------------------------------------------------
    // Task 5.4 — Back navigation
    // -------------------------------------------------------------------------

    /**
     * Pressing Back from About when entered from Modern mode returns to the Modern screen.
     * TSPEC §6.5: "Back → pops dest_about, returns to previous primary destination."
     */
    @Test
    fun nav_backFromAbout_returnsToModern() {
        openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)
        onView(withText(R.string.menu_about)).perform(click())
        pressBack()
        onView(withId(R.id.compassRose)).check(matches(isDisplayed()))
    }

    /**
     * Pressing Back from About when entered from Luopan mode returns to the Luopan screen.
     * TSPEC §6.5: "Back → pops dest_about, returns to previous primary destination."
     */
    @Test
    fun nav_backFromAbout_returnsToLuopan() {
        onView(withText(R.string.tab_luopan)).perform(click())
        openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)
        onView(withText(R.string.menu_about)).perform(click())
        pressBack()
        onView(withId(R.id.luopanView)).check(matches(isDisplayed()))
    }

    // -------------------------------------------------------------------------
    // Task 5.5 — Tab sync
    // -------------------------------------------------------------------------

    /**
     * Opening About from Modern mode leaves the tab selection at index 0 (Modern).
     * TSPEC §6.5: "dest_about is a non-tab destination; tab selection is not changed."
     */
    @Test
    fun tabSync_fromModern_tabUnchanged() {
        openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)
        onView(withText(R.string.menu_about)).perform(click())
        activityRule.scenario.onActivity { activity ->
            assertEquals(
                0,
                activity.findViewById<TabLayout>(R.id.tabLayout).selectedTabPosition
            )
        }
    }

    /**
     * Opening About from Luopan mode leaves the tab selection at index 1 (Luopan).
     * TSPEC §6.5: "dest_about is a non-tab destination; tab selection is not changed."
     */
    @Test
    fun tabSync_fromLuopan_tabUnchanged() {
        onView(withText(R.string.tab_luopan)).perform(click())
        openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)
        onView(withText(R.string.menu_about)).perform(click())
        activityRule.scenario.onActivity { activity ->
            assertEquals(
                1,
                activity.findViewById<TabLayout>(R.id.tabLayout).selectedTabPosition
            )
        }
    }

    // -------------------------------------------------------------------------
    // Task 5.6 — Single-top / no-duplicate
    // -------------------------------------------------------------------------

    /**
     * Navigating to About twice with launchSingleTop=true does NOT push a second About
     * instance onto the back stack — pressing Back returns to Modern, not a duplicate About.
     * TSPEC §6.5: "navOptions: launchSingleTop = true."
     */
    @Test
    fun nav_launchSingleTop_noStackDuplicate() {
        openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)
        onView(withText(R.string.menu_about)).perform(click())
        openActionBarOverflowOrOptionsMenu(getInstrumentation().targetContext)
        onView(withText(R.string.menu_about)).perform(click())
        pressBack()
        onView(withId(R.id.compassRose)).check(matches(isDisplayed()))
    }
}
