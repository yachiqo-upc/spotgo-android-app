package com.yachiqo.spotgo.presentation.driver

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yachiqo.spotgo.R
import com.yachiqo.spotgo.data.repository.ParkingRepository
import com.yachiqo.spotgo.data.repository.PeruClock
import com.yachiqo.spotgo.data.repository.UpdateRejection
import com.yachiqo.spotgo.data.repository.ReservationUpdateResult
import com.yachiqo.spotgo.data.repository.availableStartOptions
import com.yachiqo.spotgo.data.repository.formatClock
import com.yachiqo.spotgo.data.repository.isoClock
import com.yachiqo.spotgo.data.repository.isoDateLabel
import com.yachiqo.spotgo.data.repository.money
import com.yachiqo.spotgo.data.repository.moneyCompact
import com.yachiqo.spotgo.data.repository.parseClock
import com.yachiqo.spotgo.data.repository.parseTimestamp
import com.yachiqo.spotgo.presentation.common.FigmaIcon
import com.yachiqo.spotgo.presentation.driver.components.*
import com.yachiqo.spotgo.ui.theme.SpotGoAccent
import com.yachiqo.spotgo.ui.theme.SpotGoError
import com.yachiqo.spotgo.ui.theme.SpotGoTheme
import java.time.Duration
import java.time.LocalDateTime

/** Picker sheets of the edit reservation screen. */
private enum class EditSheet { DATE, TIME, DURATION }

/**
 * Mockup 37 — Edit reservation (US18 scenario 8): change the date, start time
 * and duration of an upcoming reservation. The new assignment is validated
 * against the zone hours and the spot availability and the total is
 * recalculated before the update is confirmed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditReservationScreen(
    reservationId: String,
    onBack: () -> Unit,
    onNotice: (String) -> Unit,
) {
    val reservation = ParkingRepository.reservation_by_id(reservationId)
    val zone = reservation?.let { ParkingRepository.zone_by_id(it.zone_id) }
    val spot = reservation?.let { ParkingRepository.spot_by_id(it.spot_id) }
    if (reservation == null || zone == null || spot == null) {
        // Unavailable state: the reservation no longer exists (never happens in the demo).
        Column(Modifier.fillMaxSize()) {
            TopAppBar(title = { Text(stringResource(R.string.edit_title)) },
                windowInsets = WindowInsets(0), navigationIcon = {
                    IconButton(onClick = onBack) { FigmaIcon(R.drawable.ic_back, stringResource(R.string.edit_back)) }
                })
            Text(stringResource(R.string.notice_destination_unavailable), Modifier.padding(18.dp),
                style = MaterialTheme.typography.bodyMedium, color = SpotGoError)
        }
        return
    }

    val originalStart = isoClock(reservation.start_at)
    val originalEnd = isoClock(reservation.end_at)
    val originalHours = Duration.between(
        parseTimestamp(reservation.start_at),
        parseTimestamp(reservation.end_at),
    ).toMinutes() / 60.0

    var sheet by rememberSaveable { mutableStateOf<EditSheet?>(null) }
    var dateOption by rememberSaveable { mutableStateOf(isoDateLabel(reservation.start_at)) }
    // Keep the original start time only while it is still bookable (Peru time).
    var startTime by rememberSaveable {
        val validTimes = availableStartOptions(isoDateLabel(reservation.start_at))
        mutableStateOf(originalStart.takeIf { it in validTimes } ?: (validTimes.firstOrNull() ?: originalStart))
    }
    var durationHours by rememberSaveable { mutableStateOf(originalHours) }
    var forcedError by rememberSaveable { mutableStateOf<UpdateRejection?>(null) }

    // Strings resolved while still in composable context.
    val duration1 = stringResource(R.string.duration_1)
    val duration2 = stringResource(R.string.duration_2)
    val duration3 = stringResource(R.string.duration_3)
    val duration4 = stringResource(R.string.duration_4)
    val hoursTemplate = stringResource(R.string.edit_hours_format)
    val updatedNotice = stringResource(R.string.notice_reservation_updated)
    fun durationText(hours: Double): String = when (hours) {
        1.0 -> duration1
        2.0 -> duration2
        3.0 -> duration3
        4.0 -> duration4
        else -> hoursTemplate.replace("%1\$s", formatHours(hours))
    }

    val durationOptions = (listOf(originalHours) + listOf(1.0, 2.0, 3.0, 4.0)).distinct()
    val durationValue = durationText(durationHours)

    val newStart = ParkingRepository.resolve_start(dateOption, startTime)
    val newEnd = newStart.plusMinutes((durationHours * 60).toLong())
    val newAmount = ParkingRepository.quoted_amount(zone.hourly_price * durationHours)
    val closingMinutes = parseClock(zone.open_until)
    val beyondClosing = newEnd.toLocalTime().toSecondOfDay() / 60 > closingMinutes
    val spotAvailable = ParkingRepository.is_spot_available_excluding(spot, reservation.reservation_id)
    // User request — the new slot must be in the future (Peru time).
    val startInPast = PeruClock.isPast(newStart)

    val rejection = forcedError ?: when {
        startInPast -> UpdateRejection.PAST_TIME
        beyondClosing -> UpdateRejection.BEYOND_CLOSING
        !spotAvailable -> UpdateRejection.SPOT_UNAVAILABLE
        else -> null
    }
    val errorMessage = when (rejection) {
        UpdateRejection.BEYOND_CLOSING -> stringResource(R.string.edit_error_closing, zone.open_until)
        UpdateRejection.SPOT_UNAVAILABLE -> stringResource(R.string.edit_error_spot)
        UpdateRejection.PAST_TIME -> stringResource(R.string.edit_error_past_time)
        else -> null
    }

    // Extension block (mockup 37): duration delta + new end time + price arrow.
    val delta = durationHours - originalHours
    val deltaText = if (delta % 1 == 0.0) delta.toInt().toString() else delta.toString()
    val extensionLine = when {
        delta == 0.0 -> stringResource(R.string.edit_extension_none)
        delta > 0.0 && delta == 1.0 -> stringResource(R.string.edit_extension_positive, deltaText)
        delta > 0.0 -> stringResource(R.string.edit_extension_positive_plural, deltaText)
        else -> stringResource(R.string.edit_extension_negative, deltaText.replace("-", ""))
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.edit_title)) },
            windowInsets = WindowInsets(0), navigationIcon = {
                IconButton(onClick = onBack) { FigmaIcon(R.drawable.ic_back, stringResource(R.string.edit_back)) }
            })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp).padding(top = 10.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)) {

            // Summary card (mockup 37).
            DriverPanel(outlined = true) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(zone.name, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.edit_summary_format, dateOption, originalStart, originalEnd, spot.code),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.edit_current_total, money(reservation.amount)),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = SpotGoAccent)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickerField(stringResource(R.string.zone_date), dateOption,
                    Modifier.weight(1f), onClick = { sheet = EditSheet.DATE })
                PickerField(stringResource(R.string.zone_start_time), startTime,
                    Modifier.weight(1f), onClick = { sheet = EditSheet.TIME })
            }
            PickerField(stringResource(R.string.zone_duration), durationValue,
                onClick = { sheet = EditSheet.DURATION })

            // Current spot (read-only: the spot is not editable, mockup 37).
            OutlinedTextField(
                value = stringResource(R.string.edit_current_spot_value, spot.code),
                onValueChange = {}, readOnly = true, singleLine = true,
                label = { Text(stringResource(R.string.edit_current_spot)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer),
            )

            // Extension / price delta block (mockup 37).
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(extensionLine, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text(stringResource(R.string.edit_new_end, formatClock(newEnd.toLocalTime().toSecondOfDay() / 60)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${moneyCompact(reservation.amount)} → ${moneyCompact(newAmount)}",
                        style = MaterialTheme.typography.titleMedium, color = SpotGoAccent)
                }
            }

            Text(stringResource(R.string.edit_note), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (errorMessage != null) {
                Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = SpotGoError)
            }

            DriverPrimaryButton(stringResource(R.string.edit_update), onClick = {
                when (val result = ParkingRepository.update_reservation(
                    reservation_id = reservation.reservation_id,
                    date_label = dateOption,
                    start_label = startTime,
                    duration_hours = durationHours.toInt(),
                )) {
                    is ReservationUpdateResult.Updated -> {
                        onNotice(updatedNotice)
                        onBack()
                    }
                    is ReservationUpdateResult.Rejected -> forcedError = result.reason
                }
            })
        }
    }

    when (sheet) {
        EditSheet.DATE -> OptionsSheet(
            title = stringResource(R.string.zone_date),
            options = listOf(stringResource(R.string.zone_today), stringResource(R.string.zone_tomorrow)),
            selected = dateOption,
            onSelect = { label ->
                dateOption = label
                // A date change can leave the start time in the past.
                val validTimes = availableStartOptions(label)
                if (startTime !in validTimes) startTime = validTimes.firstOrNull() ?: startTime
                forcedError = null
                sheet = null
            },
            onDismissRequest = { sheet = null },
        )

        EditSheet.TIME -> OptionsSheet(
            title = stringResource(R.string.zone_start_time),
            options = availableStartOptions(dateOption),
            selected = startTime,
            onSelect = { startTime = it; forcedError = null; sheet = null },
            onDismissRequest = { sheet = null },
        )

        EditSheet.DURATION -> OptionsSheet(
            title = stringResource(R.string.zone_duration),
            options = durationOptions.map { durationText(it) },
            selected = durationValue,
            onSelect = { label ->
                durationHours = durationOptions.firstOrNull { durationText(it) == label } ?: durationHours
                forcedError = null
                sheet = null
            },
            onDismissRequest = { sheet = null },
        )

        null -> Unit
    }
}

/** "13.5" hour formatting for non-integer durations (UI-only). */
private fun formatHours(hours: Double): String =
    if (hours % 1 == 0.0) hours.toInt().toString() else hours.toString()

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun EditReservationPreview() {
    SpotGoTheme { EditReservationScreen("res-seed-0001", {}, {}) }
}
