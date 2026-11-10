package com.yachiqo.spotgo.presentation.driver.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
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
 * - Native Maps SDK view. Every zone of the database is drawn as a marker
 *   from its own `coordinates` ("lng,lat"); tapping one selects the zone.
 * - The blue dot of the current location (isMyLocationEnabled) once the
 *   runtime location permission has been granted.
 * - The camera opens on the overview of the markers and then follows the
 *   selection, so it can move to any coordinate.
 *
 * Only rendered when [googleMapsConfigured] is true.
 */
@Composable
internal fun GoogleMapView(
    zones: List<ParkingZone>,
    selectedZoneId: String?,
    onSelectZone: (ParkingZone) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasLocationPermission = granted }
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // Seeded with the first selection: the opening frame keeps the overview
    // of every marker and only a later selection moves the camera.
    var lastCenteredZoneId by remember { mutableStateOf(selectedZoneId) }
    var cameraInitialized by remember { mutableStateOf(false) }

    AndroidView(
        modifier = modifier,
        factory = { mapContext -> MapView(mapContext).apply { onCreate(null); onStart(); onResume() } },
        update = { mapView ->
            // Read here (composition of the AndroidView) so the grant of the
            // permission re-runs this block and the map gets the blue dot.
            val locationGranted = hasLocationPermission
            mapView.getMapAsync { map ->
                map.clear()
                map.uiSettings.isZoomControlsEnabled = false
                map.uiSettings.isMapToolbarEnabled = false
                map.uiSettings.isMyLocationButtonEnabled = false
                // Blue dot of the current location (guarded: the permission
                // can be revoked between the check and here).
                if (locationGranted) {
                    try {
                        map.isMyLocationEnabled = true
                    } catch (_: SecurityException) {
                        // No permission: the map simply stays without the dot.
                    }
                }
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
                when {
                    !cameraInitialized && hasPoint -> {
                        // Center on the zones without needing a measured map size.
                        cameraInitialized = true
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(bounds.build().center, 14.5f))
                    }
                    selectedZoneId != null && selectedZoneId != lastCenteredZoneId -> {
                        // Follow the selection to its own coordinate.
                        lastCenteredZoneId = selectedZoneId
                        val point = zones.firstOrNull { it.zone_id == selectedZoneId }?.latLng()
                        if (point != null) map.animateCamera(CameraUpdateFactory.newLatLngZoom(point, 16f))
                    }
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
