package com.luopan.compass.sensor

import android.location.Location
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LocationProviderTest {

    @Test
    fun `LocationProvider returning null gives null`() {
        val provider = LocationProvider { null }
        assertNull(provider.getLastKnownLocation())
    }

    @Test
    fun `LocationProvider returning mock location gives that location`() {
        val mockLocation = Location("test").apply {
            latitude = 22.3
            longitude = 114.1
        }
        val provider = LocationProvider { mockLocation }
        assertSame(mockLocation, provider.getLastKnownLocation())
    }

    @Test
    fun `fake TimeSource returns fixed values`() {
        val fakeTimeSource = object : TimeSource {
            override fun nowMs() = 1000L
            override fun nowNs() = 1_000_000L
        }
        assertEquals(1000L, fakeTimeSource.nowMs())
        assertEquals(1_000_000L, fakeTimeSource.nowNs())
    }
}
