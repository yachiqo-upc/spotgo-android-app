package com.yachiqo.spotgo.data.remote

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.yachiqo.spotgo.data.repository.ParkingCatalogResult
import com.yachiqo.spotgo.data.repository.ParkingFacilityListing
import com.yachiqo.spotgo.data.repository.AuthRepository
import com.yachiqo.spotgo.data.repository.RemoteBillingRepository
import com.yachiqo.spotgo.data.repository.RemotePaymentMethodMutationResult
import com.yachiqo.spotgo.data.repository.RemotePaymentResult
import com.yachiqo.spotgo.data.repository.RemoteParkingRepository
import com.yachiqo.spotgo.data.repository.RemoteReservationRepository
import com.yachiqo.spotgo.data.repository.RemoteReservationResult
import com.yachiqo.spotgo.data.repository.RemoteVehicleRepository
import com.yachiqo.spotgo.data.repository.VehicleResult
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RemoteRepositoryHttpTest {
    private lateinit var server: RecordingHttpServer
    private var previousBaseUrl: String? = null
    private var previousAccessToken: String? = null
    private var previousRefreshToken: String? = null
    private var previousRefreshExpiresAt: String? = null

    @Before
    fun setUp() {
        previousBaseUrl = System.getProperty("spotgo.test.api.base.url")
        previousAccessToken = SpotGoApi.current_access_token()
        previousRefreshToken = SpotGoApi.current_refresh_token()
        previousRefreshExpiresAt = SpotGoApi.current_refresh_expires_at()
        server = RecordingHttpServer()
        System.setProperty("spotgo.test.api.base.url", server.baseUrl)
        SpotGoApi.set_tokens("test-access-token", "test-refresh-token")
    }

    @After
    fun tearDown() {
        server.close()
        if (previousBaseUrl == null) System.clearProperty("spotgo.test.api.base.url")
        else System.setProperty("spotgo.test.api.base.url", previousBaseUrl)
        if (previousAccessToken == null || previousRefreshToken == null) SpotGoApi.clear_tokens()
        else SpotGoApi.set_tokens(previousAccessToken!!, previousRefreshToken!!, previousRefreshExpiresAt)
    }

    @Test
    fun vehicleRegistrationUsesTheDriverEndpointAndMapsCreatedVehicle() {
        server.handler = { request ->
            assertEquals("POST", request.method)
            assertEquals("/api/v1/vehicles", request.path)
            assertEquals("Bearer test-access-token", request.authorization)
            StubResponse(201, """{"id":"vehicle-1","profileId":"profile-1","plate":"XYZ-987","make":"Mazda","model":"Mazda 3","color":"Red","status":"ACTIVE","createdAt":"2026-11-12T10:00:00Z"}""")
        }

        val result = RemoteVehicleRepository.addVehicle(" xyz-987 ", "Mazda", "Mazda 3", "Red")

        assertTrue(result is VehicleResult.Success)
        assertEquals("XYZ-987", (result as VehicleResult.Success).vehicle.plate)
        assertTrue(server.requests.single().body.contains("\"plate\":\"XYZ-987\""))
    }

    @Test
    fun simulatedPaymentMethodCanBeSavedAndSelectedAsDefault() {
        server.handler = { request ->
            when (request.method to request.path) {
                "POST" to "/api/v1/payment-tokens" -> StubResponse(
                    201,
                    """{"tokenId":"token-1","brand":"Visa","lastFour":"4242","expirationMonth":12,"expirationYear":2030,"status":"ACTIVE","isDefault":false}""",
                )
                "PUT" to "/api/v1/payment-tokens/token-1/default" -> StubResponse(200, "{}")
                else -> StubResponse(404, "{}")
            }
        }

        val added = RemoteBillingRepository.addSimulatedPaymentMethod("VISA", "4242")

        assertTrue(added is RemotePaymentMethodMutationResult.Success)
        assertEquals("Visa •••• 4242", (added as RemotePaymentMethodMutationResult.Success).method.label)
        assertTrue(RemoteBillingRepository.setDefaultPaymentMethod("token-1"))
        assertTrue(server.requests[0].body.contains("simulated-visa-4242"))
        assertEquals("PUT", server.requests[1].method)
        assertEquals("/api/v1/payment-tokens/token-1/default", server.requests[1].path)
        assertEquals("Bearer test-access-token", server.requests[1].authorization)

        assertTrue(RemoteBillingRepository.addSimulatedPaymentMethod("visa", "4111111111111111") is RemotePaymentMethodMutationResult.Failure)
        assertEquals(2, server.requests.size)
    }

    @Test
    fun paymentMethodCanBeRemovedThroughTheAuthenticatedBackendEndpoint() {
        server.handler = { request ->
            assertEquals("DELETE", request.method)
            assertEquals("/api/v1/payment-tokens/token-1", request.path)
            assertEquals("Bearer test-access-token", request.authorization)
            StubResponse(204, "")
        }

        assertTrue(RemoteBillingRepository.removePaymentMethod("token-1"))
        assertEquals(1, server.requests.size)
    }

    @Test
    fun eachSubscriptionCheckoutGetsANewIdempotencyKey() {
        var createdPayments = 0
        server.handler = { request ->
            when {
                request.method == "POST" && request.path == "/api/v1/digital-payments/subscriptions" -> {
                    createdPayments += 1
                    StubResponse(201, """{"paymentId":"payment-$createdPayments"}""")
                }
                request.method == "POST" && request.path.endsWith("/process") -> StubResponse(200, """{"status":"APPROVED"}""")
                else -> StubResponse(404, "{}")
            }
        }

        val first = RemoteBillingRepository.paySubscription("DRIVER_MONTHLY", "token-1")
        val second = RemoteBillingRepository.paySubscription("DRIVER_MONTHLY", "token-1")

        assertEquals(RemotePaymentResult.Success("APPROVED", "payment-1"), first)
        assertEquals(RemotePaymentResult.Success("APPROVED", "payment-2"), second)
        val createRequests = server.requests.filter { it.path == "/api/v1/digital-payments/subscriptions" }
        assertTrue(createRequests.all { it.body.contains("\"planCode\":\"DRIVER_MONTHLY\"") })
        val idempotencyKeys = createRequests.map { Regex("\"idempotencyKey\":\"([^\"]+)\"").find(it.body)!!.groupValues[1] }
        assertNotEquals(idempotencyKeys[0], idempotencyKeys[1])
    }

    @Test
    fun googleSignInExchangesTheIdTokenAndLoadsTheDriverProfile() {
        server.handler = { request ->
            when (request.path) {
                "/api/v1/authentication/google" -> StubResponse(
                    200,
                    """{"role":"DRIVER","accountId":"account-google","email":"ada@example.com","sessionId":"session-google","accessToken":"google-access","refreshToken":"google-refresh","accessExpiresAt":"2098-12-31T23:45:00Z","refreshExpiresAt":"2099-01-01T00:00:00Z"}""",
                )
                "/api/v1/profiles/me" -> StubResponse(
                    200,
                    """{"id":"profile-google","accountId":"account-google","firstName":"Ada","lastName":"Lovelace","phone":"+51123456789","createdAt":"2026-11-12T10:00:00Z","updatedAt":"2026-11-12T10:00:00Z"}""",
                )
                else -> StubResponse(404, "{}")
            }
        }

        val result = AuthRepository.sign_in_with_google(keep_signed_in = true, id_token = "google-id-token")

        assertTrue(result is com.yachiqo.spotgo.data.repository.AuthResult.Success)
        assertEquals("/api/v1/authentication/google", server.requests[0].path)
        assertTrue(server.requests[0].body.contains("\"idToken\":\"google-id-token\""))
        assertEquals(null, server.requests[0].authorization)
        assertEquals("Bearer google-access", server.requests[1].authorization)
    }

    @Test
    fun savedBackendSessionRestoresTheProfileAfterProcessRecreation() {
        SpotGoApi.set_tokens(
            "saved-access",
            "saved-refresh",
            "2099-01-01T00:00:00Z",
        )
        server.handler = { request ->
            when (request.path) {
                "/api/v1/profiles/me" -> StubResponse(
                    200,
                    """{"id":"profile-1","accountId":"account-1","firstName":"Ada","lastName":"Lovelace","phone":"+51123456789"}""",
                )
                "/api/v1/accounts/me" -> StubResponse(
                    200,
                    """{"id":"account-1","email":"ada@example.com","status":"ACTIVE"}""",
                )
                else -> StubResponse(404, "{}")
            }
        }

        assertTrue(AuthRepository.restore_backend_session())
        assertEquals("ada@example.com", AuthRepository.current_profile()?.email)
        assertEquals(listOf("/api/v1/profiles/me", "/api/v1/accounts/me"), server.requests.map { it.path })
        assertTrue(server.requests.all { it.authorization == "Bearer saved-access" })
    }

    @Test
    fun mapCatalogUsesBackendTenantsZonesSpotsAndAvailability() {
        server.handler = { request ->
            when {
                request.path == "/api/v1/tenants" -> StubResponse(
                    200,
                    """[{"tenantId":"tenant-1","name":"Parking Sol","address":"Lima","status":"ACTIVE","coordinates":{"latitude":-12.1,"longitude":-77.0},"rules":{"tariff_rules":{"period_minutes":60,"price_per_period":8.5}}}]""",
                )
                request.path == "/api/v1/favorites" -> StubResponse(200, "[]")
                request.path == "/api/v1/parking-zones?tenantId=tenant-1" -> StubResponse(
                    200,
                    """[{"zoneId":"zone-1","name":"Covered","status":"ACTIVE","location":{"reference":"Level 1"},"colorCode":"#2563EB"}]""",
                )
                request.path == "/api/v1/parking-spots?zoneId=zone-1" -> StubResponse(
                    200,
                    """[{"spotId":"spot-1","code":"A-01","spotType":"CAR","operationalStatus":"AVAILABLE","features":{"covered":true,"ev_charger":false,"accessible":true}}]""",
                )
                request.path.startsWith("/api/v1/parking-spots/availability?") -> StubResponse(
                    200,
                    """[{"spotId":"spot-1","bookable":true}]""",
                )
                else -> StubResponse(404, "{}")
            }
        }

        val result = RemoteParkingRepository.loadFacilities()

        assertTrue(result is ParkingCatalogResult.Success)
        val facility = (result as ParkingCatalogResult.Success).facilities.single()
        assertEquals("tenant-1", facility.tenantId)
        assertEquals(-12.1, facility.latitude, 0.0001)
        assertEquals(8.5, facility.hourlyPrice, 0.0001)
        assertEquals(1, facility.availableSpots)
        assertTrue(facility.hasCoveredSpots)
        assertEquals("spot-1", RemoteParkingRepository.spotById("spot-1")?.spot_id)
        assertTrue(server.requests.all { it.authorization == "Bearer test-access-token" })
    }

    @Test
    fun favoriteMutationPostsTheSelectedTenant() {
        server.handler = { request ->
            assertEquals("POST", request.method)
            assertEquals("/api/v1/favorites", request.path)
            StubResponse(201, "{}")
        }
        val facility = ParkingFacilityListing(
            tenantId = "tenant-2",
            name = "Miraflores Parking",
            address = "Lima",
            latitude = -12.1,
            longitude = -77.0,
            hourlyPrice = 10.0,
            zones = emptyList(),
            availableSpots = 0,
            availabilityByZone = emptyMap(),
            hasCoveredSpots = false,
            hasEvCharging = false,
            favoriteId = null,
        )

        assertTrue(RemoteParkingRepository.toggleFavorite(facility))
        assertTrue(server.requests.single().body.contains("\"tenantId\":\"tenant-2\""))
    }

    @Test
    fun extensionUsesTheCurrentBackendContractAndReceiptPdfIsDownloadable() {
        server.handler = { request ->
            when (request.path) {
                "/api/v1/reservations/res-1/extend" -> StubResponse(
                    200,
                    """{"reservationId":"res-2","tenantId":"tenant-1","zoneId":"zone-1","spotId":"spot-2","profileId":"profile-1","vehicleId":"vehicle-1","startAt":"2026-11-12T10:00:00Z","endAt":"2026-11-12T11:00:00Z","createdAt":"2026-11-12T10:00:00Z","status":"PENDING_PAYMENT","parentReservationId":"res-1"}""",
                )
                "/api/v1/reservations/res-1/receipt" -> StubResponse(200, "%PDF-1.4\nSpotGo informational receipt", "application/pdf")
                else -> StubResponse(404, "{}")
            }
        }

        val extension = RemoteReservationRepository.extendReservation(
            reservationId = "res-1",
            endAt = "2026-11-12T11:00:00Z",
            spotId = "spot-2",
        )
        val pdf = RemoteReservationRepository.downloadReceipt("res-1")

        assertTrue(extension is RemoteReservationResult.Success)
        assertEquals("spot-2", (extension as RemoteReservationResult.Success).reservation.spot_id)
        assertTrue(server.requests[0].body.contains("\"endAt\":\"2026-11-12T11:00:00Z\""))
        assertTrue(server.requests[0].body.contains("\"spotId\":\"spot-2\""))
        assertEquals("/api/v1/reservations/res-1/receipt", server.requests[1].path)
        assertEquals("application/pdf", server.requests[1].accept)
        assertTrue(String(pdf!!, Charsets.ISO_8859_1).startsWith("%PDF-1.4"))
    }

    @Test
    fun expiredAccessTokenRefreshesAndRetriesWithRotatedCredentials() {
        server.handler = { request ->
            when {
                request.path == "/api/v1/authentication/refresh" -> StubResponse(
                    200,
                    """{"accessToken":"rotated-access","refreshToken":"rotated-refresh","refreshExpiresAt":"2099-01-02T00:00:00Z"}""",
                )
                request.path == "/api/v1/private" && request.authorization == "Bearer test-access-token" -> StubResponse(401, "{}")
                request.path == "/api/v1/private" && request.authorization == "Bearer rotated-access" -> StubResponse(200, "ok")
                else -> StubResponse(404, "{}")
            }
        }

        val result = SpotGoApi.get("/api/v1/private")

        assertEquals(ApiResult.Success(200, "ok"), result)
        assertEquals("rotated-access", SpotGoApi.current_access_token())
        assertEquals("rotated-refresh", SpotGoApi.current_refresh_token())
        assertEquals("2099-01-02T00:00:00Z", SpotGoApi.current_refresh_expires_at())
        assertEquals(listOf("Bearer test-access-token", null, "Bearer rotated-access"), server.requests.map { it.authorization })
        assertTrue(server.requests[1].body.contains("\"refreshToken\":\"test-refresh-token\""))
    }

    private data class RecordedRequest(
        val method: String,
        val path: String,
        val body: String,
        val authorization: String?,
        val accept: String?,
    )

    private data class StubResponse(val status: Int, val body: String, val contentType: String = "application/json")

    private class RecordingHttpServer : AutoCloseable {
        private val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        private val executor = Executors.newCachedThreadPool()
        val requests = CopyOnWriteArrayList<RecordedRequest>()
        @Volatile var handler: (RecordedRequest) -> StubResponse = { StubResponse(404, "{}") }

        val baseUrl: String
            get() = "http://127.0.0.1:${httpServer.address.port}"

        init {
            httpServer.executor = executor
            httpServer.createContext("/") { exchange -> respond(exchange) }
            httpServer.start()
        }

        private fun respond(exchange: HttpExchange) {
            val body = exchange.requestBody.use { String(it.readBytes(), Charsets.UTF_8) }
            val uri = exchange.requestURI
            val path = uri.rawPath + (uri.rawQuery?.let { "?$it" } ?: "")
            val request = RecordedRequest(
                method = exchange.requestMethod,
                path = path,
                body = body,
                authorization = exchange.requestHeaders.getFirst("Authorization"),
                accept = exchange.requestHeaders.getFirst("Accept"),
            )
            requests += request
            val response = handler(request)
            val bytes = response.body.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", response.contentType)
            if (response.status == 204) {
                exchange.sendResponseHeaders(response.status, -1)
            } else {
                exchange.sendResponseHeaders(response.status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        }

        override fun close() {
            httpServer.stop(0)
            executor.shutdownNow()
        }
    }
}
