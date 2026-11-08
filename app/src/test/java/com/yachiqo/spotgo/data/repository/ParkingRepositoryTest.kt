package com.yachiqo.spotgo.data.repository

import com.yachiqo.spotgo.TestDatabase
import com.yachiqo.spotgo.data.local.SpotGoDatabase
import com.yachiqo.spotgo.data.model.LockStatus
import com.yachiqo.spotgo.data.model.OccupancyStatus
import com.yachiqo.spotgo.data.model.ReservationStatus
import com.yachiqo.spotgo.data.model.SpotOperationalStatus
import com.yachiqo.spotgo.data.model.SubscriptionPlan
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Explore + reserve flow (US01 availability projection, US04 permitted zones,
 * US18 reservation lifecycle) and the shared date / money helpers.
 */
class ParkingRepositoryTest {

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

    /** Pays the seeded open balance so it does not block new reservations. */
    private fun settleOpenBalance() {
        if (PaymentRepository.has_open_balance()) {
            val token = PaymentRepository.default_token()
            assertNotNull(token)
            PaymentRepository.pay_outstanding_balance(token!!.token_id)
        }
        assertFalse(PaymentRepository.has_open_balance())
    }

    private fun spotOf(code: String) = SpotGoDatabase.parking_spots.first { it.code == code }

    private fun defaultVehicle() = VehicleRepository.default_vehicle()!!

    private fun zone(id: String) = ParkingRepository.zone_by_id(id)!!

    private fun error(result: ReservationResult): ReservationError =
        (result as ReservationResult.Failure).error

    private fun rejection(result: ReservationUpdateResult): UpdateRejection =
        (result as ReservationUpdateResult.Rejected).reason

    /* ------------------------------------------------------------------ */
    /* US01 — availability projection                                       */
    /* ------------------------------------------------------------------ */

    @Test
    fun theDemoZonesAndSpotsAreSeeded() {
        val zones = ParkingRepository.zones()
        assertEquals(5, zones.size)
        assertEquals("Javier Prado Parking", zone("zne-seed-0003").name)
        assertEquals(8.0, zone("zne-seed-0003").hourly_price, 0.001)
        assertEquals(8, ParkingRepository.spots_of_zone("zne-seed-0003").size)
        assertNull(ParkingRepository.zone_by_id("zne-unknown"))
    }

    @Test
    fun aSpotIsAvailableWhenTheSensorOperationalAndHoldsSaySo() {
        // Free spot: operational sensor AVAILABLE, no guest, no reservation.
        assertTrue(ParkingRepository.is_spot_available(spotOf("B-10")))

        // Held by an open guest session (US01 scenario 2).
        assertFalse(ParkingRepository.is_spot_available(spotOf("V-05")))
        // Sensor reports OCCUPIED.
        assertFalse(ParkingRepository.is_spot_available(spotOf("N-04")))
        assertEquals(OccupancyStatus.OCCUPIED, ParkingRepository.latest_reading(spotOf("N-04").spot_id)?.occupancy_status)
        // Spot taken out of service.
        assertFalse(ParkingRepository.is_spot_available(spotOf("F-04")))
        assertEquals(SpotOperationalStatus.OUT_OF_SERVICE, spotOf("F-04").operational_status)
        // Held by an existing reservation.
        assertFalse(ParkingRepository.is_spot_available(spotOf("F-01")))
    }

    @Test
    fun availabilityIsCountedPerZone() {
        // The single-spot zone is the one used by US01 scenario 2.
        assertEquals(1, ParkingRepository.available_count("zne-seed-0004"))
        assertTrue(ParkingRepository.available_spots("zne-seed-0004").any { it.code == "C-01" })
        assertEquals(3, ParkingRepository.available_count("zne-seed-0001"))
    }

    /* ------------------------------------------------------------------ */
    /* US18 — create                                                        */
    /* ------------------------------------------------------------------ */

    @Test
    fun aReservationNeedsAVehicle() {
        settleOpenBalance()
        val result = ParkingRepository.create_reservation(
            zone("zne-seed-0004"), spotOf("C-01"), null, "Tomorrow", "10:30 AM", 2,
        )
        assertEquals(ReservationError.VEHICLE_REQUIRED, error(result))
    }

    @Test
    fun anOutstandingBalanceBlocksNewReservations() {
        // The demo starts with the S/ 8.00 overstay balance open (US19 scenario 6).
        assertTrue(PaymentRepository.has_open_balance())
        val result = ParkingRepository.create_reservation(
            zone("zne-seed-0004"), spotOf("C-01"), defaultVehicle(), "Tomorrow", "10:30 AM", 2,
        )
        assertEquals(ReservationError.OUTSTANDING_BALANCE, error(result))
        assertTrue(ParkingRepository.reservations_for_current_profile().none { it.status == ReservationStatus.PENDING_PAYMENT })
    }

    @Test
    fun aVehicleThatAlreadyHoldsASpotCannotReserveAgain() {
        settleOpenBalance()
        // veh-seed-0002 is the vehicle of the seeded upcoming reservation.
        val busy = VehicleRepository.vehicle_by_id("veh-seed-0002")!!
        val result = ParkingRepository.create_reservation(
            zone("zne-seed-0004"), spotOf("C-01"), busy, "Tomorrow", "10:30 AM", 2,
        )
        assertEquals(ReservationError.VEHICLE_BUSY, error(result))
    }

    @Test
    fun aSpotThatStoppedBeingAvailableIsRejected() {
        settleOpenBalance()
        val result = ParkingRepository.create_reservation(
            zone("zne-seed-0002"), spotOf("V-05"), defaultVehicle(), "Tomorrow", "10:30 AM", 2,
        )
        assertEquals(ReservationError.SPOT_TAKEN, error(result))
    }

    @Test
    fun aReservationCannotStartInThePastOfPeru() {
        settleOpenBalance()
        // "Today" at midnight has already passed whatever the wall clock is.
        val result = ParkingRepository.create_reservation(
            zone("zne-seed-0004"), spotOf("C-01"), defaultVehicle(), "Today", "12:00 AM", 2,
        )
        assertEquals(ReservationError.PAST_TIME, error(result))
        assertTrue(ParkingRepository.reservations_for_current_profile().none { it.status == ReservationStatus.PENDING_PAYMENT })
    }

    @Test
    fun creatingAReservationHoldsTheSpotAndQuotesThePlanDiscount() {
        settleOpenBalance()
        val spot = spotOf("C-01")
        assertTrue(ParkingRepository.is_spot_available(spot))

        val result = ParkingRepository.create_reservation(
            zone("zne-seed-0004"), spot, defaultVehicle(), "Tomorrow", "10:30 AM", 2,
        )
        assertTrue(result is ReservationResult.Success)
        val reservation = (result as ReservationResult.Success).reservation

        assertEquals(ReservationStatus.PENDING_PAYMENT, reservation.status)
        assertEquals(LockStatus.HELD, reservation.lock_status)
        assertNotNull(reservation.lock_expires_at)
        // Free plan → 0% discount → S/ 5.00 × 2 h.
        assertEquals(10.0, reservation.amount, 0.001)
        assertEquals(ParkingRepository.quoted_amount(10.0), reservation.amount, 0.001)
        // The temporary lock hides the spot from the other Drivers.
        assertFalse(ParkingRepository.is_spot_available(spot))

        // With Plus Monthly the same booking is quoted 10% cheaper (US20).
        PaymentRepository.subscribe_plan(SubscriptionPlan.MONTHLY, 14.90)
        assertEquals(10.0, ParkingRepository.quoted_amount(11.11), 0.001)
        assertEquals(1.11, ParkingRepository.discount_amount(11.11), 0.001)
    }

    /* ------------------------------------------------------------------ */
    /* US18 — approve, cancel, modify, extend                               */
    /* ------------------------------------------------------------------ */

    @Test
    fun approvingThePaymentConfirmsTheReservationAndIssuesTheReceipt() {
        settleOpenBalance()
        val created = ParkingRepository.create_reservation(
            zone("zne-seed-0003"), spotOf("B-10"), defaultVehicle(), "Tomorrow", "10:30 AM", 2,
        ) as ReservationResult.Success
        val reservation = created.reservation

        val approved = ParkingRepository.approve_payment(reservation.reservation_id)
        assertNotNull(approved)
        assertEquals(ReservationStatus.RESERVED, approved?.status)
        assertEquals(LockStatus.RELEASED, approved?.lock_status)
        assertNotNull(approved?.payment_ref)
        assertEquals(approved, ParkingRepository.last_reservation)

        // US19 — the digital payment is recorded and approved.
        val payment = PaymentRepository.payment_by_id(approved!!.payment_ref!!)
        assertNotNull(payment)
        assertEquals("APPROVED", payment?.status)
        assertEquals("16.00", payment?.amount)

        // US21 — the receipt uses the booking code of the reservation.
        val document = PaymentRepository.document_of_payment(payment!!.payment_id)
        assertNotNull(document)
        assertEquals(ParkingRepository.booking_code(approved), document?.fiscal_reference)
        // The spot is now held by the confirmed reservation itself.
        assertFalse(ParkingRepository.is_spot_available(spotOf("B-10")))
        assertEquals(1, ParkingRepository.reservations_for_current_profile().count { it.reservation_id == reservation.reservation_id })
    }

    @Test
    fun cancellingAConfirmedReservationReleasesTheSpotAndRecordsTheRefund() {
        settleOpenBalance()
        val reservation = (ParkingRepository.create_reservation(
            zone("zne-seed-0004"), spotOf("C-01"), defaultVehicle(), "Tomorrow", "10:30 AM", 2,
        ) as ReservationResult.Success).reservation
        val approved = ParkingRepository.approve_payment(reservation.reservation_id)!!
        val spot = spotOf("C-01")
        assertFalse(ParkingRepository.is_spot_available(spot))

        val cancelled = ParkingRepository.cancel_reservation(reservation.reservation_id)
        assertNotNull(cancelled)
        assertEquals(ReservationStatus.CANCELLED, cancelled?.status)
        assertEquals(LockStatus.RELEASED, cancelled?.lock_status)
        // US18 scenario 7 — the refund of the original payment is simulated.
        assertNotNull(PaymentRepository.refund_of(approved.payment_ref!!))
        // The spot goes back to the Explore list.
        assertTrue(ParkingRepository.is_spot_available(spot))
        // Cancelling twice or an unknown id does nothing.
        assertNull(ParkingRepository.cancel_reservation("res-does-not-exist"))
    }

    @Test
    fun modifyingAReservationValidatesClosingTimePastTimeAndSpot() {
        settleOpenBalance()
        val reservation = (ParkingRepository.create_reservation(
            zone("zne-seed-0004"), spotOf("C-01"), defaultVehicle(), "Tomorrow", "10:30 AM", 2,
        ) as ReservationResult.Success).reservation

        // Unknown id.
        assertEquals(
            UpdateRejection.NOT_FOUND,
            rejection(ParkingRepository.update_reservation("res-unknown", "Tomorrow", "11:00 AM", 1)),
        )
        // Starts in the past.
        assertEquals(
            UpdateRejection.PAST_TIME,
            rejection(ParkingRepository.update_reservation(reservation.reservation_id, "Today", "12:00 AM", 1)),
        )
        // Ends after the closing time of the zone (11:00 PM).
        assertEquals(
            UpdateRejection.BEYOND_CLOSING,
            rejection(ParkingRepository.update_reservation(reservation.reservation_id, "Tomorrow", "10:00 PM", 2)),
        )

        // A valid change recalculates the total with the plan discount.
        val updated = ParkingRepository.update_reservation(reservation.reservation_id, "Tomorrow", "11:00 AM", 1)
        assertTrue(updated is ReservationUpdateResult.Updated)
        val changed = (updated as ReservationUpdateResult.Updated).reservation
        assertEquals(LocalTime.of(11, 0), parseTimestamp(changed.start_at).toLocalTime())
        assertEquals(LocalTime.of(12, 0), parseTimestamp(changed.end_at).toLocalTime())
        assertEquals(5.0, changed.amount, 0.001)
        assertEquals(changed, ParkingRepository.reservation_by_id(reservation.reservation_id))
    }

    @Test
    fun extendingAReservationAddsTheDifferenceAndRecordsThePayment() {
        settleOpenBalance()
        val reservation = (ParkingRepository.create_reservation(
            zone("zne-seed-0004"), spotOf("C-01"), defaultVehicle(), "Tomorrow", "10:30 AM", 2,
        ) as ReservationResult.Success).reservation
        ParkingRepository.approve_payment(reservation.reservation_id)
        val paymentsBefore = PaymentRepository.transactions().size

        val newEnd = LocalDateTime.of(PeruClock.today().plusDays(1), LocalTime.of(18, 0))
        val extended = ParkingRepository.extend_reservation(reservation.reservation_id, newEnd, 5.0)
        assertNotNull(extended)
        assertEquals(15.0, extended!!.amount, 0.001)
        assertEquals(LocalTime.of(18, 0), parseTimestamp(extended.end_at).toLocalTime())
        assertEquals(extended, ParkingRepository.reservation_by_id(reservation.reservation_id))

        // US18 — the extension is paid as an extra RESERVATION operation.
        val transactions = PaymentRepository.transactions()
        assertEquals(paymentsBefore + 1, transactions.size)
        assertTrue(transactions.any { it.amount == "5.00" })

        // An extension into the past is refused.
        val pastEnd = LocalDateTime.of(PeruClock.today().minusDays(1), java.time.LocalTime.of(18, 0))
        assertNull(ParkingRepository.extend_reservation(reservation.reservation_id, pastEnd, 5.0))
        assertNull(ParkingRepository.extend_reservation("res-unknown", newEnd, 5.0))
    }

    /* ------------------------------------------------------------------ */
    /* Lists, timestamps and the shared formatting helpers                  */
    /* ------------------------------------------------------------------ */

    @Test
    fun theReservationListIsScopedToTheSignedInDriver() {
        val seedDriver = ParkingRepository.reservations_for_current_profile()
        assertEquals(4, seedDriver.size)
        assertTrue(seedDriver.any { it.reservation_id == "res-seed-0001" })

        AuthRepository.sign_out()
        assertTrue(ParkingRepository.reservations_for_current_profile().isEmpty())
    }

    @Test
    fun refreshUpdatesTheDisplayedTimestamp() {
        assertEquals(ParkingRepository.INITIAL_LAST_UPDATED, ParkingRepository.last_updated)
        ParkingRepository.refresh()
        assertTrue(
            "last updated must look like '3:07 PM'",
            Regex("""^\d{1,2}:\d{2} (AM|PM)$""").matches(ParkingRepository.last_updated),
        )
    }

    @Test
    fun theStartOptionsOfTodayDropTheTimesAlreadyPassed() {
        val all = START_TIME_OPTIONS
        assertEquals(12, all.size)
        assertEquals("10:30 AM", all.first())
        assertEquals(all, availableStartOptions("Tomorrow"))

        val today = availableStartOptions("Today")
        assertTrue(today.size <= all.size)
        assertTrue(today.all { parseClock(it) > PeruClock.minutesOfDay() })
        assertTrue(today.all { all.contains(it) })
    }

    @Test
    fun dateAndMoneyHelpersMatchTheMockups() {
        assertEquals("S/ 16.00", money(16.0))
        assertEquals("S/ 8.00", money(8.0))
        assertEquals("S/ 8", moneyWhole(8.0))
        assertEquals("S/8", moneyCompact(8.4))
        assertEquals("S/ 16", moneyWhole(16.9))

        assertEquals(630, parseClock("10:30 AM"))
        assertEquals(720, parseClock("12:00 PM"))
        assertEquals(0, parseClock("12:00 AM"))
        assertEquals(1380, parseClock("11:00 PM"))
        assertEquals("10:30 AM", formatClock(630))
        assertEquals("11:59 PM", formatClock(23 * 60 + 59))

        assertEquals(8.01, round2(8.005), 0.001)
        assertEquals("2 h", hoursLabel("2026-11-09T10:30:00", "2026-11-09T12:30:00"))
        assertEquals("1 h 30 min", hoursLabel("2026-11-09T10:30:00", "2026-11-09T12:00:00"))
        assertEquals("10:30 AM – 12:30 PM", isoRange("2026-11-09T10:30:00", "2026-11-09T12:30:00"))
    }

    @Test
    fun theDateLabelsUseTheDateOfPeru() {
        assertEquals("Today", isoDateLabel(PeruClock.today().atStartOfDay().toString()))
        assertEquals("Tomorrow", isoDateLabel(PeruClock.today().plusDays(1).atStartOfDay().toString()))
        assertEquals(
            PeruClock.today().plusDays(5).format(DateTimeFormatter.ofPattern("MMM d", Locale.US)),
            isoDateLabel(PeruClock.today().plusDays(5).atStartOfDay().toString()),
        )
        // A past date keeps its calendar label.
        assertEquals("Jan 5", isoDateLabel("2020-01-05T09:00:00"))
    }
}
