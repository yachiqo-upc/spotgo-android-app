package com.yachiqo.spotgo.presentation.driver

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yachiqo.spotgo.R
import com.yachiqo.spotgo.data.model.Reservation
import com.yachiqo.spotgo.data.model.ReservationStatus
import com.yachiqo.spotgo.data.repository.ParkingRepository
import com.yachiqo.spotgo.data.repository.PaymentRepository
import com.yachiqo.spotgo.data.repository.PeruClock
import com.yachiqo.spotgo.data.repository.VehicleRepository
import com.yachiqo.spotgo.data.repository.hoursLabel
import com.yachiqo.spotgo.data.repository.formatClock
import com.yachiqo.spotgo.data.repository.isoClock
import com.yachiqo.spotgo.data.repository.isoDateLabel
import com.yachiqo.spotgo.data.repository.isoRange
import com.yachiqo.spotgo.data.repository.money
import com.yachiqo.spotgo.data.repository.moneyCompact
import com.yachiqo.spotgo.data.repository.parseTimestamp
import com.yachiqo.spotgo.presentation.common.FigmaIcon
import com.yachiqo.spotgo.presentation.driver.components.*
import com.yachiqo.spotgo.ui.theme.SpotGoAccent
import com.yachiqo.spotgo.ui.theme.SpotGoError
import com.yachiqo.spotgo.ui.theme.SpotGoSecondaryText
import com.yachiqo.spotgo.ui.theme.SpotGoSurface
import com.yachiqo.spotgo.ui.theme.SpotGoText
import com.yachiqo.spotgo.ui.theme.SpotGoTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime

/** Sheets of the reservation details screen (mockups 07 / 07a / 37 / 37a / 37b). */
private enum class DetailSheet { CANCEL, EXTEND, EXTEND_CONFIRM }

/** Chosen extension of the extend flow (mockups 37b → 37a). */
private class ExtendPlan(
    val newEnd: LocalDateTime,
    val extraAmount: Double,
    val labelRes: Int,
)

/**
 * Mockup 07 — Reservation details (US18 / US26): the virtual receipt with the
 * booking code and QR, the reservation information and the lifecycle actions
 * (edit, extend, external navigation and cancel with refund).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservationDetailsScreen(
    reservationId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onNotice: (String) -> Unit,
    onUnavailableAction: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    val reservation = remember(reservationId, revision) { ParkingRepository.reservation_by_id(reservationId) }
    val zone = reservation?.let { ParkingRepository.zone_by_id(it.zone_id) }
    val spot = reservation?.let { ParkingRepository.spot_by_id(it.spot_id) }
    val vehicle = reservation?.let { VehicleRepository.vehicle_by_id(it.vehicle_id) }
    val code = reservation?.let { ParkingRepository.booking_code(it) }
    var sheet by remember { mutableStateOf<DetailSheet?>(null) }
    var extendPlan by remember { mutableStateOf<ExtendPlan?>(null) }
    var extending by remember { mutableStateOf(false) }

    // US19 scenario 4 — a session that ended after the 5-minute tolerance
    // generates its additional charge and outstanding balance (once).
    LaunchedEffect(reservationId) { PaymentRepository.detect_overstay(reservationId) }

    if (reservation == null || zone == null) {
        // US26 scenario 3 — the destination data is gone: inform, no invalid route.
        Column(Modifier.fillMaxSize()) {
            TopAppBar(title = { Text(stringResource(R.string.details_title)) },
                windowInsets = WindowInsets(0), navigationIcon = {
                    IconButton(onClick = onBack) { FigmaIcon(R.drawable.ic_back, stringResource(R.string.details_back)) }
                })
            Text(
                stringResource(R.string.notice_destination_unavailable),
                Modifier.padding(18.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = SpotGoError,
            )
        }
        return
    }

    val statusLabelRes = when (reservation.status) {
        ReservationStatus.ACTIVE -> R.string.details_status_active
        ReservationStatus.COMPLETED, ReservationStatus.NO_SHOW, ReservationStatus.OVERSTAYED ->
            R.string.details_status_completed
        ReservationStatus.CANCELLED, ReservationStatus.PAYMENT_REJECTED -> R.string.details_status_cancelled
        else -> R.string.details_status_confirmed
    }
    val statusPositive = reservation.status == ReservationStatus.RESERVED ||
        reservation.status == ReservationStatus.ACTIVE
    val statusColor = when {
        reservation.status == ReservationStatus.CANCELLED ||
            reservation.status == ReservationStatus.PAYMENT_REJECTED -> SpotGoError
        statusPositive -> DriverAvailable
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val isUpcoming = reservation.status == ReservationStatus.RESERVED
    val isActive = reservation.status == ReservationStatus.ACTIVE
    val paidLabel = when {
        reservation.status == ReservationStatus.CANCELLED ->
            stringResource(R.string.details_refunded_visa)
        reservation.payment_ref != null -> stringResource(R.string.details_paid_visa)
        else -> stringResource(R.string.details_payment_pending)
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.details_title)) },
            windowInsets = WindowInsets(0), navigationIcon = {
                IconButton(onClick = onBack) { FigmaIcon(R.drawable.ic_back, stringResource(R.string.details_back)) }
            })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp).padding(top = 10.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {

            Text(zone.name, style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.zone_address_format, zone.location),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            // QR + booking code card.
            DriverPanel(outlined = true) {
                Row(Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Surface(Modifier.size(92.dp), color = Color.White, shape = RoundedCornerShape(12.dp)) {
                        Image(painterResource(R.drawable.booking_qr),
                            stringResource(R.string.confirmation_qr_dynamic, code ?: ""), Modifier.size(92.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(stringResource(R.string.details_booking_code),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.confirmation_booking_dynamic, code ?: ""),
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                            color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(statusLabelRes), style = MaterialTheme.typography.bodySmall, color = statusColor)
                    }
                }
            }

            Text(stringResource(R.string.details_info_title), style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp))

            DriverPanel {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val dateLabel = isoDateLabel(reservation.start_at)
                    InfoRow(stringResource(R.string.details_date), dateLabel, dateLabel)
                    InfoRow(stringResource(R.string.details_time),
                        isoRange(reservation.start_at, reservation.end_at),
                        hoursLabel(reservation.start_at, reservation.end_at))
                    InfoRow(stringResource(R.string.details_vehicle),
                        "${vehicle?.make ?: ""} ${vehicle?.model ?: ""} · ${vehicle?.plate ?: ""}".trimStart(),
                        vehicle?.plate ?: stringResource(R.string.confirmation_vehicle_value))
                    InfoRow(stringResource(R.string.details_spot),
                        stringResource(R.string.details_spot_reserved),
                        spot?.code ?: stringResource(R.string.confirmation_spot_value))
                    InfoRow(stringResource(R.string.details_total), paidLabel, money(reservation.amount))
                }
            }

            if (isUpcoming) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DriverOutlineButton(stringResource(R.string.details_edit), onEdit, Modifier.weight(1f))
                    DriverOutlineButton(stringResource(R.string.details_extend),
                        { sheet = DetailSheet.EXTEND }, Modifier.weight(1f))
                }
            }
            if (isUpcoming || isActive) {
                DriverPrimaryButton(stringResource(R.string.details_open_navigation), onClick = {
                    // US26 — external navigation: geo intent, web fallback, then inform.
                    val destination = zone.location
                    val encoded = Uri.encode(destination)
                    val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded"))
                    val web = Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/maps/search/?api=1&query=$encoded"))
                    try {
                        try {
                            context.startActivity(geo)
                        } catch (missingMaps: ActivityNotFoundException) {
                            context.startActivity(web)
                        }
                    } catch (missingBrowser: ActivityNotFoundException) {
                        onNotice(context.getString(R.string.notice_navigation_unavailable))
                    }
                })
            }
            if (isUpcoming) {
                DriverOutlineButton(stringResource(R.string.details_cancel),
                    { sheet = DetailSheet.CANCEL }, color = SpotGoError)
            }
        }
    }

    when (sheet) {
        DetailSheet.CANCEL -> CancelReservationSheet(
            code = code ?: "",
            zoneName = zone.name,
            amountLabel = money(reservation.amount),
            onConfirm = {
                ParkingRepository.cancel_reservation(reservation.reservation_id)
                revision++
                sheet = null
                onNotice(context.getString(R.string.notice_reservation_cancelled))
                onBack()
            },
            onKeep = { sheet = null },
            onDismissRequest = { sheet = null },
        )

        DetailSheet.EXTEND -> {
            val originalEnd = parseTimestamp(reservation.end_at)
            val plusOneEnd = originalEnd.plusHours(1)
            val midnightEnd = originalEnd.toLocalDate().plusDays(1).atStartOfDay()
            // User request — an extension that would end before the current
            // time of Peru (America/Lima) is not offered.
            val plusOneValid = !PeruClock.isPast(plusOneEnd)
            val midnightValid = !PeruClock.isPast(midnightEnd)
            ExtendSheet(
                currentEndLabel = isoClock(reservation.end_at),
                plusOneLabel = stringResource(R.string.extend_plus_one, clockOf(plusOneEnd)),
                plusOneEnabled = plusOneValid,
                untilMidnightEnabled = midnightValid,
                onPlusOne = {
                    extendPlan = ExtendPlan(plusOneEnd,
                        ParkingRepository.quoted_amount(zone.hourly_price), R.string.extend_plan_plus_one)
                    sheet = DetailSheet.EXTEND_CONFIRM
                },
                onUntilMidnight = {
                    val minutes = java.time.Duration.between(originalEnd, midnightEnd).toMinutes()
                    extendPlan = ExtendPlan(midnightEnd,
                        ParkingRepository.quoted_amount(zone.hourly_price * minutes / 60.0),
                        R.string.extend_plan_midnight)
                    sheet = DetailSheet.EXTEND_CONFIRM
                },
                onCancel = { sheet = null },
                onDismissRequest = { sheet = null },
            )
        }

        DetailSheet.EXTEND_CONFIRM -> extendPlan?.let { plan ->
            val newTotal = reservation.amount + plan.extraAmount
            ExtensionPaymentSheet(
                currentRange = stringResource(R.string.extend_current_range,
                    isoRange(reservation.start_at, reservation.end_at)),
                planLabel = stringResource(plan.labelRes, clockOf(plan.newEnd)),
                prices = stringResource(R.string.extend_prices,
                    moneyCompact(reservation.amount), moneyCompact(newTotal), money(plan.extraAmount)),
                spotNote = stringResource(R.string.extend_spot_stays,
                    spot?.code ?: stringResource(R.string.confirmation_spot_value)),
                amountLabel = money(plan.extraAmount),
                paying = extending,
                onPay = {
                    extending = true
                    scope.launch {
                        delay(1500) // simulated extension payment approval
                        ParkingRepository.extend_reservation(reservation.reservation_id, plan.newEnd, plan.extraAmount)
                        extending = false
                        revision++
                        sheet = null
                        onNotice(context.getString(R.string.notice_reservation_extended))
                    }
                },
                onBack = { sheet = DetailSheet.EXTEND },
                onDismissRequest = { if (!extending) sheet = null },
            )
        }

        null -> Unit
    }
}

/** "1:30 PM" from a local date-time (mockups 37 / 37a). */
private fun clockOf(dateTime: LocalDateTime): String =
    formatClock(dateTime.toLocalTime().toSecondOfDay() / 60)

@Composable
private fun InfoRow(label: String, sublabel: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(sublabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

/** Mockup 07a — cancel and refund confirmation (US18 scenario 7). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CancelReservationSheet(
    code: String,
    zoneName: String,
    amountLabel: String,
    onConfirm: () -> Unit,
    onKeep: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest, containerColor = SpotGoSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.cancel_title), style = MaterialTheme.typography.titleLarge, color = SpotGoText)
            Text(stringResource(R.string.cancel_booking_format, code, zoneName),
                style = MaterialTheme.typography.bodySmall, color = SpotGoSecondaryText)
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.cancel_refund_label, amountLabel),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = SpotGoAccent)
                    Text(stringResource(R.string.cancel_refund_message),
                        style = MaterialTheme.typography.bodySmall, color = SpotGoSecondaryText)
                }
            }
            DriverPrimaryButton(stringResource(R.string.cancel_confirm), onConfirm)
            DriverOutlineButton(stringResource(R.string.cancel_keep), onKeep)
        }
    }
}

/** Mockup 37b — extend options (US18 scenario 8). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtendSheet(
    currentEndLabel: String,
    plusOneLabel: String,
    plusOneEnabled: Boolean,
    untilMidnightEnabled: Boolean,
    onPlusOne: () -> Unit,
    onUntilMidnight: () -> Unit,
    onCancel: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest, containerColor = SpotGoSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.extend_title), style = MaterialTheme.typography.titleLarge, color = SpotGoText)
            Text(stringResource(R.string.extend_current_end, currentEndLabel),
                style = MaterialTheme.typography.bodySmall, color = SpotGoSecondaryText)
            // User request — options ending in the past (Peru time) are hidden.
            if (plusOneEnabled) DriverPrimaryButton(plusOneLabel, onPlusOne)
            if (untilMidnightEnabled)
                DriverOutlineButton(stringResource(R.string.extend_until_midnight), onUntilMidnight)
            if (!plusOneEnabled && !untilMidnightEnabled)
                Text(stringResource(R.string.extend_options_past),
                    style = MaterialTheme.typography.bodyMedium, color = SpotGoError)
            DriverOutlineButton(stringResource(R.string.extend_cancel), onCancel)
        }
    }
}

/** Mockup 37a — extension payment confirmation (US18 / US19). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtensionPaymentSheet(
    currentRange: String,
    planLabel: String,
    prices: String,
    spotNote: String,
    amountLabel: String,
    paying: Boolean,
    onPay: () -> Unit,
    onBack: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest, containerColor = SpotGoSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.extend_confirm_title), style = MaterialTheme.typography.titleLarge, color = SpotGoText)
            Text(currentRange, style = MaterialTheme.typography.bodySmall, color = SpotGoSecondaryText)
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(planLabel, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = SpotGoAccent)
                    Text(prices, style = MaterialTheme.typography.bodySmall, color = SpotGoText)
                    Text(spotNote, style = MaterialTheme.typography.bodySmall, color = SpotGoSecondaryText)
                }
            }
            if (paying) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = SpotGoAccent)
                    Spacer(Modifier.size(10.dp))
                    Text(stringResource(R.string.reserve_processing),
                        style = MaterialTheme.typography.bodyMedium, color = SpotGoSecondaryText)
                }
            } else {
                DriverPrimaryButton(stringResource(R.string.extend_pay, amountLabel), onPay)
                DriverOutlineButton(stringResource(R.string.extend_back), onBack)
            }
        }
    }
}

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun ReservationDetailsPreview() {
    SpotGoTheme { ReservationDetailsScreen("res-seed-0001", {}, {}, {}, {}) }
}
