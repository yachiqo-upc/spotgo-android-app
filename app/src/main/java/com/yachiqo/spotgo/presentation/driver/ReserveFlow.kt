package com.yachiqo.spotgo.presentation.driver

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yachiqo.spotgo.R
import com.yachiqo.spotgo.data.model.ParkingSpot
import com.yachiqo.spotgo.presentation.common.FigmaIcon
import com.yachiqo.spotgo.presentation.driver.components.DriverOutlineButton
import com.yachiqo.spotgo.ui.theme.SpotGoAccent
import com.yachiqo.spotgo.ui.theme.SpotGoOnAccent
import com.yachiqo.spotgo.ui.theme.SpotGoSecondaryText
import com.yachiqo.spotgo.ui.theme.SpotGoSurface
import com.yachiqo.spotgo.ui.theme.SpotGoText

/**
 * Interactive bottom sheets of the reserve flow. The wireflow
 * (driver-02) defines the "Available spot selector" and "Review and pay"
 * steps without a Figma mockup, so they follow the sheet style used by
 * the rest of the app.
 */

/** Generic single-option picker used by the reserve form fields (mockup 04). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OptionsSheet(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = SpotGoSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = SpotGoText,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(option) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        option,
                        style = MaterialTheme.typography.bodyLarge,
                        color = SpotGoText,
                        modifier = Modifier.weight(1f),
                    )
                    if (option == selected) {
                        Icon(
                            painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = SpotGoAccent,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.6.dp)
            }
        }
    }
}

/** US18 — available spot selector of the chosen zone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpotSelectorSheet(
    zoneName: String,
    spots: List<ParkingSpot>,
    selected: ParkingSpot?,
    onSelect: (ParkingSpot) -> Unit,
    onContinue: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
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
            Text(stringResource(R.string.reserve_spot_selector_title), style = MaterialTheme.typography.titleLarge, color = SpotGoText)
            Text(zoneName, style = MaterialTheme.typography.bodyMedium, color = SpotGoSecondaryText)
            Text(stringResource(R.string.reserve_spot_selector_hint), style = MaterialTheme.typography.bodySmall, color = SpotGoSecondaryText)
            if (spots.isEmpty()) {
                Text(
                    stringResource(R.string.reserve_spot_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpotGoSecondaryText,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            spots.forEach { spot ->
                val isSelected = spot.spot_id == selected?.spot_id
                Surface(
                    onClick = { onSelect(spot) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else SpotGoSurface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) SpotGoAccent else MaterialTheme.colorScheme.outline,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(spot.code, style = MaterialTheme.typography.titleMedium, color = SpotGoText, modifier = Modifier.weight(1f))
                        Text(spotSubtitle(spot), style = MaterialTheme.typography.bodySmall, color = SpotGoSecondaryText)
                        if (isSelected) {
                            Icon(
                                painterResource(R.drawable.ic_check),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = SpotGoAccent,
                            )
                        }
                    }
                }
            }
            Button(
                onClick = onContinue,
                enabled = selected != null && spots.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SpotGoAccent, contentColor = SpotGoOnAccent),
            ) {
                Text(stringResource(R.string.zone_continue), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** Vehicle selector sheet of the reserve flow (mockup 04a). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VehicleSheet(
    vehicleLabels: List<String>,
    selectedLabel: String,
    onSelect: (String) -> Unit,
    onAddVehicle: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
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
            Text(stringResource(R.string.reserve_vehicle_sheet_title), style = MaterialTheme.typography.titleLarge, color = SpotGoText)
            Text(stringResource(R.string.reserve_vehicle_sheet_hint), style = MaterialTheme.typography.bodySmall, color = SpotGoSecondaryText)
            vehicleLabels.forEach { label ->
                val isSelected = label == selectedLabel
                Surface(
                    onClick = { onSelect(label) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = SpotGoSurface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) SpotGoAccent else MaterialTheme.colorScheme.outline,
                    ),
                ) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = SpotGoText,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
            Text(selectedLabel, style = MaterialTheme.typography.bodySmall, color = SpotGoAccent)
            Text(stringResource(R.string.reserve_vehicle_selected), style = MaterialTheme.typography.labelSmall, color = SpotGoSecondaryText)
            Button(
                onClick = onAddVehicle,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SpotGoAccent, contentColor = SpotGoOnAccent),
            ) {
                Text(stringResource(R.string.reserve_add_vehicle), style = MaterialTheme.typography.bodyLarge)
            }
            DriverOutlineButton(stringResource(R.string.state_action_close), onDismissRequest)
        }
    }
}

/** US18 — review summary with the simulated digital payment (pending → approved). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReviewPaySheet(
    spotCode: String,
    vehicleLabel: String,
    dateLabel: String,
    startLabel: String,
    endLabel: String,
    zoneName: String,
    amountLabel: String,
    paymentMethodLabel: String? = null,
    paying: Boolean,
    onPay: () -> Unit,
    onChangePaymentMethod: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { if (!paying) onDismissRequest() },
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
            Text(stringResource(R.string.reserve_review_heading), style = MaterialTheme.typography.titleLarge, color = SpotGoText)
            Text(spotCode, style = MaterialTheme.typography.titleMedium, color = SpotGoText)
            Text(vehicleLabel, style = MaterialTheme.typography.bodyLarge, color = SpotGoText)
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        stringResource(R.string.reserve_review_period, dateLabel, startLabel, endLabel),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = SpotGoText,
                    )
                    Text(stringResource(R.string.reserve_review_held, zoneName),
                        style = MaterialTheme.typography.bodySmall, color = SpotGoSecondaryText)
                    Text(stringResource(R.string.reserve_review_requires_approval),
                        style = MaterialTheme.typography.bodySmall, color = SpotGoSecondaryText)
                }
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(stringResource(R.string.reserve_review_total, amountLabel),
                        style = MaterialTheme.typography.titleMedium, color = SpotGoAccent)
                    Text(paymentMethodLabel ?: stringResource(R.string.reserve_review_payment_method),
                        style = MaterialTheme.typography.bodySmall, color = SpotGoSecondaryText)
                }
            }
            if (paying) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = SpotGoAccent)
                    androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
                    Text(
                        stringResource(R.string.reserve_processing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SpotGoSecondaryText,
                    )
                }
            } else {
                Button(
                    onClick = onPay,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(top = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SpotGoAccent, contentColor = SpotGoOnAccent),
                ) {
                    Text(stringResource(R.string.reserve_confirm_payment), style = MaterialTheme.typography.bodyLarge)
                }
                DriverOutlineButton(stringResource(R.string.reserve_change_payment_method), onChangePaymentMethod)
                DriverOutlineButton(stringResource(R.string.extend_back), onDismissRequest)
            }
        }
    }
}

/** "Standard · Covered · EV" subtitle of a spot row. */
@Composable
private fun spotSubtitle(spot: ParkingSpot): String {
    val type = when (spot.spot_type) {
        "ACCESSIBLE" -> stringResource(R.string.spot_type_accessible)
        else -> stringResource(R.string.spot_type_standard)
    }
    val features = spot.features
        .split(",")
        .filter { it.isNotBlank() }
        .map { token ->
            when (token) {
                "COVERED" -> stringResource(R.string.driver_covered)
                "EV" -> stringResource(R.string.driver_ev)
                "ACCESSIBLE" -> stringResource(R.string.zone_accessible)
                else -> token.lowercase().replaceFirstChar { it.uppercase() }
            }
        }
    return if (features.isEmpty()) type else "$type · ${features.joinToString(" · ")}"
}
