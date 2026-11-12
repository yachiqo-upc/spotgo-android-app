package com.yachiqo.spotgo.data.repository

import com.yachiqo.spotgo.data.local.SpotGoDatabase
import com.yachiqo.spotgo.data.model.AdditionalCharge
import com.yachiqo.spotgo.data.model.AdjustmentView
import com.yachiqo.spotgo.data.model.BalanceStatus
import com.yachiqo.spotgo.data.model.BillingDocument
import com.yachiqo.spotgo.data.model.BillingDocumentType
import com.yachiqo.spotgo.data.model.BillingDocumentView
import com.yachiqo.spotgo.data.model.ChargeStatus
import com.yachiqo.spotgo.data.model.DigitalPayment
import com.yachiqo.spotgo.data.model.DocumentRow
import com.yachiqo.spotgo.data.model.OutstandingBalance
import com.yachiqo.spotgo.data.model.OutstandingChargeView
import com.yachiqo.spotgo.data.model.PaymentAttempt
import com.yachiqo.spotgo.data.model.PaymentAttemptStatus
import com.yachiqo.spotgo.data.model.PaymentOperationType
import com.yachiqo.spotgo.data.model.PaymentStatus
import com.yachiqo.spotgo.data.model.PaymentToken
import com.yachiqo.spotgo.data.model.Refund
import com.yachiqo.spotgo.data.model.RefundStatus
import com.yachiqo.spotgo.data.model.Reservation
import com.yachiqo.spotgo.data.model.Subscription
import com.yachiqo.spotgo.data.model.SubscriptionPlan
import com.yachiqo.spotgo.data.model.SubscriptionStatus
import com.yachiqo.spotgo.data.model.TokenStatus
import com.yachiqo.spotgo.data.repository.ParkingRepository.booking_code
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

/**
 * In-memory Payments & Billing service (24-bounded-4) of the demo while the
 * backend does not exist. Mirrors payment_tokens / digital_payments /
 * payment_attempts / refunds / billing_documents / additional_charges /
 * outstanding_balances / subscriptions and implements US19 (payment of
 * reservations and additional charges) and US21 (receipts and invoices).
 *
 * The digital payment approval is simulated by the callers with a 1.5 s
 * delay before calling the functions below (US19 — approval simulation).
 */
object PaymentRepository {

    /** parking_rules.overstay_tolerance_minutes = 5 (US19 scenario 4). */
    private const val OVERSTAY_TOLERANCE_MINUTES = 5L

    /**
     * Demo window: only sessions that ended in the last 24 h can generate an
     * additional charge, so historical seed reservations stay untouched.
     */
    private const val OVERSTAY_WINDOW_HOURS = 24L

    /** UI-only district shown by the mockups ("San Isidro, Lima"). */
    private const val DISTRICT_LABEL = "San Isidro, Lima"

    private fun owner(): String = AuthRepository.current_profile()?.profile_id ?: ""

    /** Amount stored in digital_payments.amount (plain, e.g. "16.00"). */
    private fun amountOf(value: Double): String =
        java.lang.String.format(Locale.US, "%.2f", value)

    /* ------------------------------------------------------------------ */
    /* payment_tokens                                                       */
    /* ------------------------------------------------------------------ */

    fun tokens(): List<PaymentToken> =
        SpotGoDatabase.payment_tokens.filter { it.owner_ref == owner() && it.status == TokenStatus.ACTIVE }

    fun default_token(): PaymentToken? = tokens().firstOrNull { it.is_default } ?: tokens().firstOrNull()

    fun token_by_id(token_id: String): PaymentToken? =
        SpotGoDatabase.payment_tokens.firstOrNull { it.token_id == token_id }

    /* ------------------------------------------------------------------ */
    /* subscriptions                                                        */
    /* ------------------------------------------------------------------ */

    fun subscription(): Subscription? =
        SpotGoDatabase.subscriptions.firstOrNull { it.driver_id == owner() }
            ?: SpotGoDatabase.subscriptions.firstOrNull()

    /** Discount applied to additional charges (Free 0% · Plus 10/15%). */
    fun subscription_discount_percent(): Int = subscription()?.discount_percent ?: 0

    /** US20 scenario 5 — the Driver cancelled the renewal of the plan. */
    fun renewal_cancelled(): Boolean = subscription()?.auto_renew == false

    /**
     * US20 scenarios 2 and 3 — activates a paid plan after the simulated
     * digital payment approval (the caller delays 1.5 s): the subscription
     * is renewed for 30 days (Plus Monthly) or 12 months (Plus Annual) and
     * its electronic invoice is issued (billing_documents).
     */
    fun subscribe_plan(plan: String, price: Double): DigitalPayment? {
        if (owner().isEmpty()) return null
        if (plan != SubscriptionPlan.MONTHLY && plan != SubscriptionPlan.ANNUAL) return null
        val now = Instant.now().toString()
        val days = if (plan == SubscriptionPlan.ANNUAL) 365L else 30L
        val discount = if (plan == SubscriptionPlan.ANNUAL) 15 else 10
        val payment = insert_payment(
            operation_type = PaymentOperationType.SUBSCRIPTION,
            owner_ref = owner(),
            amount = amountOf(price),
            status = PaymentStatus.APPROVED,
            created_at = now,
            approved_at = now,
            context_label = "${isoDateLabel(now)} · $plan",
            summary = plan,
            idempotency_key = "sub:${plan}:${now}",
            operation_label = plan,
        )
        insert_document(
            payment = payment,
            document_type = BillingDocumentType.ELECTRONIC_INVOICE,
            fiscal_reference = next_invoice_reference(),
            issued_at = now,
        )
        upsert_subscription(plan = plan, endAt = PeruClock.now().plusDays(days),
            discount = discount, autoRenew = true)
        return payment
    }

    /**
     * US20 scenario 1 — switch to Free: immediate, no charge, 0% discount
     * and no special zones.
     */
    fun activate_free_plan() {
        if (owner().isEmpty()) return
        upsert_subscription(plan = SubscriptionPlan.FREE, endAt = null, discount = 0, autoRenew = true)
    }

    /**
     * US20 scenario 5 — cancel the renewal: the benefits stay until the
     * current `end_at` and no new charge is generated. Downgrading to Free
     * happens immediately (no renewal to cancel).
     */
    fun cancel_renewal() {
        set_auto_renew(false)
    }

    /** US20 scenario 5 — re-enable the automatic renewal of the plan. */
    fun resume_renewal() {
        set_auto_renew(true)
    }

    private fun set_auto_renew(value: Boolean) {
        val index = SpotGoDatabase.subscriptions.indexOfFirst { it.driver_id == owner() }
        if (index < 0) return
        SpotGoDatabase.subscriptions[index] =
            SpotGoDatabase.subscriptions[index].copy(auto_renew = value)
    }

    private fun upsert_subscription(plan: String, endAt: LocalDateTime?, discount: Int, autoRenew: Boolean) {
        val index = SpotGoDatabase.subscriptions.indexOfFirst { it.driver_id == owner() }
        val current = index.takeIf { it >= 0 }?.let { SpotGoDatabase.subscriptions[it] }
        val updated = Subscription(
            subscription_id = current?.subscription_id
                ?: "sub-${UUID.randomUUID().toString().take(8).uppercase(Locale.US)}",
            driver_id = owner(),
            plan = plan,
            start_at = Instant.now().toString(),
            end_at = endAt?.toString() ?: "",
            status = SubscriptionStatus.ACTIVE,
            discount_rules = "${plan.lowercase(Locale.US)}:$discount",
            discount_percent = discount,
            auto_renew = autoRenew,
        )
        if (index >= 0) SpotGoDatabase.subscriptions[index] = updated
        else SpotGoDatabase.subscriptions += updated
    }

    /** Next invoice number of the demo series (seeded invoice is F001-0048). */
    private fun next_invoice_reference(): String {
        val issued = SpotGoDatabase.billing_documents.count {
            it.document_type == BillingDocumentType.ELECTRONIC_INVOICE
        }
        return java.lang.String.format(Locale.US, "F001-%04d", 48 + issued)
    }

    /* ------------------------------------------------------------------ */
    /* digital_payments                                                     */
    /* ------------------------------------------------------------------ */

    /** Transaction history of the Payments tab, newest first. */
    fun transactions(): List<DigitalPayment> =
        SpotGoDatabase.digital_payments
            .filter { it.owner_ref == owner() }
            .sortedByDescending { it.created_at }

    fun payment_by_id(payment_id: String): DigitalPayment? =
        SpotGoDatabase.digital_payments.firstOrNull { it.payment_id == payment_id }

    fun payment_of_reservation(reservation_id: String): DigitalPayment? {
        val reservation = ParkingRepository.reservation_by_id(reservation_id) ?: return null
        val ref = reservation.payment_ref ?: return null
        return payment_by_id(ref)
    }

    /**
     * US19 scenario 2 — the internal payment service approved the reservation
     * payment: digital_payments + payment_attempts + the electronic receipt
     * (billing_documents) are recorded. Idempotent per reservation.
     */
    fun record_reservation_payment(reservation: Reservation): DigitalPayment? {
        reservation.payment_ref?.let { existing -> payment_by_id(existing)?.let { return it } }
        val now = Instant.now().toString()
        val payment = insert_payment(
            operation_type = PaymentOperationType.RESERVATION,
            owner_ref = reservation.profile_id,
            amount = amountOf(reservation.amount),
            status = PaymentStatus.APPROVED,
            created_at = now,
            approved_at = now,
            context_label = "${isoDateLabel(now)} · $DISTRICT_LABEL",
            summary = facility_name(reservation.zone_id),
            idempotency_key = "res:${reservation.reservation_id}",
        )
        // Receipt identified by the booking code of the reservation
        // (mockup 40: "Receipt SG-4821").
        insert_document(
            payment = payment,
            document_type = BillingDocumentType.ELECTRONIC_RECEIPT,
            fiscal_reference = booking_code(reservation),
            issued_at = now,
        )
        return payment
    }

    /**
     * US18 scenario 8 — the extension payment is recorded as a new
     * RESERVATION operation. No receipt is issued for extensions (the
     * mockups only define receipts for reservations and additional charges).
     */
    fun record_extension_payment(reservation: Reservation, extra_amount: Double): DigitalPayment? {
        val now = Instant.now().toString()
        return insert_payment(
            operation_type = PaymentOperationType.RESERVATION,
            owner_ref = reservation.profile_id,
            amount = amountOf(extra_amount),
            status = PaymentStatus.APPROVED,
            created_at = now,
            approved_at = now,
            context_label = "${isoDateLabel(now)} · $DISTRICT_LABEL",
            summary = "${facility_name(reservation.zone_id)} · Extension",
            idempotency_key = "ext:${reservation.reservation_id}:${reservation.updated_at}",
        )
    }

    /* ------------------------------------------------------------------ */
    /* refunds (US18 cancel and refund → US21 refund receipt)               */
    /* ------------------------------------------------------------------ */

    /**
     * Records the refund of a cancelled reservation so its receipt shows the
     * Refund adjustment (US21 scenario 3). Idempotent per payment.
     */
    fun record_refund(reservation_id: String) {
        val reservation = ParkingRepository.reservation_by_id(reservation_id) ?: return
        var payment = reservation.payment_ref?.let { payment_by_id(it) }
        if (payment == null) {
            // The reservation was never mirrored as a payment: record it now.
            payment = record_reservation_payment(reservation) ?: return
            val index = SpotGoDatabase.reservations.indexOfFirst { it.reservation_id == reservation_id }
            if (index >= 0) {
                SpotGoDatabase.reservations[index] =
                    SpotGoDatabase.reservations[index].copy(payment_ref = payment.payment_id)
            }
        }
        val recorded = payment
        if (SpotGoDatabase.refunds.any { it.payment_id == recorded.payment_id }) return
        SpotGoDatabase.refunds += Refund(
            refund_id = "rfd-${UUID.randomUUID().toString().take(8).uppercase(Locale.US)}",
            payment_id = recorded.payment_id,
            amount = recorded.amount,
            currency = recorded.currency,
            status = RefundStatus.PROCESSED,
            created_at = Instant.now().toString(),
        )
    }

    fun refund_of(payment_id: String): Refund? =
        SpotGoDatabase.refunds.firstOrNull { it.payment_id == payment_id }

    /* ------------------------------------------------------------------ */
    /* billing_documents (US21)                                             */
    /* ------------------------------------------------------------------ */

    /** Receipts &amp; invoices list (mockup 14), newest first. */
    fun documents(): List<BillingDocument> =
        SpotGoDatabase.billing_documents
            .filter { it.owner_ref == owner() }
            .sortedByDescending { it.issued_at }

    fun document_by_id(document_id: String): BillingDocument? =
        SpotGoDatabase.billing_documents.firstOrNull { it.document_id == document_id }

    fun document_of_payment(payment_id: String): BillingDocument? =
        SpotGoDatabase.billing_documents.firstOrNull { it.payment_id == payment_id }

    /** US21 scenario 1 — the document is marked as downloaded. */
    fun mark_downloaded(document_id: String) = set_document_flag(document_id) { it.copy(is_downloaded = true) }

    /** US21 scenario 2 — the document is marked as sent by e-mail. */
    fun mark_emailed(document_id: String) = set_document_flag(document_id) { it.copy(is_emailed = true) }

    private fun set_document_flag(document_id: String, update: (BillingDocument) -> BillingDocument) {
        val index = SpotGoDatabase.billing_documents.indexOfFirst { it.document_id == document_id }
        if (index >= 0) SpotGoDatabase.billing_documents[index] = update(SpotGoDatabase.billing_documents[index])
    }

    /**
     * Full projection of mockup 40 (receipt / invoice detail): header, the
     * Parking zone / Reservation / Payment method / Subtotal / Total paid
     * card and the Refund / Outstanding adjustments.
     */
    fun document_view(document_id: String): BillingDocumentView? {
        val document = document_by_id(document_id) ?: return null
        val payment = payment_by_id(document.payment_id) ?: return null
        val token = token_by_id(payment.payment_token_id)
        // An additional charge is linked to its reservation through the
        // balance id kept in idempotency_key ("bal:<balance_id>").
        val charge = if (payment.operation_type == PaymentOperationType.ADDITIONAL_CHARGE) {
            val balanceId = payment.idempotency_key.removePrefix("bal:")
            SpotGoDatabase.outstanding_balances.firstOrNull { it.balance_id == balanceId }
                ?.let { balance -> SpotGoDatabase.additional_charges.firstOrNull { it.charge_id == balance.source_reference } }
        } else {
            null
        }
        val reservation = SpotGoDatabase.reservations.firstOrNull { it.payment_ref == payment.payment_id }
            ?: charge?.let { ParkingRepository.reservation_by_id(it.reservation_id) }
        val zone = reservation?.let { ParkingRepository.zone_by_id(it.zone_id) }
        val spot = reservation?.let { ParkingRepository.spot_by_id(it.spot_id) }
        val isReceipt = document.document_type == BillingDocumentType.ELECTRONIC_RECEIPT
        val serviceLabel =
            if (payment.operation_type == PaymentOperationType.SUBSCRIPTION) "Subscription" else "Parking service"

        // US20 — when a paid plan is active the receipt shows the gross
        // subtotal, the plan discount and the net total actually paid.
        // Only payments made while the plan was active (created_at >=
        // start_at) carry the discount; the demo receipts issued before
        // keep subtotal = total.
        val sub = subscription()
        val discountPercent = subscription_discount_percent()
        val paidAmount = payment.amount.toDoubleOrNull() ?: 0.0
        val paidUnderPlan = sub != null && discountPercent > 0 &&
            payment.created_at.isNotBlank() &&
            runCatching { parseTimestamp(payment.created_at) >= parseTimestamp(sub.start_at) }
                .getOrDefault(false)
        val hasDiscount = paidUnderPlan && paidAmount > 0.0
        val grossAmount = if (hasDiscount) {
            Math.round(paidAmount * 100.0 / (100 - discountPercent)) / 100.0
        } else paidAmount
        val discountRows = if (hasDiscount) {
            listOf(DocumentRow("Plan discount", subscription()?.plan ?: "Plus plan",
                "-S/ ${amountOf(round2(grossAmount - paidAmount))}"))
        } else emptyList()

        val rows = when {
            reservation != null && zone != null -> listOf(
                DocumentRow("Parking zone", "${facility_name(zone.zone_id)} · San Isidro", "Zone"),
                DocumentRow(
                    "Reservation",
                    "${document.fiscal_reference} · Spot ${spot?.code ?: "—"}",
                    hoursLabel(reservation.start_at, reservation.end_at),
                ),
                DocumentRow("Payment method", token?.display_label ?: "••••", "Card"),
                DocumentRow("Subtotal", serviceLabel, "S/ ${amountOf(grossAmount)}"),
            ) + discountRows + listOf(
                DocumentRow("Total paid", "Paid successfully", "S/ ${amountOf(paidAmount)}"),
            )
            payment.operation_type == PaymentOperationType.SUBSCRIPTION -> listOf(
                DocumentRow("Subscription", payment.operation_label ?: payment.summary, "Plan"),
                DocumentRow("Payment method", token?.display_label ?: "••••", "Card"),
                DocumentRow("Subtotal", payment.summary, "S/ ${amountOf(paidAmount)}"),
                DocumentRow("Total paid", "Paid successfully", "S/ ${amountOf(paidAmount)}"),
            )
            // Receipt of a payment whose reservation is not in the demo data.
            else -> listOf(
                DocumentRow("Parking zone", payment.summary, "Zone"),
                DocumentRow("Payment method", token?.display_label ?: "••••", "Card"),
                DocumentRow("Subtotal", serviceLabel, "S/ ${amountOf(grossAmount)}"),
            ) + discountRows + listOf(
                DocumentRow("Total paid", "Paid successfully", "S/ ${amountOf(paidAmount)}"),
            )
        }

        // Adjustments (US21 scenario 3): processed refunds of this payment and
        // the current outstanding balance when it belongs to this document.
        val refund = refund_of(payment.payment_id)
        val refundAmount = refund?.amount?.toDoubleOrNull() ?: 0.0
        val outstanding = open_balance_amount_for(reservation)

        return BillingDocumentView(
            document = document,
            payment = payment,
            token = token,
            title = (if (isReceipt) "Receipt " else "Invoice ") + document.fiscal_reference,
            subtitle = "${fullDateLabel(document.issued_at)} · $serviceLabel",
            status_label = if (refundAmount > 0.0) "Refunded" else "Paid",
            rows = rows,
            adjustments = listOf(
                AdjustmentView(
                    label = "Refund",
                    value = if (refundAmount > 0.0) "-S/ ${amountOf(refundAmount)}" else "S/ 0.00",
                    positive = true,
                ),
                AdjustmentView(
                    label = "Outstanding",
                    value = outstanding ?: "S/ 0.00",
                    positive = false,
                ),
            ),
        )
    }

    /** Open balance amount when its charge belongs to the same reservation. */
    private fun open_balance_amount_for(reservation: Reservation?): String? {
        val balance = open_balance() ?: return null
        if (reservation == null) return null
        val charge = SpotGoDatabase.additional_charges.firstOrNull { it.charge_id == balance.source_reference }
            ?: return null
        return if (charge.reservation_id == reservation.reservation_id) "S/ ${balance.amount}" else null
    }

    /* ------------------------------------------------------------------ */
    /* outstanding balances (US19 scenarios 4-6)                            */
    /* ------------------------------------------------------------------ */

    fun open_balance(): OutstandingBalance? =
        SpotGoDatabase.outstanding_balances.firstOrNull {
            it.owner_ref == owner() && it.status == BalanceStatus.OPEN
        }

    /** US19 scenario 6 — an open balance blocks new reservations. */
    fun has_open_balance(): Boolean = open_balance() != null

    /** Mockup 38 projection: the open balance plus its additional charge. */
    fun outstanding_charge(): OutstandingChargeView? {
        val balance = open_balance() ?: return null
        val charge = SpotGoDatabase.additional_charges.firstOrNull { it.charge_id == balance.source_reference }
            ?: return null
        val reservation = ParkingRepository.reservation_by_id(charge.reservation_id)
        val label = if (reservation != null) {
            "Overstay · ${booking_code(reservation)}"
        } else {
            "Overstay · ${charge.charge_id}"
        }
        return OutstandingChargeView(balance = balance, charge = charge, label = label)
    }

    /**
     * US19 scenario 4 — overstay detected after the 5-minute tolerance of
     * parking_rules: an additional charge and an outstanding balance are
     * created (once) for the session. Called when the Driver opens the
     * details of a session that finished recently.
     */
    fun detect_overstay(reservation_id: String): OutstandingChargeView? {
        val reservation = ParkingRepository.reservation_by_id(reservation_id) ?: return null
        val end = parseTimestamp(reservation.end_at)
        val now = PeruClock.now()
        if (!end.plusMinutes(OVERSTAY_TOLERANCE_MINUTES).isBefore(now)) return null
        if (Duration.between(end, now).toHours() > OVERSTAY_WINDOW_HOURS) return null
        if (SpotGoDatabase.additional_charges.any { it.reservation_id == reservation_id }) return null
        if (has_open_balance()) return null

        val zone = ParkingRepository.zone_by_id(reservation.zone_id) ?: return null
        val createdAt = Instant.now().toString()
        // One overstay hour at the price of the zone (S/ 8.00 in Javier Prado).
        val chargeAmount = amountOf(zone.hourly_price)
        val charge = AdditionalCharge(
            charge_id = "chg-${UUID.randomUUID().toString().take(8).uppercase(Locale.US)}",
            reservation_id = reservation_id,
            driver_id = reservation.profile_id,
            amount = chargeAmount,
            status = ChargeStatus.OPEN,
            created_at = createdAt,
        )
        SpotGoDatabase.additional_charges += charge
        SpotGoDatabase.outstanding_balances += OutstandingBalance(
            balance_id = "bal-${UUID.randomUUID().toString().take(8).uppercase(Locale.US)}",
            owner_ref = reservation.profile_id,
            amount = chargeAmount,
            currency = "PEN",
            status = BalanceStatus.OPEN,
            source_reference = charge.charge_id,
            created_at = createdAt,
        )
        return outstanding_charge()
    }

    /**
     * US19 scenarios 5 and 6 — retry the payment of the outstanding balance
     * with the selected token (the caller simulates the 1.5 s approval).
     * Applies the subscription discount (Free 0% · Plus 10/15%) to the
     * additional charge and issues its receipt (US21).
     */
    fun pay_outstanding_balance(token_id: String): DigitalPayment? {
        val balance = open_balance() ?: return null
        val charge = SpotGoDatabase.additional_charges.firstOrNull { it.charge_id == balance.source_reference }
            ?: return null
        val now = Instant.now().toString()
        val discount = subscription_discount_percent()
        val gross = balance.amount.toDoubleOrNull() ?: 0.0
        val net = gross * (100 - discount) / 100.0
        val reservation = ParkingRepository.reservation_by_id(charge.reservation_id)

        val payment = insert_payment(
            operation_type = PaymentOperationType.ADDITIONAL_CHARGE,
            owner_ref = balance.owner_ref,
            amount = amountOf(net),
            status = PaymentStatus.APPROVED,
            created_at = now,
            approved_at = now,
            context_label = "${isoDateLabel(now)} · $DISTRICT_LABEL",
            summary = "Additional charge",
            idempotency_key = "bal:${balance.balance_id}",
            token_id = token_id,
            operation_label = reservation?.let { "Overstay · ${booking_code(it)}" },
        )
        insert_document(
            payment = payment,
            document_type = BillingDocumentType.ELECTRONIC_RECEIPT,
            // Receipt of the additional charge: "SG-" + hash of the charge.
            fiscal_reference = "SG-${abs(charge.charge_id.hashCode()) % 10000}",
            issued_at = now,
        )

        val chargeIndex = SpotGoDatabase.additional_charges.indexOfFirst { it.charge_id == charge.charge_id }
        if (chargeIndex >= 0) {
            SpotGoDatabase.additional_charges[chargeIndex] = charge.copy(status = ChargeStatus.PAID)
        }
        val balanceIndex = SpotGoDatabase.outstanding_balances.indexOfFirst { it.balance_id == balance.balance_id }
        if (balanceIndex >= 0) {
            SpotGoDatabase.outstanding_balances[balanceIndex] = balance.copy(status = BalanceStatus.PAID)
        }
        return payment
    }

    /* ------------------------------------------------------------------ */
    /* internals                                                            */
    /* ------------------------------------------------------------------ */

    private fun facility_name(zone_id: String): String {
        val zone = ParkingRepository.zone_by_id(zone_id)
        val facilityId = zone?.facility_id
        return SpotGoDatabase.parking_facilities.firstOrNull { it.facility_id == facilityId }?.name
            ?: zone?.name
            ?: "SpotGo"
    }

    private fun insert_payment(
        operation_type: String,
        owner_ref: String,
        amount: String,
        status: String,
        created_at: String,
        approved_at: String?,
        context_label: String,
        summary: String,
        idempotency_key: String,
        token_id: String = default_token()?.token_id ?: "",
        operation_label: String? = null,
    ): DigitalPayment {
        val paymentId = "pay-${UUID.randomUUID().toString().take(8).uppercase(Locale.US)}"
        val payment = DigitalPayment(
            payment_id = paymentId,
            payment_token_id = token_id,
            operation_type = operation_type,
            owner_ref = owner_ref,
            amount = amount,
            currency = "PEN",
            status = status,
            provider_operation_ref = "prov-${paymentId.takeLast(6)}",
            idempotency_key = idempotency_key,
            request_hash = "req-${paymentId.takeLast(6)}",
            first_seen_at = created_at,
            last_seen_at = approved_at ?: created_at,
            result_reference = null,
            created_at = created_at,
            approved_at = approved_at,
            context_label = context_label,
            summary = summary,
            operation_label = operation_label,
        )
        SpotGoDatabase.digital_payments += payment
        SpotGoDatabase.payment_attempts += PaymentAttempt(
            attempt_id = "att-${paymentId.takeLast(6)}",
            payment_id = paymentId,
            number = 1,
            started_at = created_at,
            finished_at = approvedAtOrNull(status, approved_at),
            status = if (status == PaymentStatus.APPROVED) PaymentAttemptStatus.SUCCEEDED else PaymentAttemptStatus.STARTED,
            error_code = null,
            next_attempt_at = null,
        )
        return payment
    }

    private fun approvedAtOrNull(status: String, approved_at: String?): String? =
        if (status == PaymentStatus.APPROVED) approved_at else null

    private fun insert_document(
        payment: DigitalPayment,
        document_type: String,
        fiscal_reference: String,
        issued_at: String,
    ) {
        val document = BillingDocument(
            document_id = "doc-${UUID.randomUUID().toString().take(8).uppercase(Locale.US)}",
            payment_id = payment.payment_id,
            owner_ref = payment.owner_ref,
            document_type = document_type,
            fiscal_reference = fiscal_reference,
            issued_at = issued_at,
            pdf_uri = null,
        )
        SpotGoDatabase.billing_documents += document
        val index = SpotGoDatabase.digital_payments.indexOfFirst { it.payment_id == payment.payment_id }
        if (index >= 0) {
            SpotGoDatabase.digital_payments[index] = payment.copy(result_reference = document.document_id)
        }
    }

    /** "29 Sep 2026" date of the receipt / invoice header (mockup 40). */
    fun fullDateLabel(iso: String): String {
        val date = if (iso.endsWith("Z")) {
            java.time.Instant.parse(iso).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        } else {
            parseTimestamp(iso).toLocalDate()
        }
        return date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
    }

    /** "29 Sep" date used by the documents list (mockup 14). */
    fun shortDateLabel(iso: String): String {
        val date = if (iso.endsWith("Z")) {
            java.time.Instant.parse(iso).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        } else {
            parseTimestamp(iso).toLocalDate()
        }
        return date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    }
}
