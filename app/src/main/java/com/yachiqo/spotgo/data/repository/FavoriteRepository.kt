package com.yachiqo.spotgo.data.repository

import com.yachiqo.spotgo.data.local.SpotGoDatabase
import com.yachiqo.spotgo.data.model.Favorite
import com.yachiqo.spotgo.data.model.ParkingZone
import java.time.Instant
import java.util.UUID

/**
 * Favorites of the Driver (mockup 03: heart on a parking + "Saved" chip).
 *
 * UI-only — the database has no favorites table, only the future REST
 * resource `/api/v1/favorites` (GET, POST, DELETE /{favoriteId}) documented
 * in 41-sprint-1.md, so this repository mirrors that contract in memory
 * (data is lost when the process dies).
 *
 * TODO(backend): replace the body of every function with the real call to
 * GET/POST/DELETE /api/v1/favorites; the signatures already match it.
 */
object FavoriteRepository {

    /** Rows of the current profile (a Snapshot list, so the UI refreshes). */
    private val rows: List<Favorite>
        get() = SpotGoDatabase.favorites.filter { it.profile_id == owner() }

    private fun owner(): String = AuthRepository.current_profile()?.profile_id ?: ""

    /** Zones saved by the current profile, by id (Saved chip filter). */
    fun saved_zone_ids(): Set<String> = rows.map { it.zone_id }.toSet()

    /** True when the zone is saved by the current profile (heart state). */
    fun is_saved(zone_id: String): Boolean = rows.any { it.zone_id == zone_id }

    /**
     * POST /api/v1/favorites when the zone is not saved, DELETE
     * /api/v1/favorites/{favoriteId} when it is. Returns the new state.
     */
    fun toggle(zone_id: String): Boolean {
        val profileId = owner()
        if (profileId.isEmpty()) return is_saved(zone_id)
        val existing = SpotGoDatabase.favorites
            .indexOfFirst { it.zone_id == zone_id && it.profile_id == profileId }
        return if (existing >= 0) {
            SpotGoDatabase.favorites.removeAt(existing)
            false
        } else {
            SpotGoDatabase.favorites += Favorite(
                favorite_id = "fav-" + UUID.randomUUID().toString().take(8),
                profile_id = profileId,
                zone_id = zone_id,
                created_at = Instant.now().toString(),
            )
            true
        }
    }

    /** Saved zones with their catalog data, in save order (Saved tab list). */
    fun saved_zones(): List<ParkingZone> =
        rows.mapNotNull { row -> ParkingRepository.zone_by_id(row.zone_id) }
}
