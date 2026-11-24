package com.yachiqo.spotgo.presentation.driver.components

import android.Manifest
import android.content.Context
import android.graphics.PointF
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.yachiqo.spotgo.R
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdate
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
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
    followLocation: Boolean = false,
    showCameraControls: Boolean = false,
    controls: @Composable BoxScope.() -> Unit = {},
) {
    val context = LocalContext.current
    var hasLocationPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    var permissionResolved by rememberSaveable { mutableStateOf(hasLocationPermission) }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasLocationPermission = hasLocationPermission(context)
        permissionResolved = true
    }
    LaunchedEffect(Unit) {
        if (!permissionResolved && !permissionRequested) {
            permissionRequested = true
            locationPermission.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        }
    }

    var locationResolved by remember { mutableStateOf(false) }
    var userPosition by remember { mutableStateOf<LatLng?>(null) }
    LaunchedEffect(permissionResolved, hasLocationPermission) {
        if (permissionResolved) {
            userPosition = if (hasLocationPermission) currentMapLocation(context) else null
            locationResolved = true
        }
    }

    if (!locationResolved) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.explore_map_locating))
        }
        return
    }

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    var lastCenteredPointId by remember { mutableStateOf(selectedPointId) }

    Box(modifier) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { mapContext ->
                MapLibre.getInstance(mapContext)
                val initialCamera = CameraPosition.Builder()
                    .target(userPosition ?: LatLng(-12.0464, -77.0428))
                    .zoom(if (userPosition != null) 15.0 else 12.0)
                    .build()
                val options = MapLibreMapOptions.createFromAttributes(mapContext).camera(initialCamera)
                MapView(mapContext, options).apply {
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

        LaunchedEffect(map, styleReady, points, selectedPointId, hasLocationPermission) {
            val readyMap = map?.takeIf { styleReady } ?: return@LaunchedEffect
            val markerById = linkedMapOf<Marker, String>()
            readyMap.clear()
            readyMap.uiSettings.setAllGesturesEnabled(hasLocationPermission)
            if (!hasLocationPermission) return@LaunchedEffect

            points.forEach { item ->
                val point = LatLng(item.latitude, item.longitude)
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

            if (selectedPointId != lastCenteredPointId) {
                lastCenteredPointId = selectedPointId
                markerById.entries.firstOrNull { it.value == selectedPointId }?.key?.let { marker ->
                    if (followLocation && readyMap.locationComponent.isLocationComponentActivated) {
                        readyMap.locationComponent.cameraMode = CameraMode.NONE
                    }
                    readyMap.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.position, 16.0))
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
                    if (followLocation) cameraMode = CameraMode.TRACKING
                }
            }
        }

        if (hasLocationPermission) {
            controls()
            val readyMap = map
            if (showCameraControls && styleReady && readyMap != null) {
                MapCameraControls(
                    map = readyMap,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 36.dp),
                    onMove = { update ->
                        if (followLocation && readyMap.locationComponent.isLocationComponentActivated) {
                            readyMap.locationComponent.cameraMode = CameraMode.NONE
                        }
                        readyMap.animateCamera(update, 180)
                    },
                )
            }
        }

        if (userPosition == null && hasLocationPermission) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 40.dp),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    stringResource(R.string.explore_map_location_unavailable),
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.labelSmall,
                )
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
        if (!hasLocationPermission) {
            Box(
                modifier = Modifier.matchParentSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.explore_map_location_denied),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun MapCameraControls(map: MapLibreMap, modifier: Modifier, onMove: (CameraUpdate) -> Unit) {
    val offset = with(LocalDensity.current) { 48.dp.toPx() }

    fun panBy(x: Float, y: Float) {
        val projection = map.projection
        val center = projection.toScreenLocation(map.cameraPosition.target ?: return)
        val destination = projection.fromScreenLocation(PointF(center.x + x, center.y + y))
        onMove(CameraUpdateFactory.newLatLng(destination))
    }

    var arrowsVisible by rememberSaveable { mutableStateOf(false) }
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (arrowsVisible) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                MapControl("←", R.string.explore_map_pan_left) { panBy(-offset, 0f) }
                MapControl("↑", R.string.explore_map_pan_up) { panBy(0f, -offset) }
                MapControl("↓", R.string.explore_map_pan_down) { panBy(0f, offset) }
                MapControl("→", R.string.explore_map_pan_right) { panBy(offset, 0f) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            MapControl(
                "✥",
                if (arrowsVisible) R.string.explore_map_hide_arrows else R.string.explore_map_show_arrows,
            ) { arrowsVisible = !arrowsVisible }
            MapControl("−", R.string.explore_map_zoom_out) { onMove(CameraUpdateFactory.zoomOut()) }
            MapControl("+", R.string.explore_map_zoom_in) { onMove(CameraUpdateFactory.zoomIn()) }
        }
    }
}

@Composable
private fun MapControl(symbol: String, label: Int, onClick: () -> Unit) {
    val description = stringResource(label)
    Surface(
        modifier = Modifier.size(28.dp)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
        color = Color.White,
        contentColor = Color(0xFF333333),
        shape = MaterialTheme.shapes.extraSmall,
        shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(symbol, style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun hasLocationPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private suspend fun currentMapLocation(context: Context): LatLng? {
    if (!hasLocationPermission(context)) return null
    val manager = context.getSystemService(LocationManager::class.java) ?: return null
    val providers = listOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        .filter { LocationManagerCompat.hasProvider(manager, it) && manager.isProviderEnabled(it) }
    val location = withTimeoutOrNull(5_000L) {
        coroutineScope {
            val results = Channel<Location?>(providers.size)
            val requests = providers.map { provider ->
                launch { results.send(requestLocation(manager, provider, context)) }
            }
            try {
                repeat(providers.size) {
                    results.receive()?.let { return@coroutineScope it }
                }
                null
            } finally {
                requests.forEach { it.cancel() }
                results.cancel()
            }
        }
    }
    return location?.let { LatLng(it.latitude, it.longitude) }
}

@SuppressLint("MissingPermission")
private suspend fun requestLocation(
    manager: LocationManager,
    provider: String,
    context: Context,
): Location? = suspendCancellableCoroutine { continuation ->
    val cancellation = CancellationSignal()
    continuation.invokeOnCancellation { cancellation.cancel() }
    try {
        LocationManagerCompat.getCurrentLocation(
            manager, provider, cancellation, ContextCompat.getMainExecutor(context),
        ) { fix -> continuation.resume(fix) }
    } catch (_: SecurityException) {
        continuation.resume(null)
    } catch (_: IllegalArgumentException) {
        continuation.resume(null)
    }
}

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
