package com.luopan.compass.luopan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for [ZuoXiangLock].
 *
 * All tests are pure JVM — no Android dependencies.
 *
 * Covers:
 *   - Initial null state
 *   - lock() zuoBearing derivation (BR-06)
 *   - lock() 山 label lookups via SectorLookup.ring5() + RingLabelProvider.ring5Label()
 *   - clear() resets to null
 *   - rederive() updates only display bearings; stored True North never changes (FSPEC §4d)
 *   - Thread-safety: concurrent lock()/read produces no torn state (TE-F04)
 */
class ZuoXiangLockTest {

    // -------------------------------------------------------------------------
    // Initial state
    // -------------------------------------------------------------------------

    @Test
    fun `lockState is null on fresh instance`() {
        val lock = ZuoXiangLock()
        assertNull(lock.lockState)
    }

    // -------------------------------------------------------------------------
    // lock() — zuoBearing derivation (BR-06)
    // -------------------------------------------------------------------------

    @Test
    fun `lock 270 - zuoBearing is 90`() {
        val lock = ZuoXiangLock()
        lock.lock(270f)
        assertEquals(90f, lock.lockState!!.zuoBearing, 0.001f)
    }

    @Test
    fun `lock 350 - zuoBearing is 170`() {
        val lock = ZuoXiangLock()
        lock.lock(350f)
        assertEquals(170f, lock.lockState!!.zuoBearing, 0.001f)
    }

    @Test
    fun `lock 0 - zuoBearing is 180`() {
        val lock = ZuoXiangLock()
        lock.lock(0f)
        assertEquals(180f, lock.lockState!!.zuoBearing, 0.001f)
    }

    @Test
    fun `lock 180 - zuoBearing is 0`() {
        val lock = ZuoXiangLock()
        lock.lock(180f)
        assertEquals(0f, lock.lockState!!.zuoBearing, 0.001f)
    }

    // -------------------------------------------------------------------------
    // lock() — xiangBearing stored as passed
    // -------------------------------------------------------------------------

    @Test
    fun `lock stores xiangBearing exactly`() {
        val lock = ZuoXiangLock()
        lock.lock(45f)
        assertEquals(45f, lock.lockState!!.xiangBearing, 0.001f)
    }

    // -------------------------------------------------------------------------
    // lock() — 山 label lookups via real SectorLookup + RingLabelProvider
    // -------------------------------------------------------------------------

    @Test
    fun `lock 45 - xiangMountain is Gen (艮) from Ring 5 sector`() {
        // 45° → Ring 5 index 4 → 艮 [37.5°, 52.5°)
        val lock = ZuoXiangLock()
        lock.lock(45f)
        assertEquals("艮", lock.lockState!!.xiangMountain)
    }

    @Test
    fun `lock 45 - zuoMountain is Kun (坤) from Ring 5 sector for 225 degrees`() {
        // zuoBearing = (45 + 180) % 360 = 225° → Ring 5 index 16 → 坤 [217.5°, 232.5°)
        val lock = ZuoXiangLock()
        lock.lock(45f)
        assertEquals("坤", lock.lockState!!.zuoMountain)
    }

    @Test
    fun `lock 90 - xiangMountain is Mao (卯) from Ring 5 sector`() {
        // 90° → Ring 5 index 7 → 卯 [82.5°, 97.5°)
        val lock = ZuoXiangLock()
        lock.lock(90f)
        assertEquals("卯", lock.lockState!!.xiangMountain)
    }

    @Test
    fun `lock 90 - zuoMountain is You (酉) from Ring 5 sector for 270 degrees`() {
        // zuoBearing = (90 + 180) % 360 = 270° → Ring 5 index 19 → 酉 [262.5°, 277.5°)
        val lock = ZuoXiangLock()
        lock.lock(90f)
        assertEquals("酉", lock.lockState!!.zuoMountain)
    }

    // -------------------------------------------------------------------------
    // lock() — display bearings initialised to True North values
    // -------------------------------------------------------------------------

    @Test
    fun `lock initialises displayXiangBearing equal to xiangBearing`() {
        val lock = ZuoXiangLock()
        lock.lock(45f)
        assertEquals(
            lock.lockState!!.xiangBearing,
            lock.lockState!!.displayXiangBearing,
            0.001f
        )
    }

    @Test
    fun `lock initialises displayZuoBearing equal to zuoBearing`() {
        val lock = ZuoXiangLock()
        lock.lock(45f)
        assertEquals(
            lock.lockState!!.zuoBearing,
            lock.lockState!!.displayZuoBearing,
            0.001f
        )
    }

    // -------------------------------------------------------------------------
    // clear()
    // -------------------------------------------------------------------------

    @Test
    fun `clear after lock sets lockState to null`() {
        val lock = ZuoXiangLock()
        lock.lock(45f)
        assertNotNull(lock.lockState)
        lock.clear()
        assertNull(lock.lockState)
    }

    @Test
    fun `clear on unlocked instance keeps lockState null`() {
        val lock = ZuoXiangLock()
        lock.clear()
        assertNull(lock.lockState)
    }

    // -------------------------------------------------------------------------
    // rederive() — stored True North bearing never changes (FSPEC §4d)
    // -------------------------------------------------------------------------

    @Test
    fun `rederive does not change stored True North xiangBearing`() {
        // lock at 45° True N; switch to Mag N with declination -3.5°
        // displayXiangBearing = 45 - (-3.5) = 48.5°; xiangBearing must remain 45°
        val lock = ZuoXiangLock()
        lock.lock(45f)
        lock.rederive(declinationDeg = -3.5f, isMagneticNorth = true)
        assertEquals(45f, lock.lockState!!.xiangBearing, 0.001f)
    }

    @Test
    fun `rederive magnetic north updates displayXiangBearing correctly`() {
        // lock at 45° True N; declination = -3.5° (East-positive convention)
        // displayXiangBearing = xiangBearing - declinationDeg = 45 - (-3.5) = 48.5
        val lock = ZuoXiangLock()
        lock.lock(45f)
        lock.rederive(declinationDeg = -3.5f, isMagneticNorth = true)
        assertEquals(48.5f, lock.lockState!!.displayXiangBearing, 0.001f)
    }

    @Test
    fun `rederive magnetic north updates displayZuoBearing correctly`() {
        // zuoBearing = 225°; displayZuoBearing = 225 - (-3.5) = 228.5
        val lock = ZuoXiangLock()
        lock.lock(45f)
        lock.rederive(declinationDeg = -3.5f, isMagneticNorth = true)
        assertEquals(228.5f, lock.lockState!!.displayZuoBearing, 0.001f)
    }

    @Test
    fun `rederive true north sets displayXiangBearing equal to stored True North`() {
        // isMagneticNorth = false → displayXiangBearing = xiangBearing = 45°
        val lock = ZuoXiangLock()
        lock.lock(45f)
        lock.rederive(declinationDeg = 0f, isMagneticNorth = false)
        assertEquals(45f, lock.lockState!!.displayXiangBearing, 0.001f)
    }

    @Test
    fun `rederive does not change stored zuoBearing (True North invariant)`() {
        val lock = ZuoXiangLock()
        lock.lock(45f)
        lock.rederive(declinationDeg = -3.5f, isMagneticNorth = true)
        assertEquals(225f, lock.lockState!!.zuoBearing, 0.001f)
    }

    @Test
    fun `rederive does not change mountain labels`() {
        val lock = ZuoXiangLock()
        lock.lock(45f)
        lock.rederive(declinationDeg = -3.5f, isMagneticNorth = true)
        assertEquals("艮", lock.lockState!!.xiangMountain)
        assertEquals("坤", lock.lockState!!.zuoMountain)
    }

    @Test
    fun `rederive on unlocked instance is a no-op`() {
        val lock = ZuoXiangLock()
        lock.rederive(declinationDeg = -3.5f, isMagneticNorth = true)
        assertNull(lock.lockState)
    }

    @Test
    fun `rederive near 0 degree wrap - displayBearing stays in 0-360 range`() {
        // lock at 5° True N; declination = 10° (positive East)
        // displayXiangBearing = 5 - 10 = -5 → should wrap to 355°
        val lock = ZuoXiangLock()
        lock.lock(5f)
        lock.rederive(declinationDeg = 10f, isMagneticNorth = true)
        val displayXiang = lock.lockState!!.displayXiangBearing
        assertTrue(
            "displayXiangBearing should be in [0, 360) but was $displayXiang",
            displayXiang >= 0f && displayXiang < 360f
        )
        assertEquals(355f, displayXiang, 0.001f)
    }

    // -------------------------------------------------------------------------
    // Thread-safety — concurrent lock() and read produce no torn state (TE-F04)
    // -------------------------------------------------------------------------

    @Test
    fun `concurrent lock and read produce no torn state`() {
        val lock = ZuoXiangLock()
        val iterations = 5_000
        val errorsFound = AtomicInteger(0)
        val latch = CountDownLatch(2)
        val executor = Executors.newFixedThreadPool(2)

        // Writer thread: repeatedly calls lock() and clear()
        executor.submit {
            try {
                repeat(iterations) { i ->
                    if (i % 2 == 0) lock.lock(45f) else lock.clear()
                }
            } finally {
                latch.countDown()
            }
        }

        // Reader thread: reads lockState and checks consistency — either fully null or fully populated.
        // Since LockState fields are non-nullable, a non-null LockState is always internally
        // consistent (AtomicReference provides safe publication, so we never observe a partial write).
        // The test verifies no exception is thrown during concurrent reads (i.e., no torn reference).
        executor.submit {
            try {
                repeat(iterations) {
                    val state = lock.lockState
                    if (state != null) {
                        // A non-null LockState must have non-empty mountain labels — verifies
                        // that SectorLookup and RingLabelProvider were called correctly at lock time
                        // and that AtomicReference publishes the complete object atomically.
                        if (state.xiangMountain.isEmpty() || state.zuoMountain.isEmpty()) {
                            errorsFound.incrementAndGet()
                        }
                    }
                    // null state (after clear()) is always a consistent state
                }
            } finally {
                latch.countDown()
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()
        assertEquals("Torn state detected during concurrent lock/read", 0, errorsFound.get())
    }
}
