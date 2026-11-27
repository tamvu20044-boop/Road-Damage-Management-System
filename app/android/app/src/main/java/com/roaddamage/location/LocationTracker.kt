package com.roaddamage.location
import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.location.*
class LocationTracker(context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)
    @SuppressLint("MissingPermission") fun currentLocation(callback: (Double?, Double?) -> Unit) {
        val cancellation = CancellationTokenSource()
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
            .addOnSuccessListener { point ->
                if (point != null) callback(point.latitude, point.longitude)
                else lastKnownLocation(callback)
            }
            .addOnFailureListener { lastKnownLocation(callback) }
    }

    @SuppressLint("MissingPermission") private fun lastKnownLocation(callback: (Double?, Double?) -> Unit) {
        client.lastLocation.addOnSuccessListener { point -> callback(point?.latitude, point?.longitude) }
            .addOnFailureListener { callback(null, null) }
    }
}
