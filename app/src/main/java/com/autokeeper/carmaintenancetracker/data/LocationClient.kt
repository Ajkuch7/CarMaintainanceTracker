package com.autokeeper.carmaintenancetracker.data

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await

class LocationClient(context: Context) {
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun lastLocation(): Pair<Double, Double>? {
        val location = fusedClient.lastLocation.await() ?: return null
        return location.latitude to location.longitude
    }
}
