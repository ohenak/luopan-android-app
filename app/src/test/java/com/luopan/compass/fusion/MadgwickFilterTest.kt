package com.luopan.compass.fusion

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MadgwickFilterTest {

    private lateinit var filter: MadgwickFilter

    @Before fun setUp() { filter = MadgwickFilter(beta = 0.1f) }

    @Test fun `initial quaternion is identity`() {
        assertEquals(1f, filter.q0, 1e-6f)
        assertEquals(0f, filter.q1, 1e-6f)
        assertEquals(0f, filter.q2, 1e-6f)
        assertEquals(0f, filter.q3, 1e-6f)
    }

    @Test fun `quaternion remains unit after update`() {
        repeat(100) {
            filter.update(0f, 0f, 9.8f, 0.01f, 0f, 0f, 30f, 0f, -30f, 0.02f)
        }
        val q = filter.getQuaternion()
        val norm = q[0]*q[0] + q[1]*q[1] + q[2]*q[2] + q[3]*q[3]
        assertEquals(1.0f, norm, 1e-5f)
    }

    @Test fun `quaternion remains unit after updateNoGyro`() {
        repeat(100) {
            filter.updateNoGyro(0f, 0f, 9.8f, 30f, 0f, -30f, 0.02f)
        }
        val q = filter.getQuaternion()
        val norm = q[0]*q[0] + q[1]*q[1] + q[2]*q[2] + q[3]*q[3]
        assertEquals(1.0f, norm, 1e-5f)
    }

    @Test fun `reset returns to identity`() {
        repeat(50) { filter.update(0f, 0f, 9.8f, 0.01f, 0f, 0f, 30f, 0f, -30f, 0.02f) }
        filter.reset()
        assertEquals(1f, filter.q0, 1e-6f)
        assertEquals(0f, filter.q1, 1e-6f)
        assertEquals(0f, filter.q2, 1e-6f)
        assertEquals(0f, filter.q3, 1e-6f)
    }

    @Test fun `higher beta converges faster than lower beta`() {
        val slow = MadgwickFilter(beta = 0.01f)
        val fast = MadgwickFilter(beta = 0.5f)
        val ax = 0f; val ay = 0f; val az = 9.8f
        val mx = 30f; val my = 0f; val mz = -30f
        repeat(50) {
            slow.update(ax, ay, az, 0f, 0f, 0f, mx, my, mz, 0.02f)
            fast.update(ax, ay, az, 0f, 0f, 0f, mx, my, mz, 0.02f)
        }
        val slowDiff = Math.abs(slow.q0 - 1f)
        val fastDiff = Math.abs(fast.q0 - 1f)
        // fast should deviate more from identity (converged to new pose) than slow
        assertTrue("fast ($fastDiff) should differ from identity more than slow ($slowDiff)", fastDiff >= slowDiff)
    }

    @Test fun `getQuaternion returns copy`() {
        val q1 = filter.getQuaternion()
        val q2 = filter.getQuaternion()
        assertNotSame(q1, q2)
    }
}
