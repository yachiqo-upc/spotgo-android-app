package com.yachiqo.spotgo.data.repository

import com.yachiqo.spotgo.data.local.SpotGoDatabase
import com.yachiqo.spotgo.data.model.Vehicle
import com.yachiqo.spotgo.data.model.VehicleStatus
import java.time.Instant
import java.util.UUID

/** Outcome of a vehicle operation (US27). */
sealed interface VehicleResult {
    data class Success(val vehicle: Vehicle) : VehicleResult
    data class Failure(val error: VehicleError) : VehicleResult
}

/**
 * Vehicle failures modelled after the Figma/wireflow states:
 * - INVALID_FORM: required data missing (plate, brand, model or color).
 * - INVALID_PLATE: plate does not match the Peruvian format (state 12b).
 * - DUPLICATE_PLATE: plate already linked to the Driver account (state 12a).
 */
enum class VehicleError { INVALID_FORM, INVALID_PLATE, DUPLICATE_PLATE }

/**
 * In-memory vehicles of the signed-in Driver while the backend does not exist.
 * Follows US27: register the vehicle, associate it with the Driver, keep the
 * plate and descriptive data, and reject invalid or duplicated vehicles.
 */
object VehicleRepository {

    /**
     * Peruvian plate as shown in mockup 12: three characters, a dash and three
     * digits — for example AJK-247 or B9R-314.
     */
    private val PLATE_FORMAT = Regex("""^[A-Z0-9]{3}-[0-9]{3}$""")

    /** Active vehicles of the current profile, oldest first (the first is the default). */
    fun vehicles_for_current_profile(): List<Vehicle> {
        val profile = AuthRepository.current_profile() ?: return emptyList()
        return SpotGoDatabase.vehicles
            .filter { it.profile_id == profile.profile_id && it.status == VehicleStatus.ACTIVE }
            .sortedBy { it.created_at }
    }

    /** The default vehicle is the first one registered on the account. */
    fun default_vehicle(): Vehicle? = vehicles_for_current_profile().firstOrNull()

    /** Vehicle by its vehicles.vehicle_id reference (reservations, receipts). */
    fun vehicle_by_id(vehicle_id: String): Vehicle? =
        SpotGoDatabase.vehicles.firstOrNull { it.vehicle_id == vehicle_id }

    /**
     * Soft delete: vehicles.status = INACTIVE (the row is kept so existing
     * reservations and receipts can still resolve the plate).
     */
    fun remove_vehicle(vehicle_id: String): Boolean {
        val index = SpotGoDatabase.vehicles.indexOfFirst { it.vehicle_id == vehicle_id }
        if (index < 0) return false
        val vehicle = SpotGoDatabase.vehicles[index]
        SpotGoDatabase.vehicles[index] = vehicle.copy(
            status = VehicleStatus.INACTIVE,
            updated_at = Instant.now().toString(),
        )
        return true
    }

    /** US27 — register a vehicle for the current Driver. */
    fun add_vehicle(
        plate: String,
        make: String,
        model: String,
        color: String,
        vehicle_type: String,
    ): VehicleResult {
        val profile = AuthRepository.current_profile()
            ?: return VehicleResult.Failure(VehicleError.INVALID_FORM)
        if (plate.isBlank() || make.isBlank() || model.isBlank() || color.isBlank()) {
            return VehicleResult.Failure(VehicleError.INVALID_FORM)
        }

        val cleanPlate = plate.trim().uppercase()
        if (!PLATE_FORMAT.matches(cleanPlate)) {
            return VehicleResult.Failure(VehicleError.INVALID_PLATE)
        }

        val duplicated = SpotGoDatabase.vehicles.any {
            it.profile_id == profile.profile_id &&
                it.status == VehicleStatus.ACTIVE &&
                it.plate.equals(cleanPlate, ignoreCase = true)
        }
        if (duplicated) return VehicleResult.Failure(VehicleError.DUPLICATE_PLATE)

        val now = Instant.now().toString()
        val vehicle = Vehicle(
            vehicle_id = "veh-${UUID.randomUUID().toString().take(12).uppercase()}",
            profile_id = profile.profile_id,
            plate = cleanPlate,
            make = make.trim(),
            model = model.trim(),
            color = color.trim(),
            status = VehicleStatus.ACTIVE,
            created_at = now,
            updated_at = now,
            vehicle_type = vehicle_type,
        )
        SpotGoDatabase.vehicles += vehicle
        return VehicleResult.Success(vehicle)
    }
}
