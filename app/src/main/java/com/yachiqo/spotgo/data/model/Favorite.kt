package com.yachiqo.spotgo.data.model

/**
 * Favorite parking zone of a Driver (mockup 03: heart + "Saved" chip).
 *
 * UI-only — NOT in any database diagram: the parking infrastructure database
 * has no favorites table, only the future REST resource
 * `/api/v1/favorites` (GET, POST, DELETE /{favoriteId}) documented in
 * 41-sprint-1.md. The field names are the ones that endpoint is expected to
 * exchange (snake_case) so the backend contract stays one-to-one.
 *
 * Lives in memory like every other mirror; it is lost when the process dies.
 *
 * TODO(backend): back this model with GET/POST/DELETE /api/v1/favorites.
 */
data class Favorite(
    val favorite_id: String,
    val profile_id: String,
    val zone_id: String,
    val created_at: String,
)
