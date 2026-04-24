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
}
