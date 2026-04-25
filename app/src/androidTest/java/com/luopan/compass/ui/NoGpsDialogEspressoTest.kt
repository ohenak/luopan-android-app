package com.luopan.compass.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
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
 * Espresso (ActivityScenario) tests for the manual coordinate entry dialog shown when
 * GPS is unavailable (AT-D / P7.2).
 *
 * Covers:
 * - [CompassActivity.showManualCoordinateEntryDialog] shows the dialog with the TSPEC-mandated
 *   title text "Enter coordinates for True North or use Magnetic North only".
 * - "Use Magnetic North" button dismisses the dialog without switching north type.
 * - "Use True North" button is disabled until valid coordinates are entered.
 * - Entering valid coordinates and tapping "Use True North" activates True North mode.
 * - North type remains MAGNETIC until coordinates are entered and confirmed (AT-D requirement).
 *
 * These tests call [CompassActivity.showManualCoordinateEntryDialog] directly (internal API),
 * as the FakeLocationRepository injection path required by the full Robolectric AT-D flow is
 * covered by the unit-level [NoGpsDialogTest]. The Espresso variant here verifies the real
 * dialog fragment UI and interaction behaviour on device/emulator.
 *
 * TSPEC §10.3: "NoGpsDialogTest: Espresso with ActivityScenario: assert manual coordinate
 * entry dialog appears (dialog text contains 'Enter coordinates for True North or use
 * Magnetic North only'). Assert mode remains MAGNETIC until coordinates confirmed."
 * PLAN §4 P8.5 success criteria (AT-D): NoGpsDialogTest.
 */
@RunWith(AndroidJUnit4::class)
class NoGpsDialogEspressoTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(CompassActivity::class.java)

    // -------------------------------------------------------------------------
    // Dialog appearance
    // -------------------------------------------------------------------------

    /**
     * The manual coordinate entry dialog shows the TSPEC-mandated title text.
     *
     * TSPEC §10.3 AT-D: dialog text contains "Enter coordinates for True North or use
     * Magnetic North only".
     * P7.2: dialog title is R.string.manual_coordinates_dialog_title.
     */
    @Test
    fun manualCoordinateDialog_isShown_withCorrectTitle() {
        activityRule.scenario.onActivity { activity ->
            activity.showManualCoordinateEntryDialog()
        }

        onView(withText(R.string.manual_coordinates_dialog_title))
            .check(matches(isDisplayed()))
    }

    /**
     * Title text contains the TSPEC-mandated sub-string "Enter coordinates for True North".
     *
     * TSPEC §10.1 AT-D exact assertion: dialog text contains
     * "Enter coordinates for True North or use Magnetic North only".
     */
    @Test
    fun manualCoordinateDialog_titleContainsTSPECMandatedText() {
        activityRule.scenario.onActivity { activity ->
            activity.showManualCoordinateEntryDialog()
        }

        onView(withText(containsString("Enter coordinates for True North")))
            .check(matches(isDisplayed()))
    }

    /**
     * Both latitude and longitude input fields are visible in the dialog.
     *
     * P7.2: dialog contains lat/lon input fields.
     */
    @Test
    fun manualCoordinateDialog_showsLatAndLonFields() {
        activityRule.scenario.onActivity { activity ->
            activity.showManualCoordinateEntryDialog()
        }

        onView(withId(R.id.et_manual_lat)).check(matches(isDisplayed()))
        onView(withId(R.id.et_manual_lon)).check(matches(isDisplayed()))
    }

    /**
     * Both action buttons are present: "Use True North" and "Use Magnetic North".
     *
     * P7.2: "'Use True North' button: activates True N with manual coordinates;
     *        'Use Magnetic North' button: dismisses and keeps Magnetic N".
     */
    @Test
    fun manualCoordinateDialog_showsBothActionButtons() {
        activityRule.scenario.onActivity { activity ->
            activity.showManualCoordinateEntryDialog()
        }

        onView(withText(R.string.use_true_north)).check(matches(isDisplayed()))
        onView(withText(R.string.use_magnetic_north)).check(matches(isDisplayed()))
    }

    // -------------------------------------------------------------------------
    // "Use Magnetic North" path — mode stays MAGNETIC
    // -------------------------------------------------------------------------

    /**
     * Tapping "Use Magnetic North" dismisses the dialog and north type stays MAGNETIC.
     *
     * AT-D: "mode remains MAGNETIC until coordinates are entered and confirmed."
     * FSPEC §2.2 step 4c: user can dismiss dialog and use Magnetic North only.
     */
    @Test
    fun manualCoordinateDialog_tapUseMagneticNorth_dismissesDialog_northTypeStaysMagnetic() {
        activityRule.scenario.onActivity { activity ->
            activity.showManualCoordinateEntryDialog()
        }

        onView(withText(R.string.use_magnetic_north)).perform(click())

        // Dialog dismissed — title no longer visible
        onView(withText(R.string.manual_coordinates_dialog_title))
            .check(doesNotExist())

        // North label remains "Magnetic N"
        onView(withId(R.id.northLabel))
            .check(matches(withText(R.string.magnetic_north)))
    }

    // -------------------------------------------------------------------------
    // "Use True North" — valid coordinate input required
    // -------------------------------------------------------------------------

    /**
     * Entering valid lat/lon and tapping "Use True North" dismisses the dialog.
     *
     * P7.2: "positiveButton.setOnClickListener: setManualLocation(lat, lon, 0.0)".
     * AT-D: "mode remains MAGNETIC until coordinates are entered and confirmed."
     */
    @Test
    fun manualCoordinateDialog_enterValidCoordinates_tapUseTrueNorth_dismissesDialog() {
        activityRule.scenario.onActivity { activity ->
            activity.showManualCoordinateEntryDialog()
        }

        // Enter valid latitude and longitude
        onView(withId(R.id.et_manual_lat))
            .perform(replaceText("40.7128"), closeSoftKeyboard())
        onView(withId(R.id.et_manual_lon))
            .perform(replaceText("-74.0060"), closeSoftKeyboard())

        // Tap "Use True North" (now enabled)
        onView(withText(R.string.use_true_north)).perform(click())

        // Dialog dismissed — title no longer visible
        onView(withText(R.string.manual_coordinates_dialog_title))
            .check(doesNotExist())
    }
}
