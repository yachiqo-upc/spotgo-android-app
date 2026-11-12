package com.yachiqo.spotgo.data.repository

import com.yachiqo.spotgo.data.remote.ApiResult
import com.yachiqo.spotgo.data.remote.SpotGoApi
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class RemotePaymentMethod(
    val tokenId: String,
    val brand: String,
    val lastFour: String,
    val expirationMonth: Int,
    val expirationYear: Int,
    val isDefault: Boolean,
) {
    val label: String get() = "$brand •••• $lastFour"
}

data class RemoteDigitalPayment(
    val paymentId: String,
    val tokenId: String?,
    val operationType: String,
    val amount: String,
    val currency: String,
    val status: String,
    val resultReference: String?,
    val createdAt: String,
    val approvedAt: String?,
)

data class RemoteOutstandingBalance(
    val balanceId: String,
    val chargeId: String,
    val amount: String,
    val status: String,
    val dueAt: String?,
    val createdAt: String,
)

data class RemoteAdditionalCharge(
    val chargeId: String,
    val reservationId: String,
    val paymentId: String?,
    val amount: String,
    val reason: String,
    val status: String,
    val createdAt: String,
)

data class RemoteSubscription(
    val subscriptionId: String,
    val paymentId: String,
    val planCode: String,
    val plan: Map<String, Any?>,
    val startAt: String,
    val endAt: String?,
    val status: String,
)

data class RemoteSubscriptionPlan(
    val code: String,
    val billingPeriodMonths: Int,
    val price: String,
    val discountPercent: Int,
)

data class RemoteBillingDocument(
    val documentId: String,
    val paymentId: String,
    val documentType: String,
    val documentNumber: String,
    val series: String,
    val issuedAt: String,
    val status: String,
) {
    val reference: String get() = listOf(series, documentNumber).filter(String::isNotBlank).joinToString("-")
}

data class RemoteBillingDocumentEntry(
    val document: RemoteBillingDocument,
    val payment: RemoteDigitalPayment,
)

data class RemoteRefund(
    val refundId: String,
    val paymentId: String,
    val amount: String,
    val status: String,
)

data class RemoteBillingDocumentDetails(
    val document: RemoteBillingDocument,
    val payment: RemoteDigitalPayment,
    val paymentMethod: RemotePaymentMethod?,
    val refunds: List<RemoteRefund>,
    val charges: List<RemoteAdditionalCharge>,
    val balances: List<RemoteOutstandingBalance>,
)

sealed interface RemotePaymentMethodsResult {
    data class Success(val methods: List<RemotePaymentMethod>) : RemotePaymentMethodsResult
    data object Failure : RemotePaymentMethodsResult
}

sealed interface RemotePaymentMethodMutationResult {
    data class Success(val method: RemotePaymentMethod) : RemotePaymentMethodMutationResult
    data object Failure : RemotePaymentMethodMutationResult
}

sealed interface RemotePaymentResult {
    data class Success(val status: String, val paymentId: String) : RemotePaymentResult
    data object Failure : RemotePaymentResult
}

/** All billing reads and writes in the Driver app go through the SpotGo API. */
object RemoteBillingRepository {

    fun loadPaymentMethods(): RemotePaymentMethodsResult {
        val rows = getArray("/api/v1/payment-tokens") ?: return RemotePaymentMethodsResult.Failure
        return RemotePaymentMethodsResult.Success(buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                if (row.optString("status") != "ACTIVE") continue
                parsePaymentMethod(row)?.let(::add)
            }
        })
    }

    fun addSimulatedPaymentMethod(brand: String, lastFour: String): RemotePaymentMethodMutationResult {
        val normalizedBrand = brand.lowercase().takeIf { it in SIMULATED_CARD_BRANDS }
            ?: return RemotePaymentMethodMutationResult.Failure
        if (!lastFour.matches(Regex("[0-9]{4}"))) return RemotePaymentMethodMutationResult.Failure
        val body = JSONObject().put("providerTokenRef", "simulated-$normalizedBrand-$lastFour").toString()
        return when (val response = SpotGoApi.post("/api/v1/payment-tokens", body)) {
            is ApiResult.Success -> if (response.status in 200..299) {
                runCatching { parsePaymentMethod(JSONObject(response.body)) }
                    .getOrNull()?.let(RemotePaymentMethodMutationResult::Success)
                    ?: RemotePaymentMethodMutationResult.Failure
            } else RemotePaymentMethodMutationResult.Failure
            ApiResult.NotConfigured, is ApiResult.Failure -> RemotePaymentMethodMutationResult.Failure
        }
    }

    fun setDefaultPaymentMethod(tokenId: String): Boolean {
        if (tokenId.isBlank()) return false
        return when (val response = SpotGoApi.put("/api/v1/payment-tokens/$tokenId/default")) {
            is ApiResult.Success -> response.status in 200..299
            ApiResult.NotConfigured, is ApiResult.Failure -> false
        }
    }

    fun loadPayments(): List<RemoteDigitalPayment>? = getArray("/api/v1/digital-payments")?.let(::paymentsFrom)

    fun loadBalances(): List<RemoteOutstandingBalance>? =
        getArray("/api/v1/outstanding-balances")?.let(::balancesFrom)

    fun loadCharges(): List<RemoteAdditionalCharge>? =
        getArray("/api/v1/additional-charges")?.let(::chargesFrom)

    fun loadSubscriptions(): List<RemoteSubscription>? =
        getArray("/api/v1/subscriptions")?.let(::subscriptionsFrom)

    fun loadPlans(): List<RemoteSubscriptionPlan>? {
        val rows = getArray("/api/v1/subscriptions/plans") ?: return null
        return buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                if (row.optString("audience") != "DRIVER") continue
                val code = row.optString("code").takeIf(String::isNotBlank) ?: continue
                val price = row.optString("price").takeIf(String::isNotBlank)
                    ?: row.opt("price")?.toString()?.takeIf(String::isNotBlank)
                    ?: continue
                add(RemoteSubscriptionPlan(
                    code = code,
                    billingPeriodMonths = row.optInt("billing_period_months", 1),
                    price = price,
                    discountPercent = row.optInt("discount_percent"),
                ))
            }
        }
    }

    /** The API exposes documents by payment, so enumerate the account's payments. */
    fun loadDocuments(): List<RemoteBillingDocumentEntry>? {
        val payments = loadPayments() ?: return null
        val result = mutableListOf<RemoteBillingDocumentEntry>()
        for (payment in payments.filter { it.status == "APPROVED" }.sortedByDescending { it.createdAt }) {
            when (val fetched = fetchDocument(payment.paymentId)) {
                is DocumentFetch.Found -> result += RemoteBillingDocumentEntry(fetched.document, payment)
                DocumentFetch.Missing -> Unit
                DocumentFetch.Failed -> return null
            }
        }
        return result
    }

    fun loadDocumentDetails(paymentId: String): RemoteBillingDocumentDetails? {
        val payment = fetchPayment(paymentId) ?: return null
        val document = when (val fetched = fetchDocument(paymentId)) {
            is DocumentFetch.Found -> fetched.document
            DocumentFetch.Missing, DocumentFetch.Failed -> return null
        }
        val methods = (loadPaymentMethods() as? RemotePaymentMethodsResult.Success)?.methods ?: return null
        val refunds = getArray("/api/v1/refunds?paymentId=$paymentId")?.let(::refundsFrom) ?: return null
        val charges = loadCharges() ?: return null
        val balances = loadBalances() ?: return null
        return RemoteBillingDocumentDetails(
            document = document,
            payment = payment,
            paymentMethod = methods.firstOrNull { it.tokenId == payment.tokenId },
            refunds = refunds,
            charges = charges,
            balances = balances,
        )
    }

    fun payReservation(reservationId: String, tokenId: String): RemotePaymentResult =
        createAndProcess(
            "/api/v1/digital-payments/reservations/$reservationId",
            tokenId,
            "reservation-$reservationId",
        )

    fun payReservationChange(changeId: String, tokenId: String): RemotePaymentResult =
        createAndProcess(
            "/api/v1/digital-payments/reservation-changes/$changeId",
            tokenId,
            "reservation-change-$changeId",
        )

    fun payAdditionalCharge(chargeId: String, tokenId: String): RemotePaymentResult =
        createAndProcess(
            "/api/v1/digital-payments/additional-charges/$chargeId",
            tokenId,
            "additional-charge-$chargeId",
        )

    fun paySubscription(planCode: String, tokenId: String): RemotePaymentResult =
        createAndProcess(
            "/api/v1/digital-payments/subscriptions",
            tokenId,
            "subscription-$planCode-${UUID.randomUUID()}",
            planCode,
        )

    fun cancelSubscription(subscriptionId: String): Boolean = when (
        val response = SpotGoApi.post("/api/v1/subscriptions/$subscriptionId/cancel", "{}")
    ) {
        is ApiResult.Success -> response.status in 200..299
        ApiResult.NotConfigured, is ApiResult.Failure -> false
    }

    fun payment(paymentId: String): RemotePaymentResult =
        fetchPayment(paymentId)?.let { RemotePaymentResult.Success(it.status, it.paymentId) }
            ?: RemotePaymentResult.Failure

    fun process(paymentId: String): RemotePaymentResult =
        paymentAction("/api/v1/digital-payments/$paymentId/process", "{}", paymentId)

    fun reconcile(paymentId: String): RemotePaymentResult =
        paymentAction("/api/v1/digital-payments/$paymentId/reconcile", "{}", paymentId)

    fun retry(paymentId: String, tokenId: String? = null): RemotePaymentResult {
        val body = JSONObject().apply { tokenId?.takeIf(String::isNotBlank)?.let { put("tokenId", it) } }.toString()
        return paymentAction("/api/v1/digital-payments/$paymentId/retry", body, paymentId)
    }

    private fun createAndProcess(path: String, tokenId: String, idempotencyKey: String, planCode: String? = null): RemotePaymentResult {
        if (!SpotGoApi.is_configured() || tokenId.isBlank()) return RemotePaymentResult.Failure
        val body = JSONObject().apply {
            put("tokenId", tokenId)
            put("idempotencyKey", idempotencyKey)
            planCode?.let { put("planCode", it) }
        }.toString()
        val paymentId = when (val created = SpotGoApi.post(path, body)) {
            is ApiResult.Success -> if (created.status in 200..299) {
                runCatching { JSONObject(created.body).getString("paymentId") }.getOrNull()
            } else null
            ApiResult.NotConfigured, is ApiResult.Failure -> null
        } ?: return RemotePaymentResult.Failure
        return paymentAction("/api/v1/digital-payments/$paymentId/process", "{}", paymentId)
    }

    private fun paymentAction(path: String, body: String, paymentId: String): RemotePaymentResult = when (
        val response = SpotGoApi.post(path, body)
    ) {
        is ApiResult.Success -> if (response.status in 200..299) {
            runCatching { JSONObject(response.body).optString("status") }
                .getOrNull()?.takeIf(String::isNotBlank)?.let { RemotePaymentResult.Success(it, paymentId) }
                ?: RemotePaymentResult.Success("UNKNOWN", paymentId)
        } else if (response.status >= 500) {
            // The operation exists; retain its id so the caller can reconcile instead of charging again.
            RemotePaymentResult.Success("UNKNOWN", paymentId)
        } else RemotePaymentResult.Failure
        ApiResult.NotConfigured -> RemotePaymentResult.Failure
        is ApiResult.Failure -> RemotePaymentResult.Success("UNKNOWN", paymentId)
    }

    private fun fetchPayment(paymentId: String): RemoteDigitalPayment? = when (
        val response = SpotGoApi.get("/api/v1/digital-payments/$paymentId")
    ) {
        is ApiResult.Success -> if (response.status in 200..299) {
            runCatching { paymentFrom(JSONObject(response.body)) }.getOrNull()
        } else null
        ApiResult.NotConfigured, is ApiResult.Failure -> null
    }

    private sealed interface DocumentFetch {
        data class Found(val document: RemoteBillingDocument) : DocumentFetch
        data object Missing : DocumentFetch
        data object Failed : DocumentFetch
    }

    private fun fetchDocument(paymentId: String): DocumentFetch = when (
        val response = SpotGoApi.get("/api/v1/billing-documents?paymentId=$paymentId")
    ) {
        is ApiResult.Success -> when {
            response.status in 200..299 -> runCatching { documentFrom(JSONObject(response.body)) }
                .getOrNull()?.let(DocumentFetch::Found) ?: DocumentFetch.Failed
            response.status == 404 -> DocumentFetch.Missing
            else -> DocumentFetch.Failed
        }
        ApiResult.NotConfigured, is ApiResult.Failure -> DocumentFetch.Failed
    }

    private fun getArray(path: String): JSONArray? = when (val response = SpotGoApi.get(path)) {
        is ApiResult.Success -> if (response.status in 200..299) runCatching { JSONArray(response.body) }.getOrNull() else null
        ApiResult.NotConfigured, is ApiResult.Failure -> null
    }

    private fun parsePaymentMethod(row: JSONObject): RemotePaymentMethod? {
        val tokenId = row.optString("tokenId").takeIf(String::isNotBlank) ?: return null
        return RemotePaymentMethod(
            tokenId = tokenId,
            brand = row.optString("brand").ifBlank { "Card" },
            lastFour = row.optString("lastFour"),
            expirationMonth = row.optInt("expirationMonth"),
            expirationYear = row.optInt("expirationYear"),
            isDefault = row.optBoolean("isDefault"),
        )
    }

    private fun paymentsFrom(rows: JSONArray): List<RemoteDigitalPayment> = buildList {
        for (index in 0 until rows.length()) rows.optJSONObject(index)?.let { row ->
            runCatching { paymentFrom(row) }.getOrNull()?.let(::add)
        }
    }

    private fun paymentFrom(row: JSONObject): RemoteDigitalPayment {
        val paymentId = row.getString("paymentId")
        return RemoteDigitalPayment(
            paymentId = paymentId,
            tokenId = row.optString("tokenId").takeUnless { it.isBlank() || it == "null" },
            operationType = row.optString("operationType"),
            amount = row.opt("amount")?.toString().orEmpty(),
            currency = row.optString("currency"),
            status = row.optString("status"),
            resultReference = row.optString("resultReference").takeUnless { it.isBlank() || it == "null" },
            createdAt = row.optString("createdAt"),
            approvedAt = row.optString("approvedAt").takeUnless { it.isBlank() || it == "null" },
        )
    }

    private fun balancesFrom(rows: JSONArray): List<RemoteOutstandingBalance> = buildList {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val id = row.optString("balanceId").takeIf(String::isNotBlank) ?: continue
            add(RemoteOutstandingBalance(
                balanceId = id,
                chargeId = row.optString("chargeId"),
                amount = row.opt("amount")?.toString().orEmpty(),
                status = row.optString("status"),
                dueAt = row.optString("dueAt").takeUnless { it.isBlank() || it == "null" },
                createdAt = row.optString("createdAt"),
            ))
        }
    }

    private fun chargesFrom(rows: JSONArray): List<RemoteAdditionalCharge> = buildList {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val id = row.optString("chargeId").takeIf(String::isNotBlank) ?: continue
            add(RemoteAdditionalCharge(
                chargeId = id,
                reservationId = row.optString("reservationId"),
                paymentId = row.optString("paymentId").takeUnless { it.isBlank() || it == "null" },
                amount = row.opt("amount")?.toString().orEmpty(),
                reason = row.optString("reason"),
                status = row.optString("status"),
                createdAt = row.optString("createdAt"),
            ))
        }
    }

    private fun subscriptionsFrom(rows: JSONArray): List<RemoteSubscription> = buildList {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val id = row.optString("subscriptionId").takeIf(String::isNotBlank) ?: continue
            val plan = row.optJSONObject("plan") ?: JSONObject()
            val map = buildMap<String, Any?> {
                for (key in plan.keys()) put(key, plan.opt(key))
            }
            add(RemoteSubscription(
                subscriptionId = id,
                paymentId = row.optString("paymentId"),
                planCode = plan.optString("code"),
                plan = map,
                startAt = row.optString("startAt"),
                endAt = row.optString("endAt").takeUnless { it.isBlank() || it == "null" },
                status = row.optString("status"),
            ))
        }
    }

    private fun documentFrom(row: JSONObject): RemoteBillingDocument = RemoteBillingDocument(
        documentId = row.getString("documentId"),
        paymentId = row.getString("paymentId"),
        documentType = row.optString("documentType"),
        documentNumber = row.optString("documentNumber"),
        series = row.optString("series"),
        issuedAt = row.optString("issuedAt"),
        status = row.optString("status"),
    )

    private fun refundsFrom(rows: JSONArray): List<RemoteRefund> = buildList {
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val id = row.optString("refundId").takeIf(String::isNotBlank) ?: continue
            add(RemoteRefund(
                refundId = id,
                paymentId = row.optString("paymentId"),
                amount = row.opt("amount")?.toString().orEmpty(),
                status = row.optString("status"),
            ))
        }
    }

    private val SIMULATED_CARD_BRANDS = setOf("visa", "mastercard", "amex", "discover")
}
