package com.yachiqo.spotgo.data.repository

import com.yachiqo.spotgo.data.model.Vehicle
import com.yachiqo.spotgo.data.model.VehicleStatus
import com.yachiqo.spotgo.data.remote.ApiResult
import com.yachiqo.spotgo.data.remote.SpotGoApi
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

sealed interface VehicleListResult {
    data class Success(val vehicles: List<Vehicle>) : VehicleListResult
    data object Failure : VehicleListResult
}

/** Driver-owned vehicles persisted by the SpotGo backend. */
object RemoteVehicleRepository {
    private val platePattern = Regex("^[A-Z0-9]{3}-[0-9]{3}$")
    @Volatile private var cachedVehicles: List<Vehicle> = emptyList()

    fun vehicleById(vehicleId: String): Vehicle? =
        cachedVehicles.firstOrNull { it.vehicle_id == vehicleId }

    fun loadVehicles(): VehicleListResult {
        if (!SpotGoApi.is_configured()) return VehicleListResult.Failure
        return when (val response = SpotGoApi.get("/api/v1/vehicles")) {
            is ApiResult.Success -> {
                if (response.status !in 200..299) return VehicleListResult.Failure
                val rows = runCatching { JSONArray(response.body) }.getOrNull()
                    ?: return VehicleListResult.Failure
                val vehicles = buildList {
                    for (index in 0 until rows.length()) {
                        val row = rows.optJSONObject(index) ?: continue
                        if (row.optString("status") != VehicleStatus.ACTIVE) continue
                        toVehicle(row)?.let(::add)
                    }
                }.sortedBy(Vehicle::created_at)
                cachedVehicles = vehicles
                VehicleListResult.Success(vehicles)
            }
            ApiResult.NotConfigured, is ApiResult.Failure -> VehicleListResult.Failure
        }
    }

    fun addVehicle(plate: String, make: String, model: String, color: String): VehicleResult {
        if (!SpotGoApi.is_configured()) return VehicleResult.Failure(VehicleError.API_ERROR)
        if (plate.isBlank() || make.isBlank() || model.isBlank() || color.isBlank()) {
            return VehicleResult.Failure(VehicleError.INVALID_FORM)
        }
        val normalizedPlate = plate.trim().uppercase()
        if (!platePattern.matches(normalizedPlate)) {
            return VehicleResult.Failure(VehicleError.INVALID_PLATE)
        }
        val body = JSONObject()
            .put("plate", normalizedPlate)
            .put("make", make.trim())
            .put("model", model.trim())
            .put("color", color.trim())
            .toString()
        return when (val response = SpotGoApi.post("/api/v1/vehicles", body)) {
            is ApiResult.Success -> when (response.status) {
            201 -> runCatching { toVehicle(JSONObject(response.body)) }
                    .getOrNull()
                    ?.let {
                        cachedVehicles = (cachedVehicles + it).sortedBy(Vehicle::created_at)
                        VehicleResult.Success(it)
                    }
                    ?: VehicleResult.Failure(VehicleError.API_ERROR)
                409 -> VehicleResult.Failure(VehicleError.DUPLICATE_PLATE)
                400 -> VehicleResult.Failure(VehicleError.INVALID_FORM)
                else -> VehicleResult.Failure(VehicleError.API_ERROR)
            }
            ApiResult.NotConfigured, is ApiResult.Failure -> VehicleResult.Failure(VehicleError.API_ERROR)
        }
    }

    fun removeVehicle(vehicleId: String): Boolean {
        if (!SpotGoApi.is_configured()) return false
        return when (val response = SpotGoApi.delete("/api/v1/vehicles/$vehicleId")) {
            is ApiResult.Success -> {
                val removed = response.status == 204
                if (removed) cachedVehicles = cachedVehicles.filterNot { it.vehicle_id == vehicleId }
                removed
            }
            ApiResult.NotConfigured, is ApiResult.Failure -> false
        }
    }

    private fun toVehicle(row: JSONObject): Vehicle? {
        val id = row.optString("id").takeIf(String::isNotBlank) ?: return null
        val profileId = row.optString("profileId").takeIf(String::isNotBlank) ?: return null
        val make = row.optString("make").takeIf(String::isNotBlank) ?: return null
        val model = row.optString("model").takeIf(String::isNotBlank) ?: return null
        val color = row.optString("color").takeIf(String::isNotBlank) ?: return null
        val createdAt = row.optString("createdAt").takeIf(String::isNotBlank) ?: Instant.EPOCH.toString()
        return Vehicle(
            vehicle_id = id,
            profile_id = profileId,
            plate = row.optString("plate").takeUnless { it == "null" }.orEmpty(),
            make = make,
            model = model,
            color = color,
            status = row.optString("status", VehicleStatus.ACTIVE),
            created_at = createdAt,
            updated_at = row.optString("updatedAt").takeUnless(String::isBlank) ?: createdAt,
        )
    }
}
