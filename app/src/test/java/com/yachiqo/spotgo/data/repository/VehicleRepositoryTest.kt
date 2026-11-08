package com.yachiqo.spotgo.data.repository

import com.yachiqo.spotgo.TestDatabase
import com.yachiqo.spotgo.data.local.SpotGoDatabase
import com.yachiqo.spotgo.data.model.VehicleStatus
import com.yachiqo.spotgo.data.model.VehicleType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Vehicle management of the Driver (US27): register, associate with the
 * profile, keep the plate and reject invalid or duplicated vehicles.
 */
class VehicleRepositoryTest {

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

    private fun error(result: VehicleResult): VehicleError =
        (result as VehicleResult.Failure).error

    /* ------------------------------------------------------------------ */

    @Test
    fun theSeedVehiclesAreListedOldestFirstAndTheFirstIsTheDefault() {
        val vehicles = VehicleRepository.vehicles_for_current_profile()
        assertEquals(listOf("AJK-247", "C8P-521"), vehicles.map { it.plate })
        assertEquals("AJK-247", VehicleRepository.default_vehicle()?.plate)
        assertEquals("Toyota", VehicleRepository.default_vehicle()?.make)
    }

    @Test
    fun onlyTheActiveVehiclesOfTheSignedInProfileAreListed() {
        // The Google demo profile has no vehicles of its own.
        AuthRepository.sign_in_with_google(keep_signed_in = true)
        assertEquals(0, VehicleRepository.vehicles_for_current_profile().size)
        assertNull(VehicleRepository.default_vehicle())
    }

    @Test
    fun aVehicleIsRegisteredWithThePeruvianPlateFormat() {
        val result = VehicleRepository.add_vehicle(
            plate = "  xyz-987 ",
            make = "Mazda",
            model = "Mazda 3",
            color = "Red",
            vehicle_type = VehicleType.CAR,
        )
        assertTrue(result is VehicleResult.Success)
        val vehicle = (result as VehicleResult.Success).vehicle
        // Trimmed and upper-cased before it is stored.
        assertEquals("XYZ-987", vehicle.plate)
        assertEquals("Mazda", vehicle.make)
        assertEquals(VehicleStatus.ACTIVE, vehicle.status)

        val listed = VehicleRepository.vehicles_for_current_profile()
        assertTrue(listed.any { it.plate == "XYZ-987" })
        assertEquals(vehicle.vehicle_id, VehicleRepository.vehicle_by_id(vehicle.vehicle_id)?.vehicle_id)
        // The seed vehicle stays the default (it was registered first).
        assertEquals("AJK-247", VehicleRepository.default_vehicle()?.plate)
    }

    @Test
    fun aMalformedPlateIsRejected() {
        listOf("ABC123", "AB-123", "ABC-12", "ABC 123", "ABCD-123").forEach { plate ->
            val result = VehicleRepository.add_vehicle(plate, "Mazda", "Mazda 3", "Red", VehicleType.CAR)
            assertEquals("plate '$plate'", VehicleError.INVALID_PLATE, error(result))
        }
        assertTrue(VehicleRepository.vehicles_for_current_profile().none { it.plate.startsWith("ABC") })
    }

    @Test
    fun aVehicleWithoutTheRequiredDataIsRejected() {
        assertEquals(
            VehicleError.INVALID_FORM,
            error(VehicleRepository.add_vehicle("", "Mazda", "Mazda 3", "Red", VehicleType.CAR)),
        )
        assertEquals(
            VehicleError.INVALID_FORM,
            error(VehicleRepository.add_vehicle("XYZ-987", "  ", "Mazda 3", "Red", VehicleType.CAR)),
        )
        assertEquals(
            VehicleError.INVALID_FORM,
            error(VehicleRepository.add_vehicle("XYZ-987", "Mazda", "", "Red", VehicleType.CAR)),
        )
        assertEquals(
            VehicleError.INVALID_FORM,
            error(VehicleRepository.add_vehicle("XYZ-987", "Mazda", "Mazda 3", "", VehicleType.CAR)),
        )
        assertEquals(2, VehicleRepository.vehicles_for_current_profile().size)
    }

    @Test
    fun theSamePlateCannotBeRegisteredTwiceOnTheAccount() {
        val first = VehicleRepository.add_vehicle("AJK-247", "Toyota", "Yaris", "White", VehicleType.CAR)
        assertEquals(VehicleError.DUPLICATE_PLATE, error(first))

        // Also when the case differs.
        val second = VehicleRepository.add_vehicle("ajk-247", "Toyota", "Yaris", "White", VehicleType.CAR)
        assertEquals(VehicleError.DUPLICATE_PLATE, error(second))
        assertEquals(2, VehicleRepository.vehicles_for_current_profile().size)
    }

    @Test
    fun registeringAVehicleRequiresASignedInDriver() {
        AuthRepository.sign_out()
        val result = VehicleRepository.add_vehicle("XYZ-987", "Mazda", "Mazda 3", "Red", VehicleType.CAR)
        assertEquals(VehicleError.INVALID_FORM, error(result))
        assertTrue(VehicleRepository.vehicles_for_current_profile().isEmpty())
    }

    @Test
    fun removingAVehicleIsASoftDeleteThatKeepsTheRowForReceipts() {
        val removed = VehicleRepository.remove_vehicle("veh-seed-0002")
        assertTrue(removed)

        // Gone from the list, but the row still resolves the plate.
        assertTrue(VehicleRepository.vehicles_for_current_profile().none { it.plate == "C8P-521" })
        val kept = VehicleRepository.vehicle_by_id("veh-seed-0002")
        assertNotNull(kept)
        assertEquals(VehicleStatus.INACTIVE, kept?.status)
        assertEquals("C8P-521", kept?.plate)
        assertEquals("AJK-247", VehicleRepository.default_vehicle()?.plate)
    }

    @Test
    fun removingAnUnknownVehicleReturnsFalse() {
        assertFalse(VehicleRepository.remove_vehicle("veh-does-not-exist"))
        assertEquals(2, SpotGoDatabase.vehicles.size)
    }
}
