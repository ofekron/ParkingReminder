package com.ofekdev.parkingreminder

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
val MINIMUM_TIME = 1000*60*30
class GeofenceBroadcastReceiver : BroadcastReceiver() {
    companion object {
        var inside = false
        var count = 0
        var lastEnter = System.currentTimeMillis()
    }
    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent.hasError()) {
            showError(context,GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode))
        } else {
            when(geofencingEvent.geofenceTransition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> {
                    lastEnter = System.currentTimeMillis()
                    GeofenceBroadcastReceiver.inside=true
                }
                Geofence.GEOFENCE_TRANSITION_EXIT -> {
                    inside=false
                    count++
                    if (lastEnter+MINIMUM_TIME<=System.currentTimeMillis() || count>=2)
                        updateNotification(context)
                }

            }
        }


    }

    private fun updateNotification(context: Context) {
        var builder = NotificationCompat.Builder(context, CHANNEL_ID).setSmallIcon(R.drawable.ic_stat_driving)
            .setContentTitle(context.getString(R.string.parking_reminder))
            .setContentText(context.getString(R.string.parking_reminder_driving))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true).setSound(Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.old_car))
            .addAction(android.R.drawable.ic_input_delete,context.getString(R.string.stop), PendingIntent.getBroadcast(context, 0, Intent(context, StopGeofenceReceiver::class.java).apply {
                action = "ACTION_STOP"
            }, 0))
        with(NotificationManagerCompat.from(context)) {
            // notificationId is a unique int for each notification that you must define
            notify(15, builder.build())
        }
    }
}
