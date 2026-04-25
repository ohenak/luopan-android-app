package com.luopan.compass.sensor

interface TimeSource {
    fun nowMs(): Long
    fun nowNs(): Long
}
