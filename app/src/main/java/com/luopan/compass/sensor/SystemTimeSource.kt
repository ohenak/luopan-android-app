package com.luopan.compass.sensor

import android.os.SystemClock

object SystemTimeSource : TimeSource {
    override fun nowMs(): Long = System.currentTimeMillis()
    override fun nowNs(): Long = SystemClock.elapsedRealtimeNanos()
}
