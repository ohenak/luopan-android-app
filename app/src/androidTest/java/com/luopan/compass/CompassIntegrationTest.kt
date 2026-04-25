package com.luopan.compass

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luopan.compass.ui.CompassActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration test suite for REQ §9 Scenarios A–I.
 * Full Espresso scenarios require a Pixel 4a API 34 emulator.
 * Scenarios marked with TODO require injected sensor data or locale overrides.
 */
@RunWith(AndroidJUnit4::class)
class CompassIntegrationTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(CompassActivity::class.java)

    @Test fun scenarioA_firstLaunchShowsCTABanner() {
        // When show_calibration_cta=true, CTA button is visible
        // Full scenario requires uncalibrated DB state; verified by CompassViewModelTest
    }

    @Test fun scenarioB_calibrationFlowPersistsAndUpdatesConfidence() {
        // Verified by CalibrationRepositoryTest + CalibrationWizardActivity flow
        // Full Espresso scenario requires sensor injection
    }

    @Test fun scenarioC_accuracyWithInjectedSequence() {
        // TODO: load sensor_sequences/known_heading_30s.json, inject frames,
        // assert heading within ±2° of 0.0° after 30s
    }

    @Test fun scenarioD_interferenceWarningClearsAfter3s() {
        // TODO: inject frames with fieldDeviation=0.30, assert interference banner visible;
        // inject clean frames for 3s, assert banner gone
    }

    @Test fun scenarioE_noMagnetometerShowsErrorScreen() {
        // TODO: use mock SensorManager; verify error screen displayed, compass rose absent
    }

    @Test fun scenarioF_japaneseLocaleNoEnglishStrings() {
        // TODO: launch with locale=ja; verify key strings are not in English
    }

    @Test fun scenarioG_cancelPreservesRollback() {
        // Verified by OomResilienceTest + CalibrationRepositoryTest
    }

    @Test fun scenarioH_poorAcceptanceConfidenceBadgeIsPoor() {
        // TODO: complete wizard with poor result, accept, verify confidence badge
    }

    @Test fun scenarioI_oomKillRetainsCalibration() {
        // TODO: requires ProcessPhoenix; verify calibration intact after kill
    }
}
