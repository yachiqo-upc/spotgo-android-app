package com.yachiqo.spotgo.presentation.driver

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yachiqo.spotgo.R
import com.yachiqo.spotgo.data.model.Vehicle
import com.yachiqo.spotgo.data.repository.VehicleRepository
import com.yachiqo.spotgo.data.repository.RemoteVehicleRepository
import com.yachiqo.spotgo.data.repository.VehicleListResult
import com.yachiqo.spotgo.data.remote.SpotGoApi
import com.yachiqo.spotgo.data.repository.isoDateLabel
import com.yachiqo.spotgo.presentation.common.FigmaIcon
import com.yachiqo.spotgo.presentation.driver.components.DriverOutlineButton
import com.yachiqo.spotgo.presentation.driver.components.DriverPanel
import com.yachiqo.spotgo.presentation.driver.components.DriverSectionTitle
import com.yachiqo.spotgo.ui.theme.SpotGoError
import com.yachiqo.spotgo.ui.theme.SpotGoSurface
import com.yachiqo.spotgo.ui.theme.SpotGoText
import com.yachiqo.spotgo.ui.theme.SpotGoTheme
import com.yachiqo.spotgo.ui.theme.SpotGoWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Mockup 11 — My vehicles: the registered vehicles of the Driver account,
 * the default vehicle badge and the entry point to add a new one (US27).
 */
@Composable
fun MyVehiclesScreen(
    onBack: () -> Unit,
    onAddVehicle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backendMode = SpotGoApi.is_configured()
    var remoteVehicles by remember { mutableStateOf(emptyList<Vehicle>()) }
    var loading by remember { mutableStateOf(backendMode) }
    var loadFailed by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var actionFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(backendMode) {
        if (backendMode) {
            loading = true
            when (val result = withContext(Dispatchers.IO) { RemoteVehicleRepository.loadVehicles() }) {
                is VehicleListResult.Success -> {
                    remoteVehicles = result.vehicles
                    loadFailed = false
                }
                VehicleListResult.Failure -> loadFailed = true
            }
            loading = false
        }
    }
    val vehicles = if (backendMode) remoteVehicles else VehicleRepository.vehicles_for_current_profile()
    val defaultVehicleId = vehicles.firstOrNull()?.vehicle_id
    var detailVehicle by remember { mutableStateOf<Vehicle?>(null) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                FigmaIcon(R.drawable.ic_back, stringResource(R.string.back_to_login))
            }
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.vehicles_title), style = MaterialTheme.typography.titleLarge)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_vehicle),
                        contentDescription = null,
                        modifier = Modifier.size(30.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.vehicles_info_title),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            stringResource(R.string.vehicles_info_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            DriverSectionTitle(stringResource(R.string.vehicles_section))

            DriverPanel {
                if (backendMode && loading) {
                    CircularProgressIndicator(Modifier.padding(18.dp))
                } else if (backendMode && loadFailed) {
                    Text(
                        stringResource(R.string.vehicles_load_error),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (vehicles.isEmpty()) {
                    Text(
                        stringResource(R.string.vehicles_empty),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    vehicles.forEachIndexed { index, vehicle ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline,
                                thickness = 0.6.dp,
                            )
                        }
                        VehicleRow(
                            vehicle = vehicle,
                            isDefault = vehicle.vehicle_id == defaultVehicleId,
                            onClick = {
                                detailVehicle = vehicle
                                confirmRemove = false
                            },
                        )
                    }
                }
            }

            DriverOutlineButton(stringResource(R.string.vehicles_add), onAddVehicle)

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        stringResource(R.string.vehicles_note_title),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = SpotGoWarning,
                    )
                    Text(
                        stringResource(R.string.vehicles_note_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    detailVehicle?.let { vehicle ->
        VehicleDetailSheet(
            vehicle = vehicle,
            confirmRemove = confirmRemove,
            onConfirmRemove = { confirmRemove = true },
            onRemove = {
                if (!removing) {
                    if (backendMode) {
                        scope.launch {
                            removing = true
                            actionFailed = false
                            val removed = withContext(Dispatchers.IO) {
                                RemoteVehicleRepository.removeVehicle(vehicle.vehicle_id)
                            }
                            removing = false
                            if (removed) {
                                remoteVehicles = remoteVehicles.filterNot { it.vehicle_id == vehicle.vehicle_id }
                                detailVehicle = null
                                confirmRemove = false
                            } else actionFailed = true
                        }
                    } else {
                        VehicleRepository.remove_vehicle(vehicle.vehicle_id)
                        detailVehicle = null
                        confirmRemove = false
                    }
                }
            },
            removing = removing,
            actionError = if (actionFailed) stringResource(R.string.vehicles_backend_error) else null,
            onClose = {
                if (confirmRemove) confirmRemove = false else detailVehicle = null
            },
        )
    }
}

/** Detail sheet of a registered vehicle with the remove option (US27). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleDetailSheet(
    vehicle: Vehicle,
    confirmRemove: Boolean,
    onConfirmRemove: () -> Unit,
    onRemove: () -> Unit,
    removing: Boolean,
    actionError: String?,
    onClose: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = SpotGoSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.vehicles_row_title, vehicle.make, vehicle.model, vehicle.plate),
                style = MaterialTheme.typography.titleLarge,
                color = SpotGoText,
            )
            if (confirmRemove) {
                Text(
                    stringResource(R.string.vehicles_remove_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = SpotGoError,
                )
                Text(
                    stringResource(R.string.vehicles_remove_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                actionError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = onRemove,
                    enabled = !removing,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SpotGoError),
                ) {
                    Text(stringResource(R.string.vehicles_remove), style = MaterialTheme.typography.bodyLarge)
                }
                OutlinedButton(
                    onClick = onClose,
                    enabled = !removing,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(stringResource(R.string.state_action_close), style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                VehicleDetailRow(stringResource(R.string.add_vehicle_plate), vehicle.plate)
                VehicleDetailRow(stringResource(R.string.add_vehicle_make), vehicle.make)
                VehicleDetailRow(stringResource(R.string.add_vehicle_model), vehicle.model)
                VehicleDetailRow(stringResource(R.string.add_vehicle_color), vehicle.color)
                VehicleDetailRow(
                    stringResource(R.string.add_vehicle_type),
                    stringResource(
                        when (vehicle.vehicle_type) {
                            com.yachiqo.spotgo.data.model.VehicleType.SUV -> R.string.add_vehicle_type_suv
                            com.yachiqo.spotgo.data.model.VehicleType.MOTORCYCLE -> R.string.add_vehicle_type_motorcycle
                            else -> R.string.add_vehicle_type_car
                        },
                    ),
                )
                VehicleDetailRow(stringResource(R.string.vehicles_added), isoDateLabel(vehicle.created_at))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.6.dp)
                Button(
                    onClick = onConfirmRemove,
                    enabled = !removing,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SpotGoError),
                ) {
                    Text(stringResource(R.string.vehicles_remove), style = MaterialTheme.typography.bodyLarge)
                }
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(stringResource(R.string.state_action_close), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun VehicleDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, color = SpotGoText)
    }
}

@Composable
private fun VehicleRow(vehicle: Vehicle, isDefault: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                stringResource(
                    R.string.vehicles_row_title,
                    vehicle.make,
                    vehicle.model,
                    vehicle.plate,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(
                    R.string.vehicles_row_subtitle,
                    stringResource(if (isDefault) R.string.vehicles_default_role else R.string.vehicles_secondary_role),
                    vehicle.color,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isDefault) {
            Text(
                stringResource(R.string.vehicles_default_badge),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                stringResource(R.string.payments_chevron),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(name = "11 · My vehicles", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun MyVehiclesPreview() {
    SpotGoTheme { MyVehiclesScreen(onBack = {}, onAddVehicle = {}) }
}
