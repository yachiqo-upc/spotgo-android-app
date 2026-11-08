package com.yachiqo.spotgo.data.repository

import com.yachiqo.spotgo.TestDatabase
import com.yachiqo.spotgo.data.local.SpotGoDatabase
import com.yachiqo.spotgo.data.model.BalanceStatus
import com.yachiqo.spotgo.data.model.BillingDocumentType
import com.yachiqo.spotgo.data.model.ChargeStatus
import com.yachiqo.spotgo.data.model.LockStatus
import com.yachiqo.spotgo.data.model.RefundStatus
import com.yachiqo.spotgo.data.model.Reservation
import com.yachiqo.spotgo.data.model.ReservationStatus
import com.yachiqo.spotgo.data.model.SubscriptionPlan
import com.yachiqo.spotgo.data.remote.SpotGoApi
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test

/**
 * Payments & Billing (US19 payment of reservations and additional charges,
 * US20 plans, US21 receipts and invoices) against the seeded demo data.
 */
class PaymentRepositoryTest {

    private lateinit var db: TestDatabase

    @Before
    fun setUp() {
        db = TestDatabase()
        AuthRepository.sign_out()
        assertTrue(
            AuthRepository.sign_in("brigitte.carhuaz@example.com", "SpotGo2026", true) is AuthResult.Success,
        )
    }

    @After
    fun tearDown() {
        AuthRepository.sign_out()
        db.restore()
    }

    /** Pays the seeded S/ 8.00 overstay balance so it stops blocking the flow. */
    private fun settleOpenBalance() {
        if (PaymentRepository.has_open_balance()) {
            PaymentRepository.pay_outstanding_balance(PaymentRepository.default_token()!!.token_id)
        }
        assertFalse(PaymentRepository.has_open_balance())
    }

    /* ------------------------------------------------------------------ */
    /* payment_tokens / transactions / documents                            */
    /* ------------------------------------------------------------------ */

    @Test
    fun theSavedCardsOfTheDriverAreListedWithTheDefaultFirst() {
        val tokens = PaymentRepository.tokens()
        assertEquals(2, tokens.size)
        val default = PaymentRepository.default_token()
        assertEquals("Visa", default?.brand)
        assertEquals("4242", default?.last_four)
        assertTrue(default!!.is_default)
        assertEquals("Visa •••• 4242", PaymentRepository.token_by_id("ptk-seed-0001")?.display_label)
        assertNull(PaymentRepository.token_by_id("ptk-unknown"))
    }

    @Test
    fun theTransactionHistoryIsScopedToTheDriverAndSortedNewestFirst() {
        val transactions = PaymentRepository.transactions()
        assertTrue(transactions.isNotEmpty())
        assertTrue(transactions.all { it.owner_ref == "prf-seed-0001" })
        val dates = transactions.map { it.created_at }
        assertEquals(dates.sortedDescending(), dates)
        assertTrue(transactions.any { it.payment_id == "pay-seed-0001" })
        assertTrue(transactions.any { it.payment_id == "pay-seed-0004" })

        AuthRepository.sign_out()
        assertTrue(PaymentRepository.transactions().isEmpty())
        assertTrue(PaymentRepository.documents().isEmpty())
    }

    @Test
    fun receiptsAndInvoicesOfTheDriverAreListedNewestFirst() {
        val documents = PaymentRepository.documents()
        assertEquals(5, documents.size)
        val dates = documents.map { it.issued_at }
        assertEquals(dates.sortedDescending(), dates)
        assertTrue(documents.any { it.fiscal_reference == "SG-4821" })
        assertTrue(documents.any { it.document_type == BillingDocumentType.ELECTRONIC_INVOICE })
        assertNull(PaymentRepository.document_by_id("doc-unknown"))
    }

    /* ------------------------------------------------------------------ */
    /* US21 — receipt / invoice detail                                      */
    /* ------------------------------------------------------------------ */

    @Test
    fun theReceiptOfMockup40ShowsTheZoneTheSpotAndTheTotals() {
        val view = PaymentRepository.document_view("doc-seed-0001")
        assertNotNull(view)
        assertEquals("Receipt SG-4821", view!!.title)
        assertEquals("Paid", view.status_label)
        assertEquals("Parking service", view.subtitle.substringAfter(" · "))

        val rows = view.rows.associateBy { it.label }
        assertEquals("Zone", rows["Parking zone"]?.tag)
        assertEquals("Card", rows["Payment method"]?.tag)
        assertEquals("Visa •••• 4242", rows["Payment method"]?.value)
        // The money of the breakdown lives in the tag of the row.
        assertEquals("Parking service", rows["Subtotal"]?.value)
        assertEquals("S/ 16.00", rows["Subtotal"]?.tag)
        assertEquals("S/ 16.00", rows["Total paid"]?.tag)
        assertTrue(rows["Reservation"]?.value?.contains("SG-4821") == true)
        assertTrue(rows["Reservation"]?.value?.contains("Spot B-14") == true)

        // The seeded open balance belongs to this very reservation.
        assertEquals("S/ 0.00", view.adjustments[0].value)
        assertEquals("S/ 8.00", view.adjustments[1].value)
    }

    @Test
    fun aRefundedPaymentShowsTheRefundAdjustment() {
        val view = PaymentRepository.document_view("doc-seed-0004")
        assertNotNull(view)
        assertEquals("Refunded", view!!.status_label)
        assertEquals("-S/ 14.00", view.adjustments[0].value)
        assertEquals("S/ 0.00", view.adjustments[1].value)
        // Its balance is not the seeded one, so no outstanding is shown.
        assertNull(PaymentRepository.document_view("doc-unknown"))
    }

    @Test
    fun theSubscriptionInvoiceShowsThePlanAndTheTotal() {
        val view = PaymentRepository.document_view("doc-seed-0005")
        assertNotNull(view)
        assertEquals("Invoice F001-0048", view!!.title)
        val rows = view.rows.associateBy { it.label }
        assertEquals("Plus Monthly", rows["Subscription"]?.value)
        assertEquals("S/ 14.90", rows["Subtotal"]?.tag)
        assertEquals("S/ 14.90", rows["Total paid"]?.tag)
        assertEquals("Paid successfully", rows["Total paid"]?.value)
    }

    @Test
    fun theDownloadedAndEmailedFlagsAreMarked() {
        val document = PaymentRepository.document_by_id("doc-seed-0001")!!
        assertFalse(document.is_downloaded)
        assertFalse(document.is_emailed)

        PaymentRepository.mark_downloaded("doc-seed-0001")
        PaymentRepository.mark_emailed("doc-seed-0001")
        assertTrue(PaymentRepository.document_by_id("doc-seed-0001")!!.is_downloaded)
        assertTrue(PaymentRepository.document_by_id("doc-seed-0001")!!.is_emailed)
        // Unknown ids are ignored instead of crashing.
        PaymentRepository.mark_downloaded("doc-unknown")
    }

    @Test
    fun sendingTheReceiptByEmailWaitsForTheBackend() {
        // The demo build has no SPOTGO_API_BASE_URL, so the feature reports
        // "backend missing" and nothing is marked as sent (Pendiente 3).
        Assume.assumeFalse(SpotGoApi.is_configured())
        val result = PaymentRepository.send_document_by_email("doc-seed-0001", "Receipt SG-4821", "Body")
        assertEquals(EmailSendResult.BackendMissing, result)
        assertFalse(PaymentRepository.document_by_id("doc-seed-0001")!!.is_emailed)

        // Without a session there is no recipient at all.
        AuthRepository.sign_out()
        val noRecipient = PaymentRepository.send_document_by_email("doc-seed-0001", "Receipt SG-4821", "Body")
        assertTrue(noRecipient is EmailSendResult.Failed)
    }

    /* ------------------------------------------------------------------ */
    /* US19 — outstanding balance and overstay                              */
    /* ------------------------------------------------------------------ */

    @Test
    fun theSeededOverstayBalanceIsProjectedAndPaysOff() {
        assertTrue(PaymentRepository.has_open_balance())
        val charge = PaymentRepository.outstanding_charge()
        assertNotNull(charge)
        assertEquals("8.00", charge!!.charge.amount)
        assertEquals(ChargeStatus.OPEN, charge.charge.status)
        assertEquals("Overstay · ${ParkingRepository.booking_code(ParkingRepository.reservation_by_id("res-seed-06264")!!)}", charge.label)

        val payment = PaymentRepository.pay_outstanding_balance(PaymentRepository.default_token()!!.token_id)!!
        // Free plan → no discount on the additional charge (US20).
        assertEquals("8.00", payment.amount)
        assertEquals("APPROVED", payment.status)

        assertFalse(PaymentRepository.has_open_balance())
        assertNull(PaymentRepository.open_balance())
        assertNull(PaymentRepository.outstanding_charge())
        assertEquals(BalanceStatus.PAID, SpotGoDatabase.outstanding_balances.first { it.balance_id == "bal-seed-0001" }.status)
        assertEquals(ChargeStatus.PAID, SpotGoDatabase.additional_charges.first { it.charge_id == "chg-seed-0001" }.status)

        // The receipt of the additional charge was issued.
        val receipt = PaymentRepository.document_of_payment(payment.payment_id)
        assertNotNull(receipt)
        assertEquals(BillingDocumentType.ELECTRONIC_RECEIPT, receipt?.document_type)
        assertTrue(receipt!!.fiscal_reference.startsWith("SG-"))
    }

    @Test
    fun anOverstayIsDetectedOnlyAfterTheToleranceAndOnlyOnce() {
        settleOpenBalance()
        val zone = ParkingRepository.zone_by_id("zne-seed-0003")!!
        val spot = ParkingRepository.spot_by_id(
            ParkingRepository.spots_of_zone(zone.zone_id).first { it.code == "B-10" }.spot_id,
        )!!

        fun session(id: String, ended: LocalDateTime): Reservation = Reservation(
            reservation_id = id,
            tenant_id = zone.tenant_id,
            profile_id = "prf-seed-0001",
            vehicle_id = "veh-seed-0001",
            zone_id = zone.zone_id,
            spot_id = spot.spot_id,
            start_at = ended.minusHours(2).toString(),
            end_at = ended.toString(),
            amount = 16.0,
            status = ReservationStatus.COMPLETED,
            payment_ref = null,
            lock_status = LockStatus.RELEASED,
            locked_at = null,
            lock_expires_at = null,
            created_at = ended.minusDays(1).toString(),
            updated_at = ended.toString(),
        )

        // Still inside the 5-minute tolerance: nothing to charge.
        SpotGoDatabase.reservations += session("res-test-tolerance", PeruClock.now().minusMinutes(2))
        assertNull(PaymentRepository.detect_overstay("res-test-tolerance"))

        // 1 hour late (and inside the 24 h demo window): one hour of the zone.
        SpotGoDatabase.reservations += session("res-test-late", PeruClock.now().minusMinutes(60))
        val detected = PaymentRepository.detect_overstay("res-test-late")
        assertNotNull(detected)
        assertEquals("8.00", detected!!.charge.amount)
        assertEquals(ChargeStatus.OPEN, detected.charge.status)
        assertTrue(detected.label.startsWith("Overstay · SG-"))

        // A second detection for the same session does not charge twice.
        assertNull(PaymentRepository.detect_overstay("res-test-late"))
        assertEquals(1, SpotGoDatabase.additional_charges.count { it.reservation_id == "res-test-late" })
    }

    @Test
    fun anOverstayIsIgnoredWhenTheSessionIsTooOld() {
        settleOpenBalance()
        val zone = ParkingRepository.zone_by_id("zne-seed-0003")!!
        SpotGoDatabase.reservations += Reservation(
            reservation_id = "res-test-old",
            tenant_id = zone.tenant_id,
            profile_id = "prf-seed-0001",
            vehicle_id = "veh-seed-0001",
            zone_id = zone.zone_id,
            spot_id = ParkingRepository.spots_of_zone(zone.zone_id).first().spot_id,
            start_at = PeruClock.now().minusDays(3).minusHours(2).toString(),
            end_at = PeruClock.now().minusDays(3).toString(),
            amount = 16.0,
            status = ReservationStatus.COMPLETED,
            payment_ref = null,
            lock_status = LockStatus.RELEASED,
            locked_at = null,
            lock_expires_at = null,
            created_at = PeruClock.now().minusDays(4).toString(),
            updated_at = PeruClock.now().minusDays(3).toString(),
        )
        assertNull(PaymentRepository.detect_overstay("res-test-old"))
        assertTrue(SpotGoDatabase.additional_charges.none { it.reservation_id == "res-test-old" })
    }

    @Test
    fun theRefundOfTheCancelledSeedReservationIsProcessed() {
        val refund = PaymentRepository.refund_of("pay-0003")
        assertNotNull(refund)
        assertEquals(RefundStatus.PROCESSED, refund?.status)
        assertEquals("14.00", refund?.amount)
        assertNull(PaymentRepository.refund_of("pay-seed-0001"))
    }

    /* ------------------------------------------------------------------ */
    /* US20 — plans                                                         */
    /* ------------------------------------------------------------------ */

    @Test
    fun theSeedSubscriptionIsFreeWithoutDiscount() {
        assertEquals(SubscriptionPlan.FREE, PaymentRepository.subscription()?.plan)
        assertEquals(0, PaymentRepository.subscription_discount_percent())
        assertFalse(PaymentRepository.renewal_cancelled())
        assertEquals(0.0, ParkingRepository.discount_amount(8.0), 0.001)
        assertEquals(8.0, ParkingRepository.quoted_amount(8.0), 0.001)
    }

    @Test
    fun aPaidPlanActivatesTheDiscountAndIssuesItsInvoice() {
        val payment = PaymentRepository.subscribe_plan(SubscriptionPlan.ANNUAL, 149.0)!!
        assertEquals("APPROVED", payment.status)
        assertEquals("149.00", payment.amount)
        assertEquals(SubscriptionPlan.ANNUAL, PaymentRepository.subscription()?.plan)
        assertEquals(15, PaymentRepository.subscription_discount_percent())
        assertTrue(PaymentRepository.subscription()!!.auto_renew)
        assertTrue(PaymentRepository.subscription()!!.end_at.isNotEmpty())
        assertFalse(PaymentRepository.renewal_cancelled())

        // The invoice of the plan (next number of the demo series).
        val invoice = PaymentRepository.document_of_payment(payment.payment_id)
        assertNotNull(invoice)
        assertEquals(BillingDocumentType.ELECTRONIC_INVOICE, invoice?.document_type)
        assertEquals("F001-0049", invoice?.fiscal_reference)

        // Every quoted amount now carries the 15% discount (US20).
        assertEquals(85.0, ParkingRepository.quoted_amount(100.0), 0.001)
        assertEquals(15.0, ParkingRepository.discount_amount(100.0), 0.001)

        // US20 scenario 5 — the renewal can be cancelled and resumed.
        PaymentRepository.cancel_renewal()
        assertTrue(PaymentRepository.renewal_cancelled())
        PaymentRepository.resume_renewal()
        assertFalse(PaymentRepository.renewal_cancelled())

        // US20 scenario 1 — switching back to Free is immediate.
        PaymentRepository.activate_free_plan()
        assertEquals(SubscriptionPlan.FREE, PaymentRepository.subscription()?.plan)
        assertEquals(0, PaymentRepository.subscription_discount_percent())
        assertEquals(100.0, ParkingRepository.quoted_amount(100.0), 0.001)
    }

    @Test
    fun monthlyPlanAppliesATenPercentDiscount() {
        val payment = PaymentRepository.subscribe_plan(SubscriptionPlan.MONTHLY, 14.90)!!
        assertEquals("14.90", payment.amount)
        assertEquals(10, PaymentRepository.subscription_discount_percent())
        assertEquals(90.0, ParkingRepository.quoted_amount(100.0), 0.001)
        assertEquals(10.0, ParkingRepository.discount_amount(100.0), 0.001)
    }

    @Test
    fun subscribingRequiresASessionAndAKnownPlan() {
        assertNull(PaymentRepository.subscribe_plan(SubscriptionPlan.FREE, 0.0))
        assertNull(PaymentRepository.subscribe_plan("Not a plan", 10.0))

        AuthRepository.sign_out()
        assertNull(PaymentRepository.subscribe_plan(SubscriptionPlan.MONTHLY, 14.90))
        PaymentRepository.activate_free_plan() // ignored without a session
        assertTrue(PaymentRepository.subscription() != null)
    }
}
