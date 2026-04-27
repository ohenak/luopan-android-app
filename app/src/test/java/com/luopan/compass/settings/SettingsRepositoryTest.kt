package com.luopan.compass.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {

    private lateinit var repo: SettingsRepository

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        repo = SettingsRepository(ctx)
    }

    @Test fun `default declinationMode is magnetic`() {
        assertEquals(SettingsRepository.DECLINATION_MAGNETIC, repo.declinationMode)
    }

    @Test fun `default manualDeclinationDeg is 0`() {
        assertEquals(0f, repo.manualDeclinationDeg, 0.001f)
    }

    @Test fun `default displayFormat is degrees`() {
        assertEquals(SettingsRepository.FORMAT_DEGREES, repo.displayFormat)
    }

    @Test fun `default wakeLockEnabled is true`() {
        assertTrue(repo.wakeLockEnabled)
    }

    @Test fun `set and get declinationMode`() {
        repo.declinationMode = SettingsRepository.DECLINATION_MANUAL
        assertEquals(SettingsRepository.DECLINATION_MANUAL, repo.declinationMode)
    }

    @Test fun `set and get manualDeclinationDeg`() {
        repo.manualDeclinationDeg = -7.5f
        assertEquals(-7.5f, repo.manualDeclinationDeg, 0.001f)
    }

    @Test fun `set and get displayFormat`() {
        repo.displayFormat = SettingsRepository.FORMAT_DMS
        assertEquals(SettingsRepository.FORMAT_DMS, repo.displayFormat)
    }

    @Test fun `set and get wakeLockEnabled`() {
        repo.wakeLockEnabled = false
        assertFalse(repo.wakeLockEnabled)
    }

    // Task 2.2 — Phase 3 additions

    @Test fun luopanShowRomanization_default_false() {
        assertFalse(repo.luopanShowRomanization)
    }

    @Test fun luopanShowMyLanguage_default_false() {
        assertFalse(repo.luopanShowMyLanguage)
    }

    @Test fun displayMode_default_MODERN() {
        assertEquals(SettingsRepository.DISPLAY_MODE_MODERN, repo.displayMode)
    }

    @Test fun displayMode_persists_LUOPAN() {
        repo.displayMode = SettingsRepository.DISPLAY_MODE_LUOPAN
        assertEquals(SettingsRepository.DISPLAY_MODE_LUOPAN, repo.displayMode)
    }

    @Test fun luopanShowRomanization_roundtrip() {
        repo.luopanShowRomanization = true
        assertTrue(repo.luopanShowRomanization)
    }

    @Test fun luopanShowMyLanguage_roundtrip() {
        repo.luopanShowMyLanguage = true
        assertTrue(repo.luopanShowMyLanguage)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Phase 4 additions — A-9
    // ─────────────────────────────────────────────────────────────────────────

    @Test fun driftCooldownTimestampMs_default_is_zero() {
        assertEquals(0L, repo.driftCooldownTimestampMs)
    }

    @Test fun driftCooldownTimestampMs_roundtrip() {
        val ts = 1_714_003_200_000L
        repo.driftCooldownTimestampMs = ts
        assertEquals(ts, repo.driftCooldownTimestampMs)
    }

    @Test fun driftCooldownTimestampMs_overwrites_previous_value() {
        repo.driftCooldownTimestampMs = 1_000L
        repo.driftCooldownTimestampMs = 2_000L
        assertEquals(2_000L, repo.driftCooldownTimestampMs)
    }

    @Test fun sensorProfileWrittenForVersion_default_is_zero() {
        assertEquals(0, repo.sensorProfileWrittenForVersion)
    }

    @Test fun sensorProfileWrittenForVersion_roundtrip() {
        repo.sensorProfileWrittenForVersion = 42
        assertEquals(42, repo.sensorProfileWrittenForVersion)
    }

    @Test fun sensorProfileWrittenForVersion_overwrites_previous_value() {
        repo.sensorProfileWrittenForVersion = 39
        repo.sensorProfileWrittenForVersion = 40
        assertEquals(40, repo.sensorProfileWrittenForVersion)
    }

    @Test fun noSessionOnlyKeysInSharedPreferences() {
        // Perform normal operations that use the persisted keys
        repo.displayMode = SettingsRepository.DISPLAY_MODE_LUOPAN
        repo.luopanShowRomanization = true
        repo.luopanShowMyLanguage = true

        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences("luopan_settings", Context.MODE_PRIVATE)
        val allKeys = prefs.all.keys

        assertFalse("No key containing 'ring' should be present in SharedPreferences",
            allKeys.any { it.contains("ring") })
        assertFalse("No key containing 'zoom' should be present in SharedPreferences",
            allKeys.any { it.contains("zoom") })
        // TE2-F03: broad assertion — catches exact "lock" plus variants like "is_lock_active",
        // "xiang_bearing", "zuo_bearing" that would indicate session state accidentally persisted.
        // "wake_lock_enabled" is a legitimate settings key and is excluded from this check.
        assertFalse("No lock-related session-state key must be stored in SharedPreferences",
            allKeys.any { key ->
                (key.contains("lock") && key != "wake_lock_enabled") ||
                key.contains("xiang") ||
                key.contains("zuo")
            })
    }
}
