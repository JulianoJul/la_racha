package com.laracha.app.calendar

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Converts the user's wall-clock schedule to the provider's instant using Java's gap/overlap policy. */
object CalendarOccurrenceTime {
    fun resolveStart(date: LocalDate, time: LocalTime, zone: ZoneId): ZonedDateTime = ZonedDateTime.of(date, time, zone)
}
