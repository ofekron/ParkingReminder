package com.ofekdev.parkingreminder

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

const val GEOFENCE_RADIUS_IN_METERS = 150.0f
const val GEOFENCE_EXPIRATION_IN_MILLISECONDS = 24 * 60 * 60 * 1000L
const val GEOFENCE_ID = "parking_reminder"
const val TAG = "LCOATION"


class GeofenceReminderBrodcastReceiver : BroadcastReceiver() {

    private fun getGeofencingRequest(location: Location): GeofencingRequest {
        var geofenceList = mutableListOf<Geofence>()
        geofenceList.add(
            Geofence.Builder()
                .setRequestId(GEOFENCE_ID)
                .setCircularRegion(
                    location.latitude,
                    location.longitude,
                    GEOFENCE_RADIUS_IN_METERS
                )
                .setExpirationDuration(GEOFENCE_EXPIRATION_IN_MILLISECONDS)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                .build()
        )
        return GeofencingRequest.Builder().apply {
            setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            addGeofences(geofenceList)
        }.build()
    }



    override fun onReceive(context: Context, intent: Intent) {

        when {
            hasLocationPermission(context) -> {

                try {
                    CoroutineScope(IO).launch {
                        var geofencingClient = LocationServices.getGeofencingClient(context)
                        var fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                        var location = fusedLocationClient.colastLocation()
                        val geofencingRequest = getGeofencingRequest(location)
                        val broadcast = PendingIntent.getBroadcast(
                            context,
                            0,
                            Intent(context, GeofenceBroadcastReceiver::class.java),
                            PendingIntent.FLAG_UPDATE_CURRENT
                        )
                        geofencingClient.coremoveGeofences(listOf(GEOFENCE_ID))
                        geofencingClient.coaddGeofences(geofencingRequest, broadcast)
                        geofence.set(Location(location.latitude, location.longitude))
                        showGeofenceNotification(context)
                    }
                } catch (e: Exception) {
                    showError(context, context.getString(R.string.geofence_error), e)
                }

            }
            else -> {

                ContextCompat.startActivity(context,
                    Intent(
                        context,
                        MainActivity::class.java
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
                    Bundle().apply {
                        this.putString("permission", Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                )

            }
        }
    }


    private fun showGeofenceNotification(context: Context) {
        var builder =
            NotificationCompat.Builder(context, CHANNEL_ID).setSmallIcon(R.drawable.ic_stat_gps)
                .setContentTitle(context.getString(R.string.geofence_reminder_is_activated))
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(context.getString(R.string.geofence_reminder_desc))
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOngoing(true)
                .addAction(
                    android.R.drawable.ic_input_delete,
                    context.getString(R.string.stop),
                    PendingIntent.getBroadcast(
                        context,
                        0,
                        Intent(context, StopGeofenceReceiver::class.java).apply {
                            action = "ACTION_STOP"
                        },
                        0
                    )
                )
        with(NotificationManagerCompat.from(context)) {
            notify(15, builder.build())
        }
    }


}

private suspend fun GeofencingClient.coaddGeofences(geofencingRequest: GeofencingRequest, broadcast: PendingIntent?) : Void = suspendCoroutine {
    addGeofences(geofencingRequest,broadcast).addOnSuccessListener { v ->  it.resume(v) }.addOnFailureListener { e -> it.resumeWithException(e) }
}

private suspend fun GeofencingClient.coremoveGeofences(toRemove: List<String>) : Void = suspendCoroutine {
    removeGeofences(toRemove).addOnSuccessListener { v ->  it.resume(v) }.addOnFailureListener { e -> it.resumeWithException(e) }
}

private suspend fun FusedLocationProviderClient.colastLocation(): Location = suspendCoroutine {
    lastLocation.addOnSuccessListener { l -> l?.run { it.resume(l) } ?: run{ it.resumeWithException(Exception("location is null"))} }.addOnFailureListener { e -> it.resumeWithException(e) }
}

