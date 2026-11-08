package com.yachiqo.spotgo.data.model

/**
 * In-memory mirror of the `vehicles` table (Profiles & Vehicles Management):
 * vehicle_id, profile_id, plate, make, model, color, status, created_at, updated_at.
 * Field names match the database diagram one-to-one (snake_case).
 */
data class Vehicle(
    val vehicle_id: String,
    val profile_id: String,
    val plate: String,
    val make: String,
    val model: String,
    val color: String,
    val status: String,
    val created_at: String,
    val updated_at: String,
    /**
     * UI-only field: mockup 12 ("Vehicle type": Car / SUV / Motorcycle) shows it,
     * but the DB diagram has no such column yet. Kept here so the value survives
     * while the data model decision is pending.
     */
    val vehicle_type: String = VehicleType.CAR,
)

/** Values of vehicles.status. */
object VehicleStatus {
    const val ACTIVE = "ACTIVE"
    const val INACTIVE = "INACTIVE"
}

/** Values of the UI-only vehicle type selector (mockup 12). */
object VehicleType {
    const val CAR = "CAR"
    const val SUV = "SUV"
    const val MOTORCYCLE = "MOTORCYCLE"
}
