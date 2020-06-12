package com.ofekdev.parkingreminder

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import androidx.core.app.ActivityCompat.*
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.ofekdev.prefs.GsonPreference

const val GEOFENCE_RADIUS_IN_METERS = 150.0f
const val GEOFENCE_EXPIRATION_IN_MILLISECONDS = 24*60*60*1000L
const val GEOFENCE_ID="parking_reminder"

class GeofenceReminderBrodcastReceiver : BroadcastReceiver() {

    lateinit var geofencingClient: GeofencingClient
    private fun getGeofencingRequest(location: Location): GeofencingRequest {
        var geofenceList = mutableListOf<Geofence> ()
        geofenceList.add(
            Geofence.Builder()
                // Set the request ID of the geofence. This is a string to identify this
                // geofence.
                .setRequestId(GEOFENCE_ID)

                // Set the circular region of this geofence.
                .setCircularRegion(
                    location.latitude,
                    location.longitude,
                    GEOFENCE_RADIUS_IN_METERS
                )

                // Set the expiration duration of the geofence. This geofence gets automatically
                // removed after this period of time.
                .setExpirationDuration(GEOFENCE_EXPIRATION_IN_MILLISECONDS)

                // Set the transition types of interest. Alerts are only generated for these
                // transition. We track entry and exit transitions in this sample.
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)

                // Create the geofence.
                .build())
        return GeofencingRequest.Builder().apply {
            setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            addGeofences(geofenceList)
        }.build()
    }
    override fun onReceive(context: Context, intent: Intent) {
        when {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                var fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location : Location? ->
                        location?.let {
                            geofencingClient = LocationServices.getGeofencingClient(context)
                            geofencingClient?.addGeofences(getGeofencingRequest(it), PendingIntent.getBroadcast(context, 0, Intent(context, GeofenceBroadcastReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT))
                                ?.run {
                                    addOnSuccessListener {
                                        geofence.set(Location(location.latitude,location.longitude))
                                        showGeofenceNotification(context)
                                    }
                                    addOnFailureListener {
                                        showError(context, context.getString(R.string.geofence_error))
                                    }
                                }
                        } ?: run {
                            showError(context, context.getString(R.string.geofence_error))
                        }
                    }.addOnFailureListener {
                        showError(context,context.getString(R.string.couldnt_get_location))
                    }
            }
            else -> {

                ContextCompat.startActivity(context,Intent(context,MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
                    Bundle().apply {
                        this.putString("permission",Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                )

            }
        }




    }

    private fun showGeofenceNotification(context: Context) {
        var builder = NotificationCompat.Builder(context, CHANNEL_ID).setSmallIcon(R.drawable.ic_stat_gps)
            .setContentTitle(context.getString(R.string.geofence_reminder_is_activated))
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(context.getString(R.string.geofence_reminder_desc)))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_input_delete,context.getString(R.string.stop), PendingIntent.getBroadcast(context, 0, Intent(context, StopGeofenceReceiver::class.java).apply {
                action = "ACTION_STOP"
            }, 0))
        with(NotificationManagerCompat.from(context)) {
            // notificationId is a unique int for each notification that you must define
            notify(15, builder.build())
        }
    }


}

