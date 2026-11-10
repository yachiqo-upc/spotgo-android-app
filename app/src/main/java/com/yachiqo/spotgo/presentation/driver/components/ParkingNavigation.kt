package com.yachiqo.spotgo.presentation.driver.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.yachiqo.spotgo.data.model.ParkingZone

/**
 * "lng,lat" (the stored format of ParkingZone.coordinates) → "lat,lng" for
 * the geo: scheme and the Google Maps URLs. Null when the zone has no usable
 * coordinates yet, so callers can fall back to the printed address.
 */
internal fun ParkingZone.coordinatesQuery(): String? {
    val parts = coordinates.split(",")
    if (parts.size != 2) return null
    val lng = parts[0].trim().toDoubleOrNull() ?: return null
    val lat = parts[1].trim().toDoubleOrNull() ?: return null
    return "$lat,$lng"
}

/**
 * US26 — opens [destination] (parking coordinates, address as fallback) in
 * Google Maps: the installed app first (geo: scheme), the web version after
 * it, and [onUnavailable] only when neither exists on the device.
 */
internal fun Context.openMapsTo(destination: String, onUnavailable: () -> Unit) {
    val encoded = Uri.encode(destination)
    val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded"))
    val web = Intent(Intent.ACTION_VIEW,
        Uri.parse("https://www.google.com/maps/search/?api=1&query=$encoded"))
    try {
        try {
            startActivity(geo)
        } catch (missingMaps: ActivityNotFoundException) {
            startActivity(web)
        }
    } catch (missingBrowser: ActivityNotFoundException) {
        onUnavailable()
    }
}

/** Opens the coordinates of [zone] in Google Maps (address as fallback). */
internal fun Context.openParkingNavigation(zone: ParkingZone, onUnavailable: () -> Unit) =
    openMapsTo(zone.coordinatesQuery() ?: zone.location, onUnavailable)
