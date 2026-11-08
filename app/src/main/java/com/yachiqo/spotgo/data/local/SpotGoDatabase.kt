package com.yachiqo.spotgo.data.local

import androidx.compose.runtime.mutableStateListOf
import com.yachiqo.spotgo.data.sha256
import com.yachiqo.spotgo.data.model.Account
import com.yachiqo.spotgo.data.model.AccountStatus
import com.yachiqo.spotgo.data.model.AuthMethod
import com.yachiqo.spotgo.data.model.Credential
import com.yachiqo.spotgo.data.model.Favorite
import com.yachiqo.spotgo.data.model.Profile
import com.yachiqo.spotgo.data.model.ProfileType
import com.yachiqo.spotgo.data.model.Session
import com.yachiqo.spotgo.data.model.Vehicle
import com.yachiqo.spotgo.data.model.VehicleStatus
import com.yachiqo.spotgo.data.model.VehicleType
import com.yachiqo.spotgo.data.model.AdditionalCharge
import com.yachiqo.spotgo.data.model.BalanceStatus
import com.yachiqo.spotgo.data.model.BillingDocument
import com.yachiqo.spotgo.data.model.BillingDocumentType
import com.yachiqo.spotgo.data.model.ChargeStatus
import com.yachiqo.spotgo.data.model.DigitalPayment
import com.yachiqo.spotgo.data.model.OutstandingBalance
import com.yachiqo.spotgo.data.model.PaymentAttempt
import com.yachiqo.spotgo.data.model.PaymentAttemptStatus
import com.yachiqo.spotgo.data.model.PaymentOperationType
import com.yachiqo.spotgo.data.model.PaymentStatus
import com.yachiqo.spotgo.data.model.PaymentToken
import com.yachiqo.spotgo.data.model.Refund
import com.yachiqo.spotgo.data.model.RefundStatus
import com.yachiqo.spotgo.data.model.Subscription
import com.yachiqo.spotgo.data.model.SubscriptionPlan
import com.yachiqo.spotgo.data.model.SubscriptionStatus
import com.yachiqo.spotgo.data.model.TokenStatus
import com.yachiqo.spotgo.data.model.GuestParkingSession
import com.yachiqo.spotgo.data.model.GuestSessionStatus
import com.yachiqo.spotgo.data.model.LockStatus
import com.yachiqo.spotgo.data.model.OccupancyReading
import com.yachiqo.spotgo.data.model.OccupancySensor
import com.yachiqo.spotgo.data.model.OccupancyStatus
import com.yachiqo.spotgo.data.model.ParkingFacility
import com.yachiqo.spotgo.data.model.ParkingSpot
import com.yachiqo.spotgo.data.model.ParkingZone
import com.yachiqo.spotgo.data.model.Reservation
import com.yachiqo.spotgo.data.model.ReservationStatus
import com.yachiqo.spotgo.data.model.SensorStatus
import com.yachiqo.spotgo.data.model.SpotOperationalStatus
import com.yachiqo.spotgo.data.model.Tenant
import com.yachiqo.spotgo.data.model.TenantStatus
import com.yachiqo.spotgo.data.model.ZoneStatus
import com.yachiqo.spotgo.data.repository.PeruClock
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Temporary in-memory store for the Driver access flow (US09 / US13) and the
 * parking reservation flow (US01 / US04 / US18 / US25).
 * It mirrors the database tables until the real backend exists; every
 * mutation happens here so the functional behaviour can be exercised.
 * Data is lost when the process dies — that is intentional.
 */
object SpotGoDatabase {

    val accounts = mutableListOf<Account>()
    val credentials = mutableListOf<Credential>()
    val sessions = mutableListOf<Session>()
    val profiles = mutableListOf<Profile>()
    val vehicles = mutableListOf<Vehicle>()

    // Parking Infrastructure (23-bounded-3)
    val tenants = mutableListOf<Tenant>()
    val parking_facilities = mutableListOf<ParkingFacility>()
    val parking_zones = mutableListOf<ParkingZone>()
    val parking_spots = mutableListOf<ParkingSpot>()
    val reservations = mutableListOf<Reservation>()

    // UI-only — there is no favorites table in the database diagrams; this
    // mirrors the future REST resource /api/v1/favorites (41-sprint-1.md).
    // Snapshot state so the heart and the "Saved" chip refresh on change.
    val favorites = mutableStateListOf<Favorite>()

    // Occupancy & Monitoring (25-bounded-5)
    val occupancy_sensors = mutableListOf<OccupancySensor>()
    val occupancy_readings = mutableListOf<OccupancyReading>()
    val guest_parking_sessions = mutableListOf<GuestParkingSession>()

    // Payments & Billing (24-bounded-4)
    val payment_tokens = mutableListOf<PaymentToken>()
    val digital_payments = mutableListOf<DigitalPayment>()
    val payment_attempts = mutableListOf<PaymentAttempt>()
    val refunds = mutableListOf<Refund>()
    val billing_documents = mutableListOf<BillingDocument>()
    val additional_charges = mutableListOf<AdditionalCharge>()
    val outstanding_balances = mutableListOf<OutstandingBalance>()
    val subscriptions = mutableListOf<Subscription>()

    init {
        seedAccess()
        seedVehicles()
        seedParking()
        seedReservations()
        seedFavorites()
        seedPayments()
    }

    /** Demo Driver used by the mockups (duplicate e-mail tests use this account). */
    private fun seedAccess() {
        val account = Account(
            account_id = "acc-seed-0001",
            email = "brigitte.carhuaz@example.com",
            status = AccountStatus.ACTIVE,
            created_at = "2026-01-15T09:00:00Z",
            updated_at = "2026-01-15T09:00:00Z",
        )
        accounts += account
        credentials += Credential(
            credential_id = "cred-seed-0001",
            account_id = account.account_id,
            authentication_method = AuthMethod.SPOTGO_CREDENTIALS,
            secret_hash = sha256("SpotGo2026"),
            external_subject = null,
            last_used_at = null,
            status = AccountStatus.ACTIVE,
        )
        profiles += Profile(
            profile_id = "prf-seed-0001",
            identity_ref = account.account_id,
            profile_type = ProfileType.DRIVER,
            first_name = "Brigitte",
            last_name = "Carhuaz",
            email = account.email,
            phone = "+51 999 123 456",
            status = AccountStatus.ACTIVE,
            created_at = "2026-01-15T09:00:00Z",
            updated_at = "2026-01-15T09:00:00Z",
        )
    }

    /** Demo vehicles of the seed Driver (mockup 11: default + secondary). */
    private fun seedVehicles() {
        vehicles += Vehicle(
            vehicle_id = "veh-seed-0001",
            profile_id = "prf-seed-0001",
            plate = "AJK-247",
            make = "Toyota",
            model = "Yaris",
            color = "White",
            status = VehicleStatus.ACTIVE,
            created_at = "2026-01-16T09:00:00Z",
            updated_at = "2026-01-16T09:00:00Z",
            vehicle_type = VehicleType.CAR,
        )
        vehicles += Vehicle(
            vehicle_id = "veh-seed-0002",
            profile_id = "prf-seed-0001",
            plate = "C8P-521",
            make = "Kia",
            model = "Soluto",
            color = "Silver",
            status = VehicleStatus.ACTIVE,
            created_at = "2026-01-17T09:00:00Z",
            updated_at = "2026-01-17T09:00:00Z",
            vehicle_type = VehicleType.CAR,
        )
    }

    /**
     * Demo parking infrastructure (mockups 03/04/05): one tenant, five zones
     * with the prices of the explore map (S/ 6 · 7 · 8 · 5 · 10), sensors and
     * readings behind the availability projection (US01) and one open guest
     * session; demo reservations are seeded by seedReservations().
     */
    private fun seedParking() {
        val ts = "2026-01-10T09:00:00Z"
        tenants += Tenant(
            tenant_id = "tnt-seed-0001",
            name = "SpotGo San Isidro",
            status = TenantStatus.ACTIVE,
            contact_data = "operaciones@spotgo.pe",
            rules = "lock_duration_minutes=10;overstay_tolerance_minutes=5",
            created_at = ts,
            updated_at = ts,
        )
        parking_facilities += ParkingFacility(
            facility_id = "fcl-seed-0001",
            tenant_id = "tnt-seed-0001",
            name = "Javier Prado Parking",
            coordinates = "-77.0215,-12.1078",
            status = ZoneStatus.ACTIVE,
            created_at = ts,
            updated_at = ts,
        )

        fun addZone(
            id: String,
            name: String,
            location: String,
            color: String,
            price: Double,
            distance: Double,
            amenities: List<String>,
            coordinates: String = "",
        ): ParkingZone {
            val zone = ParkingZone(
                zone_id = id,
                facility_id = "fcl-seed-0001",
                tenant_id = "tnt-seed-0001",
                name = name,
                location = location,
                color_code = color,
                status = ZoneStatus.ACTIVE,
                created_at = ts,
                updated_at = ts,
                hourly_price = price,
                distance_km = distance,
                open_until = "11:00 PM",
                amenities = amenities,
                coordinates = coordinates,
            )
            parking_zones += zone
            return zone
        }

        val readingAt = "2026-11-06T10:29:00Z"
        var sensorSeq = 0
        fun addSpot(
            zone: ParkingZone,
            code: String,
            spotType: String,
            features: String,
            operational: String,
            occupancy: String,
        ) {
            sensorSeq++
            val spotId = "spc-seed-%04d".format(sensorSeq)
            parking_spots += ParkingSpot(
                spot_id = spotId,
                zone_id = zone.zone_id,
                code = code,
                spot_type = spotType,
                features = features,
                operational_status = operational,
            )
            occupancy_sensors += OccupancySensor(
                sensor_id = "sen-seed-%04d".format(sensorSeq),
                tenant_id = zone.tenant_id,
                zone_id = zone.zone_id,
                spot_id = spotId,
                protocol = "MQTT",
                status = SensorStatus.ACTIVE,
                last_seen_at = readingAt,
                configuration = "{}",
                installed_at = ts,
            )
            occupancy_readings += OccupancyReading(
                reading_id = "ocr-seed-%04d".format(sensorSeq),
                sensor_id = "sen-seed-%04d".format(sensorSeq),
                spot_id = spotId,
                occupancy_status = occupancy,
                occurred_at = readingAt,
                received_at = readingAt,
                quality = "GOOD",
                payload_hash = sha256(code),
            )
        }

        // Map markers from mockup 03: S/ 6 · S/ 7 · S/ 8 (selected) · S/ 5 · S/ 10.
        // coordinates: "lng,lat" around San Isidro / Javier Prado (Lima).
        val z1 = addZone("zne-seed-0001", "San Isidro Centro", "Av. La Fontana", "#7C8CF8", 6.0, 0.7, listOf("COVERED"),
            coordinates = "-77.0301,-12.1121")
        addSpot(z1, "N-01", "STANDARD", "COVERED", SpotOperationalStatus.OPERATIONAL, OccupancyStatus.AVAILABLE)
        addSpot(z1, "N-02", "STANDARD", "COVERED", SpotOperationalStatus.OPERATIONAL, OccupancyStatus.AVAILABLE)
        addSpot(z1, "N-03", "STANDARD", "COVERED", SpotOperationalStatus.OPERATIONAL, OccupancyStatus.AVAILABLE)
        addSpot(z1, "N-04", "STANDARD", "COVERED", SpotOperationalStatus.OPERATIONAL, OccupancyStatus.OCCUPIED)

        val z2 = addZone("zne-seed-0002", "Cavalini Parking", "Av. Javier Prado Norte", "#F5A623", 7.0, 0.5, listOf("COVERED", "EV"),
            coordinates = "-77.0186,-12.0987")
        addSpot(z2, "V-01", "STANDARD", "COVERED,EV", SpotOperationalStatus.OPERATIONAL, OccupancyStatus.AVAILABLE)
        addSpot(z2, "V-02", "STANDARD", "COVERED,EV", SpotOperationalStatus.OPERATIONAL, OccupancyStatus.AVAILABLE)
        addSpot(z2, "V-03", "STANDARD", "COVERED", SpotOperationalStatus.OPERATIONAL, OccupancyStatus.AVAILABLE)
        addSpot(z2, "V-04", "STANDARD", "COVERED", SpotOperationalStatus.OPERATIONAL, OccupancyStatus.AVAILABLE)
        addSpot(z2, "V-05", "STANDARD", "COVERED", SpotOperationalStatus.OPERATIONAL, OccupancyStatus.OCCUPIED)

        val z3 = addZone(
            "zne-seed-0003", "Javier Prado Parking", "Av. Javier Prado Oeste", "#44E38B",
            8.0, 0.4, listOf("COVERED", "EV", "ACCESSIBLE"),
            coordinates = "-77.0215,-12.1078",
        )
        addSpot(z3, "B-10", "STANDARD", "COVERED,EV", SpotOperationalStatus.OPERATIONAL, OccupancyStatus.AVAILABLE)
        addSpot(z3, "B-11", "STANDARD", "COVERED,EV", SpotOperationalStatus.OPERATIONAL, OccupancyStatus.AVAILABLE)
        addSpot(z3, "B-12", "STANDARD", "COVERED", SpotOperationalStatus.OPERATIONAL, OccupancyStatus.AVAILABLE)
        addSpot(z3, "B-13", "STANDARD", "COVERED", SpotOperationalStatus.OPERATIONAL, OccupancyStatus.AVAILABLE)
        addSpot(z3, "B-14", "STANDARD", "COVERED,EV", SpotOperationalStatus.OPERATIONAL, OccupancyStatus.AVAILABLE)
        addSpot(z3, "B-15", "ACCESSIBLE", "COVERED", SpotOperationalStatus.OPERATIONAL, OccupancyStatus.AVAILABLE)
        addSpot(z3, "B-16", "STANDARD", "COVERED", SpotOperationalStatus.OPERATIONAL, OccupancyStatus.OCCUPIED)
        addSpot(z3, "B-17", "STANDARD", "COVERED", SpotOperationalStatus.OPERATIONAL, OccupancyStatus.OCCUPIED)

        // Single-spot zone: once the Driver reserves it, availability reaches 0
        // and the app must inform "no spots available" (US01 scenario 2).
        val z4 = addZone("zne-seed-0004", "Canaval y Moreyra", "C. Canaval y Moreyra", "#E344E3", 5.0, 0.9, listOf("COVERED"),
            coordinates = "-77.0276,-12.1147")
        addSpot(z4, "C-01", "STANDARD", "COVERED", SpotOperationalStatus.OPERATIONAL, OccupancyStatus.AVAILABLE)

        val z5 = addZone("zne-seed-0005", "Fontana Plaza", "Av. La Fontana", "#44C7E3", 10.0, 1.1, listOf("EV"),
            coordinates = "-77.0336,-12.1102")
        addSpot(z5, "F-01", "STANDARD", "EV", SpotOperationalStatus.OPERATIONAL, OccupancyStatus.AVAILABLE)
        addSpot(z5, "F-02", "STANDARD", "EV", SpotOperationalStatus.OPERATIONAL, OccupancyStatus.AVAILABLE)
        addSpot(z5, "F-03", "STANDARD", "EV", SpotOperationalStatus.OPERATIONAL, OccupancyStatus.OCCUPIED)
        addSpot(z5, "F-04", "STANDARD", "EV", SpotOperationalStatus.OUT_OF_SERVICE, OccupancyStatus.AVAILABLE)

        // Open guest session holding V-05 (availability cause of US01 scenario 2).
        guest_parking_sessions += GuestParkingSession(
            guest_session_id = "gst-seed-0001",
            tenant_id = "tnt-seed-0001",
            zone_id = z2.zone_id,
            spot_id = parking_spots.first { it.code == "V-05" }.spot_id,
            manual_plate = "7XK-902",
            entry_at = "2026-11-06T09:15:00Z",
            exit_at = null,
            calculated_amount = null,
            physical_payment_method = null,
            physical_payment_confirmed = false,
            status = GuestSessionStatus.OPEN,
        )
    }

    /** Demo reservations of the seed Driver (mockups 06/07): one upcoming,
     *  one completed and one cancelled so the three tabs of My reservations
     *  have data before the Driver books from Explore. They use the secondary
     *  vehicle so the default vehicle (AJK-247) stays free for the reserve flow. */
    private fun seedReservations() {
        fun reservation(
            id: String,
            zone: ParkingZone,
            spotCode: String,
            status: String,
            start: LocalDateTime,
            hours: Int,
        ): Reservation = Reservation(
            reservation_id = id,
            tenant_id = zone.tenant_id,
            profile_id = "prf-seed-0001",
            vehicle_id = "veh-seed-0002",
            zone_id = zone.zone_id,
            spot_id = parking_spots.first { it.code == spotCode }.spot_id,
            start_at = start.toString(),
            end_at = start.plusHours(hours.toLong()).toString(),
            amount = zone.hourly_price * hours,
            status = status,
            payment_ref = "pay-${id.takeLast(4).uppercase()}",
            lock_status = LockStatus.RELEASED,
            locked_at = null,
            lock_expires_at = null,
            created_at = start.minusDays(1).toString(),
            updated_at = start.minusDays(1).toString(),
        )

        // Upcoming (mockup 06): RESERVED tomorrow 10:30 AM – 12:30 PM.
        reservations += reservation(
            "res-seed-0001",
            parking_zones.first { it.zone_id == "zne-seed-0005" },
            "F-01",
            ReservationStatus.RESERVED,
            LocalDateTime.of(PeruClock.today().plusDays(1), LocalTime.of(10, 30)),
            2,
        )
        // Completed tab: a paid reservation that already finished.
        reservations += reservation(
            "res-seed-0002",
            parking_zones.first { it.zone_id == "zne-seed-0001" },
            "N-02",
            ReservationStatus.COMPLETED,
            LocalDateTime.of(PeruClock.today().minusDays(3), LocalTime.of(9, 0)),
            2,
        )
        // Cancelled tab: cancelled before start, refund simulated (Part 5 shows it).
        reservations += reservation(
            "res-seed-0003",
            parking_zones.first { it.zone_id == "zne-seed-0002" },
            "V-02",
            ReservationStatus.CANCELLED,
            LocalDateTime.of(PeruClock.today().minusDays(2), LocalTime.of(14, 0)),
            2,
        )
        // Session finished today at Javier Prado Parking (spot B-14, S/ 16.00):
        // backs the receipt of mockup 40 ("Receipt SG-4821") and the overstay
        // additional charge of mockup 38. The id is chosen so the demo booking
        // code of ParkingRepository.booking_code() hashes to SG-4821.
        reservations += reservation(
            "res-seed-06264",
            parking_zones.first { it.zone_id == "zne-seed-0003" },
            "B-14",
            ReservationStatus.COMPLETED,
            LocalDateTime.of(PeruClock.today(), LocalTime.of(8, 0)),
            2,
        ).copy(payment_ref = "pay-seed-0001")
    }

    /** Demo favorite of the seed Driver (mockup 03 — Saved chip). UI-only. */
    private fun seedFavorites() {
        favorites += Favorite(
            favorite_id = "fav-seed-0001",
            profile_id = "prf-seed-0001",
            zone_id = "zne-seed-0001",
            created_at = "2026-01-15T09:00:00Z",
        )
    }

    /**
     * Demo Payments & Billing data (mockups 08/13/14/38/40): two saved
     * tokens, the transaction history of the Payments tab, the invoices
     * and receipts of US21, the subscription of the driver (Free, 0%
     * discount) and one open overstay balance (S/ 8.00) that reproduces
     * mockup 38 until the Driver retries the payment (US19).
     */
    private fun seedPayments() {
        val profileId = "prf-seed-0001"
        val ts = "2026-01-10T09:00:00Z"

        /* payment_tokens ------------------------------------------------ */
        payment_tokens += PaymentToken(
            token_id = "ptk-seed-0001",
            owner_ref = profileId,
            provider_token_ref = "tok_visa_seed_4242",
            brand = "Visa",
            last_four = "4242",
            expiration_month = 8,
            expiration_year = 29,
            display_label = "Visa •••• 4242",
            display_details = "Expires 08/29 · Default",
            is_default = true,
            status = TokenStatus.ACTIVE,
            created_at = ts,
            revoked_at = null,
        )
        payment_tokens += PaymentToken(
            token_id = "ptk-seed-0002",
            owner_ref = profileId,
            provider_token_ref = "tok_mc_seed_5518",
            brand = "Mastercard",
            last_four = "5518",
            expiration_month = 11,
            expiration_year = 28,
            display_label = "Mastercard •••• 5518",
            display_details = "Expires 11/28",
            is_default = false,
            status = TokenStatus.ACTIVE,
            created_at = ts,
            revoked_at = null,
        )

        /* subscriptions -------------------------------------------------- */
        subscriptions += Subscription(
            subscription_id = "sub-seed-0001",
            driver_id = profileId,
            plan = SubscriptionPlan.FREE,
            start_at = "2026-01-15T09:00:00Z",
            end_at = "",
            status = SubscriptionStatus.ACTIVE,
            discount_rules = "free:0",
            discount_percent = 0,
            auto_renew = true,
        )

        /* digital_payments + payment_attempts ---------------------------- */
        fun payment(
            id: String,
            operationType: String,
            amount: String,
            status: String,
            createdAt: String,
            approvedAt: String?,
            contextLabel: String,
            summary: String,
            idempotency: String,
            resultReference: String?,
            operationLabel: String? = null,
        ): DigitalPayment {
            val payment = DigitalPayment(
                payment_id = id,
                payment_token_id = "ptk-seed-0001",
                operation_type = operationType,
                owner_ref = profileId,
                amount = amount,
                currency = "PEN",
                status = status,
                provider_operation_ref = "prov-$id",
                idempotency_key = idempotency,
                request_hash = "req-$id",
                first_seen_at = createdAt,
                last_seen_at = approvedAt ?: createdAt,
                result_reference = resultReference,
                created_at = createdAt,
                approved_at = approvedAt,
                context_label = contextLabel,
                summary = summary,
                operation_label = operationLabel,
            )
            digital_payments += payment
            if (status == PaymentStatus.APPROVED || status == PaymentStatus.PENDING) {
                payment_attempts += PaymentAttempt(
                    attempt_id = "att-$id",
                    payment_id = id,
                    number = 1,
                    started_at = createdAt,
                    finished_at = approvedAt,
                    status = if (status == PaymentStatus.APPROVED) {
                        PaymentAttemptStatus.SUCCEEDED
                    } else {
                        PaymentAttemptStatus.STARTED
                    },
                    error_code = null,
                    next_attempt_at = null,
                )
            }
            return payment
        }

        // Mockup 08 first row + mockup 40 receipt ("Receipt SG-4821"): the
        // completed session of today at Javier Prado Parking (spot B-14).
        payment(
            id = "pay-seed-0001",
            operationType = PaymentOperationType.RESERVATION,
            amount = "16.00",
            status = PaymentStatus.APPROVED,
            createdAt = "2026-09-29T10:00:00Z",
            approvedAt = "2026-09-29T10:00:02Z",
            contextLabel = "Today · San Isidro, Lima",
            summary = "Javier Prado Parking",
            idempotency = "idem-seed-0001",
            resultReference = "doc-seed-0001",
        )
        // Mockup 08 second row ("24 Sep · Miraflores, Lima").
        payment(
            id = "pay-seed-0002",
            operationType = PaymentOperationType.RESERVATION,
            amount = "12.00",
            status = PaymentStatus.APPROVED,
            createdAt = "2026-09-24T09:00:00Z",
            approvedAt = "2026-09-24T09:00:02Z",
            contextLabel = "24 Sep · Miraflores, Lima",
            summary = "Larco Parking",
            idempotency = "idem-seed-0002",
            resultReference = "doc-seed-0002",
        )
        // Mockup 08 third row ("20 Sep · San Borja, Lima").
        payment(
            id = "pay-seed-0003",
            operationType = PaymentOperationType.RESERVATION,
            amount = "10.00",
            status = PaymentStatus.APPROVED,
            createdAt = "2026-09-20T14:00:00Z",
            approvedAt = "2026-09-20T14:00:02Z",
            contextLabel = "20 Sep · San Borja, Lima",
            summary = "San Borja Parking",
            idempotency = "idem-seed-0003",
            resultReference = "doc-seed-0003",
        )
        // Payment of the cancelled seed reservation (res-seed-0003 keeps
        // payment_ref = "pay-0003"): refunded, so its receipt shows the
        // Refund adjustment of US21 scenario 3.
        payment(
            id = "pay-0003",
            operationType = PaymentOperationType.RESERVATION,
            amount = "14.00",
            status = PaymentStatus.APPROVED,
            createdAt = "2026-09-18T14:00:00Z",
            approvedAt = "2026-09-18T14:00:02Z",
            contextLabel = "18 Sep · Miraflores, Lima",
            summary = "Cavalini Parking",
            idempotency = "idem-seed-0004",
            resultReference = "doc-seed-0004",
        )
        // Subscription payment that generated the invoice of mockup 14.
        payment(
            id = "pay-seed-0004",
            operationType = PaymentOperationType.SUBSCRIPTION,
            amount = "14.90",
            status = PaymentStatus.APPROVED,
            createdAt = "2026-09-01T10:00:00Z",
            approvedAt = "2026-09-01T10:00:02Z",
            contextLabel = "01 Sep · Plus Monthly",
            summary = "Plus Monthly",
            idempotency = "idem-seed-0005",
            resultReference = "doc-seed-0005",
            operationLabel = "Plus Monthly",
        )

        /* refunds -------------------------------------------------------- */
        refunds += Refund(
            refund_id = "rfd-seed-0001",
            payment_id = "pay-0003",
            amount = "14.00",
            currency = "PEN",
            status = RefundStatus.PROCESSED,
            created_at = "2026-09-18T14:30:00Z",
        )

        /* billing_documents ---------------------------------------------- */
        // Receipts use the booking code of the reservation (mockup 40).
        billing_documents += BillingDocument(
            document_id = "doc-seed-0001",
            payment_id = "pay-seed-0001",
            owner_ref = profileId,
            document_type = BillingDocumentType.ELECTRONIC_RECEIPT,
            fiscal_reference = "SG-4821",
            issued_at = "2026-09-29T10:00:02Z",
            pdf_uri = null,
        )
        billing_documents += BillingDocument(
            document_id = "doc-seed-0002",
            payment_id = "pay-seed-0002",
            owner_ref = profileId,
            document_type = BillingDocumentType.ELECTRONIC_RECEIPT,
            fiscal_reference = "SG-4714",
            issued_at = "2026-09-24T09:00:02Z",
            pdf_uri = null,
        )
        billing_documents += BillingDocument(
            document_id = "doc-seed-0003",
            payment_id = "pay-seed-0003",
            owner_ref = profileId,
            document_type = BillingDocumentType.ELECTRONIC_RECEIPT,
            fiscal_reference = "SG-4398",
            issued_at = "2026-09-20T14:00:02Z",
            pdf_uri = null,
        )
        billing_documents += BillingDocument(
            document_id = "doc-seed-0004",
            payment_id = "pay-0003",
            owner_ref = profileId,
            document_type = BillingDocumentType.ELECTRONIC_RECEIPT,
            fiscal_reference = "SG-4460",
            issued_at = "2026-09-18T14:00:02Z",
            pdf_uri = null,
        )
        billing_documents += BillingDocument(
            document_id = "doc-seed-0005",
            payment_id = "pay-seed-0004",
            owner_ref = profileId,
            document_type = BillingDocumentType.ELECTRONIC_INVOICE,
            fiscal_reference = "F001-0048",
            issued_at = "2026-09-01T10:00:02Z",
            pdf_uri = null,
        )

        /* additional_charges + outstanding_balances (mockup 38) ---------- */
        additional_charges += AdditionalCharge(
            charge_id = "chg-seed-0001",
            reservation_id = "res-seed-06264",
            driver_id = profileId,
            amount = "8.00",
            status = ChargeStatus.OPEN,
            created_at = "2026-11-06T10:05:00Z",
        )
        outstanding_balances += OutstandingBalance(
            balance_id = "bal-seed-0001",
            owner_ref = profileId,
            amount = "8.00",
            currency = "PEN",
            status = BalanceStatus.OPEN,
            source_reference = "chg-seed-0001",
            created_at = "2026-11-06T10:05:00Z",
        )
    }
}
