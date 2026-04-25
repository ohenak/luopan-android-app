package com.luopan.compass.util

class FakeClock(private var currentMs: Long = 0L) : Clock {
    override fun nowMs(): Long = currentMs
    fun advance(ms: Long) { currentMs += ms }
    fun set(ms: Long) { currentMs = ms }
}
