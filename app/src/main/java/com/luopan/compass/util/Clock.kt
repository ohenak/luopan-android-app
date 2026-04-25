package com.luopan.compass.util

interface Clock {
    /** Returns the current UTC time in milliseconds since the Unix epoch. */
    fun nowMs(): Long
}

/**
 * Production implementation of [Clock].
 * Named WallClock (not SystemClock) to avoid shadowing android.os.SystemClock.
 */
class WallClock : Clock {
    override fun nowMs(): Long = System.currentTimeMillis()
}
