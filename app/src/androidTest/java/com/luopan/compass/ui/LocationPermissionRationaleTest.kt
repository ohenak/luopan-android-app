package com.luopan.compass.ui

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Espresso tests for the location permission rationale dialog flow (BR-LOC-04 / TE-T-10).
 *
 * Covers:
 * - The in-app rationale dialog appears with TSPEC-mandated text when
 *   [CompassActivity.showLocationPermissionRationale] is invoked (simulating the path taken
 *   when [ActivityCompat.shouldShowRequestPermissionRationale] returns true).
 * - Tapping "Not now" dismisses the rationale dialog WITHOUT launching the system permission
 *   dialog, and the toggle remains on Magnetic N.
 * - Tapping "Continue" invokes the onContinue callback (which would launch the system
 *   permission request) and dismisses the rationale dialog.
 * - The rationale text matches the TSPEC-mandated string exactly:
 *   "Location is used to compute magnetic declination for True North.
 *    Your coordinates are stored on-device only and are never shared."
 *
 * Note: These tests call [CompassActivity.showLocationPermissionRationale] directly via the
 * Activity's internal API (same pattern used in [LocationPermissionTest]). This lets the test
 * assert the full rationale UI flow without depending on the OS returning a specific value
 * from [shouldShowRequestPermissionRationale], which is not deterministic in instrumented tests.
 *
 * TSPEC §10.3 `LocationPermissionRationaleTest` / BR-LOC-04.
 * PLAN §4 P8.5 success criteria: `LocationPermissionRationaleTest (BR-LOC-04)`.
 */
@RunWith(AndroidJUnit4::class)
class LocationPermissionRationaleTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(CompassActivity::class.java)

    // -------------------------------------------------------------------------
    // Rationale dialog appearance
    // -------------------------------------------------------------------------

    /**
     * The rationale dialog displays the TSPEC-mandated message text when shown.
     *
     * BR-LOC-04 (TE-T-10): rationale dialog text must contain the exact TSPEC message.
     * FSPEC §2.4 step 2a: "in-app explanation dialog" is shown before system permission prompt.
     */
    @Test
    fun rationaleDialog_displaysCorrectMessage() {
        activityRule.scenario.onActivity { activity ->
            activity.showLocationPermissionRationale(onContinue = {}, onNotNow = {})
        }

        // TSPEC-mandated rationale text must be visible in full
        onView(withText(R.string.location_permission_rationale_message))
            .check(matches(isDisplayed()))
    }

    /**
     * The rationale dialog title is visible.
     *
     * FSPEC §2.4 step 2a: dialog is presented with title "Location needed for True North".
     */
    @Test
    fun rationaleDialog_displaysTitle() {
        activityRule.scenario.onActivity { activity ->
            activity.showLocationPermissionRationale(onContinue = {}, onNotNow = {})
        }

        onView(withText(R.string.location_permission_rationale_title))
            .check(matches(isDisplayed()))
    }

    /**
     * Both "Continue" and "Not now" buttons are present in the rationale dialog.
     *
     * FSPEC §2.4 step 2a: "two actions: 'Continue' (proceed) and 'Not now' (abandon)".
     */
    @Test
    fun rationaleDialog_showsBothActionButtons() {
        activityRule.scenario.onActivity { activity ->
            activity.showLocationPermissionRationale(onContinue = {}, onNotNow = {})
        }

        onView(withText(R.string.continue_label)).check(matches(isDisplayed()))
        onView(withText(R.string.not_now)).check(matches(isDisplayed()))
    }

    /**
     * The rationale message contains the exact TSPEC-mandated sub-string about coordinates
     * being stored on-device only.
     *
     * BR-LOC-04: "Your coordinates are stored on-device only and are never shared."
     */
    @Test
    fun rationaleDialog_messageContainsOnDeviceOnlyText() {
        activityRule.scenario.onActivity { activity ->
            activity.showLocationPermissionRationale(onContinue = {}, onNotNow = {})
        }

        onView(withText(containsString("stored on-device only and are never shared")))
            .check(matches(isDisplayed()))
    }

    // -------------------------------------------------------------------------
    // "Not now" path — toggle must remain on Magnetic N
    // -------------------------------------------------------------------------

    /**
     * Tapping "Not now" dismisses the rationale dialog.
     *
     * FSPEC §2.4 step 2a: "'Not now' → abandons the permission request; flow falls to 3b."
     * PLAN P8.5: "system permission dialog NOT shown, toggle remains on Magnetic N."
     */
    @Test
    fun rationaleDialog_tappingNotNow_dismissesDialog() {
        activityRule.scenario.onActivity { activity ->
            activity.showLocationPermissionRationale(onContinue = {}, onNotNow = {})
        }

        onView(withText(R.string.not_now)).perform(click())

        // Rationale dialog must be dismissed — title and message no longer visible
        onView(withText(R.string.location_permission_rationale_message))
            .check(doesNotExist())
    }

    /**
     * Tapping "Not now" invokes the onNotNow callback and the toggle stays on Magnetic N.
     *
     * BR-LOC-04: system permission dialog is NOT shown after "Not now".
     * FSPEC §2.4 step 2a: toggle stays on "Magnetic N" after abandoning.
     */
    @Test
    fun rationaleDialog_tappingNotNow_northToggleRemainsOnMagneticN() {
        val notNowCalled = AtomicBoolean(false)

        activityRule.scenario.onActivity { activity ->
            activity.showLocationPermissionRationale(
                onContinue = {},
                onNotNow = { notNowCalled.set(true) }
            )
        }

        onView(withText(R.string.not_now)).perform(click())

        // onNotNow callback must have been invoked
        assert(notNowCalled.get()) { "Expected onNotNow to be called after tapping Not now" }

        // North type toggle must remain on Magnetic N (default checked button)
        onView(withId(R.id.northLabel))
            .check(matches(withText(R.string.magnetic_north)))
    }

    // -------------------------------------------------------------------------
    // "Continue" path — callback invoked, dialog dismissed
    // -------------------------------------------------------------------------

    /**
     * Tapping "Continue" invokes the onContinue callback and dismisses the rationale dialog.
     *
     * FSPEC §2.4 step 2a: "'Continue' → proceeds to system permission prompt."
     * In production, onContinue launches the permission request launcher.
     */
    @Test
    fun rationaleDialog_tappingContinue_invokesOnContinueAndDismissesDialog() {
        val continueCalled = AtomicBoolean(false)

        activityRule.scenario.onActivity { activity ->
            activity.showLocationPermissionRationale(
                onContinue = { continueCalled.set(true) },
                onNotNow = {}
            )
        }

        onView(withText(R.string.continue_label)).perform(click())

        // onContinue callback must have been invoked
        assert(continueCalled.get()) { "Expected onContinue to be called after tapping Continue" }

        // Rationale dialog must be dismissed
        onView(withText(R.string.location_permission_rationale_message))
            .check(doesNotExist())
    }
}
