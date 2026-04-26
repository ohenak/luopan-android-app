package com.luopan.compass.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luopan.compass.R
import org.hamcrest.Matchers.containsString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Espresso tests asserting that GRID north is completely absent from the UI (AT-G-08).
 *
 * The toggle MUST be a strict binary TRUE / MAGNETIC choice in Phase 2. No view anywhere
 * in the main compass screen view hierarchy may display the text "Grid" or "GRID".
 *
 * FSPEC §2.2 step 3: "GRID north is not offered in this toggle. The toggle must not show
 * a GRID option, a greyed-out GRID option, or any 'coming soon' GRID placeholder."
 * PLAN §4 P4.3 AT-G-08 success criterion.
 */
@RunWith(AndroidJUnit4::class)
class GridNorthAbsenceTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(CompassActivity::class.java)

    /**
     * No view with the exact text "Grid" appears anywhere in the main screen view hierarchy.
     *
     * AT-G-08: "asserts no view with text containing 'Grid' or 'GRID' exists in main screen".
     */
    @Test
    fun mainScreen_containsNoViewWithText_Grid() {
        onView(withText("Grid"))
            .check(doesNotExist())
    }

    /**
     * No view with the exact text "GRID" appears anywhere in the main screen view hierarchy.
     *
     * AT-G-08: uppercase variant guard.
     */
    @Test
    fun mainScreen_containsNoViewWithText_GRID() {
        onView(withText("GRID"))
            .check(doesNotExist())
    }

    /**
     * No view with text containing "Grid N" (the canonical toggle label format) appears.
     *
     * Guards against "Grid N", "Grid North", "Grid North (coming soon)", etc.
     */
    @Test
    fun mainScreen_containsNoViewWithTextContaining_GridN() {
        onView(withText(containsString("Grid N")))
            .check(doesNotExist())
    }

    /**
     * The NorthType.GRID enum value must NEVER reach the UI north_label field.
     *
     * FSPEC §2.2 step 3: toggle is strictly binary. TSPEC §5.6: GRID defined in enum
     * only for the BearingCaptureUseCase guard, never reachable via the UI toggle.
     */
    @Test
    fun mainScreen_northLabel_doesNotContainText_Grid() {
        onView(withText(containsString("Grid")))
            .check(doesNotExist())
    }

    // -------------------------------------------------------------------------
    // P8.5 / AT-G-08: Capture dialog must also be free of any "Grid" text
    // -------------------------------------------------------------------------

    /**
     * The bearing capture dialog must NOT display any view with text "Grid".
     *
     * AT-G-08 (P8.5 extension): "asserts no view with text containing 'Grid' or 'GRID'
     * exists in main screen OR capture dialog".
     * FSPEC §2.2 step 3: GRID is not offered anywhere in the capture flow UI.
     */
    @Test
    fun captureDialog_containsNoViewWithText_Grid() {
        onView(withId(R.id.fab_save_bearing)).perform(click())

        onView(withText("Grid")).check(doesNotExist())
    }

    /**
     * The bearing capture dialog must NOT display any view with text "GRID".
     *
     * AT-G-08 uppercase guard in capture dialog.
     */
    @Test
    fun captureDialog_containsNoViewWithText_GRID() {
        onView(withId(R.id.fab_save_bearing)).perform(click())

        onView(withText("GRID")).check(doesNotExist())
    }

    /**
     * The bearing meta label in the capture dialog must NOT contain "Grid".
     *
     * The bearing meta field shows "True N · High" or "Magnetic N · High" — never "Grid N".
     * FSPEC §2.2 step 3: GRID label must never appear in any dialog or modal.
     */
    @Test
    fun captureDialog_bearingMetaLabel_doesNotContainText_Grid() {
        onView(withId(R.id.fab_save_bearing)).perform(click())

        onView(withText(containsString("Grid"))).check(doesNotExist())
    }
}
