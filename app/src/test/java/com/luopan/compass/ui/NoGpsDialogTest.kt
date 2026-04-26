package com.luopan.compass.ui

import com.luopan.compass.location.LocationResult
import com.luopan.compass.model.NorthType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the "No GPS" manual coordinate entry dialog flow (AT-D).
 *
 * TSPEC §10.1 — NoGpsDialogTest:
 *   FakeLocationRepository emits LocationState.Unavailable.
 *   Tap True North toggle → assert manual coordinate entry dialog appears
 *   (signalled via NorthTypeEngine.showManualLocationDialog).
 *   Mode stays MAGNETIC until coordinates confirmed.
 *
 * This is a pure JVM unit test — it exercises [NorthTypeEngine] directly (the same
 * state machine that [CompassViewModel.requestTrueNorth] delegates to).
 *
 * FSPEC §2.2 step 4c, PLAN §4 P8.2 success criteria (AT-D).
 */
class NoGpsDialogTest {

    private lateinit var engine: NorthTypeEngine

    @Before
    fun setUp() {
        engine = NorthTypeEngine()
    }

    // ── AT-D: Unavailable location → manual entry dialog signalled ────────────

    /**
     * AT-D primary assertion: when GPS is unavailable and the user taps True North,
     * the engine signals the Activity to show the manual coordinate entry dialog.
     *
     * The returned result must be NeedsManualEntry (not Switched).
     */
    @Test
    fun `requestTrueNorth with Unavailable location returns NeedsManualEntry`() {
        val result = engine.requestTrueNorth(LocationResult.Unavailable)

        assertEquals(TrueNorthRequestResult.NeedsManualEntry, result)
    }

    /**
     * AT-D: northType stays MAGNETIC when GPS is unavailable and user taps True North.
     *
     * Mode must remain MAGNETIC until the user confirms manual coordinates — the toggle
     * must not switch to TRUE prematurely (FSPEC §2.2 step 4c).
     */
    @Test
    fun `northType stays MAGNETIC after requestTrueNorth with Unavailable location`() {
        engine.requestTrueNorth(LocationResult.Unavailable)

        assertEquals(NorthType.MAGNETIC, engine.northType.value)
    }

    /**
     * AT-D: the manual location dialog event is emitted on [showManualLocationDialog]
     * when GPS is unavailable and the user taps True North.
     */
    @Test
    @Suppress("OPT_IN_USAGE")
    fun `showManualLocationDialog emits when requestTrueNorth called with Unavailable`() =
        runBlocking {
            // SharedFlow with extraBufferCapacity=1 uses tryEmit; collect the buffered event.
            val collected = mutableListOf<Unit>()
            val job = GlobalScope.launch(Dispatchers.Unconfined) {
                engine.showManualLocationDialog.collect {
                    collected.add(Unit)
                }
            }

            engine.requestTrueNorth(LocationResult.Unavailable)

            // Allow the collect coroutine to process the emitted event
            delay(50)
            job.cancel()

            assertEquals("Expected showManualLocationDialog to emit once", 1, collected.size)
        }

    /**
     * AT-D: northType switches to TRUE only AFTER manual coordinates are confirmed
     * (i.e., after calling setNorthType(TRUE) or setManualLocation equivalent).
     */
    @Test
    fun `northType switches to TRUE only after manual coordinates are confirmed`() {
        // Step 1: tap True North with no GPS — stays MAGNETIC
        engine.requestTrueNorth(LocationResult.Unavailable)
        assertEquals(NorthType.MAGNETIC, engine.northType.value)

        // Step 2: user enters coordinates and confirms — engine switches to TRUE
        engine.setNorthType(NorthType.TRUE)
        assertEquals(NorthType.TRUE, engine.northType.value)
    }

    // ── Contrast: location available → switches immediately ──────────────────

    /**
     * When a GPS fix IS available, requestTrueNorth returns Switched (not NeedsManualEntry).
     */
    @Test
    fun `requestTrueNorth with GpsFix returns Switched`() {
        val gpsFix = LocationResult.GpsFix(lat = 40.0, lon = -105.0, altM = 0.0)

        val result = engine.requestTrueNorth(gpsFix)

        assertEquals(TrueNorthRequestResult.Switched, result)
    }

    /**
     * When a GPS fix is available, northType transitions to TRUE immediately.
     */
    @Test
    fun `northType switches to TRUE immediately when GPS fix is available`() {
        val gpsFix = LocationResult.GpsFix(lat = 40.0, lon = -105.0, altM = 0.0)

        engine.requestTrueNorth(gpsFix)

        assertEquals(NorthType.TRUE, engine.northType.value)
    }

    /**
     * When a CachedFix is available, requestTrueNorth returns Switched (location present).
     */
    @Test
    fun `requestTrueNorth with CachedFix returns Switched`() {
        val cachedFix = LocationResult.CachedFix(
            lat = 40.0, lon = -105.0, altM = 0.0, ageMs = 86_400_000L
        )

        val result = engine.requestTrueNorth(cachedFix)

        assertEquals(TrueNorthRequestResult.Switched, result)
    }

    /**
     * When a ManualEntry is available, requestTrueNorth returns Switched.
     */
    @Test
    fun `requestTrueNorth with ManualEntry returns Switched`() {
        val manual = LocationResult.ManualEntry(lat = 22.3193, lon = 114.1694)

        val result = engine.requestTrueNorth(manual)

        assertEquals(TrueNorthRequestResult.Switched, result)
    }

    /**
     * Multiple calls with Unavailable: each call re-emits the dialog signal.
     * northType stays MAGNETIC throughout.
     */
    @Test
    fun `northType remains MAGNETIC after multiple requestTrueNorth calls with Unavailable`() {
        repeat(3) {
            engine.requestTrueNorth(LocationResult.Unavailable)
            assertEquals("Iteration $it: northType must stay MAGNETIC", NorthType.MAGNETIC, engine.northType.value)
        }
    }
}
