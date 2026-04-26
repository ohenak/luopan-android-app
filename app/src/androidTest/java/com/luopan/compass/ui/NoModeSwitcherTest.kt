package com.luopan.compass.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luopan.compass.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test for the bottom TabLayout mode switcher.
 *
 * Phase 2 asserted TabLayout was NOT present (no mode switcher).
 * Phase 3 (Task 3.1) migrates CompassActivity to NavHostFragment + TabLayout architecture:
 * the TabLayout IS now present with "Modern" and "Luopan" tabs (TSPEC §9.1, FSPEC §1.4).
 */
@RunWith(AndroidJUnit4::class)
class NoModeSwitcherTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(CompassActivity::class.java)

    @Test
    fun tabLayoutIsVisible() {
        onView(withId(R.id.tabLayout))
            .check(matches(isDisplayed()))
    }

    @Test
    fun modernTabIsPresent() {
        onView(withText("Modern"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun luopanTabIsPresent() {
        onView(withText("Luopan"))
            .check(matches(isDisplayed()))
    }
}
