package com.luopan.compass.ui

import android.content.Context
import android.os.PowerManager

class WakeLockManager(context: Context) {

    private val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    fun acquire() {
        if (wakeLock?.isHeld == true) return
        wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
            "luopan:compass"
        ).also { it.acquire(10 * 60 * 1000L) }  // 10-minute timeout
    }

    fun release() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }
}
