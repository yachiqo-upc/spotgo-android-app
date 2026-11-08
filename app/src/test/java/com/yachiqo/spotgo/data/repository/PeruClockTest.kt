package com.yachiqo.spotgo.data.repository

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wall clock of the app: every "now" of the reservation flow must be the
 * current time of Peru (America/Lima), never the timezone of the device.
 */
class PeruClockTest {

    @Test
    fun theZoneOfTheAppIsLima() {
        assertEquals("America/Lima", PeruClock.ZONE.id)
        assertEquals(ZoneId.of("America/Lima"), PeruClock.ZONE)
    }

    @Test
    fun nowAndTodayComeFromTheSamePeruvianDate() {
        val now = PeruClock.now()
        assertEquals(now.toLocalDate(), PeruClock.today())
        assertEquals(now.toLocalTime().toSecondOfDay() / 60, PeruClock.minutesOfDay())
        // The clock of Lima agrees with the system clock of Lima within a minute.
        val lima = LocalDateTime.now(ZoneId.of("America/Lima"))
        assertTrue(Math.abs(java.time.Duration.between(now, lima).seconds) < 60)
    }

    @Test
    fun minutesOfDayStaysInsideTheDay() {
        val minutes = PeruClock.minutesOfDay()
        assertTrue(minutes in 0..1439)
        assertEquals(PeruClock.time().toSecondOfDay() / 60, minutes)
    }

    @Test
    fun aTimeInThePastIsRecognisedAndAFutureOneIsNot() {
        assertTrue(PeruClock.isPast(PeruClock.now().minusMinutes(1)))
        assertTrue(PeruClock.isPast(PeruClock.now().minusSeconds(1)))
        assertFalse(PeruClock.isPast(PeruClock.now().plusMinutes(5)))
        // "now" is never after itself, so it is already past.
        assertTrue(PeruClock.isPast(PeruClock.now()))
        // Midnight of today has already passed (except exactly at 00:00:00.000).
        assertFalse(PeruClock.isPast(PeruClock.today().plusDays(1).atStartOfDay()))
    }
}
