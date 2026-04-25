package com.luopan.compass.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticBufferTest {

    @Test
    fun `adding capacity+1 elements evicts oldest and size equals capacity`() {
        val capacity = 5
        val buffer = DiagnosticBuffer<Int>(capacity)

        // Add capacity+1 elements: 0..5 (6 total)
        for (i in 0..capacity) {
            buffer.add(i)
        }

        assertEquals(capacity, buffer.size)
        // Element 0 should be evicted; min should now be 1
        assertEquals(1, buffer.percentile(0.0))
    }

    @Test
    fun `percentile returns correct values for 1 to 5`() {
        val buffer = DiagnosticBuffer<Int>(10)
        listOf(1, 2, 3, 4, 5).forEach { buffer.add(it) }

        assertEquals(1, buffer.percentile(0.0))
        assertEquals(5, buffer.percentile(1.0))
        assertEquals(3, buffer.percentile(0.5))
    }

    @Test
    fun `concurrent adds from two threads do not throw ConcurrentModificationException`() {
        val buffer = DiagnosticBuffer<Int>(1000)
        val iterations = 10_000
        var thrown: Throwable? = null

        val t1 = Thread {
            try {
                repeat(iterations) { buffer.add(it) }
            } catch (e: Throwable) {
                thrown = e
            }
        }

        val t2 = Thread {
            try {
                repeat(iterations) { buffer.add(it + iterations) }
            } catch (e: Throwable) {
                thrown = e
            }
        }

        t1.start()
        t2.start()
        t1.join()
        t2.join()

        if (thrown != null) throw AssertionError("Concurrent add threw an exception", thrown)
    }
}
