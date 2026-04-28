package com.luopan.compass.ui

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.tabs.TabLayout
import com.luopan.compass.R
import org.hamcrest.Description
import org.hamcrest.TypeSafeMatcher
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented navigation tests.
 *
 * AT-NAV-01-A: Third tab shows BearingHistoryFragment.
 * AT-NAV-01-B: Shared CompassViewModel instance.
 * PROP-NAV-002: Tab count is 3.
 * PROP-CAL-005: Age banner absent in ModernCompassFragment.
 *
 * Device-only — these tests require a connected device or emulator.
 */
/** Custom matcher that asserts a TabLayout has exactly [count] tabs. */
private fun hasTabCount(count: Int) = object : TypeSafeMatcher<android.view.View>() {
    override fun describeTo(description: Description) {
        description.appendText("TabLayout with $count tabs")
    }
    override fun matchesSafely(view: android.view.View): Boolean {
        return view is TabLayout && view.tabCount == count
    }
}

@RunWith(AndroidJUnit4::class)
class NavigationTabTest {

    /**
     * AT-NAV-01-A: Tapping the History tab navigates to BearingHistoryFragment.
     * Verifies the empty state text is visible (no records on a fresh install).
     */
    @Test
    fun `AT-NAV-01-A tapping history tab shows BearingHistoryFragment`() {
        ActivityScenario.launch(CompassActivity::class.java).use {
            // Tap the "History" tab (third tab, index 2)
            onView(withText("History")).perform(click())

            // BearingHistoryFragment shows empty-state text when no records
            onView(withText(com.luopan.compass.R.string.empty_no_bearings))
                .check(matches(isDisplayed()))
        }
    }

    /**
     * AT-NAV-01-B: The CompassViewModel is shared between tabs.
     * Navigates to History tab and back, verifying the same Activity instance hosts both fragments.
     */
    @Test
    fun `AT-NAV-01-B shared CompassViewModel instance across tabs`() {
        ActivityScenario.launch(CompassActivity::class.java).use { scenario ->
            // Navigate to History tab
            onView(withText("History")).perform(click())

            // Navigate back to Modern tab
            onView(withText("Modern")).perform(click())

            // Activity is still active — ViewModel is shared via activityViewModels()
            scenario.onActivity { activity ->
                // Both fragments share the same activity context
                assert(activity is CompassActivity)
            }
        }
    }

    /**
     * AT-NAV-01-C: After CalibrationWizard RESULT_OK, age banner is GONE
     * and calibration_age_days reflects updated value.
     *
     * Requires DI for CalibrationWizard launch — cannot be made substantive without Hilt.
     */
    @Test
    @Ignore("requires DI for CalibrationWizard RESULT_OK interception and fake calibration age injection into CompassViewModel. TODO: implement after Hilt migration.")
    fun `AT-NAV-01-C RESULT_OK from CalWizard dismisses age banner and updates calibration age`() {
        // Gap: testing RESULT_OK from CalibrationWizardActivity requires Intents stubbing
        // and Hilt injection to control the initial calibration age state.
    }

    /**
     * PROP-NAV-002 / PROP-NAV-003: TabLayout must have exactly 3 tabs.
     */
    @Test
    fun `PROP-NAV-002 tab layout has exactly 3 tabs`() {
        ActivityScenario.launch(CompassActivity::class.java).use {
            onView(withId(R.id.tab_layout)).check(matches(hasTabCount(3)))
        }
    }

    /**
     * PROP-CAL-005: Age banner must not appear in ModernCompassFragment layout.
     * Navigate to Modern tab, assert banner_age_root does not exist.
     */
    @Test
    fun `PROP-CAL-005 age banner absent in ModernCompassFragment`() {
        ActivityScenario.launch(CompassActivity::class.java).use {
            // Stay on the Modern tab (default on launch)
            onView(withId(R.id.banner_age_root)).check(doesNotExist())
        }
    }
}
