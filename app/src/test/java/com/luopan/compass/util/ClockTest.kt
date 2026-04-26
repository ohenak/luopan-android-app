package com.luopan.compass.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockTest {

    @Test
    fun `FakeClock nowMs returns injected initial value`() {
        val clock = FakeClock(currentMs = 1_000L)
        assertEquals(1_000L, clock.nowMs())
    }

    @Test
    fun `FakeClock nowMs returns zero when constructed with default`() {
        val clock = FakeClock()
        assertEquals(0L, clock.nowMs())
    }

    @Test
    fun `FakeClock advance increments current time by given amount`() {
        val clock = FakeClock(currentMs = 500L)
        clock.advance(250L)
        assertEquals(750L, clock.nowMs())
    }

    @Test
    fun `FakeClock advance accumulates across multiple calls`() {
        val clock = FakeClock(currentMs = 0L)
        clock.advance(100L)
        clock.advance(200L)
        assertEquals(300L, clock.nowMs())
    }

    @Test
    fun `FakeClock set overrides current time to exact value`() {
        val clock = FakeClock(currentMs = 9_999L)
        clock.set(42_000L)
        assertEquals(42_000L, clock.nowMs())
    }

    @Test
    fun `FakeClock set to zero resets time`() {
        val clock = FakeClock(currentMs = 99_000L)
        clock.set(0L)
        assertEquals(0L, clock.nowMs())
    }

    @Test
    fun `WallClock nowMs returns a positive wall-clock millisecond value`() {
        val clock = WallClock()
        val before = System.currentTimeMillis()
        val result = clock.nowMs()
        val after = System.currentTimeMillis()
        assert(result >= before) { "nowMs() $result < before $before" }
        assert(result <= after) { "nowMs() $result > after $after" }
    }
}
