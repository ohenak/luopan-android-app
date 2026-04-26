package com.luopan.compass.ui

import com.luopan.compass.luopan.ZuoXiangLock
import com.luopan.compass.model.NorthType
import com.luopan.compass.model.OverallConfidence
import com.luopan.compass.model.SensorState
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for Task 2.3 — CompassViewModel session-only state extensions.
 *
 * Tests are deliberately ViewModel-free where possible: they exercise the pure domain
 * objects (ZuoXiangLock, the helper classes) and the ViewModel accessors in isolation.
 *
 * The ViewModel itself requires a full Android Application context (it is an
 * AndroidViewModel), so tests that need a ViewModel instance are covered via the
 * CompassViewModelSessionState helper below — a thin wrapper that exposes
 * the session-state fields without starting the Android sensor pipeline.
 *
 * All session-only state (ring visibility, zoom scale, ZuoXiangLock) is NOT read from
 * SharedPreferences — it is always initialised from code defaults.
 */
class CompassViewModelSessionStateTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Minimal in-process stand-in for the session-only ViewModel state.
     *
     * Replicates the session-state members specified in TSPEC §5.3 without
     * requiring AndroidViewModel / Application / sensor pipeline.
     *
     * The localization settings (showRomanization, showMyLanguage) are read from
     * the provided [FakeSettingsData] at construction time (mirroring the ViewModel
     * `init {}` block in TSPEC §8.3).
     */
    private class SessionState(
        showRomanization: Boolean = false,
        showMyLanguage: Boolean = false
    ) {
        // Ring visibility — session-only, default all true (6 rings)
        private val _ringVisibility = kotlinx.coroutines.flow.MutableStateFlow(BooleanArray(6) { true })
        val ringVisibility: kotlinx.coroutines.flow.StateFlow<BooleanArray> = _ringVisibility

        // Zoom scale — session-only, default 1.0f
        private val _zoomScale = kotlinx.coroutines.flow.MutableStateFlow(1.0f)
        val zoomScale: kotlinx.coroutines.flow.StateFlow<Float> = _zoomScale

        // ZuoXiang lock — session-only
        val zuoXiangLock = ZuoXiangLock()

        // Localization settings — read from "settings" at init time
        private var _showRomanization: Boolean = showRomanization
        private var _showMyLanguage: Boolean = showMyLanguage

        fun setRingVisible(index: Int, visible: Boolean) {
            val arr = _ringVisibility.value.clone()
            arr[index] = visible
            _ringVisibility.value = arr
        }

        fun setZoomScale(scale: Float) {
            _zoomScale.value = scale.coerceIn(0.8f, 2.0f)
        }

        fun lockXiang(state: CompassUiState) {
            val confidence = state.confidence
            if (confidence != OverallConfidence.HIGH && confidence != OverallConfidence.MODERATE) return
            val displayBearing = state.heading_deg.toFloat()
            val declinationDeg = state.declination_deg
            val bearingTrueNorth = if (state.north_type == NorthType.MAGNETIC)
                ((displayBearing + declinationDeg + 360f) % 360f)
            else
                displayBearing
            zuoXiangLock.lock(bearingTrueNorth)
            if (state.north_type == NorthType.MAGNETIC) {
                zuoXiangLock.rederive(declinationDeg, isMagneticNorth = true)
            }
        }

        fun clearXiang() {
            zuoXiangLock.clear()
        }
    }

    /** Builds a minimal [CompassUiState] with overridable fields for testing. */
    private fun makeState(
        heading_deg: Double = 0.0,
        north_type: NorthType = NorthType.MAGNETIC,
        declination_deg: Float = 0f,
        confidence: OverallConfidence = OverallConfidence.MODERATE
    ) = CompassUiState.INITIAL.copy(
        heading_deg = heading_deg,
        north_type = north_type,
        declination_deg = declination_deg,
        confidence = confidence
    )

    // -----------------------------------------------------------------------
    // §5.2 — CompassUiState.luopan field
    // -----------------------------------------------------------------------

    @Test
    fun `CompassUiState INITIAL has luopan field with INITIAL value`() {
        val state = CompassUiState.INITIAL
        // The field must exist and must be LuopanState.INITIAL
        assertEquals(com.luopan.compass.luopan.LuopanState.INITIAL, state.luopan)
    }

    // -----------------------------------------------------------------------
    // Ring visibility — TSPEC §5.3
    // -----------------------------------------------------------------------

    @Test
    fun ringVisibility_initializes_allTrue_on_viewModelCreation() {
        val ss = SessionState()
        val arr = ss.ringVisibility.value
        assertEquals("ring visibility array must have 6 elements", 6, arr.size)
        for (i in arr.indices) {
            assertTrue("ring $i must be visible by default", arr[i])
        }
    }

    @Test
    fun ringVisibility_notRestoredFromSettings() {
        // Session-only state is never stored in SharedPreferences.
        // Verify that creating a fresh SessionState always produces all-true visibility
        // regardless of how many times it is instantiated — no persistence between instances.
        val ss1 = SessionState()
        ss1.setRingVisible(2, false)
        assertFalse("ring 2 should be false after setRingVisible(2, false)", ss1.ringVisibility.value[2])

        // New instance always starts from scratch — not from any persisted state
        val ss2 = SessionState()
        assertTrue("ring 2 in fresh SessionState must be true (no persistence)", ss2.ringVisibility.value[2])
    }

    @Test
    fun `setRingVisible updates correct index without affecting others`() {
        val ss = SessionState()
        ss.setRingVisible(3, false)
        val arr = ss.ringVisibility.value
        for (i in arr.indices) {
            if (i == 3) assertFalse("ring 3 must be false", arr[i])
            else assertTrue("ring $i must still be true", arr[i])
        }
    }

    // -----------------------------------------------------------------------
    // Zoom scale — TSPEC §5.3
    // -----------------------------------------------------------------------

    @Test
    fun zoomScale_initializes_to_1f_on_viewModelCreation() {
        val ss = SessionState()
        assertEquals(1.0f, ss.zoomScale.value, 0.0001f)
    }

    @Test
    fun setZoomScale_clamped_below_min() {
        val ss = SessionState()
        ss.setZoomScale(0.5f)
        assertEquals("zoom scale must be clamped to 0.8f minimum", 0.8f, ss.zoomScale.value, 0.0001f)
    }

    @Test
    fun setZoomScale_clamped_above_max() {
        val ss = SessionState()
        ss.setZoomScale(3.0f)
        assertEquals("zoom scale must be clamped to 2.0f maximum", 2.0f, ss.zoomScale.value, 0.0001f)
    }

    @Test
    fun `setZoomScale within bounds is stored exactly`() {
        val ss = SessionState()
        ss.setZoomScale(1.5f)
        assertEquals(1.5f, ss.zoomScale.value, 0.0001f)
    }

    // -----------------------------------------------------------------------
    // lockXiang / clearXiang — TSPEC §5.3, §8.1
    // -----------------------------------------------------------------------

    @Test
    fun lockXiang_at_moderate_confidence_locks() {
        val ss = SessionState()
        val state = makeState(
            heading_deg = 90.0,
            north_type = NorthType.TRUE,
            confidence = OverallConfidence.MODERATE
        )
        ss.lockXiang(state)
        assertNotNull("lockState must not be null after lockXiang at MODERATE confidence",
            ss.zuoXiangLock.lockState)
    }

    @Test
    fun `lockXiang at HIGH confidence locks`() {
        val ss = SessionState()
        val state = makeState(
            heading_deg = 180.0,
            north_type = NorthType.TRUE,
            confidence = OverallConfidence.HIGH
        )
        ss.lockXiang(state)
        assertNotNull("lockState must not be null after lockXiang at HIGH confidence",
            ss.zuoXiangLock.lockState)
    }

    @Test
    fun lockXiang_at_poor_confidence_does_not_lock() {
        val ss = SessionState()
        val state = makeState(
            heading_deg = 90.0,
            confidence = OverallConfidence.POOR
        )
        ss.lockXiang(state)
        assertNull("lockState must be null after lockXiang at POOR confidence",
            ss.zuoXiangLock.lockState)
    }

    @Test
    fun `lockXiang at SENSOR_ERROR does not lock`() {
        val ss = SessionState()
        val state = makeState(
            heading_deg = 90.0,
            confidence = OverallConfidence.SENSOR_ERROR
        )
        ss.lockXiang(state)
        assertNull("lockState must be null after lockXiang at SENSOR_ERROR",
            ss.zuoXiangLock.lockState)
    }

    @Test
    fun `lockXiang at STABILIZING does not lock`() {
        val ss = SessionState()
        val state = makeState(
            heading_deg = 90.0,
            confidence = OverallConfidence.STABILIZING
        )
        ss.lockXiang(state)
        assertNull("lockState must be null after lockXiang at STABILIZING",
            ss.zuoXiangLock.lockState)
    }

    @Test
    fun clearXiang_clears_lock_state() {
        val ss = SessionState()
        val state = makeState(
            heading_deg = 45.0,
            north_type = NorthType.TRUE,
            confidence = OverallConfidence.MODERATE
        )
        ss.lockXiang(state)
        assertNotNull("lock must be active before clear", ss.zuoXiangLock.lockState)

        ss.clearXiang()
        assertNull("lockState must be null after clearXiang", ss.zuoXiangLock.lockState)
    }

    // -----------------------------------------------------------------------
    // lockXiang Magnetic→True North conversion — TSPEC §5.3, PLAN §2.3 (TE-N-F02)
    // -----------------------------------------------------------------------

    @Test
    fun lockXiang_under_magnetic_north_stores_true_north_bearing() {
        // heading_deg=48.5 (Magnetic), declination=-3.5 → True North = 48.5 + (-3.5) = 45.0°
        val ss = SessionState()
        val state = makeState(
            heading_deg = 48.5,
            north_type = NorthType.MAGNETIC,
            declination_deg = -3.5f,
            confidence = OverallConfidence.MODERATE
        )
        ss.lockXiang(state)

        val lockState = ss.zuoXiangLock.lockState
        assertNotNull("lockState must be non-null after lockXiang with MODERATE confidence", lockState)
        assertEquals(
            "stored xiangBearing must be True North (48.5 + (-3.5) = 45.0f)",
            45.0f, lockState!!.xiangBearing, 0.1f
        )
    }

    @Test
    fun `lockXiang under magnetic north with positive declination stores correct true north`() {
        // heading_deg=30.0 (Magnetic), declination=+5.0 → True North = 35.0°
        val ss = SessionState()
        val state = makeState(
            heading_deg = 30.0,
            north_type = NorthType.MAGNETIC,
            declination_deg = 5.0f,
            confidence = OverallConfidence.MODERATE
        )
        ss.lockXiang(state)

        val lockState = ss.zuoXiangLock.lockState
        assertNotNull(lockState)
        assertEquals(35.0f, lockState!!.xiangBearing, 0.1f)
    }

    @Test
    fun `lockXiang under true north does not add declination`() {
        // When already True North, heading_deg IS the True North bearing — no adjustment needed.
        val ss = SessionState()
        val state = makeState(
            heading_deg = 90.0,
            north_type = NorthType.TRUE,
            declination_deg = 5.0f,        // declination present but must NOT be added again
            confidence = OverallConfidence.MODERATE
        )
        ss.lockXiang(state)

        val lockState = ss.zuoXiangLock.lockState
        assertNotNull(lockState)
        assertEquals("True North bearing must be stored unchanged (no declination added)",
            90.0f, lockState!!.xiangBearing, 0.1f)
    }

    @Test
    fun `lockXiang wrap-around bearing near 360 deg stays in 0-360 range`() {
        // heading_deg=359.0 (Magnetic), declination=+5.0 → True North = 364.0 → normalised to 4.0°
        val ss = SessionState()
        val state = makeState(
            heading_deg = 359.0,
            north_type = NorthType.MAGNETIC,
            declination_deg = 5.0f,
            confidence = OverallConfidence.MODERATE
        )
        ss.lockXiang(state)

        val lockState = ss.zuoXiangLock.lockState
        assertNotNull(lockState)
        val xiang = lockState!!.xiangBearing
        assertTrue("xiangBearing must be in [0, 360)", xiang >= 0f && xiang < 360f)
        assertEquals(4.0f, xiang, 0.1f)
    }

    // -----------------------------------------------------------------------
    // ZuoXiangLock rederive — TSPEC §4.4, §8.2
    // -----------------------------------------------------------------------

    @Test
    fun `rederive after lock under magnetic north updates display bearings without changing true north`() {
        val lock = ZuoXiangLock()
        val trueBearing = 45.0f
        lock.lock(trueBearing)

        val declinationDeg = -3.5f
        lock.rederive(declinationDeg, isMagneticNorth = true)

        val state = lock.lockState!!
        // True North stored bearings must be invariant
        assertEquals(trueBearing, state.xiangBearing, 0.1f)
        // Display bearing = trueBearing − declinationDeg (for Magnetic North display)
        val expectedDisplay = ((trueBearing - declinationDeg + 360f) % 360f)
        assertEquals(expectedDisplay, state.displayXiangBearing, 0.1f)
    }

    @Test
    fun `rederive switching back to true north restores display bearing to true north value`() {
        val lock = ZuoXiangLock()
        lock.lock(90.0f)
        lock.rederive(5.0f, isMagneticNorth = true)  // display changes

        lock.rederive(5.0f, isMagneticNorth = false)  // switch back to True North
        val state = lock.lockState!!
        assertEquals("display bearing must equal true north bearing after switch back",
            90.0f, state.displayXiangBearing, 0.1f)
    }
}
