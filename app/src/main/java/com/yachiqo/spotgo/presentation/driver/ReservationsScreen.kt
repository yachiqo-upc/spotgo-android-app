package com.yachiqo.spotgo.presentation.driver

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yachiqo.spotgo.R
import com.yachiqo.spotgo.data.model.Reservation
import com.yachiqo.spotgo.data.model.ReservationStatus
import com.yachiqo.spotgo.data.repository.ParkingRepository
import com.yachiqo.spotgo.data.repository.VehicleRepository
import com.yachiqo.spotgo.data.repository.isoDateLabel
import com.yachiqo.spotgo.data.repository.isoRange
import com.yachiqo.spotgo.data.repository.money
import com.yachiqo.spotgo.presentation.driver.components.*
import com.yachiqo.spotgo.ui.theme.SpotGoTheme

/** Statuses shown by each tab of My reservations (mockup 06). */
private val TAB_STATUSES = listOf(
    listOf(ReservationStatus.PENDING_PAYMENT, ReservationStatus.RESERVED, ReservationStatus.ACTIVE),
    listOf(ReservationStatus.COMPLETED, ReservationStatus.NO_SHOW, ReservationStatus.OVERSTAYED),
    listOf(ReservationStatus.CANCELLED, ReservationStatus.PAYMENT_REJECTED),
)

/**
 * Mockup 06 — My reservations (US18): the reservation history of the signed-in
 * Driver grouped by lifecycle status, with the navigation hint before you go.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservationsScreen(
    onProfile: () -> Unit,
    onOpenReservation: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Read on every recomposition so a reservation created in the reserve flow shows up.
    val reservations = ParkingRepository.reservations_for_current_profile()
    val statuses = TAB_STATUSES.getOrElse(tab) { TAB_STATUSES[0] }
    val filtered = reservations.filter { it.status in statuses }
    // Upcoming: soonest first. History: most recent first.
    val visible = if (tab == 0) filtered.sortedBy { it.start_at }
    else filtered.sortedByDescending { it.start_at }

    DriverPage(stringResource(R.string.reservations_title), stringResource(R.string.reservations_subtitle), onProfile) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(R.string.reservations_upcoming, R.string.reservations_completed, R.string.reservations_cancelled)
                .forEachIndexed { index, label ->
                    SegmentedButton(selected = index == tab, onClick = { tab = index },
                        icon = {}, shape = SegmentedButtonDefaults.itemShape(index, 3)) {
                        Text(stringResource(label), style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, softWrap = false)
                    }
                }
        }

        if (visible.isEmpty()) {
            DriverPanel {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(stringResource(R.string.reservations_empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.reservations_empty_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        visible.forEach { reservation ->
            ReservationCard(reservation, onViewDetails = { onOpenReservation(reservation.reservation_id) })
        }

        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(stringResource(R.string.reservations_before_you_go), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.reservations_navigation_hint), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ReservationCard(reservation: Reservation, onViewDetails: () -> Unit) {
    val zone = ParkingRepository.zone_by_id(reservation.zone_id)
    val plate = VehicleRepository.vehicle_by_id(reservation.vehicle_id)?.plate
        ?: stringResource(R.string.confirmation_vehicle_value)
    val statusLabel: String
    val positive: Boolean
    when (reservation.status) {
        ReservationStatus.RESERVED -> { statusLabel = stringResource(R.string.reservations_confirmed); positive = true }
        ReservationStatus.ACTIVE -> { statusLabel = stringResource(R.string.reservations_status_active); positive = true }
        ReservationStatus.PENDING_PAYMENT -> { statusLabel = stringResource(R.string.reservations_upcoming); positive = false }
        ReservationStatus.COMPLETED,
        ReservationStatus.NO_SHOW,
        ReservationStatus.OVERSTAYED -> { statusLabel = stringResource(R.string.reservations_completed); positive = false }
        else -> { statusLabel = stringResource(R.string.reservations_cancelled); positive = false }
    }

    DriverPanel(outlined = true) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 46.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(zone?.name ?: stringResource(R.string.driver_parking_name), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.driver_location), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusBadge(statusLabel, positive = positive)
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(isoDateLabel(reservation.start_at), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                    Text(isoRange(reservation.start_at, reservation.end_at), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(money(reservation.amount), style = MaterialTheme.typography.labelLarge)
                    Text(plate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = onViewDetails, shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)) {
                    Text(stringResource(R.string.reservations_view_details))
                }
            }
        }
    }
}

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun ReservationsPreview() {
    SpotGoTheme { ReservationsScreen({}, {}) }
}
