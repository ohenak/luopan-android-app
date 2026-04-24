package com.luopan.compass.sensor

import android.location.Location

fun interface LocationProvider {
    fun getLastKnownLocation(): Location?
}
