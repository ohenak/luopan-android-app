package com.luopan.compass.diagnostics

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import com.luopan.compass.settings.SettingsRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter

// ---------------------------------------------------------------------------
// SensorInfo — pure Kotlin data class (no android.hardware.Sensor dependency)
//
// PROP-SENSOR-037: buildJsonFromSensors() must accept List<SensorInfo>, not
// List<android.hardware.Sensor>, because Sensor is a final class with no
// public constructor and cannot be instantiated in a JVM unit test.
// ---------------------------------------------------------------------------

/**
 * Immutable wrapper over the fields from [android.hardware.Sensor] needed for
 * the sensor capability profile JSON.
 *
 * Production code maps each [Sensor] to a [SensorInfo] before calling
 * [buildJsonFromSensors]. Test code constructs [SensorInfo] directly.
 *
 * TSPEC §5.5; PROP-SENSOR-037.
 */
data class SensorInfo(
    val type: Int,
    val name: String,
    val vendor: String,
    val resolution: Float,
    val maximumRange: Float,
    val reportingMode: Int
)

// ---------------------------------------------------------------------------
// SensorCapabilityLogger
// ---------------------------------------------------------------------------

/**
 * Writes a sensor capability profile JSON to internal app storage on first launch
 * after a new app version is installed.
 *
 * **Version gate:** only writes when `currentVersion > storedVersionProvider()`.
 * On successful write, updates the stored version via [settingsVersionWriter].
 * On any failure ([Exception], including [IOException] and [SecurityException]),
 * logs at [logger] and does NOT update the stored version — guaranteeing a retry
 * on the next launch.
 *
 * **Thread model:** [maybeWrite] is a blocking call that writes to the filesystem;
 * it must be called from a background dispatcher (e.g., `Dispatchers.IO`).
 *
 * **Testability:**
 * - Primary constructor accepts all dependencies as parameters, enabling JVM unit tests
 *   without an Android runtime. Override [buildJson] to bypass SensorManager in tests.
 * - [buildJsonFromSensors] is a pure `internal` package-level function, callable directly
 *   from tests without instantiating [SensorCapabilityLogger].
 * - [Logger] interface replaces `android.util.Log` for JVM unit test compatibility.
 *
 * @param storedVersionProvider Returns the currently persisted version code (default 0).
 * @param currentVersion        App version code to compare against stored version.
 * @param fileWriter            Abstraction over file write (injectable; production = [RealSensorFileWriter]).
 * @param logger                Log delegate (production = [AndroidLogger]; tests use [Logger] impl).
 * @param settingsVersionWriter Called with the new version code on successful write.
 *
 * TSPEC §5.5; PROP-SENSOR-001–038.
 */
open class SensorCapabilityLogger(
    private val storedVersionProvider: () -> Int,
    val currentVersion: Int,
    private val fileWriter: SensorFileWriter,
    private val logger: Logger,
    private val settingsVersionWriter: (Int) -> Unit
) {
    companion object {
        private const val TAG = "SensorCapabilityLogger"
        const val FILE_NAME = "sensor_profile.json"
    }

    /**
     * Injectable logger interface — replaces [android.util.Log] for JVM unit test
     * compatibility ([android.util.Log] is not available in a plain JVM test environment).
     */
    interface Logger {
        fun e(tag: String, msg: String, t: Throwable? = null)
    }

    /** Production [Logger] implementation that delegates to [android.util.Log]. */
    class AndroidLogger : Logger {
        override fun e(tag: String, msg: String, t: Throwable?) {
            Log.e(tag, msg, t)
        }
    }

    // ---------------------------------------------------------------------------
    // Production convenience constructor
    // ---------------------------------------------------------------------------

    /**
     * Production constructor — delegates to the primary constructor.
     *
     * Wires [storedVersionProvider] and [settingsVersionWriter] to [SettingsRepository].
     * [versionCode] defaults to [com.luopan.compass.BuildConfig.VERSION_CODE], but callers
     * may supply a different value for testing.
     *
     * The [Context] is retained for [resolveOutputFile] and the default [buildJson]
     * implementation (which calls [Context.getSystemService]).
     */
    constructor(
        context: Context,
        settings: SettingsRepository,
        fileWriter: SensorFileWriter = RealSensorFileWriter(),
        logger: Logger = AndroidLogger(),
        versionCode: Int = context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionCode
    ) : this(
        storedVersionProvider = { settings.sensorProfileWrittenForVersion },
        currentVersion = versionCode,
        fileWriter = fileWriter,
        logger = logger,
        settingsVersionWriter = { settings.sensorProfileWrittenForVersion = it }
    ) {
        this.context = context
    }

    /** Retained only by the production constructor path. Null in the primary constructor path. */
    private var context: Context? = null

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    /**
     * Checks the version gate and writes `sensor_profile.json` if needed.
     *
     * Must be called from a background dispatcher ([kotlinx.coroutines.Dispatchers.IO]).
     * Returns immediately if the version gate blocks the write.
     *
     * Failure contract (PROP-SENSOR-022): any [Exception] — including [IOException] and
     * [SecurityException] — is caught, logged at [Logger.e], and swallowed. The stored
     * version is NOT updated on failure, guaranteeing a retry on the next launch.
     */
    fun maybeWrite() {
        if (currentVersion <= storedVersionProvider()) return

        try {
            val json = buildJson()
            val file = resolveOutputFile()
            fileWriter.write(file, json)
            settingsVersionWriter(currentVersion)
        } catch (e: Exception) {
            logger.e(TAG, "Failed to write $FILE_NAME — will retry on next launch", e)
            // storedVersionProvider() NOT updated → retry guaranteed on next launch
        }
    }

    // ---------------------------------------------------------------------------
    // Protected — overrideable for testing
    // ---------------------------------------------------------------------------

    /**
     * Resolves the output [File] path.
     *
     * Overrideable for tests that need to redirect the output file to a temp directory.
     * Production: [Context.filesDir] / [FILE_NAME].
     */
    protected open fun resolveOutputFile(): File {
        val ctx = requireNotNull(context) {
            "resolveOutputFile() requires Context. In tests, override this method."
        }
        return File(ctx.filesDir, FILE_NAME)
    }

    /**
     * Resolves the sensor list and serialises it to a JSON string.
     *
     * Overrideable in tests: subclass and return a fixed string to remove the
     * [Context] / [SensorManager] dependency from the test boundary.
     *
     * Production: queries [SensorManager.getSensorList(Sensor.TYPE_ALL)], maps each
     * [Sensor] to a [SensorInfo], and delegates to [buildJsonFromSensors].
     */
    open fun buildJson(): String {
        val ctx = requireNotNull(context) {
            "buildJson() requires Context. In tests, override this method."
        }
        val sensorManager = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensors: List<Sensor> = sensorManager.getSensorList(Sensor.TYPE_ALL)
        val sensorInfos = sensors.map { sensor ->
            SensorInfo(
                type = sensor.type,
                name = sensor.name,
                vendor = sensor.vendor,
                resolution = sensor.resolution,
                maximumRange = sensor.maximumRange,
                reportingMode = sensor.reportingMode
            )
        }
        val writtenAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        return buildJsonFromSensors(
            sensors = sensorInfos,
            versionCode = currentVersion,
            writtenAtIso8601 = writtenAt
        )
    }
}

// ---------------------------------------------------------------------------
// Package-level pure functions — callable from tests without a class instance
//
// PROP-SENSOR-037: buildJsonFromSensors must be a pure internal function with
// List<SensorInfo> parameter — no Context or SensorManager dependency.
// ---------------------------------------------------------------------------

/**
 * Builds the sensor capability profile JSON from a pre-resolved [SensorInfo] list.
 *
 * This is a pure function: no side effects, no Context dependency, no dependency on
 * [android.hardware.Sensor] or [android.os.Build]. It is `internal` so test code in
 * the same module can call it directly without instantiating [SensorCapabilityLogger].
 *
 * All parameters are required (no Android-specific defaults) to enable JVM unit testing
 * without Robolectric. Production callers pass values sourced from [android.os.Build]
 * and [BuildConfig].
 *
 * @param sensors          Pre-resolved list of sensor descriptors.
 * @param versionCode      App version code. Production callers pass [SensorCapabilityLogger.currentVersion].
 * @param writtenAtIso8601 ISO-8601 UTC timestamp string, must end with "Z" (PROP-SENSOR-033).
 * @param deviceModel      Device model string (production: [Build.MODEL]).
 * @param deviceManufacturer Device manufacturer (production: [Build.MANUFACTURER]).
 * @param androidVersion   Android OS version string (production: [Build.VERSION.RELEASE]).
 * @param androidApiLevel  Android API level integer (production: [Build.VERSION.SDK_INT]).
 * @return 2-space-indented JSON string (PROP-SENSOR-032).
 *
 * TSPEC §5.5; PROP-SENSOR-030–037.
 */
internal fun buildJsonFromSensors(
    sensors: List<SensorInfo>,
    versionCode: Int,
    writtenAtIso8601: String,
    deviceModel: String = Build.MODEL ?: "",
    deviceManufacturer: String = Build.MANUFACTURER ?: "",
    androidVersion: String = Build.VERSION.RELEASE ?: "",
    androidApiLevel: Int = Build.VERSION.SDK_INT
): String {
    val sensorsArray = JSONArray()
    for (info in sensors) {
        val obj = JSONObject()
        obj.put("type_constant", info.type)
        obj.put("name", info.name)
        obj.put("vendor", info.vendor)
        obj.put("resolution_ut_or_native", info.resolution.toDouble())
        obj.put("max_range_native", info.maximumRange.toDouble())
        obj.put("reporting_mode", mapReportingMode(info.reportingMode))
        sensorsArray.put(obj)
    }

    val root = JSONObject()
    root.put("device_model", deviceModel)
    root.put("device_manufacturer", deviceManufacturer)
    root.put("android_version", androidVersion)
    root.put("android_api_level", androidApiLevel)
    root.put("app_version_code", versionCode)
    root.put("written_at_iso8601", writtenAtIso8601)
    root.put("sensors", sensorsArray)

    return root.toString(2) // 2-space indent per PROP-SENSOR-032
}

/**
 * Maps a sensor reporting mode integer to its canonical string name.
 *
 * Uses explicit integer literals (not [android.hardware.Sensor] constants) so this
 * function is callable in JVM unit tests without Android stubs.
 *
 * TSPEC §5.5; PROP-SENSOR-035 (known constants) / PROP-SENSOR-036 (unknown fallback).
 */
internal fun mapReportingMode(mode: Int): String = when (mode) {
    0 -> "CONTINUOUS"
    1 -> "ON_CHANGE"
    2 -> "ONE_SHOT"
    3 -> "SPECIAL_TRIGGER"
    else -> "UNKNOWN($mode)"
}
