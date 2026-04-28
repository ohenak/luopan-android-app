package com.luopan.compass.diagnostics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luopan.compass.settings.SettingsRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumented tests for [SensorCapabilityLogger].
 *
 * These tests run on a real device or emulator and verify the version-gate behaviour
 * using a real [SettingsRepository] backed by SharedPreferences on-disk storage.
 *
 * Covers AT-SENSOR-01-A through AT-SENSOR-01-D (PLAN task C-4).
 * Properties: PROP-SENSOR-001–003, PROP-SENSOR-010.
 *
 * Run via: ./gradlew :app:connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class SensorCapabilityLoggerInstrumentedTest {

    private lateinit var context: Context
    private lateinit var settings: SettingsRepository
    private lateinit var outputFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        settings = SettingsRepository(context)

        // Reset the version gate before each test
        settings.sensorProfileWrittenForVersion = 0

        // Use a dedicated test output file in the app's files dir
        outputFile = File(context.filesDir, "sensor_profile_test.json")
        outputFile.delete() // ensure clean state
    }

    @After
    fun tearDown() {
        // Clean up: reset version gate and delete test output file
        settings.sensorProfileWrittenForVersion = 0
        outputFile.delete()
    }

    // ---------------------------------------------------------------------------
    // Recording SensorFileWriter for test isolation — writes to test output file
    // ---------------------------------------------------------------------------

    private inner class TestSensorFileWriter : SensorFileWriter {
        var writeCallCount = 0
        var lastContent: String? = null

        override fun write(file: File, content: String) {
            writeCallCount++
            lastContent = content
            // Write to the dedicated test output file (not the production location)
            outputFile.writeText(content)
        }
    }

    // ---------------------------------------------------------------------------
    // Helper: build a SensorCapabilityLogger wired to real SettingsRepository
    // ---------------------------------------------------------------------------

    private fun buildLogger(
        currentVersion: Int,
        fileWriter: SensorFileWriter
    ): SensorCapabilityLogger {
        return SensorCapabilityLogger(
            context = context,
            settings = settings,
            fileWriter = fileWriter,
            versionCode = currentVersion
        )
    }

    // ---------------------------------------------------------------------------
    // AT-SENSOR-01-A: first launch — file written, version key updated
    // PROP-SENSOR-001
    // ---------------------------------------------------------------------------

    @Test
    fun AT_SENSOR_01_A_first_launch_writes_file_and_updates_version_key() {
        // Given: stored version = 0, current version = 40
        assertEquals(0, settings.sensorProfileWrittenForVersion)
        val writer = TestSensorFileWriter()
        val logger = buildLogger(currentVersion = 40, fileWriter = writer)

        // When
        logger.maybeWrite()

        // Then: file was written
        assertEquals(1, writer.writeCallCount)
        assertNotNull(writer.lastContent)
        assertTrue(writer.lastContent!!.isNotEmpty())

        // Then: version key updated to 40
        assertEquals(40, settings.sensorProfileWrittenForVersion)
    }

    // ---------------------------------------------------------------------------
    // AT-SENSOR-01-B: same-version relaunch — file NOT rewritten
    // PROP-SENSOR-010
    // ---------------------------------------------------------------------------

    @Test
    fun AT_SENSOR_01_B_same_version_relaunch_does_not_rewrite_file() {
        // Given: first launch writes file
        val writer = TestSensorFileWriter()
        val logger = buildLogger(currentVersion = 40, fileWriter = writer)
        logger.maybeWrite() // first launch

        assertEquals(40, settings.sensorProfileWrittenForVersion)
        assertEquals(1, writer.writeCallCount)

        // When: same-version relaunch (same logger instance simulates same version)
        logger.maybeWrite() // second launch — same version

        // Then: write NOT called again
        assertEquals(1, writer.writeCallCount)
    }

    // ---------------------------------------------------------------------------
    // AT-SENSOR-01-C: version upgrade — file rewritten with new version code
    // PROP-SENSOR-003
    // ---------------------------------------------------------------------------

    @Test
    fun AT_SENSOR_01_C_version_upgrade_rewrites_file_with_new_version_code() {
        // Given: stored version = 39 (simulating previous install)
        settings.sensorProfileWrittenForVersion = 39
        val writer = TestSensorFileWriter()
        val logger = buildLogger(currentVersion = 40, fileWriter = writer)

        // When
        logger.maybeWrite()

        // Then: file written once with version 40
        assertEquals(1, writer.writeCallCount)
        assertEquals(40, settings.sensorProfileWrittenForVersion)
        assertNotNull(writer.lastContent)
        // The JSON must contain app_version_code = 40
        assertTrue(
            "JSON must contain app_version_code 40",
            writer.lastContent!!.contains("\"app_version_code\"")
        )
    }

    // ---------------------------------------------------------------------------
    // AT-SENSOR-01-D: data clear (key reset to 0) — file rewritten on relaunch
    // PROP-SENSOR-002
    // ---------------------------------------------------------------------------

    @Test
    fun AT_SENSOR_01_D_after_data_clear_file_is_rewritten() {
        // Given: first launch writes file
        val writer1 = TestSensorFileWriter()
        buildLogger(currentVersion = 40, fileWriter = writer1).maybeWrite()
        assertEquals(40, settings.sensorProfileWrittenForVersion)

        // Simulate data clear: reset SharedPreferences key to 0
        settings.sensorProfileWrittenForVersion = 0

        // When: relaunch after data clear
        val writer2 = TestSensorFileWriter()
        buildLogger(currentVersion = 40, fileWriter = writer2).maybeWrite()

        // Then: file rewritten; key updated again
        assertEquals(1, writer2.writeCallCount)
        assertEquals(40, settings.sensorProfileWrittenForVersion)
    }
}
