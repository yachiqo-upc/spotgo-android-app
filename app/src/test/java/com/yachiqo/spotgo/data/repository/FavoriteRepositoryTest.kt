package com.yachiqo.spotgo.data.repository

import com.yachiqo.spotgo.TestDatabase
import com.yachiqo.spotgo.data.local.SpotGoDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Favorites of the Driver (mockup 03: heart on a parking + "Saved" chip).
 * UI-only resource that mirrors the future `/api/v1/favorites`.
 */
class FavoriteRepositoryTest {

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

    @Test
    fun theSeededFavoriteIsListedForItsProfile() {
        assertTrue(FavoriteRepository.is_saved("zne-seed-0001"))
        assertEquals(setOf("zne-seed-0001"), FavoriteRepository.saved_zone_ids())
        val zones = FavoriteRepository.saved_zones()
        assertEquals(1, zones.size)
        assertEquals("San Isidro Centro", zones.single().name)
        assertFalse(FavoriteRepository.is_saved("zne-seed-0003"))
    }

    @Test
    fun togglingSavesAndRemovesTheZone() {
        assertTrue(FavoriteRepository.toggle("zne-seed-0003"))
        assertTrue(FavoriteRepository.is_saved("zne-seed-0003"))
        assertEquals(setOf("zne-seed-0001", "zne-seed-0003"), FavoriteRepository.saved_zone_ids())
        assertEquals(2, FavoriteRepository.saved_zones().size)

        assertFalse(FavoriteRepository.toggle("zne-seed-0003"))
        assertFalse(FavoriteRepository.is_saved("zne-seed-0003"))
        assertEquals(setOf("zne-seed-0001"), FavoriteRepository.saved_zone_ids())

        // The seed favorite survives the toggling of another zone.
        assertTrue(FavoriteRepository.is_saved("zne-seed-0001"))
    }

    @Test
    fun togglingWithoutASessionChangesNothing() {
        val before = SpotGoDatabase.favorites.size
        AuthRepository.sign_out()
        assertFalse(FavoriteRepository.toggle("zne-seed-0003"))
        assertEquals(before, SpotGoDatabase.favorites.size)
        assertTrue(FavoriteRepository.saved_zone_ids().isEmpty())
    }

    @Test
    fun favoritesBelongToTheProfileThatCreatedThem() {
        // The Google account is a different profile with its own favorites.
        AuthRepository.sign_in_with_google(keep_signed_in = true)
        assertTrue(FavoriteRepository.saved_zone_ids().isEmpty())
        assertFalse(FavoriteRepository.is_saved("zne-seed-0001"))

        assertTrue(FavoriteRepository.toggle("zne-seed-0001"))
        assertEquals(setOf("zne-seed-0001"), FavoriteRepository.saved_zone_ids())

        // Back to the seed Driver: its own favorite was never touched.
        assertTrue(
            AuthRepository.sign_in("brigitte.carhuaz@example.com", "SpotGo2026", true) is AuthResult.Success,
        )
        assertEquals(setOf("zne-seed-0001"), FavoriteRepository.saved_zone_ids())
        assertEquals(2, SpotGoDatabase.favorites.size)
    }
}
