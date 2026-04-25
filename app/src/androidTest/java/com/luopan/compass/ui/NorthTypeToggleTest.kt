package com.luopan.compass.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
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
 * Espresso tests for the north type toggle button (P4.3).
 *
 * Verifies:
 * - Toggle button is visible on the main compass screen.
 * - Tapping the True N button changes the north label to "True N".
 * - Tapping the Magnetic N button changes the north label back to "Magnetic N".
 * - The toggle is strictly binary (TRUE / MAGNETIC only — AT-G-08 complemented by GridNorthAbsenceTest).
 *
 * See TSPEC §7.1, PLAN §4 P4.3, FSPEC §2.2.
 */
@RunWith(AndroidJUnit4::class)
class NorthTypeToggleTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(CompassActivity::class.java)

    /**
     * The "True N" toggle button is visible on the main compass screen without scrolling.
     *
     * TSPEC §7.1: "a MaterialButtonToggleGroup … is added to the main compass layout,
     * positioned … where it is visible without scrolling or entering any settings screen."
     */
    @Test
    fun trueNButton_isVisibleOnMainScreen() {
        onView(withId(R.id.btn_true_n))
            .check(matches(isDisplayed()))
    }

    /**
     * The "Magnetic N" toggle button is visible on the main compass screen without scrolling.
     *
     * TSPEC §7.1: both buttons in the group are always visible.
     */
    @Test
    fun magneticNButton_isVisibleOnMainScreen() {
        onView(withId(R.id.btn_magnetic_n))
            .check(matches(isDisplayed()))
    }

    /**
     * Tapping the True N toggle button updates the north label to "True N".
     *
     * PLAN §4 P4.3: "tap toggle, assert heading label changes".
     * FSPEC §2.2 step 4b: heading label changes to "True N".
     * The 200 ms heading-update budget is satisfied by the sensor frame cycle (≤50 ms).
     */
    @Test
    fun tapTrueN_northLabelChangesToTrueN() {
        onView(withId(R.id.btn_true_n)).perform(click())

        onView(withId(R.id.northLabel))
            .check(matches(withText(R.string.true_north)))
    }

    /**
     * After tapping True N and then Magnetic N, the north label reverts to "Magnetic N".
     *
     * FSPEC §2.2 step 5: "The heading returns to the magnetic heading value.
     * The north label changes to 'Magnetic N'."
     */
    @Test
    fun tapTrueN_thenMagneticN_northLabelChangesBackToMagneticN() {
        // First switch to True N
        onView(withId(R.id.btn_true_n)).perform(click())

        // Then switch back to Magnetic N
        onView(withId(R.id.btn_magnetic_n)).perform(click())

        onView(withId(R.id.northLabel))
            .check(matches(withText(R.string.magnetic_north)))
    }

    /**
     * The toggle group does NOT contain a third "Grid" state or any "Grid" label.
     *
     * AT-G-08 (complementary check): the toggle group itself must be a strict binary choice.
     * Full GridNorthAbsenceTest covers the entire view hierarchy.
     * FSPEC §2.2 step 3: "GRID north is not offered in this toggle."
     */
    @Test
    fun toggleGroup_doesNotContainGridLabel() {
        onView(withText("Grid")).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist())
        onView(withText("GRID")).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist())
        onView(withText("Grid N")).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist())
    }
}
