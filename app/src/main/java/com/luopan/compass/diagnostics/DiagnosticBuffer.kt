package com.luopan.compass.diagnostics

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class DiagnosticBuffer<T : Comparable<T>>(val capacity: Int) {
    private val buffer = ArrayDeque<T>(capacity)
    private val lock = ReentrantReadWriteLock()

    val size: Int get() = lock.read { buffer.size }

    fun add(value: T) {
        lock.write {
            if (buffer.size >= capacity) buffer.removeFirst()
            buffer.addLast(value)
        }
    }

    fun percentile(p: Double): T {
        val snapshot = lock.read { buffer.toList() }
        require(snapshot.isNotEmpty()) { "Buffer is empty" }
        val sorted = snapshot.sorted()
        val index = (sorted.size * p).toInt().coerceIn(0, sorted.size - 1)
        return sorted[index]
    }
}
