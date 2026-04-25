package com.luopan.compass.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoModeSwitcherTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(CompassActivity::class.java)

    @Test fun `no bottom navigation view`() {
        onView(withContentDescription("Luopan")).check(doesNotExist())
        onView(withContentDescription("Sighting")).check(doesNotExist())
    }
}
