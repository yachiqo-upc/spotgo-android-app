package com.yachiqo.spotgo.presentation.driver

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yachiqo.spotgo.R
import com.yachiqo.spotgo.data.model.ParkingSpot
import com.yachiqo.spotgo.data.model.Reservation
import com.yachiqo.spotgo.data.repository.ParkingCatalogResult
import com.yachiqo.spotgo.data.repository.RemoteParkingRepository
import com.yachiqo.spotgo.data.repository.isoClock
import com.yachiqo.spotgo.data.repository.isoDateLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

private data class ExtensionAvailability(
    val sameSpotAvailable: Boolean,
    val alternatives: List<ParkingSpot>,
    val zoneNames: Map<String, String>,
)

@Composable
internal fun ReservationExtensionDialog(
    reservation: Reservation,
    onDismiss: () -> Unit,
    onContinue: (endAt: String, spotId: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lima = remember { ZoneId.of("America/Lima") }
    val currentEnd = remember(reservation.reservation_id) { Instant.parse(reservation.end_at) }
    val defaultEnd = remember(reservation.reservation_id) {
        currentEnd.atZone(lima).plusHours(1).withSecond(0).withNano(0)
    }
    var endDate by rememberSaveable(reservation.reservation_id) {
        mutableStateOf(defaultEnd.toLocalDate().toString())
    }
    var endTime by rememberSaveable(reservation.reservation_id) {
        mutableStateOf(defaultEnd.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm", Locale.US)))
    }
    var checking by remember { mutableStateOf(false) }
    var availabilityChecked by remember { mutableStateOf(false) }
    var sameSpotAvailable by remember { mutableStateOf(false) }
    var alternatives by remember { mutableStateOf(emptyList<ParkingSpot>()) }
    var zoneNames by remember { mutableStateOf(emptyMap<String, String>()) }
    var selectedSpotId by remember { mutableStateOf("") }
    var availabilityFailed by remember { mutableStateOf(false) }

    val selectedEnd = remember(endDate, endTime) {
        runCatching {
            LocalDate.parse(endDate).atTime(LocalTime.parse(endTime)).atZone(lima).toInstant()
        }.getOrNull()
    }
    val validEnd = selectedEnd?.isAfter(currentEnd) == true
    val endDateLabel = remember(endDate) {
        runCatching {
            LocalDate.parse(endDate).format(DateTimeFormatter.ofPattern(if (Locale.getDefault().language == "es") "d MMM yyyy" else "MMM d, yyyy", Locale.getDefault()))
        }.getOrDefault(endDate)
    }

    fun clearAvailability() {
        availabilityChecked = false
        sameSpotAvailable = false
        alternatives = emptyList()
        zoneNames = emptyMap()
        selectedSpotId = ""
        availabilityFailed = false
    }

    fun chooseDate() {
        val date = LocalDate.parse(endDate)
        val minimum = currentEnd.atZone(lima).toLocalDate()
        val minDate = Calendar.getInstance().apply {
            set(Calendar.YEAR, minimum.year)
            set(Calendar.MONTH, minimum.monthValue - 1)
            set(Calendar.DAY_OF_MONTH, minimum.dayOfMonth)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        DatePickerDialog(
            context,
            { _, year, month, day ->
                endDate = LocalDate.of(year, month + 1, day).toString()
                clearAvailability()
            },
            date.year,
            date.monthValue - 1,
            date.dayOfMonth,
        ).apply { datePicker.minDate = minDate }.show()
    }

    fun chooseTime() {
        val time = LocalTime.parse(endTime)
        TimePickerDialog(
            context,
            { _, hour, minute ->
                endTime = LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))
                clearAvailability()
            },
            time.hour,
            time.minute,
            true,
        ).show()
    }

    suspend fun checkAvailability() {
        val endAt = selectedEnd?.toString() ?: return
        checking = true
        availabilityFailed = false
        val result = withContext(Dispatchers.IO) {
            if (RemoteParkingRepository.loadFacilities() !is ParkingCatalogResult.Success) return@withContext null
            val facility = RemoteParkingRepository.facilityForZone(reservation.zone_id) ?: return@withContext null
            val available = mutableListOf<ParkingSpot>()
            for (zone in facility.zones) {
                val rows = RemoteParkingRepository.availableSpots(zone.zone_id, reservation.end_at, endAt)
                    ?: return@withContext null
                available += rows
            }
            ExtensionAvailability(
                sameSpotAvailable = available.any { it.spot_id == reservation.spot_id },
                alternatives = available.filterNot { it.spot_id == reservation.spot_id }
                    .distinctBy(ParkingSpot::spot_id)
                    .sortedWith(compareBy({ facility.zones.firstOrNull { zone -> zone.zone_id == it.zone_id }?.name.orEmpty() }, ParkingSpot::code)),
                zoneNames = facility.zones.associate { it.zone_id to it.name },
            )
        }
        checking = false
        availabilityChecked = result != null
        availabilityFailed = result == null
        if (result != null) {
            sameSpotAvailable = result.sameSpotAvailable
            alternatives = result.alternatives
            zoneNames = result.zoneNames
            selectedSpotId = if (result.sameSpotAvailable) reservation.spot_id else ""
        }
    }

    AlertDialog(
        onDismissRequest = { if (!checking) onDismiss() },
        title = { Text(stringResource(R.string.remote_extension_title)) },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.extend_current_end,
                    "${isoDateLabel(reservation.end_at)} · ${isoClock(reservation.end_at)}"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = ::chooseDate, modifier = Modifier.weight(1f), enabled = !checking) {
                        Text("${stringResource(R.string.remote_extension_end_date)} · $endDateLabel")
                    }
                    OutlinedButton(onClick = ::chooseTime, modifier = Modifier.weight(1f), enabled = !checking) {
                        Text("${stringResource(R.string.remote_extension_end_time)} · $endTime")
                    }
                }
                if (!validEnd) Text(
                    stringResource(R.string.remote_extension_end_after_current),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                )
                Text(stringResource(R.string.remote_extension_payment_note))
                if (availabilityFailed) Text(
                    stringResource(R.string.remote_extension_availability_error),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                )
                if (availabilityChecked && sameSpotAvailable) {
                    Text(stringResource(R.string.remote_extension_same_spot_available))
                } else if (availabilityChecked && alternatives.isEmpty()) {
                    Text(stringResource(R.string.remote_extension_same_spot_unavailable))
                    Text(stringResource(R.string.remote_extension_no_spots))
                } else if (availabilityChecked) {
                    Text(stringResource(R.string.remote_extension_same_spot_unavailable))
                    Text(stringResource(R.string.remote_extension_alternative_move))
                    alternatives.forEach { spot ->
                        Row(
                            Modifier.fillMaxWidth().clickable { selectedSpotId = spot.spot_id }
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selectedSpotId == spot.spot_id,
                                onClick = { selectedSpotId = spot.spot_id },
                            )
                            Text("${zoneNames[spot.zone_id].orEmpty()} · ${spot.code}")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !checking && if (availabilityChecked) validEnd && selectedSpotId.isNotBlank() else validEnd,
                onClick = {
                    if (availabilityChecked) {
                        val endAt = selectedEnd?.toString() ?: return@TextButton
                        onContinue(endAt, selectedSpotId)
                    } else scope.launch { checkAvailability() }
                },
            ) {
                Text(stringResource(if (checking) R.string.remote_extension_checking else if (availabilityChecked)
                    R.string.remote_extension_continue else R.string.remote_extension_check_availability))
            }
        },
        dismissButton = {
            TextButton(enabled = !checking, onClick = onDismiss) {
                Text(stringResource(R.string.remote_extension_cancel))
            }
        },
    )
}
