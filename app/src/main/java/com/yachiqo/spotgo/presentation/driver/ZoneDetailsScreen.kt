package com.yachiqo.spotgo.presentation.driver

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yachiqo.spotgo.R
import com.yachiqo.spotgo.data.model.ParkingSpot
import com.yachiqo.spotgo.data.model.ParkingZone
import com.yachiqo.spotgo.data.repository.FavoriteRepository
import com.yachiqo.spotgo.data.repository.ParkingRepository
import com.yachiqo.spotgo.data.repository.PaymentRepository
import com.yachiqo.spotgo.data.repository.PeruClock
import com.yachiqo.spotgo.data.repository.ReservationError
import com.yachiqo.spotgo.data.repository.ReservationResult
import com.yachiqo.spotgo.data.repository.START_TIME_OPTIONS
import com.yachiqo.spotgo.data.repository.VehicleRepository
import com.yachiqo.spotgo.data.repository.availableStartOptions
import com.yachiqo.spotgo.data.repository.formatClock
import com.yachiqo.spotgo.data.repository.money
import com.yachiqo.spotgo.data.repository.parseClock
import com.yachiqo.spotgo.presentation.common.FigmaIcon
import com.yachiqo.spotgo.presentation.common.StateBottomSheet
import com.yachiqo.spotgo.presentation.driver.components.*
import com.yachiqo.spotgo.ui.theme.SpotGoTheme
import com.yachiqo.spotgo.ui.theme.SpotGoWarning
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Sheets of the zone-details reserve flow (mockup 04 + wireflow driver-02). */
private enum class ZoneSheet {
    VEHICLE, DATE, TIME, DURATION, SPOTS, REVIEW,
    NO_VEHICLE, ZONE_FULL, VEHICLE_BUSY, SPOT_TAKEN, OUTSTANDING_BALANCE, PAST_TIME,
}

/**
 * Mockup 04 — Zone details and reserve (US04 / US18): live availability of
 * the zone, vehicle selection from the registered vehicles, date / start
 * time / duration with the estimated total, then the spot selector,
 * review & pay and the simulated payment confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneDetailsScreen(
    zoneId: String,
    onBack: () -> Unit,
    onPaid: () -> Unit,
    onMyVehicles: () -> Unit,
    onOpenBalance: () -> Unit,
    onUnavailableAction: () -> Unit,
) {
    val zone = ParkingRepository.zone_by_id(zoneId) ?: ParkingRepository.zones().first()
    val availableCount = ParkingRepository.available_count(zone.zone_id)
    val vehicles = remember { VehicleRepository.vehicles_for_current_profile() }
    var vehicleId by rememberSaveable { mutableStateOf(VehicleRepository.default_vehicle()?.vehicle_id ?: "") }
    // User request — no reservations in the past (Peru time): when today has
    // no slot left the form starts on "Tomorrow" with a valid start time.
    var dateOption by rememberSaveable {
        mutableStateOf(if (availableStartOptions("Today").isEmpty()) "Tomorrow" else "Today")
    }
    var startTime by rememberSaveable {
        mutableStateOf(availableStartOptions("Today").firstOrNull() ?: START_TIME_OPTIONS.first())
    }
    var durationHours by rememberSaveable { mutableStateOf(2) }
    var sheet by remember { mutableStateOf<ZoneSheet?>(null) }
    var selectedSpot by remember { mutableStateOf<ParkingSpot?>(null) }
    var paying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val vehicle = vehicles.firstOrNull { it.vehicle_id == vehicleId }
    val vehicleOptions = vehicles.map { it to stringResource(R.string.vehicles_row_title, it.make, it.model, it.plate) }
    val vehicleLabel = vehicleOptions.firstOrNull { it.first.vehicle_id == vehicleId }?.second
        ?: stringResource(R.string.reserve_no_vehicle_field)
    val durationOptions = listOf(
        stringResource(R.string.duration_1), stringResource(R.string.duration_2),
        stringResource(R.string.duration_3), stringResource(R.string.duration_4),
    )
    val durationLabel = when (durationHours) {
        1 -> durationOptions[0]
        3 -> durationOptions[2]
        4 -> durationOptions[3]
        else -> durationOptions[1]
    }
    val grossTotal = zone.hourly_price * durationHours
    // US20 — the plan discount (Free 0% · Plus 10/15%) applies to the quote.
    val total = ParkingRepository.quoted_amount(grossTotal)
    val planDiscount = PaymentRepository.subscription_discount_percent()
    val startMinutes = parseClock(startTime)

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.zone_title)) },
            windowInsets = WindowInsets(0), navigationIcon = {
                IconButton(onClick = onBack) { FigmaIcon(R.drawable.ic_back, stringResource(R.string.zone_back)) }
            })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)
            .padding(top = 14.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            ZoneMap(zone, onUnavailableAction)
            Row(Modifier.fillMaxWidth().heightIn(min = 42.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(zone.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                if (availableCount > 0) StatusBadge(stringResource(R.string.driver_available))
                else StatusBadge(stringResource(R.string.explore_no_spots), positive = false)
            }
            Text(stringResource(R.string.zone_address_format, zone.location),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().heightIn(min = 78.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ZoneStat(if (availableCount > 0) availableCount.toString() else "0",
                    stringResource(R.string.zone_available_spots),
                    if (availableCount > 0) DriverAvailable else SpotGoWarning, Modifier.weight(1f))
                ZoneStat(money(zone.hourly_price), stringResource(R.string.driver_per_hour),
                    MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                ZoneStat(zone.open_until, stringResource(R.string.zone_open_until),
                    MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).height(40.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(R.string.driver_covered to "COVERED", R.string.driver_ev to "EV",
                    R.string.zone_accessible to "ACCESSIBLE").forEach { (label, token) ->
                    if (zone.amenities.contains(token)) {
                        FilterChip(false, {}, { Text(stringResource(label)) })
                    }
                }
            }
            DriverSectionTitle(stringResource(R.string.zone_reserve_title))

            PickerField(
                label = stringResource(R.string.zone_vehicle),
                value = vehicleLabel,
                onClick = { sheet = ZoneSheet.VEHICLE },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickerField(stringResource(R.string.zone_date), dateOption,
                    Modifier.weight(1f), onClick = { sheet = ZoneSheet.DATE })
                PickerField(stringResource(R.string.zone_start_time), startTime,
                    Modifier.weight(1f), onClick = { sheet = ZoneSheet.TIME })
            }
            PickerField(stringResource(R.string.zone_duration), durationLabel,
                onClick = { sheet = ZoneSheet.DURATION })

            if (planDiscount > 0) {
                Row(Modifier.fillMaxWidth().heightIn(min = 34.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.zone_plan_discount, planDiscount), Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("-${money(grossTotal - total)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 38.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.zone_total), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(money(total), style = MaterialTheme.typography.titleLarge)
            }

            if (availableCount == 0) {
                Text(
                    stringResource(R.string.reserve_zone_full_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpotGoWarning,
                )
            }

            DriverPrimaryButton(
                label = stringResource(R.string.zone_continue),
                onClick = {
                    when {
                        vehicles.isEmpty() -> sheet = ZoneSheet.NO_VEHICLE
                        availableCount == 0 -> sheet = ZoneSheet.ZONE_FULL
                        // US19 scenario 6 — an open balance blocks new reservations.
                        PaymentRepository.has_open_balance() -> sheet = ZoneSheet.OUTSTANDING_BALANCE
                        // User request — the slot must still be in the future (Peru time).
                        PeruClock.isPast(ParkingRepository.resolve_start(dateOption, startTime)) ->
                            sheet = ZoneSheet.PAST_TIME
                        else -> {
                            selectedSpot = null
                            sheet = ZoneSheet.SPOTS
                        }
                    }
                },
            )
        }
    }

    when (sheet) {
        ZoneSheet.VEHICLE -> VehicleSheet(
            vehicleLabels = vehicleOptions.map { it.second },
            selectedLabel = vehicleLabel,
            onSelect = { label ->
                vehicleId = vehicleOptions.firstOrNull { it.second == label }?.first?.vehicle_id ?: vehicleId
            },
            onAddVehicle = { sheet = null; onMyVehicles() },
            onDismissRequest = { sheet = null },
        )

        ZoneSheet.DATE -> OptionsSheet(
            title = stringResource(R.string.zone_date),
            options = listOf(stringResource(R.string.zone_today), stringResource(R.string.zone_tomorrow)),
            selected = dateOption,
            onSelect = { label ->
                dateOption = label
                // A date change can leave the start time in the past.
                val validTimes = availableStartOptions(label)
                if (startTime !in validTimes) startTime = validTimes.firstOrNull() ?: startTime
                sheet = null
            },
            onDismissRequest = { sheet = null },
        )

        ZoneSheet.TIME -> OptionsSheet(
            title = stringResource(R.string.zone_start_time),
            options = availableStartOptions(dateOption),
            selected = startTime,
            onSelect = { startTime = it; sheet = null },
            onDismissRequest = { sheet = null },
        )

        ZoneSheet.DURATION -> OptionsSheet(
            title = stringResource(R.string.zone_duration),
            options = durationOptions,
            selected = durationLabel,
            onSelect = { label ->
                durationHours = when (label) {
                    durationOptions[0] -> 1
                    durationOptions[2] -> 3
                    durationOptions[3] -> 4
                    else -> 2
                }
                sheet = null
            },
            onDismissRequest = { sheet = null },
        )

        ZoneSheet.SPOTS -> SpotSelectorSheet(
            zoneName = zone.name,
            spots = ParkingRepository.available_spots(zone.zone_id),
            selected = selectedSpot,
            onSelect = { selectedSpot = it },
            onContinue = { sheet = ZoneSheet.REVIEW },
            onDismissRequest = { sheet = null },
        )

        ZoneSheet.REVIEW -> ReviewPaySheet(
            spotCode = selectedSpot?.code ?: "",
            vehicleLabel = vehicleLabel,
            dateLabel = dateOption,
            startLabel = formatClock(startMinutes),
            endLabel = formatClock(startMinutes + durationHours * 60),
            zoneName = zone.name,
            amountLabel = money(total),
            paying = paying,
            onPay = {
                val spot = selectedSpot
                if (spot != null) {
                    paying = true
                    scope.launch {
                        when (val result = ParkingRepository.create_reservation(
                            zone = zone, spot = spot, vehicle = vehicle,
                            date_label = dateOption, start_label = startTime, duration_hours = durationHours,
                        )) {
                            is ReservationResult.Failure -> {
                                paying = false
                                sheet = when (result.error) {
                                    ReservationError.VEHICLE_REQUIRED -> ZoneSheet.NO_VEHICLE
                                    ReservationError.VEHICLE_BUSY -> ZoneSheet.VEHICLE_BUSY
                                    ReservationError.SPOT_TAKEN -> ZoneSheet.SPOT_TAKEN
                                    ReservationError.OUTSTANDING_BALANCE -> ZoneSheet.OUTSTANDING_BALANCE
                                    // The selected slot is already in the past (Peru time).
                                    ReservationError.PAST_TIME -> ZoneSheet.PAST_TIME
                                }
                            }
                            is ReservationResult.Success -> {
                                delay(1500) // simulated digital payment approval
                                ParkingRepository.approve_payment(result.reservation.reservation_id)
                                paying = false
                                onPaid()
                            }
                        }
                    }
                }
            },
            onChangePaymentMethod = onUnavailableAction,
            onDismissRequest = { sheet = null },
        )

        ZoneSheet.NO_VEHICLE -> StateBottomSheet(
            title = stringResource(R.string.reserve_no_vehicle_title),
            message = stringResource(R.string.reserve_no_vehicle_message),
            primaryText = stringResource(R.string.reserve_no_vehicle_action),
            onPrimary = { sheet = null; onMyVehicles() },
            secondaryText = stringResource(R.string.state_action_close),
            onSecondary = { sheet = null },
            onDismissRequest = { sheet = null },
        )

        ZoneSheet.ZONE_FULL -> StateBottomSheet(
            title = stringResource(R.string.reserve_spot_none),
            message = stringResource(R.string.reserve_zone_full_message),
            primaryText = stringResource(R.string.state_action_close),
            onPrimary = { sheet = null },
            secondaryText = stringResource(R.string.zone_title),
            onSecondary = { sheet = null; onBack() },
            onDismissRequest = { sheet = null },
        )

        ZoneSheet.VEHICLE_BUSY -> StateBottomSheet(
            title = stringResource(R.string.reserve_vehicle_busy_title),
            message = stringResource(R.string.reserve_vehicle_busy_message),
            primaryText = stringResource(R.string.state_action_review_form),
            onPrimary = { sheet = null },
            secondaryText = stringResource(R.string.state_action_close),
            onSecondary = { sheet = null },
            onDismissRequest = { sheet = null },
        )

        ZoneSheet.SPOT_TAKEN -> StateBottomSheet(
            title = stringResource(R.string.reserve_spot_taken_title),
            message = stringResource(R.string.reserve_spot_taken_message),
            primaryText = stringResource(R.string.state_action_review_form),
            onPrimary = { sheet = null; selectedSpot = null },
            secondaryText = stringResource(R.string.state_action_close),
            onSecondary = { sheet = null },
            onDismissRequest = { sheet = null },
        )

        // US19 scenario 6 — the outstanding balance blocks the reservation.
        ZoneSheet.OUTSTANDING_BALANCE -> StateBottomSheet(
            title = stringResource(R.string.reserve_balance_blocked_title),
            message = stringResource(R.string.reserve_balance_blocked_message),
            primaryText = stringResource(R.string.reserve_balance_blocked_action),
            onPrimary = { sheet = null; onOpenBalance() },
            secondaryText = stringResource(R.string.state_action_close),
            onSecondary = { sheet = null },
            onDismissRequest = { sheet = null },
        )

        // User request — the chosen date / time is already in the past (Peru).
        ZoneSheet.PAST_TIME -> StateBottomSheet(
            title = stringResource(R.string.reserve_past_time_title),
            message = stringResource(R.string.reserve_past_time_message),
            primaryText = stringResource(R.string.reserve_past_time_action),
            onPrimary = { sheet = ZoneSheet.DATE },
            secondaryText = stringResource(R.string.state_action_close),
            onSecondary = { sheet = null },
            onDismissRequest = { sheet = null },
        )

        null -> Unit
    }
}

/** Read-only field that opens an options bottom sheet on tap (mockup 04). */
@Composable
internal fun PickerField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(modifier) {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true, singleLine = true,
            label = { Text(label) },
            trailingIcon = { Text(stringResource(R.string.zone_dropdown), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer),
        )
        Box(Modifier.matchParentSize().clickable(onClick = onClick))
    }
}

@Composable
private fun ZoneMap(zone: ParkingZone, onUnavailableAction: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = DriverMapBackground,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))) {
            val scale = maxWidth / 376.dp
            Box(Modifier.fillMaxWidth().height(206.dp * scale)) {
                ParkingMapBase()
                Surface(Modifier.offset(152.dp * scale, 68.dp * scale).size(155.dp, 36.dp),
                    color = DriverAvailable, shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.explore_parking_marker), style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary)
                        Text(zone.name.removeSuffix(" Parking"), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary, maxLines = 1)
                    }
                }
                // Heart saves the parking: it appears on the Saved chip.
                FavoriteButton(FavoriteRepository.is_saved(zone.zone_id),
                    onClick = { FavoriteRepository.toggle(zone.zone_id) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 14.dp))
                Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 13.dp, vertical = 16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.padding(start = 10.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.zone_map_distance, zone.distance_km.toString()),
                            Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onUnavailableAction, contentPadding = PaddingValues(horizontal = 10.dp)) {
                            Text(stringResource(R.string.zone_directions), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoneStat(value: String, label: String, color: Color, modifier: Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), color = color, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun ZoneDetailsPreview() {
    SpotGoTheme {
        ZoneDetailsScreen(zoneId = "zne-seed-0003", onBack = {}, onPaid = {}, onMyVehicles = {},
            onOpenBalance = {}, onUnavailableAction = {})
    }
}
