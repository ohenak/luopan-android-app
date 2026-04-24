package com.luopan.compass.sensor

import android.content.Context
import android.location.Location
import android.location.LocationManager

class SystemLocationProvider(private val context: Context) : LocationProvider {
    override fun getLastKnownLocation(): Location? = runCatching {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
    }.getOrNull()
}
