package com.yachiqo.spotgo.presentation.driver.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

internal data class ParkingMapPoint(
    val id: String,
    val title: String,
    val snippet: String,
    val latitude: Double,
    val longitude: Double,
)

/** Integrated MapLibre map using public OpenStreetMap tiles at low volume. */
@Composable
internal fun MapLibreMapView(
    points: List<ParkingMapPoint>,
    selectedPointId: String?,
    onSelectPoint: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var hasLocationPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> hasLocationPermission = result.values.any { it } }
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            locationPermission.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        }
    }

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    var cameraInitialized by remember { mutableStateOf(false) }
    var lastCenteredPointId by remember { mutableStateOf(selectedPointId) }

    Box(modifier) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { mapContext ->
                MapLibre.getInstance(mapContext)
                MapView(mapContext).apply {
                    onCreate(null)
                    onStart()
                    onResume()
                    getMapAsync { readyMap ->
                        map = readyMap
                        readyMap.setStyle(Style.Builder().fromJson(OSM_STYLE)) {
                            styleReady = true
                        }
                    }
                }
            },
            onRelease = { mapView ->
                mapView.onPause()
                mapView.onStop()
                mapView.onDestroy()
            },
        )

        LaunchedEffect(map, styleReady, points, selectedPointId) {
            val readyMap = map?.takeIf { styleReady } ?: return@LaunchedEffect
            val markerById = linkedMapOf<Marker, String>()
            readyMap.clear()

            val bounds = LatLngBounds.Builder()
            var hasPoint = false
            points.forEach { item ->
                val point = LatLng(item.latitude, item.longitude)
                hasPoint = true
                bounds.include(point)
                readyMap.addMarker(
                    MarkerOptions()
                        .position(point)
                        .title(item.title)
                        .snippet(item.snippet),
                )?.let { marker -> markerById[marker] = item.id }
            }
            readyMap.setOnMarkerClickListener { marker ->
                markerById[marker]?.let(onSelectPoint)
                false
            }

            when {
                !cameraInitialized && hasPoint -> {
                    cameraInitialized = true
                    if (markerById.size == 1) {
                        readyMap.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(markerById.keys.first().position, 14.5),
                        )
                    } else {
                        readyMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 80))
                    }
                }
                !cameraInitialized -> readyMap.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(-12.0464, -77.0428), 12.0),
                )
                selectedPointId != null && selectedPointId != lastCenteredPointId -> {
                    lastCenteredPointId = selectedPointId
                    val marker = markerById.entries.firstOrNull { it.value == selectedPointId }?.key
                    if (marker != null) {
                        readyMap.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.position, 16.0))
                    }
                }
            }
        }

        LaunchedEffect(map, styleReady, hasLocationPermission) {
            val readyMap = map?.takeIf { styleReady } ?: return@LaunchedEffect
            val style = readyMap.style ?: return@LaunchedEffect
            if (!hasLocationPermission) return@LaunchedEffect
            runCatching {
                readyMap.locationComponent.apply {
                    activateLocationComponent(
                        LocationComponentActivationOptions.builder(context, style).build(),
                    )
                    isLocationComponentEnabled = true
                    renderMode = RenderMode.NORMAL
                }
            }
        }

        // Keep attribution visible regardless of the map style's attribution control.
        Surface(
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shape = MaterialTheme.shapes.small,
        ) {
            Text(
                "© OpenStreetMap contributors",
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun hasLocationPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private val OSM_STYLE = """
    {
      "version": 8,
      "sources": {
        "openstreetmap": {
          "type": "raster",
          "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
          "tileSize": 256,
          "attribution": "© OpenStreetMap contributors"
        }
      },
      "layers": [{"id": "osm-raster", "type": "raster", "source": "openstreetmap"}]
    }
""".trimIndent()
