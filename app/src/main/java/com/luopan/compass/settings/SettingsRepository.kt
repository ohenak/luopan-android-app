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

        const val DECLINATION_AUTO = "auto"
        const val DECLINATION_MANUAL = "manual"
        const val DECLINATION_MAGNETIC = "magnetic"
        const val FORMAT_DEGREES = "degrees"
        const val FORMAT_DMS = "dms"
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
}
