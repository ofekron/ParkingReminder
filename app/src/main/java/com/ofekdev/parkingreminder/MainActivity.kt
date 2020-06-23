package com.ofekdev.parkingreminder

import android.app.Activity
import android.app.Notification.EXTRA_NOTIFICATION_ID
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import kotlin.reflect.typeOf
import com.google.android.material.snackbar.Snackbar
import com.ofekdev.prefs.GsonPreference
import com.ofekdev.prefs.Preference
import kotlinx.android.synthetic.main.activity_main.*
import java.lang.Exception
import java.security.Permission
import java.util.jar.Manifest


const val CHANNEL_ID: String = "parking_reminder"
const val PERMISSION_REQUEST_CODE: Int = 1
fun hasLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}
fun showError(context: Context, s: String, e: Exception? = null) {
    e?.let {Log.e(TAG,it.message,it)}
    Toast.makeText(context,s , Toast.LENGTH_LONG).show()
}
inline fun <reified T: Any> Activity.extra(key: String, default: T? = null) = lazy {
    val value = intent?.extras?.get(key)
    if (value is T) value else default
}
inline fun <reified T: Any> Activity.extraNotNull(key: String, default: T? = null) = lazy {
    val value = intent?.extras?.get(key)
    requireNotNull(if (value is T) value else default) { key }
}
class MarkerWithRadius {
    private var marker: Marker? = null
    private var circle: Circle? = null

    public fun show(map : GoogleMap,latlng : LatLng,title : String) {
        remove()
        marker=map?.addMarker(MarkerOptions()
            .position(latlng)
            .title(title)
        )
        circle=map?.addCircle(CircleOptions()
                .center(latlng)
                .radius(GEOFENCE_RADIUS_IN_METERS.toDouble())
                .strokeWidth(0f)
                .fillColor(0x550000FF)
        )

    }
    public fun remove() {
        marker?.apply{
            remove()
        }
        circle?.apply {
            remove()
        }
    }

}
class MainActivity : AppCompatActivity() {


    private lateinit var listener: Preference.PreferenceListener<Location>
    private lateinit var map: GoogleMap
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Preference.defaultInit(applicationContext)
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment?
        listener=object : Preference.PreferenceListener<Location> {
            var mr = MarkerWithRadius();
            override fun onValueChanged(
                firstTime: Boolean,
                preference: Preference<Location>
            ) {
                if (!::map.isInitialized) return
                preference.get()?.run {
                    mr.remove()
                    var latlng=LatLng(lat,lng);
                    mr.show(map,latlng,getString(R.string.parking_location))
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(this.lat,this.lng), 15F));
                }
            }
        }


        mapFragment?.getMapAsync {m ->
            if (m==null) return@getMapAsync
            map=m
            if (hasLocationPermission(this)) {
                map.isMyLocationEnabled = true;
                map.uiSettings?.isMyLocationButtonEnabled = true

            }

            listener.onValueChanged(true, geofence)
        }

        createNotificationChannel()
        extra<String>("permission",null).value?.let {
            ActivityCompat.requestPermissions(this,arrayOf(it),PERMISSION_REQUEST_CODE)
        }
        val fab: View = findViewById(R.id.floatingActionButton)
        fab.setOnClickListener { view ->
            ActivityCompat.requestPermissions(this,arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION,android.Manifest.permission.ACCESS_COARSE_LOCATION),PERMISSION_REQUEST_CODE)
        }

    }

    override fun onStart() {
        super.onStart()
        geofence.addPreferenceListener(listener,true)
    }

    override fun onStop() {
        super.onStop()
        geofence.removePreferenceListener(listener)
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() &&
                            grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    map?.isMyLocationEnabled = true;
                    map?.uiSettings?.isMyLocationButtonEnabled = true
                    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
                    GeofenceReminderBrodcastReceiver().onReceive(this,Intent())
                    val intent = Intent(Intent.ACTION_SEND)
                    val title = getString(R.string.parking_pay_app)
                    val chooser = Intent.createChooser(intent, title)
                    chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS,getParkingIntentsActivity(this).toTypedArray());
                    startActivityForResult(chooser, 1);
                } else {
                    Toast.makeText(applicationContext,"Allow location if you want us to remember your parking location",Toast.LENGTH_LONG).show()
                    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
                    showNotification()
                    val intent = Intent(Intent.ACTION_SEND)
                    val title = getString(R.string.parking_pay_app)
                    val chooser = Intent.createChooser(intent, title)
                    chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS,getParkingIntentsActivity(this).toTypedArray());
                    startActivityForResult(chooser, 1);
                }
                return
            }

        }

    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        data?.resolveActivity(packageManager)?.apply {
            startActivity(data)
        }
    }

    private fun showNotification() {
        var builder = NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.ic_stat_car)
            .setContentTitle(getString(R.string.parking_reminder))
            .setContentText(getString(R.string.parking_reminder_desc))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_input_delete,getString(R.string.stop), PendingIntent.getBroadcast(this, 0, Intent(this, StopReceiver::class.java).apply {
                action = "ACTION_STOP"
                putExtra(EXTRA_NOTIFICATION_ID, 0)
            }, 0))
            .addAction(android.R.drawable.ic_input_delete,getString(R.string.geofence), PendingIntent.getBroadcast(this, 0, Intent(this, GeofenceReminderBrodcastReceiver::class.java).apply {
                action = "ACTION_GEOFENCE"
                putExtra(EXTRA_NOTIFICATION_ID, 0)
            }, 0))
        with(NotificationManagerCompat.from(this)) {
            // notificationId is a unique int for each notification that you must define
            notify(15, builder.build())
        }
    }
    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.parking_reminder)
            val descriptionText = getString(R.string.parking_reminder_desc)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    fun getParkingIntentsActivity(context: Context): List<Intent> {
        return listOf("air.com.cellogroup","com.unicell.pangoandroid").mapNotNull { getIntentForPackage(context,it) }

    }
    fun getIntentForPackage(context: Context, packageName: String): Intent? {
        return context.getPackageManager().getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

}


