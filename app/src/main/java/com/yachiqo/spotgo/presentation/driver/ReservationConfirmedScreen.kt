package com.yachiqo.spotgo.presentation.driver

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yachiqo.spotgo.R
import com.yachiqo.spotgo.data.model.Reservation
import com.yachiqo.spotgo.data.repository.RemoteBillingRepository
import com.yachiqo.spotgo.data.repository.RemoteParkingRepository
import com.yachiqo.spotgo.data.repository.RemoteReservationRepository
import com.yachiqo.spotgo.data.repository.RemoteReservationResult
import com.yachiqo.spotgo.data.repository.RemoteVehicleRepository
import com.yachiqo.spotgo.data.repository.isoDateLabel
import com.yachiqo.spotgo.data.repository.isoRange
import com.yachiqo.spotgo.data.repository.money
import com.yachiqo.spotgo.presentation.driver.components.*
import com.yachiqo.spotgo.ui.theme.SpotGoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ReservationConfirmedScreen(
    reservationId: String,
    onMyReservations: () -> Unit,
    onUnavailableAction: () -> Unit,
) {
    val context = LocalContext.current
    var reservation by remember(reservationId) { mutableStateOf<Reservation?>(null) }
    var paymentStatus by remember(reservationId) { mutableStateOf("") }
    var loading by remember(reservationId) { mutableStateOf(true) }
    LaunchedEffect(reservationId) {
        loading = true
        val loaded = withContext(Dispatchers.IO) {
            RemoteParkingRepository.loadFacilities()
            RemoteVehicleRepository.loadVehicles()
            val result = RemoteReservationRepository.loadReservation(reservationId)
            val current = (result as? RemoteReservationResult.Success)?.reservation
            val payment = current?.payment_ref?.let { RemoteBillingRepository.payment(it) }
            current to payment
        }
        reservation = loaded.first
        paymentStatus = (loaded.second as? com.yachiqo.spotgo.data.repository.RemotePaymentResult.Success)?.status.orEmpty()
        loading = false
    }

    val current = reservation
    val zone = current?.let { RemoteParkingRepository.facilityForZone(it.zone_id)
        ?.zones?.firstOrNull { candidate -> candidate.zone_id == it.zone_id } }
    val spot = current?.let { RemoteParkingRepository.spotById(it.spot_id) }
    val vehicle = current?.let { RemoteVehicleRepository.vehicleById(it.vehicle_id) }
    val paymentApproved = paymentStatus == "APPROVED" && current?.status != "PENDING_PAYMENT"

    DriverPage(stringResource(R.string.confirmation_title)) {
        when {
            loading -> DriverPanel {
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            current == null -> DriverPanel {
                Text(stringResource(R.string.confirmation_load_error), Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            else -> {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(Modifier.size(44.dp), shape = RoundedCornerShape(22.dp),
                        color = if (paymentApproved) DriverAvailable else MaterialTheme.colorScheme.tertiaryContainer) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(if (paymentApproved) stringResource(R.string.confirmation_check) else "!",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 25.sp))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(if (paymentApproved) R.string.confirmation_success else R.string.confirmation_payment_pending),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp))
                        if (!paymentApproved) Text(
                            stringResource(R.string.confirmation_payment_status, paymentStatus.ifBlank { current.status }),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                DriverPanel(outlined = true) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(stringResource(R.string.confirmation_booking_code), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(current.reservation_id, style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary)
                        ReceiptRow(stringResource(R.string.confirmation_parking_zone), zone?.name
                            ?: stringResource(R.string.reservations_location_unavailable))
                        ReceiptRow(stringResource(R.string.confirmation_spot), spot?.code
                            ?: stringResource(R.string.reservations_location_unavailable))
                        ReceiptRow(stringResource(R.string.zone_date), isoDateLabel(current.start_at))
                        ReceiptRow(stringResource(R.string.confirmation_time), isoRange(current.start_at, current.end_at))
                        ReceiptRow(stringResource(R.string.zone_vehicle), vehicle?.plate
                            ?: stringResource(R.string.reservations_vehicle_unavailable))
                        ReceiptRow(stringResource(R.string.confirmation_amount_paid), money(current.amount))
                    }
                }
                if (paymentApproved) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(stringResource(R.string.confirmation_informational_pass),
                                style = MaterialTheme.typography.titleSmall)
                            Text(current.reservation_id, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.confirmation_no_qr_available),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    DriverPrimaryButton(
                        label = stringResource(R.string.confirmation_open_navigation),
                        enabled = zone != null,
                        onClick = { zone?.let { context.openParkingNavigation(it, onUnavailableAction) } },
                    )
                }
                DriverOutlineButton(stringResource(R.string.confirmation_my_reservations), onMyReservations)
            }
        }
    }
}

@Composable
private fun ReceiptRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 31.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun ReservationConfirmedPreview() {
    SpotGoTheme { ReservationConfirmedScreen("", {}, {}) }
}
