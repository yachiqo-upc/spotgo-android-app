package com.yachiqo.spotgo.data.repository

import com.yachiqo.spotgo.data.model.Reservation
import com.yachiqo.spotgo.data.remote.ApiResult
import com.yachiqo.spotgo.data.remote.SpotGoApi
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

sealed interface ReservationListResult {
    data class Success(val reservations: List<Reservation>) : ReservationListResult
    data object Failure : ReservationListResult
}

sealed interface RemoteReservationResult {
    data class Success(val reservation: Reservation) : RemoteReservationResult
    data object Failure : RemoteReservationResult
}

data class RemoteReservationChange(
    val changeId: String,
    val reservationId: String,
    val startAt: String,
    val endAt: String,
    val amount: Double,
    val paymentAmount: Double,
    val paymentId: String?,
    val status: String,
    val refundDueAt: String?,
)

sealed interface RemoteReservationChangeResult {
    data class Success(val change: RemoteReservationChange) : RemoteReservationChangeResult
    data object Failure : RemoteReservationChangeResult
}

/** Driver-scoped reservation reads and the supported cancellation action. */
object RemoteReservationRepository {
    fun loadReservations(): ReservationListResult {
        if (!SpotGoApi.is_configured()) return ReservationListResult.Failure
        return when (val response = SpotGoApi.get("/api/v1/reservations")) {
            is ApiResult.Success -> {
                if (response.status !in 200..299) return ReservationListResult.Failure
                val rows = runCatching { JSONArray(response.body) }.getOrNull()
                    ?: return ReservationListResult.Failure
                ReservationListResult.Success(
                    buildList {
                        for (index in 0 until rows.length()) {
                            rows.optJSONObject(index)?.let(::toReservation)?.let { add(it) }
                        }
                    }.sortedByDescending { it.start_at },
                )
            }
            ApiResult.NotConfigured, is ApiResult.Failure -> ReservationListResult.Failure
        }
    }

    fun loadReservation(reservationId: String): RemoteReservationResult {
        if (!SpotGoApi.is_configured()) return RemoteReservationResult.Failure
        return when (val response = SpotGoApi.get("/api/v1/reservations/$reservationId")) {
            is ApiResult.Success -> if (response.status in 200..299) {
                runCatching { toReservation(JSONObject(response.body)) }
                    .getOrNull()?.let(RemoteReservationResult::Success) ?: RemoteReservationResult.Failure
            } else RemoteReservationResult.Failure
            ApiResult.NotConfigured, is ApiResult.Failure -> RemoteReservationResult.Failure
        }
    }

    fun cancelReservation(reservationId: String): RemoteReservationResult {
        if (!SpotGoApi.is_configured()) return RemoteReservationResult.Failure
        return when (val response = SpotGoApi.post("/api/v1/reservations/$reservationId/cancel", "{}")) {
            is ApiResult.Success -> if (response.status in 200..299) {
                runCatching { toReservation(JSONObject(response.body)) }
                    .getOrNull()?.let(RemoteReservationResult::Success) ?: RemoteReservationResult.Failure
            } else RemoteReservationResult.Failure
            ApiResult.NotConfigured, is ApiResult.Failure -> RemoteReservationResult.Failure
        }
    }

    fun createReservation(spotId: String, vehicleId: String, startAt: String, endAt: String): RemoteReservationResult {
        val body = JSONObject().put("spotId", spotId).put("vehicleId", vehicleId)
            .put("startAt", startAt).put("endAt", endAt).toString()
        return reservationResponse(SpotGoApi.post("/api/v1/reservations", body))
    }

    fun rescheduleReservation(reservationId: String, startAt: String, endAt: String): RemoteReservationChangeResult {
        val body = JSONObject().put("startAt", startAt).put("endAt", endAt).toString()
        return when (val response = SpotGoApi.post("/api/v1/reservations/$reservationId/reschedule", body)) {
            is ApiResult.Success -> if (response.status in 200..299) {
                runCatching {
                    val json = JSONObject(response.body)
                    RemoteReservationChange(
                        changeId = json.getString("changeId"),
                        reservationId = json.getString("reservationId"),
                        startAt = json.getString("startAt"),
                        endAt = json.getString("endAt"),
                        amount = json.optDouble("amount", 0.0),
                        paymentAmount = json.optDouble("paymentAmount", 0.0),
                        paymentId = json.optString("paymentId").takeUnless { it.isBlank() || it == "null" },
                        status = json.optString("status"),
                        refundDueAt = json.optString("refundDueAt").takeUnless { it.isBlank() || it == "null" },
                    )
                }.getOrNull()?.let(RemoteReservationChangeResult::Success) ?: RemoteReservationChangeResult.Failure
            } else RemoteReservationChangeResult.Failure
            ApiResult.NotConfigured, is ApiResult.Failure -> RemoteReservationChangeResult.Failure
        }
    }

    fun loadReservationChanges(reservationId: String): List<RemoteReservationChange>? {
        if (!SpotGoApi.is_configured()) return null
        val rows = when (val response = SpotGoApi.get("/api/v1/reservations/$reservationId/changes")) {
            is ApiResult.Success -> if (response.status in 200..299) runCatching { JSONArray(response.body) }.getOrNull() else null
            ApiResult.NotConfigured, is ApiResult.Failure -> null
        } ?: return null
        return buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val changeId = row.optString("changeId").takeIf(String::isNotBlank) ?: continue
                add(RemoteReservationChange(
                    changeId = changeId,
                    reservationId = row.optString("reservationId"),
                    startAt = row.optString("startAt"),
                    endAt = row.optString("endAt"),
                    amount = row.optDouble("amount", 0.0),
                    paymentAmount = row.optDouble("paymentAmount", 0.0),
                    paymentId = row.optString("paymentId").takeUnless { it.isBlank() || it == "null" },
                    status = row.optString("status"),
                    refundDueAt = row.optString("refundDueAt").takeUnless { it.isBlank() || it == "null" },
                ))
            }
        }
    }

    fun extendReservation(reservationId: String, endAt: String, spotId: String? = null): RemoteReservationResult {
        val body = JSONObject().put("endAt", endAt).apply {
            spotId?.takeIf(String::isNotBlank)?.let { put("spotId", it) }
        }.toString()
        return reservationResponse(SpotGoApi.post("/api/v1/reservations/$reservationId/extend", body))
    }

    fun downloadReceipt(reservationId: String): ByteArray? = when (
        val response = SpotGoApi.getPdf("/api/v1/reservations/$reservationId/receipt")
    ) {
        is ApiResult.Success -> if (response.status in 200..299) response.body.toByteArray(StandardCharsets.ISO_8859_1) else null
        ApiResult.NotConfigured, is ApiResult.Failure -> null
    }

    fun emailReceipt(reservationId: String): Boolean = when (
        val response = SpotGoApi.post("/api/v1/reservations/$reservationId/receipt/email", "{}")
    ) {
        is ApiResult.Success -> response.status in 200..299
        ApiResult.NotConfigured, is ApiResult.Failure -> false
    }

    private fun reservationResponse(response: ApiResult): RemoteReservationResult = when (response) {
        is ApiResult.Success -> if (response.status in 200..299) {
            runCatching { toReservation(JSONObject(response.body)) }.getOrNull()
                ?.let(RemoteReservationResult::Success) ?: RemoteReservationResult.Failure
        } else RemoteReservationResult.Failure
        ApiResult.NotConfigured, is ApiResult.Failure -> RemoteReservationResult.Failure
    }

    private fun toReservation(row: JSONObject): Reservation? {
        val reservationId = row.optString("reservationId").takeIf(String::isNotBlank) ?: return null
        val tenantId = row.optString("tenantId").takeIf(String::isNotBlank) ?: return null
        val zoneId = row.optString("zoneId").takeIf(String::isNotBlank) ?: return null
        val spotId = row.optString("spotId").takeIf(String::isNotBlank) ?: return null
        val profileId = row.optString("profileId").takeIf(String::isNotBlank) ?: return null
        val vehicleId = row.optString("vehicleId").takeIf(String::isNotBlank) ?: return null
        val startAt = row.optString("startAt").takeIf(String::isNotBlank) ?: return null
        val endAt = row.optString("endAt").takeIf(String::isNotBlank) ?: return null
        val createdAt = row.optString("createdAt").takeIf(String::isNotBlank) ?: startAt
        return Reservation(
            reservation_id = reservationId,
            tenant_id = tenantId,
            profile_id = profileId,
            vehicle_id = vehicleId,
            zone_id = zoneId,
            spot_id = spotId,
            start_at = startAt,
            end_at = endAt,
            amount = row.optDouble("amount", 0.0),
            status = row.optString("status"),
            payment_ref = row.optString("paymentId").takeUnless { it.isBlank() || it == "null" },
            lock_status = row.optString("lockStatus"),
            locked_at = row.optString("lockedAt").takeUnless { it.isBlank() || it == "null" },
            lock_expires_at = row.optString("lockExpiresAt").takeUnless { it.isBlank() || it == "null" },
            created_at = createdAt,
            updated_at = row.optString("updatedAt").takeIf(String::isNotBlank) ?: createdAt,
            parent_reservation_id = row.optString("parentReservationId").takeUnless { it.isBlank() || it == "null" },
        )
    }
}
