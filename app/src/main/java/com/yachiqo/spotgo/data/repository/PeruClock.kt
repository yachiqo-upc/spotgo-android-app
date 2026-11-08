package com.yachiqo.spotgo.data.repository

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Wall clock of the app.
 *
 * SpotGo is designed for Peru, so every "now" used by the reservation flow
 * (date labels, past-time validation, demo timestamps) must be the current
 * time in America/Lima instead of the timezone of the device — the app could
 * be opened from another timezone (travel, emulator with UTC, …) and a
 * reservation for "10:30 AM Today" must be evaluated against Lima time.
 *
 * TODO(backend): when the API exists it will be the source of truth and this
 * helper only formats the server response for display.
 */
object PeruClock {

    /** The timezone of the product (Peruvian time). */
    val ZONE: ZoneId = ZoneId.of("America/Lima")

    /** Current date and time in Peru. */
    fun now(): LocalDateTime = LocalDateTime.now(ZONE)

    /** Current date in Peru ("Today" of the reserve form). */
    fun today(): LocalDate = LocalDate.now(ZONE)

    /** Current time of day in Peru. */
    fun time(): LocalTime = now().toLocalTime()

    /** Minutes of day in Peru, used to compare against the time options. */
    fun minutesOfDay(): Int = time().toSecondOfDay() / 60

    /** True when the given instant is already in the past in Peru. */
    fun isPast(start: LocalDateTime): Boolean = !start.isAfter(now())
}
