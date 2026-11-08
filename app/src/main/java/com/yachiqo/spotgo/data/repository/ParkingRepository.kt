package com.yachiqo.spotgo.data.repository

import com.yachiqo.spotgo.data.local.SpotGoDatabase
import com.yachiqo.spotgo.data.model.GuestSessionStatus
import com.yachiqo.spotgo.data.model.LockStatus
import com.yachiqo.spotgo.data.model.OccupancyStatus
import com.yachiqo.spotgo.data.model.OccupancyReading
import com.yachiqo.spotgo.data.model.ParkingSpot
import com.yachiqo.spotgo.data.model.ParkingZone
import com.yachiqo.spotgo.data.model.Reservation
import com.yachiqo.spotgo.data.model.ReservationStatus
import com.yachiqo.spotgo.data.model.SpotOperationalStatus
import com.yachiqo.spotgo.data.model.Vehicle
import com.yachiqo.spotgo.data.model.ZoneStatus
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

/** Outcome of a reservation attempt (US18). */
sealed interface ReservationResult {
    data class Success(val reservation: Reservation) : ReservationResult
    data class Failure(val error: ReservationError) : ReservationResult
}

/**
 * Reservation failures modelled after the US18 acceptance criteria:
 * - VEHICLE_REQUIRED: scenario 2 — no vehicle registered on the account.
 * - VEHICLE_BUSY: scenario 12 — the vehicle already has an active spot.
 * - SPOT_TAKEN: scenario 3 — the spot stopped being available.
 * - OUTSTANDING_BALANCE: US19 scenario 6 — an unpaid additional charge
 *   blocks new reservations until the balance is paid.
 * - PAST_TIME: user request — a reservation can never start before the
 *   current time of Peru (America/Lima), the timezone the app is built for.
 */
enum class ReservationError { VEHICLE_REQUIRED, VEHICLE_BUSY, SPOT_TAKEN, OUTSTANDING_BALANCE, PAST_TIME }

/** Outcome of a reservation modification (US18 scenario 8). */
sealed interface ReservationUpdateResult {
    data class Updated(val reservation: Reservation) : ReservationUpdateResult
    data class Rejected(val reason: UpdateRejection) : ReservationUpdateResult
}

/** Reasons why a reservation modification is rejected (US18 scenario 8). */
enum class UpdateRejection { NOT_FOUND, BEYOND_CLOSING, SPOT_UNAVAILABLE, PAST_TIME }

/**
 * In-memory parking infrastructure of the demo while the backend does not
 * exist. Mirrors parking_zones / parking_spots / occupancy_readings /
 * guest_parking_sessions / reservations and implements the availability
 * projection (US01), permitted zones (US04) and the reservation flow (US18).
 */
object ParkingRepository {

    /** Initial value shown by mockup 03 ("Last updated · 10:30:00"). */
    const val INITIAL_LAST_UPDATED = "10:30:00"

    /** parking_rules.lock_duration_minutes = 10. */
    private const val LOCK_MINUTES = 10

    var last_updated: String = INITIAL_LAST_UPDATED
        private set

    /** US01 scenario 3 — manual refresh updates the displayed timestamp. */
    fun refresh() {
        last_updated = PeruClock.time().format(CLOCK)
    }

    /** Active zones of the demo tenant (US04 — permitted zones). */
    fun zones(): List<ParkingZone> =
        SpotGoDatabase.parking_zones.filter { it.status == ZoneStatus.ACTIVE }

    fun zone_by_id(zone_id: String): ParkingZone? =
        SpotGoDatabase.parking_zones.firstOrNull { it.zone_id == zone_id }

    fun spot_by_id(spot_id: String): ParkingSpot? =
        SpotGoDatabase.parking_spots.firstOrNull { it.spot_id == spot_id }

    /** Latest sensor reading of a spot (occupancy_readings, newest last). */
    fun latest_reading(spot_id: String): OccupancyReading? =
        SpotGoDatabase.occupancy_readings.lastOrNull { it.spot_id == spot_id }

    /**
     * US01 — a spot is available when its sensor reports AVAILABLE, the spot
     * is operational, no guest session is open and no reservation or temporary
     * lock is holding it.
     */
    fun is_spot_available(spot: ParkingSpot): Boolean = is_spot_available_excluding(spot, reservation_id = null)

    /**
     * Availability of a spot ignoring one reservation (its own): used when a
     * Driver edits or extends an existing reservation (US18 scenario 8).
     */
    fun is_spot_available_excluding(spot: ParkingSpot, reservation_id: String?): Boolean {
        if (spot.operational_status != SpotOperationalStatus.OPERATIONAL) return false
        val reading = latest_reading(spot.spot_id) ?: return false
        if (reading.occupancy_status != OccupancyStatus.AVAILABLE) return false
        val guestHolding = SpotGoDatabase.guest_parking_sessions.any {
            it.spot_id == spot.spot_id && it.status == GuestSessionStatus.OPEN
        }
        if (guestHolding) return false
        val reservationHolding = SpotGoDatabase.reservations.any {
            it.spot_id == spot.spot_id && it.status in HOLDING_STATUSES && it.reservation_id != reservation_id
        }
        return !reservationHolding
    }

    fun spots_of_zone(zone_id: String): List<ParkingSpot> =
        SpotGoDatabase.parking_spots.filter { it.zone_id == zone_id }

    fun available_spots(zone_id: String): List<ParkingSpot> =
        spots_of_zone(zone_id).filter { is_spot_available(it) }

    fun available_count(zone_id: String): Int = available_spots(zone_id).size

    /**
     * US20 — amount the Driver actually pays: the subscription discount
     * (Free 0%, Plus Monthly 10%, Plus Annual 15%) is applied to every
     * reservation, extension and additional charge. The gross amount is
     * kept for the receipt breakdown (subtotal + plan discount).
     */
    fun quoted_amount(gross: Double): Double {
        val percent = PaymentRepository.subscription_discount_percent()
        return round2(gross * (100 - percent) / 100.0)
    }

    /** Discount (gross − quoted) applied by the active plan, for the receipt. */
    fun discount_amount(gross: Double): Double = gross - quoted_amount(gross)

    /**
     * US18 — create the reservation with a temporary lock while the digital
     * payment is pending. The spot is re-checked here (scenario 3).
     */
    fun create_reservation(
        zone: ParkingZone,
        spot: ParkingSpot,
        vehicle: Vehicle?,
        date_label: String,
        start_label: String,
        duration_hours: Int,
    ): ReservationResult {
        if (vehicle == null) return ReservationResult.Failure(ReservationError.VEHICLE_REQUIRED)

        val profile = AuthRepository.current_profile()
            ?: return ReservationResult.Failure(ReservationError.VEHICLE_REQUIRED)

        // Scenario 12 — one active spot per vehicle.
        val vehicleBusy = SpotGoDatabase.reservations.any {
            it.vehicle_id == vehicle.vehicle_id && it.status in HOLDING_STATUSES
        }
        if (vehicleBusy) return ReservationResult.Failure(ReservationError.VEHICLE_BUSY)

        // Scenario 3 — the spot must still be available at submission time.
        if (!is_spot_available(spot)) return ReservationResult.Failure(ReservationError.SPOT_TAKEN)

        // US19 scenario 6 — an outstanding balance blocks new reservations.
        if (PaymentRepository.has_open_balance()) {
            return ReservationResult.Failure(ReservationError.OUTSTANDING_BALANCE)
        }

        val start = resolve_start(date_label, start_label)

        // User request — the reservation cannot start in the past: the
        // current time is the wall clock of Peru (America/Lima).
        if (PeruClock.isPast(start)) {
            return ReservationResult.Failure(ReservationError.PAST_TIME)
        }

        val end = start.plusHours(duration_hours.toLong())
        val now = Instant.now().toString()
        val reservation = Reservation(
            reservation_id = "res-${UUID.randomUUID().toString().take(12).uppercase()}",
            tenant_id = zone.tenant_id,
            profile_id = profile.profile_id,
            vehicle_id = vehicle.vehicle_id,
            zone_id = zone.zone_id,
            spot_id = spot.spot_id,
            start_at = start.toString(),
            end_at = end.toString(),
            // US20 — the plan discount is applied when the total is quoted.
            amount = quoted_amount(zone.hourly_price * duration_hours),
            status = ReservationStatus.PENDING_PAYMENT,
            payment_ref = null,
            lock_status = LockStatus.HELD,
            locked_at = now,
            lock_expires_at = Instant.now().plus(Duration.ofMinutes(LOCK_MINUTES.toLong())).toString(),
            created_at = now,
            updated_at = now,
        )
        SpotGoDatabase.reservations += reservation
        return ReservationResult.Success(reservation)
    }

    /**
     * Internal payment service approves the pending transaction: the
     * reservation becomes RESERVED, the temporary lock is released (the
     * reservation itself now blocks the spot for other Drivers) and the
     * digital payment plus its receipt are recorded (US19 scenario 2).
     * TODO(test): add the payment-rejected case (US18 scenario 4: release the
     * lock, no reservation, no receipt) as an automated test when the project
     * is finished — requested by the user.
     */
    fun approve_payment(reservation_id: String): Reservation? {
        val index = SpotGoDatabase.reservations.indexOfFirst { it.reservation_id == reservation_id }
        if (index < 0) return null
        val current = SpotGoDatabase.reservations[index]
        val approved = current.copy(
            status = ReservationStatus.RESERVED,
            lock_status = LockStatus.RELEASED,
            updated_at = Instant.now().toString(),
        )
        SpotGoDatabase.reservations[index] = approved
        // digital_payments + payment_attempts + billing_documents (US19 / US21).
        val payment = PaymentRepository.record_reservation_payment(approved)
        val withReference = approved.copy(payment_ref = payment?.payment_id ?: approved.payment_ref)
        SpotGoDatabase.reservations[index] = withReference
        last_reservation = withReference
        return withReference
    }

    /** The reservation just confirmed, read by the confirmation screen (05). */
    var last_reservation: Reservation? = null
        private set

    /** Demo booking code of the virtual receipt, e.g. "#SG-4821" (UI-only). */
    fun booking_code(reservation: Reservation): String =
        java.lang.String.format(Locale.US, "SG-%04d", abs(reservation.reservation_id.hashCode()) % 10000)

    fun reservation_by_id(reservation_id: String): Reservation? =
        SpotGoDatabase.reservations.firstOrNull { it.reservation_id == reservation_id }

    /** Reservations of the signed-in Driver (My reservations list, mockup 06). */
    fun reservations_for_current_profile(): List<Reservation> {
        val profile = AuthRepository.current_profile() ?: return emptyList()
        return SpotGoDatabase.reservations.filter { it.profile_id == profile.profile_id }
    }

    /**
     * US18 scenario 7 — cancel a reservation that has not started: the status
     * becomes CANCELLED (the spot is released because the reservation stops
     * holding it) and the refund to the original payment method is simulated.
     */
    fun cancel_reservation(reservation_id: String): Reservation? {
        val index = SpotGoDatabase.reservations.indexOfFirst { it.reservation_id == reservation_id }
        if (index < 0) return null
        val current = SpotGoDatabase.reservations[index]
        if (current.status != ReservationStatus.RESERVED) return null
        val cancelled = current.copy(
            status = ReservationStatus.CANCELLED,
            lock_status = LockStatus.RELEASED,
            updated_at = Instant.now().toString(),
        )
        SpotGoDatabase.reservations[index] = cancelled
        if (last_reservation?.reservation_id == reservation_id) last_reservation = cancelled
        // Refund of the original payment (US18 → US21 refund receipt).
        PaymentRepository.record_refund(reservation_id)
        return cancelled
    }

    /**
     * US18 scenario 8 — modify a reservation (date / start time / duration):
     * the new assignment is validated against the zone hours and the spot
     * availability, and the total is recalculated before the change is
     * confirmed (the updated virtual receipt is regenerated by the UI).
     */
    fun update_reservation(
        reservation_id: String,
        date_label: String,
        start_label: String,
        duration_hours: Int,
    ): ReservationUpdateResult {
        val current = reservation_by_id(reservation_id)
            ?: return ReservationUpdateResult.Rejected(UpdateRejection.NOT_FOUND)
        val zone = zone_by_id(current.zone_id)
            ?: return ReservationUpdateResult.Rejected(UpdateRejection.NOT_FOUND)
        val spot = spot_by_id(current.spot_id)
            ?: return ReservationUpdateResult.Rejected(UpdateRejection.NOT_FOUND)

        val newStart = resolve_start(date_label, start_label)
        val newEnd = newStart.plusHours(duration_hours.toLong())

        // User request — the new assignment cannot start in the past either.
        if (PeruClock.isPast(newStart)) {
            return ReservationUpdateResult.Rejected(UpdateRejection.PAST_TIME)
        }

        // Validated against zone hours: the reservation must end before
        // closing. An assignment that rolls over past midnight is beyond the
        // closing time even though its time-of-day looks earlier.
        val closingMinutes = parseClock(zone.open_until)
        val endsAfterMidnight = newEnd.toLocalDate().isAfter(newStart.toLocalDate())
        if (endsAfterMidnight || newEnd.toLocalTime().toSecondOfDay() / 60 > closingMinutes) {
            return ReservationUpdateResult.Rejected(UpdateRejection.BEYOND_CLOSING)
        }

        // The spot must still be available apart from this very reservation.
        if (!is_spot_available_excluding(spot, current.reservation_id)) {
            return ReservationUpdateResult.Rejected(UpdateRejection.SPOT_UNAVAILABLE)
        }

        val updated = current.copy(
            start_at = newStart.toString(),
            end_at = newEnd.toString(),
            amount = quoted_amount(zone.hourly_price * duration_hours),
            updated_at = Instant.now().toString(),
        )
        replace_reservation(updated)
        return ReservationUpdateResult.Updated(updated)
    }

    /**
     * US18 scenario 8 — extend the end time of a reservation paying the
     * difference (the extension payment is simulated by the caller).
     */
    fun extend_reservation(reservation_id: String, new_end: LocalDateTime, extra_amount: Double): Reservation? {
        val current = reservation_by_id(reservation_id) ?: return null
        if (current.status != ReservationStatus.RESERVED) return null
        // User request — an extension cannot end in the past (Peru time).
        if (PeruClock.isPast(new_end)) return null
        val extended = current.copy(
            end_at = new_end.toString(),
            amount = current.amount + extra_amount,
            updated_at = Instant.now().toString(),
        )
        replace_reservation(extended)
        // The extension payment is recorded as an extra RESERVATION charge.
        PaymentRepository.record_extension_payment(extended, extra_amount)
        return extended
    }

    private fun replace_reservation(updated: Reservation) {
        val index = SpotGoDatabase.reservations.indexOfFirst { it.reservation_id == updated.reservation_id }
        if (index >= 0) SpotGoDatabase.reservations[index] = updated
        if (last_reservation?.reservation_id == updated.reservation_id) last_reservation = updated
    }

    private val HOLDING_STATUSES = listOf(
        ReservationStatus.PENDING_PAYMENT,
        ReservationStatus.RESERVED,
        ReservationStatus.ACTIVE,
    )

    internal fun resolve_start(date_label: String, start_label: String): LocalDateTime {
        val day = when (date_label) {
            "Tomorrow" -> PeruClock.today().plusDays(1)
            else -> PeruClock.today()
        }
        return LocalDateTime.of(day, LocalTime.ofSecondOfDay(parseClock(start_label) * 60L))
    }
}

// ---------------------------------------------------------------------------
// Date / money helpers shared by the reservation screens
// ---------------------------------------------------------------------------

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val CLOCK_24: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

/** Minutes of day from a 12-hour label, e.g. "10:30 AM" → 630. */
internal fun parseClock(label: String): Int {
    val cleaned = label.trim().uppercase(Locale.US)
    val timePart = cleaned.substringBeforeLast(' ')
    val meridiem = cleaned.substringAfterLast(' ', "AM")
    val parts = timePart.split(':')
    var hour = parts[0].toIntOrNull() ?: 0
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    if (meridiem == "PM" && hour != 12) hour += 12
    if (meridiem == "AM" && hour == 12) hour = 0
    return hour * 60 + minute
}

/** "10:30" minutes-of-day → "10:30 AM" (demo time options). */
internal fun formatClock(totalMinutes: Int): String =
    LocalTime.ofSecondOfDay((totalMinutes % (24 * 60)) * 60L).format(CLOCK)

/** Accepts both local timestamps (start_at) and instant seeds ("…Z"). */
internal fun parseTimestamp(iso: String): LocalDateTime =
    LocalDateTime.parse(if (iso.endsWith("Z")) iso.dropLast(1) else iso)

/** ISO timestamp → "10:30 AM". */
internal fun isoClock(iso: String): String =
    parseTimestamp(iso).toLocalTime().format(CLOCK)

/** ISO timestamp → "Today" / "Tomorrow" / "Nov 7" (Peru is the reference). */
internal fun isoDateLabel(iso: String): String {
    val date = parseTimestamp(iso).toLocalDate()
    return when (date) {
        PeruClock.today() -> "Today"
        PeruClock.today().plusDays(1) -> "Tomorrow"
        else -> date.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))
    }
}

/** ISO start/end → "10:30 AM – 12:30 PM". */
internal fun isoRange(startIso: String, endIso: String): String =
    "${isoClock(startIso)} – ${isoClock(endIso)}"

/** "S/ 16.00" money format used by the mockups. */
internal fun money(amount: Double): String = java.lang.String.format(Locale.US, "S/ %.2f", amount)

/** Rounds an amount to 2 decimals (the precision used by every price). */
internal fun round2(amount: Double): Double = Math.round(amount * 100.0) / 100.0

/** "S/ 8" whole format used by the map markers (mockup 03). */
internal fun moneyWhole(amount: Double): String = java.lang.String.format(Locale.US, "S/ %d", amount.toInt())

/** "S/ 16" whole format without the space (price arrows of mockup 37). */
internal fun moneyCompact(amount: Double): String =
    java.lang.String.format(Locale.US, "S/%d", Math.round(amount))

/** "2 h" duration label of the reservation information rows (mockup 07). */
internal fun hoursLabel(startIso: String, endIso: String): String {
    val minutes = Duration.between(parseTimestamp(startIso), parseTimestamp(endIso)).toMinutes()
    val hours = minutes / 60
    return if (minutes % 60 == 0L) "$hours h" else "$hours h ${minutes % 60} min"
}

/** Demo start-time options for the reserve form (mockup 04 starts at 10:30 AM). */
internal val START_TIME_OPTIONS: List<String> =
    (0 until 12).map { formatClock(10 * 60 + 30 + it * 30) }

/**
 * Start-time options that can still be booked for a date label.
 *
 * User request — a reservation can never start in the past: when the date is
 * not "Tomorrow" (that is, "Today", the same rule used by resolve_start) the
 * times already passed in Peru (America/Lima) are removed from the list.
 */
internal fun availableStartOptions(date_label: String): List<String> {
    if (date_label == "Tomorrow") return START_TIME_OPTIONS
    val nowMinutes = PeruClock.minutesOfDay()
    return START_TIME_OPTIONS.filter { parseClock(it) > nowMinutes }
}
