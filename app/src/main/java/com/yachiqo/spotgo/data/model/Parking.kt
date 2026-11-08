package com.yachiqo.spotgo.data.model

/**
 * In-memory mirrors of the Parking Infrastructure Database tables
 * (23-bounded-3). Field names match the database diagram one-to-one
 * (snake_case). UI-only fields required by the mockups are documented
 * individually.
 */

data class Tenant(
    val tenant_id: String,
    val name: String,
    val status: String,
    val contact_data: String,
    val rules: String,
    val created_at: String,
    val updated_at: String,
)

object TenantStatus {
    const val ACTIVE = "ACTIVE"
    const val SUSPENDED = "SUSPENDED"
}

data class ParkingFacility(
    val facility_id: String,
    val tenant_id: String,
    val name: String,
    val coordinates: String,
    val status: String,
    val created_at: String,
    val updated_at: String,
)

data class ParkingZone(
    val zone_id: String,
    val facility_id: String,
    val tenant_id: String,
    val name: String,
    /** Street of the zone, e.g. "Av. Javier Prado Oeste" (mockup 03 card). */
    val location: String,
    val color_code: String,
    val status: String,
    val created_at: String,
    val updated_at: String,
    // --- UI-only extras (not in the DB diagram yet; mockups 03/04 need them) ---
    /** Hourly tariff shown as "S/ 8.00 per hour"; could come from parking_rules.tariff_rules. */
    val hourly_price: Double,
    /** Walking distance to the zone, e.g. 0.4 (km), mockup 03/04. */
    val distance_km: Double,
    /** Opening time label, e.g. "11:00 PM" (mockup 04). */
    val open_until: String,
    /** Amenity tokens of the zone: COVERED / EV / ACCESSIBLE (mockup chips). */
    val amenities: List<String>,
    /**
     * "lng,lat" of the zone marker on the map (mockup 03 / Google Maps).
     * UI-only: the diagram keeps the coordinates at facility level, the
     * Driver app needs one point per zone to place its markers.
     */
    val coordinates: String = "",
)

object ZoneStatus {
    const val ACTIVE = "ACTIVE"
    const val INACTIVE = "INACTIVE"
}

data class ParkingSpot(
    val spot_id: String,
    val zone_id: String,
    val code: String,
    val spot_type: String,
    /** Comma separated feature tokens, e.g. "COVERED,EV". */
    val features: String,
    val operational_status: String,
)

/**
 * Demo values for parking_spots.operational_status (the diagram does not
 * enumerate them): a spot must be OPERATIONAL to be offered for reservation.
 */
object SpotOperationalStatus {
    const val OPERATIONAL = "OPERATIONAL"
    const val OUT_OF_SERVICE = "OUT_OF_SERVICE"
}

data class OccupancySensor(
    val sensor_id: String,
    val tenant_id: String,
    val zone_id: String,
    val spot_id: String,
    val protocol: String,
    val status: String,
    val last_seen_at: String,
    val configuration: String,
    val installed_at: String,
)

object SensorStatus {
    const val ACTIVE = "ACTIVE"
    const val INACTIVE = "INACTIVE"
}

data class OccupancyReading(
    val reading_id: String,
    val sensor_id: String,
    val spot_id: String,
    val occupancy_status: String,
    val occurred_at: String,
    val received_at: String,
    val quality: String,
    val payload_hash: String,
)

/** Values of occupancy_readings.occupancy_status (documented in 25-bounded-5). */
object OccupancyStatus {
    const val AVAILABLE = "AVAILABLE"
    const val OCCUPIED = "OCCUPIED"
}

data class GuestParkingSession(
    val guest_session_id: String,
    val tenant_id: String,
    val zone_id: String,
    val spot_id: String,
    val manual_plate: String,
    val entry_at: String,
    /** Null while the guest session is open. */
    val exit_at: String?,
    val calculated_amount: Double?,
    val physical_payment_method: String?,
    val physical_payment_confirmed: Boolean,
    val status: String,
)

/** Values of guest_parking_sessions.status (documented in 23-bounded-3). */
object GuestSessionStatus {
    const val OPEN = "OPEN"
    const val CLOSED = "CLOSED"
    const val PENDING_REVIEW = "PENDING_REVIEW"
}

data class Reservation(
    val reservation_id: String,
    val tenant_id: String,
    val profile_id: String,
    val vehicle_id: String,
    val zone_id: String,
    val spot_id: String,
    val start_at: String,
    val end_at: String,
    val amount: Double,
    val status: String,
    /** Logical reference to the digital payment (the payments row arrives in Part 5). */
    val payment_ref: String?,
    val lock_status: String,
    val locked_at: String?,
    val lock_expires_at: String?,
    val created_at: String,
    val updated_at: String,
)

/** Values of reservations.status (documented in 23-bounded-3). */
object ReservationStatus {
    const val PENDING_PAYMENT = "PENDING_PAYMENT"
    const val RESERVED = "RESERVED"
    const val ACTIVE = "ACTIVE"
    const val COMPLETED = "COMPLETED"
    const val CANCELLED = "CANCELLED"
    const val NO_SHOW = "NO_SHOW"
    const val OVERSTAYED = "OVERSTAYED"
    const val PAYMENT_REJECTED = "PAYMENT_REJECTED"
}

/**
 * Demo values for reservations.lock_status (the diagram does not enumerate
 * them): the temporary lock (parking_rules.lock_duration_minutes = 10) is
 * held while the payment is pending and released once the reservation is
 * confirmed.
 */
object LockStatus {
    const val HELD = "HELD"
    const val RELEASED = "RELEASED"
}
