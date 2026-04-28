package com.luopan.compass.diagnostics

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
/**
 * JVM unit tests for [SensorCapabilityLogger].
 *
 * Uses Robolectric for the [buildJsonFromSensors] tests because [org.json.JSONObject] is an
 * Android API that returns null from stubs in plain JVM mode. Robolectric provides a real
 * implementation. The [maybeWrite] tests are also included here for cohesion.
 *
 * Tests are structured in two groups:
 *
 * 1. [buildJsonFromSensors] pure-function tests: call the function directly with [SensorInfo]
 *    inputs — no Android runtime needed. Covers AT-SENSOR-01-G and reporting mode mapping.
 *
 * 2. [SensorCapabilityLogger.maybeWrite] failure tests: use [TestSensorCapabilityLogger], a
 *    subclass that overrides the private [buildJson] delegation to remove the Context/
 *    SensorManager dependency from the test boundary. Covers AT-SENSOR-01-E and AT-SENSOR-01-F.
 *
 * PLAN task C-1; Properties: PROP-SENSOR-020–024, PROP-SENSOR-030–037.
 */
class SensorCapabilityLoggerTest {

    // ---------------------------------------------------------------------------
    // Fakes
    // ---------------------------------------------------------------------------

    /** In-memory recording of sensorProfileWrittenForVersion. */
    private class FakeSettings(
        var sensorProfileWrittenForVersion: Int = 0
    )

    /** Recording SensorFileWriter; configurable to throw. */
    private class FakeSensorFileWriter(
        private val throwable: Throwable? = null
    ) : SensorFileWriter {
        var writeCallCount = 0
        var lastContent: String? = null

        override fun write(file: File, content: String) {
            writeCallCount++
            lastContent = content
            if (throwable != null) throw throwable
        }
    }

    /** Records Log.e calls instead of routing to android.util.Log. */
    private class FakeLogger : SensorCapabilityLogger.Logger {
        var errorCallCount = 0
        var lastMessage: String? = null
        override fun e(tag: String, msg: String, t: Throwable?) {
            errorCallCount++
            lastMessage = msg
        }
    }

    // ---------------------------------------------------------------------------
    // State
    // ---------------------------------------------------------------------------

    private lateinit var fakeSettings: FakeSettings
    private lateinit var fakeFileWriter: FakeSensorFileWriter
    private lateinit var fakeLogger: FakeLogger

    @Before
    fun setUp() {
        fakeSettings = FakeSettings()
        fakeFileWriter = FakeSensorFileWriter()
        fakeLogger = FakeLogger()
    }

    // ---------------------------------------------------------------------------
    // Helper: build a testable logger instance that bypasses SensorManager
    // ---------------------------------------------------------------------------

    /**
     * Creates a [SensorCapabilityLogger] whose [buildJson] method is overridden
     * to return a fixed JSON string, removing the Context/SensorManager dependency
     * from the test boundary.
     */
    private fun buildTestableLogger(
        storedVersion: Int,
        currentVersion: Int,
        fileWriter: SensorFileWriter,
        logger: SensorCapabilityLogger.Logger,
        settingsUpdater: (Int) -> Unit,
        fixedJson: String = """{"sensors":[]}"""
    ): SensorCapabilityLogger {
        val tempFile = File.createTempFile("sensor_profile_test", ".json")
        tempFile.deleteOnExit()
        return object : SensorCapabilityLogger(
            storedVersionProvider = { storedVersion },
            currentVersion = currentVersion,
            fileWriter = fileWriter,
            logger = logger,
            settingsVersionWriter = settingsUpdater
        ) {
            override fun buildJson(): String = fixedJson
            override fun resolveOutputFile(): File = tempFile
        }
    }

    // ---------------------------------------------------------------------------
    // buildJsonFromSensors() pure function tests
    // ---------------------------------------------------------------------------

    // AT-SENSOR-01-G: empty list → all 7 root keys present, sensors array empty
    // PROP-SENSOR-030, PROP-SENSOR-034
    @Test
    fun `buildJsonFromSensors with empty list has all required root keys`() {
        val json = buildJsonFromSensors(
            sensors = emptyList(),
            versionCode = 40,
            writtenAtIso8601 = "2026-04-27T00:00:00Z",
            deviceModel = "TestPhone",
            deviceManufacturer = "TestCo",
            androidVersion = "14",
            androidApiLevel = 34
        )
        val root = JSONObject(json)

        assertTrue(root.has("device_model"))
        assertTrue(root.has("device_manufacturer"))
        assertTrue(root.has("android_version"))
        assertTrue(root.has("android_api_level"))
        assertEquals(40, root.getInt("app_version_code"))
        assertEquals("2026-04-27T00:00:00Z", root.getString("written_at_iso8601"))
        assertTrue(root.has("sensors"))
        val arr = root.getJSONArray("sensors")
        assertEquals(0, arr.length())
    }

    // PROP-SENSOR-032: 2-space indentation
    @Test
    fun `buildJsonFromSensors produces 2-space indented output`() {
        val json = buildJsonFromSensors(
            sensors = emptyList(),
            versionCode = 1,
            writtenAtIso8601 = "2026-04-27T00:00:00Z",
            deviceModel = "TestPhone",
            deviceManufacturer = "TestCo",
            androidVersion = "14",
            androidApiLevel = 34
        )
        assertTrue("JSON must contain 2-space indentation", json.contains("  "))
    }

    // PROP-SENSOR-033: writtenAtIso8601 passes through correctly (ends with Z)
    @Test
    fun `buildJsonFromSensors written_at_iso8601 ends with Z`() {
        val json = buildJsonFromSensors(
            sensors = emptyList(),
            versionCode = 1,
            writtenAtIso8601 = "2026-04-27T08:30:00Z",
            deviceModel = "TestPhone",
            deviceManufacturer = "TestCo",
            androidVersion = "14",
            androidApiLevel = 34
        )
        val root = JSONObject(json)
        assertTrue(root.getString("written_at_iso8601").endsWith("Z"))
    }

    // PROP-SENSOR-031: per-sensor fields all present for a single SensorInfo
    @Test
    fun `buildJsonFromSensors maps SensorInfo to sensor object with all required fields`() {
        val info = SensorInfo(
            type = 1,
            name = "Accelerometer",
            vendor = "TestVendor",
            resolution = 0.01f,
            maximumRange = 39.2f,
            reportingMode = 0
        )
        val json = buildJsonFromSensors(
            sensors = listOf(info),
            versionCode = 40,
            writtenAtIso8601 = "2026-04-27T00:00:00Z",
            deviceModel = "TestPhone",
            deviceManufacturer = "TestCo",
            androidVersion = "14",
            androidApiLevel = 34
        )
        val root = JSONObject(json)
        val sensorsArr: JSONArray = root.getJSONArray("sensors")
        assertEquals(1, sensorsArr.length())
        val entry = sensorsArr.getJSONObject(0)
        assertEquals(1, entry.getInt("type_constant"))
        assertEquals("Accelerometer", entry.getString("name"))
        assertEquals("TestVendor", entry.getString("vendor"))
        assertEquals(0.01, entry.getDouble("resolution_ut_or_native"), 0.001)
        assertEquals(39.2, entry.getDouble("max_range_native"), 0.01)
        assertEquals("CONTINUOUS", entry.getString("reporting_mode"))
    }

    // AT-SENSOR-01-F: reporting mode mapping — known constants
    // PROP-SENSOR-035
    @Test
    fun `mapReportingMode maps 0 to CONTINUOUS`() {
        assertEquals("CONTINUOUS", mapReportingMode(0))
    }

    @Test
    fun `mapReportingMode maps 1 to ON_CHANGE`() {
        assertEquals("ON_CHANGE", mapReportingMode(1))
    }

    @Test
    fun `mapReportingMode maps 2 to ONE_SHOT`() {
        assertEquals("ONE_SHOT", mapReportingMode(2))
    }

    @Test
    fun `mapReportingMode maps 3 to SPECIAL_TRIGGER`() {
        assertEquals("SPECIAL_TRIGGER", mapReportingMode(3))
    }

    // AT-SENSOR-01-G: unknown mode → UNKNOWN($value)
    // PROP-SENSOR-036
    @Test
    fun `mapReportingMode maps unknown 99 to UNKNOWN(99)`() {
        assertEquals("UNKNOWN(99)", mapReportingMode(99))
    }

    @Test
    fun `mapReportingMode maps negative to UNKNOWN with value`() {
        assertEquals("UNKNOWN(-1)", mapReportingMode(-1))
    }

    // ---------------------------------------------------------------------------
    // maybeWrite() version-gate tests
    // ---------------------------------------------------------------------------

    // PROP-SENSOR-010, PROP-SENSOR-012: same version → no write
    @Test
    fun `maybeWrite skips write when storedVersion equals currentVersion`() {
        val logger = buildTestableLogger(
            storedVersion = 40,
            currentVersion = 40,
            fileWriter = fakeFileWriter,
            logger = fakeLogger,
            settingsUpdater = { fakeSettings.sensorProfileWrittenForVersion = it }
        )
        logger.maybeWrite()
        assertEquals(0, fakeFileWriter.writeCallCount)
    }

    // PROP-SENSOR-011: downgrade → no write
    @Test
    fun `maybeWrite skips write when storedVersion is greater than currentVersion`() {
        val logger = buildTestableLogger(
            storedVersion = 41,
            currentVersion = 40,
            fileWriter = fakeFileWriter,
            logger = fakeLogger,
            settingsUpdater = { fakeSettings.sensorProfileWrittenForVersion = it }
        )
        logger.maybeWrite()
        assertEquals(0, fakeFileWriter.writeCallCount)
    }

    // Successful write updates stored version
    @Test
    fun `maybeWrite updates storedVersion on successful write`() {
        val logger = buildTestableLogger(
            storedVersion = 0,
            currentVersion = 40,
            fileWriter = fakeFileWriter,
            logger = fakeLogger,
            settingsUpdater = { fakeSettings.sensorProfileWrittenForVersion = it }
        )
        logger.maybeWrite()
        assertEquals(40, fakeSettings.sensorProfileWrittenForVersion)
        assertEquals(1, fakeFileWriter.writeCallCount)
    }

    // ---------------------------------------------------------------------------
    // AT-SENSOR-01-E / PROP-SENSOR-020: IOException caught, version NOT updated
    // ---------------------------------------------------------------------------

    @Test
    fun `maybeWrite does not update version when fileWriter throws IOException`() {
        val throwingWriter = FakeSensorFileWriter(throwable = IOException("disk full"))
        val logger = buildTestableLogger(
            storedVersion = 0,
            currentVersion = 40,
            fileWriter = throwingWriter,
            logger = fakeLogger,
            settingsUpdater = { fakeSettings.sensorProfileWrittenForVersion = it }
        )
        logger.maybeWrite()
        assertEquals(0, fakeSettings.sensorProfileWrittenForVersion)
    }

    // PROP-SENSOR-023: Log.e called on IOException
    @Test
    fun `maybeWrite logs error when fileWriter throws IOException`() {
        val throwingWriter = FakeSensorFileWriter(throwable = IOException("disk full"))
        val logger = buildTestableLogger(
            storedVersion = 0,
            currentVersion = 40,
            fileWriter = throwingWriter,
            logger = fakeLogger,
            settingsUpdater = { fakeSettings.sensorProfileWrittenForVersion = it }
        )
        logger.maybeWrite()
        assertTrue("Log.e must be called on IOException", fakeLogger.errorCallCount >= 1)
        assertNotNull(fakeLogger.lastMessage)
    }

    // ---------------------------------------------------------------------------
    // AT-SENSOR-01-F (failure path) / PROP-SENSOR-021, PROP-SENSOR-022:
    // SecurityException swallowed, no crash, version NOT updated
    // ---------------------------------------------------------------------------

    @Test
    fun `maybeWrite swallows SecurityException without rethrowing`() {
        val throwingWriter = FakeSensorFileWriter(throwable = SecurityException("no perm"))
        val logger = buildTestableLogger(
            storedVersion = 0,
            currentVersion = 40,
            fileWriter = throwingWriter,
            logger = fakeLogger,
            settingsUpdater = { fakeSettings.sensorProfileWrittenForVersion = it }
        )
        // Must not throw
        logger.maybeWrite()
        assertEquals(0, fakeSettings.sensorProfileWrittenForVersion)
    }

    // PROP-SENSOR-023: Log.e called on SecurityException
    @Test
    fun `maybeWrite logs error when fileWriter throws SecurityException`() {
        val throwingWriter = FakeSensorFileWriter(throwable = SecurityException("no perm"))
        val logger = buildTestableLogger(
            storedVersion = 0,
            currentVersion = 40,
            fileWriter = throwingWriter,
            logger = fakeLogger,
            settingsUpdater = { fakeSettings.sensorProfileWrittenForVersion = it }
        )
        logger.maybeWrite()
        assertTrue("Log.e must be called on SecurityException", fakeLogger.errorCallCount >= 1)
    }

    // PROP-SENSOR-024: retry on next call after failure
    @Test
    fun `maybeWrite retries write on subsequent call after IOException`() {
        val throwingWriter = FakeSensorFileWriter(throwable = IOException("disk full"))
        val logger = buildTestableLogger(
            storedVersion = 0,
            currentVersion = 40,
            fileWriter = throwingWriter,
            logger = fakeLogger,
            settingsUpdater = { fakeSettings.sensorProfileWrittenForVersion = it }
        )
        logger.maybeWrite() // first attempt fails
        logger.maybeWrite() // second attempt must also try (version still not updated)
        assertEquals(2, throwingWriter.writeCallCount)
    }
}
