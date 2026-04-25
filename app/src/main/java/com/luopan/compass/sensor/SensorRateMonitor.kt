package com.luopan.compass.sensor

class SensorRateMonitor(private val windowSize: Int = 100) {

    private val timestamps = ArrayDeque<Long>(windowSize)

    fun onSample(timestampNs: Long) {
        if (timestamps.size >= windowSize) timestamps.removeFirst()
        timestamps.addLast(timestampNs)
    }

    fun getActualRateHz(): Double {
        if (timestamps.size < 2) return 0.0
        val span = timestamps.last() - timestamps.first()
        if (span <= 0L) return 0.0
        return (timestamps.size - 1).toDouble() / (span / 1_000_000_000.0)
    }

    fun reset() { timestamps.clear() }
}
