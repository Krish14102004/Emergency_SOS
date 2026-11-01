package com.example.emergencysos

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

/** Acquires and tracks location only while the app is in the foreground. */
class LocationTracker(context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)
    private var updatesCallback: LocationCallback? = null
    private var currentLocationCancellation: CancellationTokenSource? = null

    @SuppressLint("MissingPermission")
    fun getCurrentLocation(
        onLocation: (LocationSnapshot?) -> Unit,
        onFailure: (String) -> Unit
    ) {
        stopCurrentLocationRequest()
        val cancellation = CancellationTokenSource()
        currentLocationCancellation = cancellation
        try {
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(MAX_CACHED_LOCATION_AGE_MS)
                .build()
            client.getCurrentLocation(request, cancellation.token)
                .addOnSuccessListener { location ->
                    if (currentLocationCancellation !== cancellation) return@addOnSuccessListener
                    currentLocationCancellation = null
                    if (location == null) {
                        onLocation(null)
                    } else {
                        onLocation(
                            LocationSnapshot(
                                latitude = location.latitude,
                                longitude = location.longitude,
                                accuracyMeters = location.accuracy,
                                updatedAtMillis = location.time
                            )
                        )
                    }
                }
                .addOnFailureListener { error ->
                    if (currentLocationCancellation !== cancellation) return@addOnFailureListener
                    currentLocationCancellation = null
                    onFailure(error.message ?: "Android could not obtain the current location.")
                }
        } catch (error: SecurityException) {
            currentLocationCancellation = null
            onFailure("Location permission is no longer available.")
        }
    }

    @SuppressLint("MissingPermission")
    fun startUpdates(onLocation: (LocationSnapshot) -> Unit, onFailure: (String) -> Unit) {
        stopUpdates()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location ->
                    onLocation(
                        LocationSnapshot(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracyMeters = location.accuracy,
                            updatedAtMillis = location.time
                        )
                    )
                }
            }
        }
        updatesCallback = callback
        try {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
                .setMinUpdateIntervalMillis(UPDATE_INTERVAL_MS)
                .setWaitForAccurateLocation(false)
                .build()
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener { error ->
                    if (updatesCallback === callback) {
                        updatesCallback = null
                        onFailure(error.message ?: "Android could not start location updates.")
                    }
                }
        } catch (error: SecurityException) {
            updatesCallback = null
            onFailure("Location permission is no longer available.")
        }
    }

    fun stopCurrentLocationRequest() {
        currentLocationCancellation?.cancel()
        currentLocationCancellation = null
    }

    fun stopUpdates() {
        updatesCallback?.let(client::removeLocationUpdates)
        updatesCallback = null
    }

    fun stop() {
        stopCurrentLocationRequest()
        stopUpdates()
    }

    companion object {
        private const val MAX_CACHED_LOCATION_AGE_MS = 30_000L
        private const val UPDATE_INTERVAL_MS = 10_000L
    }
}
