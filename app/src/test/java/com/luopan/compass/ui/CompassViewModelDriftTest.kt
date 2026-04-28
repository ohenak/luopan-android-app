package com.luopan.compass.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.luopan.compass.calibration.CalibrationRecord
import com.luopan.compass.calibration.CalibrationRepository
import com.luopan.compass.drift.AccelerometerVarianceTracker
import com.luopan.compass.drift.DriftDetector
import com.luopan.compass.drift.DriftEvent
import com.luopan.compass.drift.FakeAccelerometerVarianceTracker
import com.luopan.compass.drift.FakeDriftDetector
import com.luopan.compass.drift.IDriftDetector
import com.luopan.compass.model.InterferenceState
import com.luopan.compass.settings.SettingsRepository
import com.luopan.compass.util.FakeClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

/**
 * Phase D unit tests for CompassViewModel drift detection wiring.
 *
 * Tests:
 * - D-1:   AT-VM-DRIFT-01  (TRIGGERED with no cooldown → VISIBLE)
 * - D-1:   AT-VM-DRIFT-01b (TRIGGERED with active cooldown → stays HIDDEN)
 * - D-1a:  AT-CAL-01-C     (computeCalibrationAgeDays floor-division semantics)
 * - D-1b:  dismissCalAgeBanner() and dismissDriftBanner() state transitions
 * - D-1c:  AT-CAL-02-D cooldown suppression; AT-CAL-02-E cooldown re-arm
 * - D-1d:  onCalibrationCompleteFromHistory() async re-flash mitigation
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class CompassViewModelDriftTest {

    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a minimal CalibrationRecord suitable for injection into FakeCalibrationRepository.
     */
    private fun makeCalibrationRecord(
        recordedAt: Long = 0L,
        expectedFieldUt: Float = 50.0f
    ) = CalibrationRecord(
        id = 1,
        recorded_at = recordedAt,
        hard_iron_x = 0f, hard_iron_y = 0f, hard_iron_z = 0f,
        soft_iron_00 = 1f, soft_iron_01 = 0f, soft_iron_02 = 0f,
        soft_iron_10 = 0f, soft_iron_11 = 1f, soft_iron_12 = 0f,
        soft_iron_20 = 0f, soft_iron_21 = 0f, soft_iron_22 = 1f,
        quality = "GOOD",
        expected_field_ut = expectedFieldUt
    )

    /**
     * Creates a CompassViewModel with injectable drift dependencies for testing.
     *
     * Uses Robolectric ApplicationProvider for the Application context.
     * The driftDetector and accVarianceTracker are injected via constructor params.
     */
    private fun makeViewModel(
        clock: FakeClock = FakeClock(700_000L),
        driftDetector: IDriftDetector = FakeDriftDetector(nextEvent = null),
        accVarianceTracker: AccelerometerVarianceTracker = FakeAccelerometerVarianceTracker(variance = 0.001f),
        settings: SettingsRepository = SettingsRepository(application),
        // Always provide a fake repo to avoid real DB/Keystore access in unit tests
        calibrationRepo: CalibrationRepository = FakeCalibrationRepository()
    ): CompassViewModel {
        return CompassViewModel(
            application = application,
            modelProvider = null,
            locationRepository = null,
            clock = clock,
            captureUseCase = null,
            calibrationRepository = calibrationRepo,
            driftDetector = driftDetector,
            accVarianceTracker = accVarianceTracker,
            settingsOverride = settings
        )
    }

    // ─── D-1: AT-VM-DRIFT-01 ──────────────────────────────────────────────────

    @Test
    fun `AT-VM-DRIFT-01 TRIGGERED with no cooldown sets driftBannerState to VISIBLE`() = runTest {
        // clock.nowMs() = 700_000L; cooldownMs = 0L; 700000 - 0 = 700000 >= 600000 → banner VISIBLE
        val clock = FakeClock(700_000L)
        val fakeDrift = FakeDriftDetector(nextEvent = DriftEvent.TRIGGERED)
        val viewModel = makeViewModel(
            clock = clock,
            driftDetector = fakeDrift
        )

        val states = mutableListOf<DriftBannerState>()
        val job = launch { viewModel.driftBannerState.collect { states.add(it) } }

        viewModel.simulateSensorFrame(
            magnitudeUt = 56.0f,
            interferenceState = InterferenceState.CLEAR,
            expectedFieldUt = 50.0f
        )
        advanceUntilIdle()
        job.cancel()

        assertTrue(
            "driftBannerState must contain VISIBLE when TRIGGERED with no active cooldown",
            states.contains(DriftBannerState.VISIBLE)
        )
    }

    @Test
    fun `AT-VM-DRIFT-01b TRIGGERED with active cooldown does NOT set driftBannerState to VISIBLE`() = runTest {
        // clock.nowMs() = 100_000L; cooldownMs = 90_000L; 100000 - 90000 = 10000 < 600000 → suppressed
        val clock = FakeClock(100_000L)
        val fakeDrift = FakeDriftDetector(nextEvent = DriftEvent.TRIGGERED)
        val settings = SettingsRepository(application).also {
            it.driftCooldownTimestampMs = 90_000L  // 10 seconds ago — within 10-minute cooldown
        }
        val viewModel = makeViewModel(
            clock = clock,
            driftDetector = fakeDrift,
            settings = settings
        )

        val states = mutableListOf<DriftBannerState>()
        val job = launch { viewModel.driftBannerState.collect { states.add(it) } }

        viewModel.simulateSensorFrame(
            magnitudeUt = 56.0f,
            interferenceState = InterferenceState.CLEAR,
            expectedFieldUt = 50.0f
        )
        advanceUntilIdle()
        job.cancel()

        assertFalse(
            "driftBannerState must NOT contain VISIBLE when TRIGGERED with active cooldown",
            states.contains(DriftBannerState.VISIBLE)
        )
    }

    // ─── D-1a: AT-CAL-01-C ───────────────────────────────────────────────────

    @Test
    fun `AT-CAL-01-C computeCalibrationAgeDays uses floor division — 31d 23h = 31 not 32`() {
        // 31 days 23 hours in milliseconds
        val thirtyOneDaysTwentyThreeHours = (31L * 24L * 60L * 60L * 1000L) + (23L * 60L * 60L * 1000L)

        // computeCalibrationAgeDays() should return floor(ageMs / MS_PER_DAY)
        // = floor(thirtyOneDaysTwentyThreeHours / 86_400_000) = 31 (not 32)
        val result = CompassViewModel.computeCalibrationAgeDays(thirtyOneDaysTwentyThreeHours)

        assertEquals(
            "computeCalibrationAgeDays must use floor division: 31d 23h elapsed = 31 days, not 32",
            31L,
            result
        )
    }

    @Test
    fun `computeCalibrationAgeDays returns 0 for less than one day elapsed`() {
        val twentyThreeHours = 23L * 60L * 60L * 1000L
        assertEquals(0L, CompassViewModel.computeCalibrationAgeDays(twentyThreeHours))
    }

    @Test
    fun `computeCalibrationAgeDays returns exactly 30 for exactly 30 days`() {
        val thirtyDays = 30L * 24L * 60L * 60L * 1000L
        assertEquals(30L, CompassViewModel.computeCalibrationAgeDays(thirtyDays))
    }

    // ─── D-1b: dismissCalAgeBanner() and dismissDriftBanner() state transitions ──

    @Test
    fun `dismissCalAgeBanner sets calAgeBannerDismissed to true`() = runTest {
        val viewModel = makeViewModel()

        assertFalse(
            "calAgeBannerDismissed must start false",
            viewModel.calAgeBannerDismissed
        )

        viewModel.dismissCalAgeBanner()

        assertTrue(
            "calAgeBannerDismissed must be true after dismissCalAgeBanner()",
            viewModel.calAgeBannerDismissed
        )
    }

    @Test
    fun `dismissDriftBanner writes driftCooldownTimestampMs to settings and emits HIDDEN`() = runTest {
        val clock = FakeClock(500_000L)
        val settings = SettingsRepository(application)
        val viewModel = makeViewModel(clock = clock, settings = settings)

        // First trigger the banner to VISIBLE so we have something to dismiss
        val states = mutableListOf<DriftBannerState>()
        val job = launch { viewModel.driftBannerState.collect { states.add(it) } }

        // Manually set driftBannerState to VISIBLE via simulateSensorFrame with TRIGGERED detector
        val triggerView = makeViewModel(
            clock = FakeClock(700_000L),
            driftDetector = FakeDriftDetector(nextEvent = DriftEvent.TRIGGERED),
            settings = settings
        )
        val states2 = mutableListOf<DriftBannerState>()
        val job2 = launch { triggerView.driftBannerState.collect { states2.add(it) } }
        triggerView.simulateSensorFrame(magnitudeUt = 56.0f, interferenceState = InterferenceState.CLEAR, expectedFieldUt = 50.0f)
        advanceUntilIdle()

        // Now dismiss the drift banner
        triggerView.dismissDriftBanner()
        advanceUntilIdle()
        job2.cancel()
        job.cancel()

        // Assert: cooldown timestamp written to settings
        assertEquals(
            "dismissDriftBanner must write clock.nowMs() to driftCooldownTimestampMs",
            700_000L,
            settings.driftCooldownTimestampMs
        )

        // Assert: driftBannerState emits HIDDEN after dismiss
        assertTrue(
            "driftBannerState must contain HIDDEN after dismissDriftBanner()",
            states2.contains(DriftBannerState.HIDDEN)
        )
    }

    @Test
    fun `dismissDriftBanner emits HIDDEN state`() = runTest {
        val clock = FakeClock(700_000L)
        val fakeDrift = FakeDriftDetector(nextEvent = DriftEvent.TRIGGERED)
        val settings = SettingsRepository(application)
        val viewModel = makeViewModel(clock = clock, driftDetector = fakeDrift, settings = settings)

        val states = mutableListOf<DriftBannerState>()
        val job = launch { viewModel.driftBannerState.collect { states.add(it) } }

        // Trigger VISIBLE
        viewModel.simulateSensorFrame(magnitudeUt = 56.0f, interferenceState = InterferenceState.CLEAR, expectedFieldUt = 50.0f)
        advanceUntilIdle()
        assertTrue("State must be VISIBLE before dismiss", states.contains(DriftBannerState.VISIBLE))

        // Dismiss
        viewModel.dismissDriftBanner()
        advanceUntilIdle()
        job.cancel()

        // Last emission must be HIDDEN
        assertEquals(
            "Last driftBannerState emission must be HIDDEN after dismissDriftBanner()",
            DriftBannerState.HIDDEN,
            states.last()
        )
    }

    // ─── D-1c: AT-CAL-02-D and AT-CAL-02-E cooldown suppression/re-arm ───────

    @Test
    fun `AT-CAL-02-D TRIGGERED within active cooldown does not emit VISIBLE`() = runTest {
        // cooldown set 5 minutes ago — within 10-minute window → suppress
        val nowMs = 600_000L
        val cooldownStart = 300_000L  // 5 minutes ago
        val clock = FakeClock(nowMs)
        val settings = SettingsRepository(application).also {
            it.driftCooldownTimestampMs = cooldownStart
        }
        val viewModel = makeViewModel(
            clock = clock,
            driftDetector = FakeDriftDetector(nextEvent = DriftEvent.TRIGGERED),
            settings = settings
        )

        val states = mutableListOf<DriftBannerState>()
        val job = launch { viewModel.driftBannerState.collect { states.add(it) } }

        viewModel.simulateSensorFrame(magnitudeUt = 56.0f, interferenceState = InterferenceState.CLEAR, expectedFieldUt = 50.0f)
        advanceUntilIdle()
        job.cancel()

        assertFalse(
            "AT-CAL-02-D: driftBannerState must NOT be VISIBLE when cooldown is active (5 min elapsed < 10 min)",
            states.contains(DriftBannerState.VISIBLE)
        )
    }

    @Test
    fun `AT-CAL-02-E TRIGGERED after cooldown expires does emit VISIBLE`() = runTest {
        // cooldown set 11 minutes ago — beyond 10-minute window → re-arm
        val nowMs = 660_001L   // 660001 ms since epoch
        val cooldownStart = 0L  // 660001 ms ago — > 600000 → expired
        val clock = FakeClock(nowMs)
        val settings = SettingsRepository(application).also {
            it.driftCooldownTimestampMs = cooldownStart
        }
        val viewModel = makeViewModel(
            clock = clock,
            driftDetector = FakeDriftDetector(nextEvent = DriftEvent.TRIGGERED),
            settings = settings
        )

        val states = mutableListOf<DriftBannerState>()
        val job = launch { viewModel.driftBannerState.collect { states.add(it) } }

        viewModel.simulateSensorFrame(magnitudeUt = 56.0f, interferenceState = InterferenceState.CLEAR, expectedFieldUt = 50.0f)
        advanceUntilIdle()
        job.cancel()

        assertTrue(
            "AT-CAL-02-E: driftBannerState must be VISIBLE when cooldown has expired (11+ min elapsed >= 10 min)",
            states.contains(DriftBannerState.VISIBLE)
        )
    }

    // ─── D-1d: onCalibrationCompleteFromHistory() async re-flash mitigation ───

    @Test
    fun `onCalibrationCompleteFromHistory sets calAgeBannerDismissed=true synchronously before IO`() = runTest {
        val clock = FakeClock(0L)
        val viewModel = makeViewModel(clock = clock)

        assertFalse(viewModel.calAgeBannerDismissed)

        // Call onCalibrationCompleteFromHistory — the flag must be set synchronously
        viewModel.onCalibrationCompleteFromHistory()

        // Before advancing the scheduler (before IO coroutine runs), flag must already be true
        assertTrue(
            "calAgeBannerDismissed must be set to true synchronously before IO coroutine resolves",
            viewModel.calAgeBannerDismissed
        )
    }

    @Test
    fun `onCalibrationCompleteFromHistory clears calAgeBannerDismissed when refreshed age is le 30`() = runTest {
        // Setup: clock at 10 days after calibration (age = 10 days ≤ 30)
        val tenDaysMs = 10L * 24L * 60L * 60L * 1000L
        val clock = FakeClock(tenDaysMs)  // now = 10 days after epoch

        // The calibration was recorded at epoch (timestamp 0) → age = 10 days ≤ 30
        val record = makeCalibrationRecord(recordedAt = 0L, expectedFieldUt = 50.0f)
        val fakeRepo = FakeCalibrationRepository(current = record)

        val viewModel = makeViewModel(clock = clock, calibrationRepo = fakeRepo)

        // Call onCalibrationCompleteFromHistory and wait for IO to complete
        viewModel.onCalibrationCompleteFromHistory()

        // Wait for the IO coroutine (real Dispatchers.IO) to complete.
        // Since FakeCalibrationDao.getCurrent() returns synchronously (no delay),
        // the IO coroutine completes quickly. We wait for the ViewModel scope to settle.
        // Thread.sleep required: onCalibrationCompleteFromHistory launches on Dispatchers.IO
        // (not test-injected), so advanceUntilIdle() cannot drain the coroutine.
        // TODO: inject IO dispatcher in CompassViewModel to replace Thread.sleep with advanceUntilIdle().
        Thread.sleep(100L)  // allow real IO thread to complete

        // After IO coroutine resolves: age = 10 days ≤ 30 → flag must be cleared to false
        assertFalse(
            "calAgeBannerDismissed must be cleared to false when refreshed age ≤ 30",
            viewModel.calAgeBannerDismissed
        )
    }

    @Test
    fun `onCalibrationCompleteFromHistory keeps calAgeBannerDismissed=true when refreshed age is gt 30`() = runTest {
        // Setup: clock at 35 days after calibration (age = 35 days > 30)
        val thirtyFiveDaysMs = 35L * 24L * 60L * 60L * 1000L
        val clock = FakeClock(thirtyFiveDaysMs)  // now = 35 days after epoch

        // The calibration was recorded at epoch → age = 35 days > 30
        val record = makeCalibrationRecord(recordedAt = 0L, expectedFieldUt = 50.0f)
        val fakeRepo = FakeCalibrationRepository(current = record)

        val viewModel = makeViewModel(clock = clock, calibrationRepo = fakeRepo)

        viewModel.onCalibrationCompleteFromHistory()
        assertTrue(viewModel.calAgeBannerDismissed)

        // After IO resolves: age = 35 > 30 → flag stays true (suppress flag remains)
        advanceUntilIdle()

        assertTrue(
            "calAgeBannerDismissed must remain true when refreshed age > 30",
            viewModel.calAgeBannerDismissed
        )
    }
    // ─── PROP-CAL-043: resetDriftDetector clears cooldown to 0L ─────────────────

    @Test
    fun `PROP-CAL-043 resetDriftDetector clears driftCooldownTimestampMs to 0L`() = runTest {
        val settings = SettingsRepository(application)
        settings.driftCooldownTimestampMs = 999_000L
        val vm = makeViewModel(settings = settings)

        vm.resetDriftDetector()
        advanceUntilIdle()

        assertEquals(
            "PROP-CAL-043: resetDriftDetector must reset driftCooldownTimestampMs to 0L",
            0L,
            settings.driftCooldownTimestampMs
        )
    }

    // ─── PROP-CAL-038: full end-to-end chain with real implementations ────────

    @Test
    fun `PROP-CAL-038 real DriftDetector and AccelerometerVarianceTracker produce VISIBLE after 61s`() = runTest {
        // Start clock beyond the 10-minute cooldown check (700_000L - 0L = 700_000 >= 600_000).
        val clock = FakeClock(700_000L)
        val realDetector = DriftDetector(clock)
        val realTracker = AccelerometerVarianceTracker(clock)
        val vm = makeViewModel(
            clock = clock,
            driftDetector = realDetector,
            accVarianceTracker = realTracker
        )

        // simulateSensorFrame passes (0, 0, 0) to accVarianceTracker.update() — the resulting
        // variance is always 0f (constant signal) which satisfies accVariance < 0.01 precondition.

        // Phase 1 — 58 s of frames (116 × 500 ms). Assert driftBannerState stays HIDDEN.
        // 56 µT measured vs 50 µT expected = 12% deviation (> 10%), but timer < 60 s.
        repeat(116) {
            clock.advance(500L)
            vm.simulateSensorFrame(
                magnitudeUt = 56.0f,
                interferenceState = InterferenceState.CLEAR,
                expectedFieldUt = 50.0f
            )
        }
        advanceUntilIdle()
        assertEquals(
            "PROP-CAL-038 Phase 1: driftBannerState must be HIDDEN at 58 s (threshold not yet reached)",
            DriftBannerState.HIDDEN,
            vm.driftBannerState.value
        )

        // Phase 2 — advance 3 s more (total ≈ 61 s), then one frame with >10% deviation.
        clock.advance(3_000L)
        vm.simulateSensorFrame(
            magnitudeUt = 56.0f,
            interferenceState = InterferenceState.CLEAR,
            expectedFieldUt = 50.0f
        )
        advanceUntilIdle()
        assertEquals(
            "PROP-CAL-038 Phase 2: driftBannerState must be VISIBLE after 61 s with >10% deviation",
            DriftBannerState.VISIBLE,
            vm.driftBannerState.value
        )
    }
}

// ─── FakeCalibrationDao / FakeCalibrationRepository ──────────────────────────

/**
 * In-memory fake [CalibrationDao] for [FakeCalibrationRepository].
 *
 * Returns [current] from [getCurrent]; all other operations are no-ops.
 */
class FakeCalibrationDao(
    private val current: CalibrationRecord? = null
) : com.luopan.compass.db.CalibrationDao {
    override suspend fun getCurrent(): CalibrationRecord? = current
    override suspend fun getRollback(): CalibrationRecord? = null
    override suspend fun upsert(record: CalibrationRecord) {}
    override suspend fun delete(id: Int) {}
    override suspend fun getAll(): List<CalibrationRecord> = listOfNotNull(current)
}

/**
 * Convenience factory: returns a [CalibrationRepository] backed by [FakeCalibrationDao].
 */
fun FakeCalibrationRepository(current: CalibrationRecord? = null): CalibrationRepository =
    CalibrationRepository(FakeCalibrationDao(current))
