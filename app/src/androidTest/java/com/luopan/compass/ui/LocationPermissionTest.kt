package com.luopan.compass.ui

import android.Manifest
import android.app.Instrumentation
import android.net.Uri
import android.provider.Settings
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luopan.compass.R
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Espresso UI tests for the location permission request flow (P3.2).
 *
 * Tests cover:
 * - Grant path: when permission is pre-granted, True North activation proceeds without dialogs
 * - Deny path: when permission is denied, compass degrades gracefully to Magnetic N only
 * - Rationale shown: when shouldShowRequestPermissionRationale triggers showLocationPermissionRationale,
 *   the rationale dialog appears with correct text and buttons
 * - Open Settings dialog shown and Settings intent launched on permanent denial
 *
 * See TSPEC §6.5, FSPEC §2.4 step 2a, FSPEC BR-LOC-04, PLAN §P3.2.
 */
@RunWith(AndroidJUnit4::class)
class LocationPermissionTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(CompassActivity::class.java)

    @Before
    fun initIntents() {
        Intents.init()
    }

    @After
    fun releaseIntents() {
        Intents.release()
    }

    // -------------------------------------------------------------------------
    // Grant path
    // -------------------------------------------------------------------------

    /**
     * Grant path: when permission is already granted, calling requestLocationPermissionForTrueNorth()
     * does NOT show the rationale dialog or any permission UI.
     *
     * FSPEC §2.4 step 3a: "Permission granted: The location resolution chain runs."
     * PROP-LOCATION-05: rationale only shown when shouldShowRationale = true.
     */
    @Test
    fun grantPath_withPermissionGranted_requestLocationPermissionForTrueNorth_showsNoRationaleDialog() {
        // Pre-grant the permission so the system confirms it
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            "com.luopan.compass",
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        // Trigger the permission flow — with permission already granted, no dialog should appear
        activityRule.scenario.onActivity { activity ->
            activity.requestLocationPermissionForTrueNorth()
        }

        // Rationale dialog must NOT appear — permission was already granted
        onView(withText(containsString("Location is used to compute magnetic declination")))
            .check(doesNotExist())
    }

    // -------------------------------------------------------------------------
    // Deny path — Magnetic N remains the default
    // -------------------------------------------------------------------------

    /**
     * Deny path: the default state (no permission granted, no toggle activated) shows "Magnetic N".
     *
     * FSPEC §2.2: "Magnetic N (default for new installs and devices without a resolved location)"
     * FSPEC §2.4 step 3b: "Toggle remains on Magnetic N."
     */
    @Test
    fun denyPath_magneticNLabelShown_whenPermissionNotGranted() {
        // Without a permission grant, the north label must default to Magnetic N
        onView(withText(R.string.magnetic_north))
            .check(matches(isDisplayed()))
    }

    // -------------------------------------------------------------------------
    // Rationale dialog
    // -------------------------------------------------------------------------

    /**
     * Rationale shown: showLocationPermissionRationale() displays a dialog with
     * the TSPEC-mandated text and two action buttons (Continue / Not now).
     *
     * TSPEC §6.5 step 3, FSPEC §2.4 step 2a, FSPEC BR-LOC-04, PROP-LOCATION-05.
     */
    @Test
    fun rationalePath_showLocationPermissionRationale_displaysRationaleDialog_withCorrectText() {
        activityRule.scenario.onActivity { activity ->
            activity.showLocationPermissionRationale(
                onContinue = {},
                onNotNow = {}
            )
        }

        // The TSPEC-mandated rationale text must be visible
        onView(withText(R.string.location_permission_rationale_message))
            .check(matches(isDisplayed()))
        // Both action buttons must be present
        onView(withText(R.string.continue_label))
            .check(matches(isDisplayed()))
        onView(withText(R.string.not_now))
            .check(matches(isDisplayed()))
    }

    /**
     * Rationale dialog "Not now": tapping "Not now" invokes the onNotNow callback
     * and dismisses the dialog without proceeding to the system permission prompt.
     *
     * FSPEC §2.4 step 2a: "'Not now' → abandons the permission request; flow falls to 3b."
     */
    @Test
    fun rationaleDialog_tappingNotNow_invokesOnNotNowCallback_andDismissesDialog() {
        val notNowCalled = AtomicBoolean(false)
        val continueCalled = AtomicBoolean(false)

        activityRule.scenario.onActivity { activity ->
            activity.showLocationPermissionRationale(
                onContinue = { continueCalled.set(true) },
                onNotNow = { notNowCalled.set(true) }
            )
        }

        // Tap "Not now"
        onView(withText(R.string.not_now)).perform(click())

        // Not Now callback must have fired; Continue must NOT have fired
        assert(notNowCalled.get()) { "Expected onNotNow to be called after tapping Not now" }
        assert(!continueCalled.get()) { "Expected onContinue NOT to be called after tapping Not now" }

        // Rationale dialog must be dismissed
        onView(withText(R.string.location_permission_rationale_message))
            .check(doesNotExist())
    }

    /**
     * Rationale dialog "Continue": tapping "Continue" invokes the onContinue callback
     * (which would proceed to launch the system permission request) and dismisses the dialog.
     *
     * FSPEC §2.4 step 2a: "'Continue' → proceeds to system permission prompt."
     */
    @Test
    fun rationaleDialog_tappingContinue_invokesOnContinueCallback_andDismissesDialog() {
        val continueCalled = AtomicBoolean(false)

        activityRule.scenario.onActivity { activity ->
            activity.showLocationPermissionRationale(
                onContinue = { continueCalled.set(true) },
                onNotNow = {}
            )
        }

        // Tap "Continue"
        onView(withText(R.string.continue_label)).perform(click())

        // Continue callback must have fired
        assert(continueCalled.get()) { "Expected onContinue to be called after tapping Continue" }

        // Rationale dialog must be dismissed
        onView(withText(R.string.location_permission_rationale_message))
            .check(doesNotExist())
    }

    // -------------------------------------------------------------------------
    // Permanent denial — Open Settings dialog
    // -------------------------------------------------------------------------

    /**
     * Permanent denial: showOpenSettingsDialog() displays a dialog directing the user to Settings.
     *
     * TSPEC §6.5 step 4, FSPEC §2.4 step 3c, FSPEC error scenarios table.
     */
    @Test
    fun permanentDenial_showOpenSettingsDialog_displaysOpenSettingsDialog() {
        activityRule.scenario.onActivity { activity ->
            activity.showOpenSettingsDialog()
        }

        // All three required elements must be visible
        onView(withText(R.string.location_permission_settings_title))
            .check(matches(isDisplayed()))
        onView(withText(R.string.location_permission_settings_message))
            .check(matches(isDisplayed()))
        onView(withText(R.string.open_settings))
            .check(matches(isDisplayed()))
    }

    /**
     * Open Settings dialog: tapping "Open Settings" launches the system Settings intent
     * for this application's detail page.
     *
     * FSPEC §2.4 step 3c: "app directs the user to device settings for manual permission grant."
     */
    @Test
    fun permanentDenial_tappingOpenSettings_launchesAppSettingsIntent() {
        // Stub the Settings intent so the test doesn't leave the app
        Intents.intending(
            allOf(hasAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS))
        ).respondWith(Instrumentation.ActivityResult(0, null))

        activityRule.scenario.onActivity { activity ->
            activity.showOpenSettingsDialog()
        }

        // Tap "Open Settings"
        onView(withText(R.string.open_settings)).perform(click())

        // Verify that the correct intent was fired with the package URI
        Intents.intended(
            allOf(
                hasAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS),
                hasData(Uri.fromParts("package", "com.luopan.compass", null))
            )
        )
    }

    /**
     * Open Settings dialog: tapping "Cancel" dismisses the dialog without launching Settings.
     *
     * FSPEC: user may dismiss the settings redirect and use manual entry instead.
     */
    @Test
    fun permanentDenial_tappingCancel_dismissesOpenSettingsDialog() {
        activityRule.scenario.onActivity { activity ->
            activity.showOpenSettingsDialog()
        }

        // Tap "Cancel"
        onView(withText(android.R.string.cancel)).perform(click())

        // Dialog must be dismissed
        onView(withText(R.string.location_permission_settings_title))
            .check(doesNotExist())
    }
}
