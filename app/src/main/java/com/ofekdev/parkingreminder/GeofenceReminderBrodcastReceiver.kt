package com.ofekdev.parkingreminder

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

const val GEOFENCE_RADIUS_IN_METERS = 3.0f
const val GEOFENCE_EXPIRATION_IN_MILLISECONDS = 24*60*60*1000L
const val GEOFENCE_ID="parking_reminder"
const val TAG = "LCOATION"

suspend fun FusedLocationProviderClient.lastLocation() : Location? = suspendCoroutine {
    lastLocation.addOnFailureListener { e->
        it.resumeWithException(e)
    }.addOnSuccessListener { l->
        l?.apply {
            it.resume(l)
        }?:run{
            it.resumeWithException(Exception("couldnt get location"))
        }
    }
}

class GeofenceReminderBrodcastReceiver : BroadcastReceiver() {

    private fun getGeofencingRequest(location: Location): GeofencingRequest {
        var geofenceList = mutableListOf<Geofence> ()
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
                .build())
        return GeofencingRequest.Builder().apply {
            setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            addGeofences(geofenceList)
        }.build()
    }

    fun addGeofence(
        context : Context,
        geofencingClient: GeofencingClient,
        location: Location
    ) {
        with (location) {
            val geofencingRequest = getGeofencingRequest(this)
            val broadcast = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, GeofenceBroadcastReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT
            )
            with (geofencingClient.addGeofences(geofencingRequest, broadcast)) {
                addOnSuccessListener {
                    geofence.set(Location(latitude,longitude))
                    showGeofenceNotification(context)
                }
                addOnFailureListener {
                    showError(context, context.getString(R.string.geofence_error))
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {

        when {
            hasLocationPermission(context)  -> {
                var geofencingClient = LocationServices.getGeofencingClient(context)
                var fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                CoroutineScope(Main).launch {
                    try {
                        fusedLocationClient.lastLocation()?.apply {
                            addGeofence(context,geofencingClient,this)
                        }
                    } catch (e : Exception) {
                        Log.e(TAG,e.message,e)
                        showError(context, context.getString(R.string.geofence_error))
                    }
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

    private fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
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
            notify(15, builder.build())
        }
    }


}

