package com.luopan.compass.diagnostics

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import com.luopan.compass.BuildConfig
import com.luopan.compass.settings.SettingsRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Writes a sensor capability profile JSON to internal app storage on first launch
 * after a new app version is installed.
 *
 * Version gate: only writes when BuildConfig.VERSION_CODE > stored sensorProfileWrittenForVersion.
 * On successful write, updates the stored version. On failure (any Exception), logs at Log.e
 * and does NOT update the stored version (guarantees retry on next launch).
 *
 * Called from CompassActivity.onCreate() on the main thread; the write is dispatched to
 * a background coroutine by the caller to avoid blocking the main thread.
 */
class SensorCapabilityLogger(
    private val context: Context,
    private val settings: SettingsRepository,
    private val fileWriter: SensorFileWriter = RealSensorFileWriter()
) {
    companion object {
        private const val TAG = "SensorCapabilityLogger"
        private const val FILE_NAME = "sensor_profile.json"
    }

    /**
     * Checks the version gate and writes sensor_profile.json if needed.
     *
     * Must be called from a background dispatcher (Dispatchers.IO).
     * Returns immediately if the version gate blocks the write.
     *
     * Failure contract: any exception thrown during sensor enumeration or file writing
     * is caught, logged at Log.e, and swallowed. The version key is NOT updated on
     * failure, guaranteeing a retry on the next launch.
     */
    fun maybeWrite() {
        if (BuildConfig.VERSION_CODE <= settings.sensorProfileWrittenForVersion) return

        try {
            val json = buildJson()
            val file = File(context.filesDir, FILE_NAME)
            fileWriter.write(file, json)
            settings.sensorProfileWrittenForVersion = BuildConfig.VERSION_CODE
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write sensor_profile.json — will retry on next launch", e)
            // sensorProfileWrittenForVersion NOT updated → retry guaranteed
        }
    }

    private fun buildJson(): String {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        return buildJsonFromSensors(sensors)
    }

    /**
     * Pure function: builds the JSON string from a pre-resolved sensor list.
     *
     * Extracted from [buildJson] to remove the Context/SensorManager dependency from the
     * serialization logic, enabling JVM unit tests (TE TSPEC-v1 F-07).
     */
    internal fun buildJsonFromSensors(
        sensors: List<Sensor>,
        versionCode: Int = BuildConfig.VERSION_CODE,
        writtenAtIso8601: String = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
    ): String {
        val sensorsArray = JSONArray()
        for (sensor in sensors) {
            val obj = JSONObject()
            obj.put("type_constant", sensor.type)
            obj.put("name", sensor.name)
            obj.put("vendor", sensor.vendor)
            obj.put("resolution_ut_or_native", sensor.resolution.toDouble())
            obj.put("max_range_native", sensor.maximumRange.toDouble())
            obj.put("reporting_mode", mapReportingMode(sensor.reportingMode))
            sensorsArray.put(obj)
        }

        val root = JSONObject()
        root.put("device_model", Build.MODEL)
        root.put("device_manufacturer", Build.MANUFACTURER)
        root.put("android_version", Build.VERSION.RELEASE)
        root.put("android_api_level", Build.VERSION.SDK_INT)
        root.put("app_version_code", versionCode)
        root.put("written_at_iso8601", writtenAtIso8601)
        root.put("sensors", sensorsArray)

        return root.toString(2)  // 2-space indent
    }

    internal fun mapReportingMode(mode: Int): String = when (mode) {
        Sensor.REPORTING_MODE_CONTINUOUS      -> "CONTINUOUS"
        Sensor.REPORTING_MODE_ON_CHANGE       -> "ON_CHANGE"
        Sensor.REPORTING_MODE_ONE_SHOT        -> "ONE_SHOT"
        Sensor.REPORTING_MODE_SPECIAL_TRIGGER -> "SPECIAL_TRIGGER"
        else                                   -> "UNKNOWN($mode)"
    }
}
