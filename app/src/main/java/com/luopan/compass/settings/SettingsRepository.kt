package com.luopan.compass.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("luopan_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DECLINATION_MODE = "declination_mode"
        private const val KEY_MANUAL_DECLINATION = "manual_declination_deg"
        private const val KEY_DISPLAY_FORMAT = "display_format"
        private const val KEY_WAKE_LOCK_ENABLED = "wake_lock_enabled"

        // Phase 3 additions
        const val KEY_DISPLAY_MODE        = "display_mode"
        const val KEY_LUOPAN_ROMANIZATION = "luopan_show_romanization"
        const val KEY_LUOPAN_MY_LANGUAGE  = "luopan_show_my_language"

        const val DECLINATION_AUTO = "auto"
        const val DECLINATION_MANUAL = "manual"
        const val DECLINATION_MAGNETIC = "magnetic"
        const val FORMAT_DEGREES = "degrees"
        const val FORMAT_DMS = "dms"

        const val DISPLAY_MODE_MODERN = "MODERN"
        const val DISPLAY_MODE_LUOPAN = "LUOPAN"

        // Phase 4 additions
        const val KEY_DRIFT_COOLDOWN_TIMESTAMP_MS = "drift_cooldown_timestamp_ms"
        const val KEY_SENSOR_PROFILE_WRITTEN_FOR_VERSION = "sensor_profile_written_for_version"
    }

    var declinationMode: String
        get() = prefs.getString(KEY_DECLINATION_MODE, DECLINATION_MAGNETIC) ?: DECLINATION_MAGNETIC
        set(value) = prefs.edit { putString(KEY_DECLINATION_MODE, value) }

    var manualDeclinationDeg: Float
        get() = prefs.getFloat(KEY_MANUAL_DECLINATION, 0f)
        set(value) = prefs.edit { putFloat(KEY_MANUAL_DECLINATION, value) }

    var displayFormat: String
        get() = prefs.getString(KEY_DISPLAY_FORMAT, FORMAT_DEGREES) ?: FORMAT_DEGREES
        set(value) = prefs.edit { putString(KEY_DISPLAY_FORMAT, value) }

    var wakeLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_WAKE_LOCK_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_WAKE_LOCK_ENABLED, value) }

    // Phase 3 additions

    var displayMode: String
        get() = prefs.getString(KEY_DISPLAY_MODE, DISPLAY_MODE_MODERN) ?: DISPLAY_MODE_MODERN
        set(value) = prefs.edit { putString(KEY_DISPLAY_MODE, value) }

    var luopanShowRomanization: Boolean
        get() = prefs.getBoolean(KEY_LUOPAN_ROMANIZATION, false)
        set(value) = prefs.edit { putBoolean(KEY_LUOPAN_ROMANIZATION, value) }

    var luopanShowMyLanguage: Boolean
        get() = prefs.getBoolean(KEY_LUOPAN_MY_LANGUAGE, false)
        set(value) = prefs.edit { putBoolean(KEY_LUOPAN_MY_LANGUAGE, value) }

    // Phase 4 additions

    var driftCooldownTimestampMs: Long
        get() = prefs.getLong(KEY_DRIFT_COOLDOWN_TIMESTAMP_MS, 0L)
        set(value) = prefs.edit { putLong(KEY_DRIFT_COOLDOWN_TIMESTAMP_MS, value) }

    var sensorProfileWrittenForVersion: Int
        get() = prefs.getInt(KEY_SENSOR_PROFILE_WRITTEN_FOR_VERSION, 0)
        set(value) = prefs.edit { putInt(KEY_SENSOR_PROFILE_WRITTEN_FOR_VERSION, value) }
}
