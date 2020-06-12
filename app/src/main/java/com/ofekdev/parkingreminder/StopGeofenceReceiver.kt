package com.ofekdev.parkingreminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.LocationServices

class StopGeofenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        var geofencingClient = LocationServices.getGeofencingClient(context)
        geofencingClient?.removeGeofences(mutableListOf(GEOFENCE_ID))?.run {
            addOnSuccessListener {
                NotificationManagerCompat.from(context).cancel(15);
                geofence.set(null)
            }
            addOnFailureListener {
                showError(context, context.getString(R.string.geofence_error))
            }
        }

    }
}
