package com.yachiqo.spotgo.presentation.driver.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.yachiqo.spotgo.BuildConfig
import com.yachiqo.spotgo.data.model.ParkingZone
import com.yachiqo.spotgo.data.repository.moneyWhole

/**
 * True when a Google Maps key has been set in local.properties
 * (SPOTGO_MAPS_API_KEY). While it is empty the Explore screen keeps the
 * designed map of mockup 03, so the Figma comparison never depends on it.
 */
internal fun googleMapsConfigured(): Boolean = BuildConfig.SPOTGO_MAPS_API_KEY.isNotBlank()

/**
 * Real Google map of the Explore screen (Pendiente 2 — Maps API).
 *
 * Each zone is placed with its UI-only `coordinates` ("lng,lat"); tapping a
 * marker selects the zone (the details card updates) and the info window
 * shows the name and the hourly price. Only rendered when
 * [googleMapsConfigured] is true.
 */
@Composable
internal fun GoogleMapView(
    zones: List<ParkingZone>,
    selectedZoneId: String?,
    onSelectZone: (ParkingZone) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).apply {
                onCreate(null)
                onStart()
                onResume()
            }
        },
        update = { mapView ->
            mapView.getMapAsync { map ->
                map.clear()
                map.uiSettings.isZoomControlsEnabled = false
                map.uiSettings.isMapToolbarEnabled = false
                val bounds = LatLngBounds.Builder()
                var hasPoint = false
                zones.forEach { zone ->
                    val point = zone.latLng() ?: return@forEach
                    hasPoint = true
                    bounds.include(point)
                    val selected = zone.zone_id == selectedZoneId
                    map.addMarker(
                        MarkerOptions()
                            .position(point)
                            .title(zone.name)
                            .snippet(moneyWhole(zone.hourly_price))
                            .zIndex(if (selected) 1f else 0f)
                            .icon(
                                BitmapDescriptorFactory.defaultMarker(
                                    if (selected) BitmapDescriptorFactory.HUE_GREEN
                                    else BitmapDescriptorFactory.HUE_ROSE,
                                ),
                            ),
                    )?.tag = zone.zone_id
                }
                if (hasPoint) {
                    // Center on the zones without needing a measured map size.
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(bounds.build().center, 14.5f))
                }
                map.setOnMarkerClickListener { marker ->
                    val zoneId = marker.tag as? String
                    val zone = zones.firstOrNull { it.zone_id == zoneId }
                    if (zone != null) onSelectZone(zone)
                    false // keep the info window (name + price) open
                }
            }
        },
        onRelease = { mapView ->
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        },
    )
}

/** "lng,lat" → LatLng; null when the zone has no coordinates yet. */
private fun ParkingZone.latLng(): LatLng? {
    if (coordinates.isBlank()) return null
    val parts = coordinates.split(",")
    if (parts.size != 2) return null
    val lng = parts[0].trim().toDoubleOrNull() ?: return null
    val lat = parts[1].trim().toDoubleOrNull() ?: return null
    return LatLng(lat, lng)
}
