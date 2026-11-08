package com.yachiqo.spotgo.data.model

/**
 * In-memory mirrors of the Payments & Billing Database tables
 * (24-bounded-4). Field names match the database diagram one-to-one
 * (snake_case). UI-only fields required by the mockups are documented
 * individually.
 *
 * Enums that the database documents (PaymentStatus,
 * PaymentOperationType, PaymentAttemptStatus, BillingDocumentType,
 * SubscriptionStatus, BalanceStatus) keep their exact values. The
 * remaining status columns (payment_tokens.status, refunds.status,
 * additional_charges.status) have no documented enum in 24-bounded-4,
 * so the demo values below are labelled DEMO and only used by the
 * in-memory prototype.
 */

/* ------------------------------------------------------------------ */
/* payment_tokens                                                      */
/* ------------------------------------------------------------------ */

/** payment_tokens — provider token reference (no real card data). */
data class PaymentToken(
    val token_id: String,
    val owner_ref: String,
    val provider_token_ref: String,
    val brand: String,
    val last_four: String,
    val expiration_month: Int,
    val expiration_year: Int,
    /** UI-only: "Visa •••• 4242" label used by the mockups. */
    val display_label: String,
    /** UI-only: card expiry label, e.g. "Expires 08/29 · Default". */
    val display_details: String,
    /** UI-only: whether this is the token charged by default. */
    val is_default: Boolean,
    /** payment_tokens.status — DEMO values (enum not in 24-bounded-4). */
    val status: String = TokenStatus.ACTIVE,
    val created_at: String = "",
    val revoked_at: String? = null,
)

object TokenStatus {
    const val ACTIVE = "ACTIVE" // DEMO value (not documented)
    const val REVOKED = "REVOKED" // DEMO value (not documented)
}

/* ------------------------------------------------------------------ */
/* digital_payments                                                    */
/* ------------------------------------------------------------------ */

/** digital_payments — one row per payment operation. */
data class DigitalPayment(
    val payment_id: String,
    val payment_token_id: String,
    val operation_type: String,
    val owner_ref: String,
    val amount: String,
    val currency: String,
    val status: String,
    val provider_operation_ref: String?,
    val idempotency_key: String,
    val request_hash: String,
    val first_seen_at: String,
    val last_seen_at: String,
    val result_reference: String?,
    val created_at: String,
    val approved_at: String?,
    /** UI-only: label shown in transaction rows ("Today · San Isidro, Lima"). */
    val context_label: String,
    /** UI-only: short summary used by the transaction row. */
    val summary: String,
    /** UI-only: "Overstay · SG-4821" label of mockup 38. */
    val operation_label: String? = null,
)

object PaymentStatus {
    const val INITIATED = "INITIATED"
    const val PENDING = "PENDING"
    const val APPROVED = "APPROVED"
    const val REJECTED = "REJECTED"
    const val CANCELLED = "CANCELLED"
    const val RECONCILIATION_REQUIRED = "RECONCILIATION_REQUIRED"
}

object PaymentOperationType {
    const val RESERVATION = "RESERVATION"
    const val SUBSCRIPTION = "SUBSCRIPTION"
    const val ADDITIONAL_CHARGE = "ADDITIONAL_CHARGE"
    const val REFUND = "REFUND"
}

/* ------------------------------------------------------------------ */
/* payment_attempts                                                     */
/* ------------------------------------------------------------------ */

/** payment_attempts — retry counter of one digital payment. */
data class PaymentAttempt(
    val attempt_id: String,
    val payment_id: String,
    val number: Int,
    val started_at: String,
    val finished_at: String?,
    val status: String,
    val error_code: String?,
    val next_attempt_at: String?,
)

object PaymentAttemptStatus {
    const val STARTED = "STARTED"
    const val SUCCEEDED = "SUCCEEDED"
    const val TRANSIENT_FAILURE = "TRANSIENT_FAILURE"
    const val DEFINITIVE_FAILURE = "DEFINITIVE_FAILURE"
}

/* ------------------------------------------------------------------ */
/* refunds                                                              */
/* ------------------------------------------------------------------ */

/** refunds — refund of an approved digital payment. */
data class Refund(
    val refund_id: String,
    val payment_id: String,
    val amount: String,
    val currency: String,
    /** refunds.status — DEMO values (enum not in 24-bounded-4). */
    val status: String,
    val created_at: String,
)

object RefundStatus {
    const val REQUESTED = "REQUESTED" // DEMO value (not documented)
    const val PROCESSED = "PROCESSED" // DEMO value (not documented)
}

/* ------------------------------------------------------------------ */
/* billing_documents                                                    */
/* ------------------------------------------------------------------ */

/** billing_documents — receipt or invoice of a paid operation. */
data class BillingDocument(
    val document_id: String,
    val payment_id: String,
    val owner_ref: String,
    val document_type: String,
    val fiscal_reference: String,
    val issued_at: String,
    val pdf_uri: String?,
    /** UI-only: whether the document has been downloaded in this session. */
    val is_downloaded: Boolean = false,
    /** UI-only: whether the document has been sent by email. */
    val is_emailed: Boolean = false,
)

object BillingDocumentType {
    const val ELECTRONIC_RECEIPT = "ELECTRONIC_RECEIPT"
    const val ELECTRONIC_INVOICE = "ELECTRONIC_INVOICE"
}

/* ------------------------------------------------------------------ */
/* additional_charges                                                   */
/* ------------------------------------------------------------------ */

/** additional_charges — overstay charge of a reservation. */
data class AdditionalCharge(
    val charge_id: String,
    val reservation_id: String,
    val driver_id: String,
    val amount: String,
    /** additional_charges.status — DEMO values (enum not in 24-bounded-4). */
    val status: String,
    val created_at: String,
)

object ChargeStatus {
    const val OPEN = "OPEN" // DEMO value (not documented)
    const val PAID = "PAID" // DEMO value (not documented)
}

/* ------------------------------------------------------------------ */
/* outstanding_balances                                                 */
/* ------------------------------------------------------------------ */

/** outstanding_balances — unpaid balance that blocks new reservations. */
data class OutstandingBalance(
    val balance_id: String,
    val owner_ref: String,
    val amount: String,
    val currency: String,
    val status: String,
    val source_reference: String,
    val created_at: String,
)

object BalanceStatus {
    const val OPEN = "OPEN"
    const val PAID = "PAID"
    const val WRITTEN_OFF = "WRITTEN_OFF"
}

/* ------------------------------------------------------------------ */
/* subscriptions                                                        */
/* ------------------------------------------------------------------ */

/**
 * subscriptions — plan of the driver. `discount_percent` is UI-only:
 * 24-bounded-4 keeps the rule in `discount_rules` (a text column) and the
 * mockup shows Free 0%, Plus Monthly 10%, Plus Annual 15%.
 * `auto_renew` is UI-only as well: US20 scenario 5 needs the "cancel
 * renewal" control (the benefits stay until `end_at` and no new charge is
 * generated) and the database only exposes `status`.
 */
data class Subscription(
    val subscription_id: String,
    val driver_id: String,
    val plan: String,
    val start_at: String,
    val end_at: String,
    val status: String,
    val discount_rules: String,
    /** UI-only: percentage applied to reservations and charges (0/10/15). */
    val discount_percent: Int,
    /** UI-only: false after the Driver cancels the renewal (US20 esc. 5). */
    val auto_renew: Boolean = true,
)

/** Plan identifiers used by the subscription flow (US20). */
object SubscriptionPlan {
    const val FREE = "Free"
    const val MONTHLY = "Plus Monthly"
    const val ANNUAL = "Plus Annual"
}

object SubscriptionStatus {
    const val ACTIVE = "ACTIVE"
    const val PAUSED = "PAUSED"
    const val EXPIRED = "EXPIRED"
    const val CANCELLED = "CANCELLED"
    const val PAST_DUE = "PAST_DUE"
}

/* ------------------------------------------------------------------ */
/* UI-only view models                                                 */
/* ------------------------------------------------------------------ */

/**
 * UI-only projection of mockup 38: one outstanding charge with its
 * label ("Overstay · SG-4821") and the charge amount (S/ 8.00).
 */
data class OutstandingChargeView(
    val balance: OutstandingBalance,
    val charge: AdditionalCharge,
    /** UI-only: "Overstay · SG-4821" label of the mockup. */
    val label: String,
)

/**
 * UI-only row of the receipt / invoice card (mockup 40): label on the left,
 * value in the middle and a right-hand tag ("Zone", "2 h", "Card", amount).
 */
data class DocumentRow(
    val label: String,
    val value: String,
    val tag: String,
)

/** UI-only adjustment of mockup 40 (Refund green · Outstanding orange). */
data class AdjustmentView(
    val label: String,
    val value: String,
    /** true → green (refund), false → orange (outstanding balance). */
    val positive: Boolean,
)

/**
 * UI-only projection of mockup 40: the whole receipt/invoice detail.
 */
data class BillingDocumentView(
    val document: BillingDocument,
    val payment: DigitalPayment,
    val token: PaymentToken?,
    /** UI-only header, e.g. "Receipt SG-4821". */
    val title: String,
    /** UI-only header, e.g. "29 Sep 2026 · Parking service". */
    val subtitle: String,
    /** UI-only status badge of the header, e.g. "Paid". */
    val status_label: String,
    /** UI-only rows of the Parking zone / Reservation / Payment method card. */
    val rows: List<DocumentRow>,
    /** UI-only adjustments: Refund and Outstanding balance. */
    val adjustments: List<AdjustmentView>,
)
