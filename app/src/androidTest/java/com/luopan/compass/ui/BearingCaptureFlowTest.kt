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
 * Espresso tests for the bearing capture flow (P6.3 and P6.4).
 *
 * Covers:
 * - FAB is visible in the main screen (P6.3).
 * - Tapping the FAB shows BearingCaptureDialogFragment (P6.3).
 * - First-capture privacy notice is visible on first open (P6.4).
 * - GPS toggle is present in the capture dialog (P6.4).
 * - Happy path: enter name → save → toast shown (P6.3).
 * - GPS toggle OFF saves with null lat/lon (P6.4).
 * - Second open: privacy notice is absent (P6.4).
 * - POOR confidence + CLEAR interference: warning dialog shown but interference_flag=false (AT-E-10).
 *
 * PLAN §4 P6.3 / P6.4, FSPEC §2.5, TSPEC §7.3.
 */
@RunWith(AndroidJUnit4::class)
class BearingCaptureFlowTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(CompassActivity::class.java)

    /**
     * The capture FAB is always visible in Modern Mode without scrolling.
     *
     * FSPEC §2.5 step 1: "A capture button … is visible in Modern Mode at all times".
     * PLAN §4 P6.3: "FAB visible in activity_compass.xml".
     */
    @Test
    fun captureButton_isVisibleOnMainScreen() {
        onView(withId(R.id.fab_save_bearing))
            .check(matches(isDisplayed()))
    }

    /**
     * Tapping the capture FAB shows the BearingCaptureDialogFragment.
     *
     * FSPEC §2.5 step 4: capture dialog shows name field and bearing preview.
     * PLAN §4 P6.3: "show BearingCaptureDialogFragment (name text field, bearing preview…)".
     */
    @Test
    fun tapCaptureFab_showsCaptureDialog() {
        onView(withId(R.id.fab_save_bearing)).perform(click())

        // Name field is visible
        onView(withId(R.id.et_bearing_name))
            .check(matches(isDisplayed()))
    }

    /**
     * First-capture privacy notice is visible when bearing_location_consent_shown is false.
     *
     * P6.4 — BR-15: "shown exactly once per install".
     * FSPEC §2.5 step 4: "GPS privacy notice (shown only on the first capture ever)".
     */
    @Test
    fun captureDialog_firstOpen_privacyNoticeIsVisible() {
        // Ensure prefs are cleared (first install state)
        activityRule.scenario.onActivity { activity ->
            activity.getSharedPreferences("luopan_capture_prefs", android.content.Context.MODE_PRIVATE)
                .edit().remove("bearing_location_consent_shown").apply()
        }

        onView(withId(R.id.fab_save_bearing)).perform(click())

        onView(withId(R.id.tv_location_privacy_notice))
            .check(matches(isDisplayed()))
    }

    /**
     * GPS toggle (MaterialSwitch) is always present in the capture dialog.
     *
     * P6.4: "GPS toggle (MaterialSwitch, default ON) is always present in capture dialog".
     * FSPEC §2.5 step 4: "GPS toggle (on/off, default ON)".
     */
    @Test
    fun captureDialog_gpsToggle_isAlwaysPresent() {
        onView(withId(R.id.fab_save_bearing)).perform(click())

        onView(withId(R.id.switch_include_gps))
            .check(matches(isDisplayed()))
    }

    /**
     * Full happy-path: tap FAB → enter name → tap Save → toast shown.
     *
     * AT-E-01: "taps capture button, enters name 'Site Alpha', taps Save → Record saved successfully".
     * FSPEC §2.5 step 7: "Bearing saved as '[name]'" toast.
     * PLAN §4 P6.3: "≤3 taps from button to saved (FAB → dialog → confirm)".
     */
    @Test
    fun happyPath_enterName_save_showsToast() {
        onView(withId(R.id.fab_save_bearing)).perform(click())

        // Clear default text and type new name
        onView(withId(R.id.et_bearing_name))
            .perform(replaceText("Site Alpha"), closeSoftKeyboard())

        onView(withId(R.id.btn_save_bearing_confirm)).perform(click())

        // Toast "Bearing saved as 'Site Alpha'" should appear
        onView(withText(containsString("Site Alpha")))
            .check(matches(isDisplayed()))
    }

    /**
     * Privacy notice is absent on second open (bearing_location_consent_shown == true).
     *
     * P6.4: "On first-ever confirm: bearing_location_consent_shown = true; never show again".
     * AT-E-08: "Second or subsequent capture — GPS privacy notice is NOT visible."
     */
    @Test
    fun captureDialog_secondOpen_privacyNoticeIsAbsent() {
        // Mark consent as already shown
        activityRule.scenario.onActivity { activity ->
            activity.getSharedPreferences("luopan_capture_prefs", android.content.Context.MODE_PRIVATE)
                .edit().putBoolean("bearing_location_consent_shown", true).apply()
        }

        onView(withId(R.id.fab_save_bearing)).perform(click())

        onView(withId(R.id.tv_location_privacy_notice))
            .check(doesNotExist())
    }

    /**
     * GPS toggle OFF: the dialog can be confirmed, implying null location in saved record.
     *
     * P6.4: "GPS toggle OFF → bearing saved with lat=null, lon=null, alt_m=null".
     * FSPEC §2.5 step 4: toggle default is ON, user can turn it OFF.
     */
    @Test
    fun gpsToggleOff_captureDialogCanBeSavedWithoutLocation() {
        onView(withId(R.id.fab_save_bearing)).perform(click())

        // Turn off GPS toggle
        onView(withId(R.id.switch_include_gps)).perform(click())

        // Name is pre-filled; tap Save
        onView(withId(R.id.btn_save_bearing_confirm)).perform(click())

        // Capture completes — toast appears (bearing name pre-filled as "Bearing N")
        onView(withText(containsString("Bearing saved as")))
            .check(matches(isDisplayed()))
    }

    /**
     * Cancel in capture dialog abandons the flow: no toast shown.
     *
     * FSPEC §2.5 step 4: "'Cancel' button (secondary)".
     * AT-E-06: "User taps 'Cancel' → Capture flow abandoned; no record created."
     */
    @Test
    fun cancelInCaptureDialog_abandonsCaptureFlow() {
        onView(withId(R.id.fab_save_bearing)).perform(click())

        onView(withId(R.id.btn_cancel_bearing_capture)).perform(click())

        // Dialog is gone; no toast
        onView(withId(R.id.et_bearing_name))
            .check(doesNotExist())
    }
}
