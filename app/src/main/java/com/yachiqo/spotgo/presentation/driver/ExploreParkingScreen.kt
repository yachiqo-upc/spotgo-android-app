package com.yachiqo.spotgo.presentation.driver

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yachiqo.spotgo.R
import com.yachiqo.spotgo.data.model.ParkingZone
import com.yachiqo.spotgo.data.repository.ParkingCatalogResult
import com.yachiqo.spotgo.data.repository.ParkingFacilityListing
import com.yachiqo.spotgo.data.repository.RemoteParkingRepository
import com.yachiqo.spotgo.data.repository.money
import com.yachiqo.spotgo.data.repository.moneyWhole
import com.yachiqo.spotgo.presentation.common.FigmaIcon
import com.yachiqo.spotgo.presentation.driver.components.DriverMapBackground
import com.yachiqo.spotgo.presentation.driver.components.DriverPage
import com.yachiqo.spotgo.presentation.driver.components.DriverPanel
import com.yachiqo.spotgo.presentation.driver.components.FavoriteButton
import com.yachiqo.spotgo.presentation.driver.components.MapLibreMapView
import com.yachiqo.spotgo.presentation.driver.components.ParkingMapPoint
import com.yachiqo.spotgo.presentation.driver.components.StatusBadge
import com.yachiqo.spotgo.ui.theme.SpotGoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Driver Explore: tenant facilities are map markers; each facility shows its backend zones. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreParkingScreen(
    onOpenZone: (String) -> Unit,
    onUnavailableAction: () -> Unit,
) {
    var facilities by remember { mutableStateOf<List<ParkingFacilityListing>>(emptyList()) }
    var selectedTenantId by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var filterIndex by rememberSaveable { mutableIntStateOf(0) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var favoriteRequest by remember { mutableStateOf(false) }
    var lastUpdated by remember { mutableStateOf("—") }
    val favoriteUpdateError = stringResource(R.string.explore_favorite_update_error)
    val backendLoadError = stringResource(R.string.explore_backend_error)
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshKey) {
        loading = true
        error = null
        when (val result = withContext(Dispatchers.IO) { RemoteParkingRepository.loadFacilities() }) {
            is ParkingCatalogResult.Success -> {
                facilities = result.facilities
                lastUpdated = java.time.LocalTime.now()
                    .format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
                if (selectedTenantId !in facilities.map { it.tenantId }) {
                    selectedTenantId = facilities.firstOrNull()?.tenantId.orEmpty()
                }
            }
            is ParkingCatalogResult.Failure -> error = backendLoadError
        }
        loading = false
    }

    val visibleFacilities = facilities.filter { facility ->
        val matchesQuery = query.isBlank() || facility.name.contains(query, ignoreCase = true) ||
            facility.address.contains(query, ignoreCase = true) ||
            facility.zones.any { it.name.contains(query, ignoreCase = true) }
        val matchesFilter = when (filterIndex) {
            0 -> true
            1 -> facility.availableSpots > 0
            2 -> facility.hasCoveredSpots
            3 -> facility.hasEvCharging
            4 -> facility.isFavorite
            else -> true
        }
        matchesQuery && matchesFilter
    }
    val selectedFacility = visibleFacilities.firstOrNull { it.tenantId == selectedTenantId }
        ?: visibleFacilities.firstOrNull()

    DriverPage(
        stringResource(R.string.explore_title),
        stringResource(R.string.explore_subtitle),
        prominentTitle = true,
    ) {
        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = {},
                    expanded = false,
                    onExpandedChange = {},
                    placeholder = { Text(stringResource(R.string.explore_search)) },
                    leadingIcon = { FigmaIcon(R.drawable.ic_explore) },
                )
            },
            expanded = false,
            onExpandedChange = {},
            modifier = Modifier.fillMaxWidth(),
            windowInsets = WindowInsets(0),
            colors = SearchBarDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {}

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).height(40.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                R.string.explore_all,
                R.string.driver_available,
                R.string.driver_covered,
                R.string.driver_ev,
                R.string.explore_saved,
            )
                .forEachIndexed { index, label ->
                    FilterChip(
                        selected = index == filterIndex,
                        onClick = { filterIndex = index },
                        label = { Text(stringResource(label), style = MaterialTheme.typography.bodyMedium) },
                    )
                }
        }

        ExploreMap(
            facilities = visibleFacilities,
            selected = selectedFacility,
            lastUpdated = lastUpdated,
            onRefresh = { refreshKey++ },
            onSelectTenant = { selectedTenantId = it },
        )

        when {
            loading -> DriverPanel(outlined = true) {
                Text(stringResource(R.string.explore_loading_parking), Modifier.padding(16.dp))
            }
            error != null -> DriverPanel(outlined = true) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(error.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { refreshKey++ }, enabled = !loading) {
                        Text(stringResource(R.string.explore_refresh))
                    }
                }
            }
            visibleFacilities.isEmpty() -> DriverPanel(outlined = true) {
                Text(
                    stringResource(if (filterIndex == 4) R.string.explore_no_saved else R.string.explore_no_match),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                selectedFacility?.let { facility ->
                    FacilityCard(
                        facility = facility,
                        favoriteRequest = favoriteRequest,
                        onToggleFavorite = {
                            if (!favoriteRequest) scope.launch {
                                favoriteRequest = true
                                val changed = withContext(Dispatchers.IO) {
                                    RemoteParkingRepository.toggleFavorite(facility)
                                }
                                favoriteRequest = false
                                if (changed) refreshKey++ else error = favoriteUpdateError
                            }
                        },
                        onOpenZone = onOpenZone,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExploreMap(
    facilities: List<ParkingFacilityListing>,
    selected: ParkingFacilityListing?,
    lastUpdated: String,
    onRefresh: () -> Unit,
    onSelectTenant: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = DriverMapBackground,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))) {
            val mapHeight = 349.dp * (maxWidth / 376.dp)
            Box(Modifier.fillMaxWidth().height(mapHeight)) {
                MapLibreMapView(
                    points = facilities.map { facility ->
                        ParkingMapPoint(
                            id = facility.tenantId,
                            title = facility.name,
                            snippet = stringResource(R.string.explore_map_snippet, moneyWhole(facility.hourlyPrice), facility.zones.size),
                            latitude = facility.latitude,
                            longitude = facility.longitude,
                        )
                    },
                    selectedPointId = selected?.tenantId,
                    onSelectPoint = onSelectTenant,
                    modifier = Modifier.matchParentSize(),
                )
                LastUpdatedBar(lastUpdated, onRefresh, Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun LastUpdatedBar(lastUpdated: String, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        ) {
            Text(
                stringResource(R.string.explore_map_updated, lastUpdated),
                Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        androidx.compose.material3.TextButton(onClick = onRefresh) {
            Text(stringResource(R.string.explore_refresh))
        }
    }
}

@Composable
private fun FacilityCard(
    facility: ParkingFacilityListing,
    favoriteRequest: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenZone: (String) -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(facility.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        facility.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FavoriteButton(facility.isFavorite, onClick = onToggleFavorite)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(money(facility.hourlyPrice), color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp))
                    Text(stringResource(R.string.driver_per_hour), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusBadge(
                    if (facility.availableSpots > 0) stringResource(R.string.explore_spots, facility.availableSpots)
                    else stringResource(R.string.explore_no_spots),
                    positive = facility.availableSpots > 0,
                )
            }
            Text(stringResource(R.string.explore_zones_and_spaces), style = MaterialTheme.typography.titleSmall)
            facility.zones.forEach { zone ->
                ZoneCard(zone, facility.availabilityByZone[zone.zone_id] ?: 0, onOpenZone)
            }
            if (favoriteRequest) {
                Text(stringResource(R.string.explore_saving_favorite), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ZoneCard(zone: ParkingZone, available: Int, onOpenZone: (String) -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(zone.name, style = MaterialTheme.typography.titleSmall)
                    Text(zone.location, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(money(zone.hourly_price), color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(
                    if (available > 0) stringResource(R.string.explore_spots, available)
                    else stringResource(R.string.explore_no_spots),
                    positive = available > 0,
                )
                if (zone.amenities.contains("COVERED")) StatusBadge(stringResource(R.string.driver_covered))
                if (zone.amenities.contains("EV")) StatusBadge(stringResource(R.string.driver_ev))
                Spacer(Modifier.weight(1f))
            }
            Button(
                onClick = { onOpenZone(zone.zone_id) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(stringResource(R.string.explore_view_parking))
            }
        }
    }
}

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun ExploreParkingPreview() {
    SpotGoTheme { ExploreParkingScreen({}, {}) }
}
