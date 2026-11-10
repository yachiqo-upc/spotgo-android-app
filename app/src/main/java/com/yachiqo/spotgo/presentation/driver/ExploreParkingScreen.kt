package com.yachiqo.spotgo.presentation.driver

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.yachiqo.spotgo.R
import com.yachiqo.spotgo.data.model.ParkingZone
import com.yachiqo.spotgo.data.repository.FavoriteRepository
import com.yachiqo.spotgo.data.repository.ParkingRepository
import com.yachiqo.spotgo.data.repository.money
import com.yachiqo.spotgo.data.repository.moneyWhole
import com.yachiqo.spotgo.presentation.common.FigmaIcon
import com.yachiqo.spotgo.presentation.driver.components.*
import com.yachiqo.spotgo.ui.theme.SpotGoTheme

/**
 * Mockup 03 — Explore parking (US01 / US25): map with the registered zones
 * as price markers, manual refresh of the availability timestamp, filters
 * and the details card of the selected zone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreParkingScreen(onOpenZone: (String) -> Unit, onProfile: () -> Unit, onUnavailableAction: () -> Unit) {
    val allZones = remember { ParkingRepository.zones() }
    var query by rememberSaveable { mutableStateOf("") }
    var filterIndex by rememberSaveable { mutableStateOf(0) }
    var selectedZoneId by rememberSaveable { mutableStateOf("zne-seed-0003") }
    var lastUpdated by remember { mutableStateOf(ParkingRepository.last_updated) }

    // Filter chips: 0 Available (default view), 1 Covered, 2 EV,
    // 3 Saved — the zones marked as favorite with the heart (UI-only
    // favorites, mirror of /api/v1/favorites).
    val savedIds = FavoriteRepository.saved_zone_ids()
    val zones = allZones.filter { zone ->
        val matchesQuery = query.isBlank() ||
            zone.name.contains(query, ignoreCase = true) ||
            zone.location.contains(query, ignoreCase = true)
        val matchesFilter = when (filterIndex) {
            1 -> zone.amenities.contains("COVERED")
            2 -> zone.amenities.contains("EV")
            3 -> zone.zone_id in savedIds
            else -> true
        }
        matchesQuery && matchesFilter
    }
    val selectedZone = allZones.firstOrNull { it.zone_id == selectedZoneId } ?: zones.firstOrNull()

    DriverPage(stringResource(R.string.explore_title), stringResource(R.string.driver_location), onProfile, prominentTitle = true) {
        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = query, onQueryChange = { query = it }, onSearch = {},
                    expanded = false, onExpandedChange = {},
                    placeholder = { Text(stringResource(R.string.explore_search)) },
                    leadingIcon = { FigmaIcon(R.drawable.ic_explore) })
            },
            expanded = false, onExpandedChange = {}, modifier = Modifier.fillMaxWidth(),
            windowInsets = WindowInsets(0), colors = SearchBarDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {}
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).height(40.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(R.string.driver_available, R.string.driver_covered, R.string.driver_ev, R.string.explore_saved)
                .forEachIndexed { index, label ->
                    FilterChip(selected = index == filterIndex, onClick = { filterIndex = index },
                        label = { Text(stringResource(label), style = MaterialTheme.typography.bodyMedium) })
                }
        }
        if (zones.isEmpty()) {
            DriverPanel(outlined = true) {
                Text(
                    stringResource(if (filterIndex == 3) R.string.explore_no_saved else R.string.explore_no_match),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            ExploreMap(allZones, zones, selectedZone, lastUpdated, onUnavailableAction,
                onSelectZone = { selectedZoneId = it.zone_id }, onRefresh = {
                    ParkingRepository.refresh()
                    lastUpdated = ParkingRepository.last_updated
                })
            ZoneCard(selectedZone, onOpenZone, onUnavailableAction)
        }
    }
}

@Composable
private fun ExploreMap(
    allZones: List<ParkingZone>,
    zones: List<ParkingZone>,
    selectedZone: ParkingZone?,
    lastUpdated: String,
    onUnavailableAction: () -> Unit,
    onSelectZone: (ParkingZone) -> Unit,
    onRefresh: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(24.dp), color = DriverMapBackground,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))) {
            val scale = maxWidth / 376.dp
            val rightMarkerOffset = minOf(282.dp * scale, maxWidth - 94.dp)
            if (googleMapsConfigured()) {
                // Pendiente 2 — real Google map (SPOTGO_MAPS_API_KEY).
                Box(Modifier.fillMaxWidth().height(349.dp * scale)) {
                    GoogleMapView(allZones, selectedZone?.zone_id, onSelectZone, Modifier.matchParentSize())
                    LastUpdatedBar(lastUpdated, onRefresh)
                }
            } else {
                Column {
                    Box(Modifier.fillMaxWidth().height(349.dp * scale)) {
                        ParkingMapBase()
                        Image(painterResource(R.drawable.map_walking_route), null,
                            Modifier.offset(106.dp * scale, 218.dp * scale).size(110.dp * scale, 93.dp * scale))
                        Image(painterResource(R.drawable.map_location_halo), null,
                            Modifier.offset(92.dp * scale, 295.dp * scale).size(30.dp * scale))
                        Image(painterResource(R.drawable.map_location_dot), null,
                            Modifier.offset(101.dp * scale, 304.dp * scale).size(12.dp * scale))
                        // Marker positions of mockup 03, indexed by the seed zone order.
                        val markerPositions = listOf(
                            23.dp to 87.dp, 180.dp to 60.dp, 165.dp to 179.dp, 20.dp to 221.dp,
                        )
                        zones.forEach { zone ->
                            val index = allZones.indexOfFirst { it.zone_id == zone.zone_id }
                            val offset = if (index == 4) rightMarkerOffset to 292.dp
                            else markerPositions.getOrElse(index) { 23.dp to 87.dp }
                            ParkingMarker(
                                price = moneyWhole(zone.hourly_price),
                                selected = zone.zone_id == selectedZone?.zone_id,
                                onClick = { onSelectZone(zone) },
                                modifier = Modifier.offset(offset.first * scale, offset.second * scale),
                            )
                        }
                        Text(stringResource(R.string.explore_walking_distance),
                            Modifier.offset(17.dp * scale, 332.dp * scale), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LastUpdatedBar(lastUpdated, onRefresh)
                    }
                }
            }
        }
    }
}

/** "Last updated · hh:mm" + Refresh overlay shared by both maps. */
@Composable
private fun LastUpdatedBar(lastUpdated: String, onRefresh: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(start = 10.dp).height(32.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.explore_last_updated, lastUpdated), Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onRefresh, modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 10.dp)) {
                Text(stringResource(R.string.explore_refresh), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ZoneCard(
    zone: ParkingZone?,
    onOpenZone: (String) -> Unit,
    onUnavailableAction: () -> Unit,
) {
    if (zone == null) return
    val available = ParkingRepository.available_count(zone.zone_id)
    Surface(Modifier.fillMaxWidth().padding(top = 4.dp),
        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(zone.name, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.explore_parking_distance, zone.distance_km.toString(), zone.location),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(money(zone.hourly_price), color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp))
                    Text(stringResource(R.string.driver_per_hour), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ParkingAmenities(zone, available)
            Button(onClick = { onOpenZone(zone.zone_id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                shape = RoundedCornerShape(16.dp)) {
                Text(stringResource(R.string.explore_view_parking))
            }
        }
    }
}

@Composable
private fun ParkingAmenities(zone: ParkingZone, available: Int) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 300.dp) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically) {
                AmenityChips(zone, available)
                // Heart saves the parking: it appears on the Saved chip.
                FavoriteButton(FavoriteRepository.is_saved(zone.zone_id),
                    onClick = { FavoriteRepository.toggle(zone.zone_id) })
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AmenityChips(zone, available)
                Spacer(Modifier.weight(1f))
                // Heart saves the parking: it appears on the Saved chip.
                FavoriteButton(FavoriteRepository.is_saved(zone.zone_id),
                    onClick = { FavoriteRepository.toggle(zone.zone_id) })
            }
        }
    }
}

@Composable
private fun AmenityChips(zone: ParkingZone, available: Int) {
    if (available > 0) {
        StatusBadge(stringResource(R.string.explore_spots, available))
    } else {
        StatusBadge(stringResource(R.string.explore_no_spots), positive = false)
    }
    if (zone.amenities.contains("COVERED")) {
        FilterChip(false, {}, { Text(stringResource(R.string.driver_covered)) })
    }
    if (zone.amenities.contains("EV")) {
        FilterChip(false, {}, { Text(stringResource(R.string.driver_ev)) })
    }
}

@Composable
private fun ParkingMarker(price: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Surface(onClick = onClick, modifier = modifier.size(82.dp, 36.dp), shape = RoundedCornerShape(12.dp),
        color = if (selected) DriverAvailable else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, if (selected) DriverAvailable else MaterialTheme.colorScheme.outline)) {
        Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.explore_parking_marker), style = MaterialTheme.typography.titleMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else DriverAvailable)
            Text(price, style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun ExploreParkingPreview() {
    SpotGoTheme { ExploreParkingScreen({}, {}, {}) }
}
