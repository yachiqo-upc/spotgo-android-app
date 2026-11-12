package com.yachiqo.spotgo.data.repository

import com.yachiqo.spotgo.data.model.ParkingZone
import com.yachiqo.spotgo.data.model.ParkingSpot
import com.yachiqo.spotgo.data.remote.ApiResult
import com.yachiqo.spotgo.data.remote.SpotGoApi
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

data class ParkingFacilityListing(
    val tenantId: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val hourlyPrice: Double,
    val zones: List<ParkingZone>,
    val availableSpots: Int,
    val availabilityByZone: Map<String, Int>,
    val hasCoveredSpots: Boolean,
    val hasEvCharging: Boolean,
    val favoriteId: String?,
) {
    val isFavorite: Boolean get() = favoriteId != null
}

sealed interface ParkingCatalogResult {
    data class Success(val facilities: List<ParkingFacilityListing>) : ParkingCatalogResult
    data class Failure(val message: String) : ParkingCatalogResult
}

/** Authenticated read/write operations used by the Driver Explore map. */
object RemoteParkingRepository {
    @Volatile private var cachedFacilities: List<ParkingFacilityListing> = emptyList()
    @Volatile private var cachedSpots: Map<String, ParkingSpot> = emptyMap()

    fun facilityForZone(zoneId: String): ParkingFacilityListing? =
        cachedFacilities.firstOrNull { facility -> facility.zones.any { it.zone_id == zoneId } }

    fun zoneAvailability(zoneId: String): Int? =
        facilityForZone(zoneId)?.availabilityByZone?.get(zoneId)

    fun spotById(spotId: String): ParkingSpot? = cachedSpots[spotId]

    fun availableSpots(zoneId: String, startAt: String, endAt: String): List<ParkingSpot>? {
        if (!SpotGoApi.is_configured()) return null
        val path = "/api/v1/parking-spots/availability?zoneId=$zoneId" +
            "&startAt=${encode(startAt)}&endAt=${encode(endAt)}"
        val rows = getArray(path) ?: return null
        return buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                if (!row.optBoolean("bookable")) continue
                cachedSpots[row.optString("spotId")]?.let(::add)
            }
        }
    }

    fun loadFacilities(): ParkingCatalogResult {
        if (!SpotGoApi.is_configured()) {
            return ParkingCatalogResult.Failure("The SpotGo backend is not configured.")
        }
        val tenantArray = getArray("/api/v1/tenants")
            ?: return ParkingCatalogResult.Failure("Could not load parking facilities from the backend.")
        val favoritesByTenant = getFavorites()
            ?: return ParkingCatalogResult.Failure("Could not load your favorites.")

        val facilities = buildList {
            val spotsById = mutableMapOf<String, ParkingSpot>()
            var catalogFailure: String? = null
            for (index in 0 until tenantArray.length()) {
                val tenant = tenantArray.optJSONObject(index) ?: continue
                if (tenant.optString("status") != "ACTIVE") continue
                val tenantId = tenant.optString("tenantId")
                val coordinates = tenant.optJSONObject("coordinates")
                if (tenantId.isBlank() || coordinates == null) {
                    catalogFailure = "An active parking facility has incomplete data."
                    break
                }
                val latitude = coordinates.optDouble("latitude", Double.NaN)
                val longitude = coordinates.optDouble("longitude", Double.NaN)
                if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
                    catalogFailure = "An active parking facility has invalid coordinates."
                    break
                }
                val tariff = tenant.optJSONObject("rules")?.optJSONObject("tariff_rules")
                val periodMinutes = tariff?.optInt("period_minutes", -1) ?: -1
                val periodPrice = tariff?.optDouble("price_per_period", Double.NaN) ?: Double.NaN
                if (periodMinutes <= 0 || !periodPrice.isFinite() || periodPrice < 0.0) {
                    catalogFailure = "An active parking facility has an invalid tariff configuration."
                    break
                }

                val zoneArray = getArray("/api/v1/parking-zones?tenantId=$tenantId")
                if (zoneArray == null) {
                    catalogFailure = "Could not load parking facility zones."
                    break
                }
                val zones = mutableListOf<ParkingZone>()
                val availabilityByZone = mutableMapOf<String, Int>()
                var availableSpots = 0
                var hasCovered = false
                var hasEv = false
                for (zoneIndex in 0 until zoneArray.length()) {
                    val zone = zoneArray.optJSONObject(zoneIndex) ?: continue
                    if (zone.optString("status") != "ACTIVE") continue
                    val zoneId = zone.optString("zoneId")
                    if (zoneId.isBlank()) continue
                    val spotArray = getArray("/api/v1/parking-spots?zoneId=$zoneId")
                    if (spotArray == null) {
                        catalogFailure = "Could not load parking spaces."
                        break
                    }
                    val zoneAmenities = mutableListOf<String>()
                    for (spotIndex in 0 until spotArray.length()) {
                        val spot = spotArray.optJSONObject(spotIndex) ?: continue
                        val features = spot.optJSONObject("features") ?: JSONObject()
                        val spotId = spot.optString("spotId")
                        if (spotId.isNotBlank()) {
                            val featureNames = buildList {
                                if (features.optBoolean("covered")) add("COVERED")
                                if (features.optBoolean("ev_charger")) add("EV")
                                if (features.optBoolean("accessible")) add("ACCESSIBLE")
                            }
                            spotsById[spotId] = ParkingSpot(
                                spot_id = spotId,
                                zone_id = zoneId,
                                code = spot.optString("code"),
                                spot_type = spot.optString("spotType"),
                                features = featureNames.joinToString(","),
                                operational_status = spot.optString("operationalStatus"),
                            )
                        }
                        if (features.optBoolean("covered")) {
                            hasCovered = true
                            zoneAmenities += "COVERED"
                        }
                        if (features.optBoolean("ev_charger")) {
                            hasEv = true
                            zoneAmenities += "EV"
                        }
                    }
                    val zoneAvailable = loadAvailableCount(zoneId)
                    if (zoneAvailable == null) {
                        catalogFailure = "Could not load parking availability."
                        break
                    }
                    availableSpots += zoneAvailable
                    availabilityByZone[zoneId] = zoneAvailable
                    val location = zone.optJSONObject("location")
                    zones += ParkingZone(
                        zone_id = zoneId,
                        facility_id = tenantId,
                        tenant_id = tenantId,
                        name = zone.optString("name", "Parking zone"),
                        location = location?.optString("reference")?.takeIf(String::isNotBlank)
                            ?: tenant.optString("address"),
                        color_code = zone.optString("colorCode", "#2563EB"),
                        status = zone.optString("status", "ACTIVE"),
                        created_at = zone.optString("createdAt"),
                        updated_at = zone.optString("updatedAt"),
                        hourly_price = periodPrice * 60.0 / periodMinutes,
                        distance_km = 0.0,
                        open_until = "",
                        amenities = zoneAmenities.distinct(),
                        coordinates = "$longitude,$latitude",
                    )
                }
                if (catalogFailure != null) break
                if (zones.isEmpty()) continue
                add(
                    ParkingFacilityListing(
                        tenantId = tenantId,
                        name = tenant.optString("name", "Parking facility"),
                        address = tenant.optString("address"),
                        latitude = latitude,
                        longitude = longitude,
                        hourlyPrice = periodPrice * 60.0 / periodMinutes,
                        zones = zones,
                        availableSpots = availableSpots,
                        availabilityByZone = availabilityByZone,
                        hasCoveredSpots = hasCovered,
                        hasEvCharging = hasEv,
                        favoriteId = favoritesByTenant[tenantId],
                    ),
                )
            }
            if (catalogFailure != null) return ParkingCatalogResult.Failure(catalogFailure!!)
            cachedSpots = spotsById
        }
        cachedFacilities = facilities
        return ParkingCatalogResult.Success(facilities)
    }

    /** Persists the favorite on the backend; no local-only success is reported. */
    fun toggleFavorite(facility: ParkingFacilityListing): Boolean {
        val result = if (facility.favoriteId == null) {
            SpotGoApi.post(
                "/api/v1/favorites",
                JSONObject().put("tenantId", facility.tenantId).toString(),
            )
        } else {
            SpotGoApi.delete("/api/v1/favorites/${facility.favoriteId}")
        }
        return result is ApiResult.Success && result.status in 200..299
    }

    private fun getFavorites(): Map<String, String>? {
        val array = getArray("/api/v1/favorites") ?: return null
        return buildMap {
            for (index in 0 until array.length()) {
                val favorite = array.optJSONObject(index) ?: continue
                val tenantId = favorite.optString("tenantId")
                val favoriteId = favorite.optString("favoriteId")
                if (tenantId.isNotBlank() && favoriteId.isNotBlank()) put(tenantId, favoriteId)
            }
        }
    }

    private fun loadAvailableCount(zoneId: String): Int? {
        val start = Instant.now()
        val end = start.plusSeconds(60)
        val path = "/api/v1/parking-spots/availability?zoneId=$zoneId" +
            "&startAt=${encode(start.toString())}&endAt=${encode(end.toString())}"
        return getArray(path)?.let { rows ->
            (0 until rows.length()).count { rows.optJSONObject(it)?.optBoolean("bookable") == true }
        }
    }

    private fun getArray(path: String): JSONArray? = when (val result = SpotGoApi.get(path)) {
        is ApiResult.Success -> if (result.status in 200..299) {
            runCatching { JSONArray(result.body) }.getOrNull()
        } else null
        ApiResult.NotConfigured, is ApiResult.Failure -> null
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
