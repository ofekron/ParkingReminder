package com.ofekdev.parkingreminder

import com.ofekdev.prefs.GsonPreference
data class Location(val lat : Double, val lng : Double)
val geofence : GsonPreference<Location> = GsonPreference("geofence_location",Location::class.java)