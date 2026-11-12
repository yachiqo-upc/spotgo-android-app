package com.yachiqo.spotgo.presentation.driver

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yachiqo.spotgo.R
import com.yachiqo.spotgo.data.model.VehicleType
import com.yachiqo.spotgo.data.repository.VehicleError
import com.yachiqo.spotgo.data.repository.VehicleRepository
import com.yachiqo.spotgo.data.repository.VehicleResult
import com.yachiqo.spotgo.data.repository.RemoteVehicleRepository
import com.yachiqo.spotgo.data.remote.SpotGoApi
import com.yachiqo.spotgo.presentation.common.FigmaIcon
import com.yachiqo.spotgo.presentation.common.StateBottomSheet
import com.yachiqo.spotgo.presentation.driver.components.DriverPrimaryButton
import com.yachiqo.spotgo.presentation.login.figmaFieldBounds
import com.yachiqo.spotgo.presentation.login.loginFieldColors
import com.yachiqo.spotgo.ui.theme.SpotGoTheme
import com.yachiqo.spotgo.ui.theme.SpotGoWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Bottom sheet states of the add-vehicle flow (12a duplicate / 12b invalid). */
private enum class AddVehicleSheet { INVALID_FORM, INVALID_PLATE, DUPLICATE_PLATE, BACKEND_ERROR }

/**
 * Mockup 12 — Add vehicle: plate, brand, model, colour and vehicle type with
 * the Peruvian plate hint. Save registers the vehicle on the account (US27).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVehicleScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backendMode = SpotGoApi.is_configured()
    // Keep mockup values only in local demo mode; backend registrations start blank.
    val plate = rememberTextFieldState(initialText = if (backendMode) "" else "B9R-314")
    val make = rememberTextFieldState(initialText = if (backendMode) "" else "Toyota")
    val model = rememberTextFieldState(initialText = if (backendMode) "" else "Yaris")
    val color = rememberTextFieldState(initialText = if (backendMode) "" else "White")
    var vehicleType by rememberSaveable { mutableStateOf(VehicleType.CAR) }
    var sheet by remember { mutableStateOf<AddVehicleSheet?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    Column(
        modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
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
            Text(stringResource(R.string.add_vehicle_title), style = MaterialTheme.typography.titleLarge)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.add_vehicle_details),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(R.string.add_vehicle_details_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            VehicleField(plate, R.string.add_vehicle_plate, KeyboardType.Text)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VehicleField(make, R.string.add_vehicle_make, KeyboardType.Text, Modifier.weight(1f))
                VehicleField(model, R.string.add_vehicle_model, KeyboardType.Text, Modifier.weight(1f))
            }
            VehicleField(color, R.string.add_vehicle_color, KeyboardType.Text)

            Text(
                stringResource(R.string.add_vehicle_type),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VehicleTypeChip(
                    label = stringResource(R.string.add_vehicle_type_car),
                    selected = vehicleType == VehicleType.CAR,
                    onClick = { vehicleType = VehicleType.CAR },
                    modifier = Modifier.weight(1f),
                )
                VehicleTypeChip(
                    label = stringResource(R.string.add_vehicle_type_suv),
                    selected = vehicleType == VehicleType.SUV,
                    onClick = { vehicleType = VehicleType.SUV },
                    modifier = Modifier.weight(1f),
                )
                VehicleTypeChip(
                    label = stringResource(R.string.add_vehicle_type_motorcycle),
                    selected = vehicleType == VehicleType.MOTORCYCLE,
                    onClick = { vehicleType = VehicleType.MOTORCYCLE },
                    modifier = Modifier.weight(1f),
                )
            }

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
                        stringResource(R.string.add_vehicle_plate_hint_title),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = SpotGoWarning,
                    )
                    Text(
                        stringResource(R.string.add_vehicle_plate_hint_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            DriverPrimaryButton(
                label = stringResource(R.string.add_vehicle_save),
                onClick = {
                    focusManager.clearFocus()
                    if (!saving) scope.launch {
                        saving = true
                        val result = withContext(Dispatchers.IO) {
                            if (backendMode) {
                                RemoteVehicleRepository.addVehicle(
                                    plate = plate.text.toString(),
                                    make = make.text.toString(),
                                    model = model.text.toString(),
                                    color = color.text.toString(),
                                )
                            } else {
                                VehicleRepository.add_vehicle(
                                    plate = plate.text.toString(),
                                    make = make.text.toString(),
                                    model = model.text.toString(),
                                    color = color.text.toString(),
                                    vehicle_type = vehicleType,
                                )
                            }
                        }
                        saving = false
                        when (result) {
                            is VehicleResult.Success -> onSaved()
                            is VehicleResult.Failure -> sheet = when (result.error) {
                                VehicleError.INVALID_FORM -> AddVehicleSheet.INVALID_FORM
                                VehicleError.INVALID_PLATE -> AddVehicleSheet.INVALID_PLATE
                                VehicleError.DUPLICATE_PLATE -> AddVehicleSheet.DUPLICATE_PLATE
                                VehicleError.API_ERROR -> AddVehicleSheet.BACKEND_ERROR
                            }
                        }
                    }
                },
                enabled = !saving,
            )
        }
    }

    when (sheet) {
        AddVehicleSheet.INVALID_FORM -> StateBottomSheet(
            title = stringResource(R.string.state_invalid_form_title),
            message = stringResource(R.string.state_invalid_form_message),
            tag = stringResource(R.string.state_invalid_form_tag),
            primaryText = stringResource(R.string.state_action_review_form),
            onPrimary = { sheet = null },
            secondaryText = stringResource(R.string.state_action_close),
            onSecondary = { sheet = null },
            onDismissRequest = { sheet = null },
        )

        AddVehicleSheet.INVALID_PLATE -> StateBottomSheet(
            title = stringResource(R.string.state_invalid_plate_title),
            message = stringResource(R.string.state_invalid_plate_message),
            tag = stringResource(R.string.state_invalid_plate_tag),
            primaryText = stringResource(R.string.state_action_review_plate),
            onPrimary = { sheet = null },
            secondaryText = stringResource(R.string.state_action_close),
            onSecondary = { sheet = null },
            onDismissRequest = { sheet = null },
        )

        AddVehicleSheet.DUPLICATE_PLATE -> StateBottomSheet(
            title = stringResource(R.string.state_duplicate_plate_title),
            message = stringResource(R.string.state_duplicate_plate_message),
            tag = stringResource(R.string.state_duplicate_plate_tag),
            tagColor = SpotGoWarning,
            primaryText = stringResource(R.string.state_action_review_plate),
            onPrimary = { sheet = null },
            secondaryText = stringResource(R.string.vehicles_title),
            onSecondary = { sheet = null; onBack() },
            onDismissRequest = { sheet = null },
        )

        AddVehicleSheet.BACKEND_ERROR -> StateBottomSheet(
            title = stringResource(R.string.state_invalid_form_title),
            message = stringResource(R.string.vehicles_backend_error),
            primaryText = stringResource(R.string.state_action_try_again),
            onPrimary = { sheet = null },
            secondaryText = stringResource(R.string.state_action_close),
            onSecondary = { sheet = null },
            onDismissRequest = { sheet = null },
        )

        null -> Unit
    }
}

@Composable
private fun VehicleField(
    state: androidx.compose.foundation.text.input.TextFieldState,
    @androidx.annotation.StringRes label: Int,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        state = state,
        labelPosition = TextFieldLabelPosition.Attached(alwaysMinimize = true),
        label = { Text(stringResource(label)) },
        modifier = modifier.fillMaxWidth().figmaFieldBounds(),
        shape = RoundedCornerShape(16.dp),
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        colors = loginFieldColors(),
    )
}

@Composable
private fun VehicleTypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedBackground = Color(0xFFE9E5F5)
    val selectedForeground = Color(0xFF241E33)
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 42.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) selectedBackground else Color.Transparent,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = selectedForeground,
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) selectedForeground else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

@Preview(name = "12 · Add vehicle", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun AddVehiclePreview() {
    SpotGoTheme { AddVehicleScreen(onBack = {}, onSaved = {}) }
}
