package com.laracha.app.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalDayTest {
    @Test
    fun sameInstantCanBelongToDifferentLocalCalendarDays() {
        val clock = Clock.fixed(Instant.parse("2026-10-16T23:30:00Z"), ZoneOffset.UTC)

        assertEquals(LocalDate.of(2026, 10, 16), LocalDay.today(clock, ZoneId.of("UTC")))
        assertEquals(LocalDate.of(2026, 10, 17), LocalDay.today(clock, ZoneId.of("Europe/Madrid")))
    }

    @Test
    fun daylightSavingTransitionDoesNotSkipCalendarDates() {
        val zone = ZoneId.of("Europe/Madrid")
        val before = Clock.fixed(Instant.parse("2026-03-29T00:30:00Z"), ZoneOffset.UTC)
        val after = Clock.fixed(Instant.parse("2026-03-29T01:30:00Z"), ZoneOffset.UTC)

        assertEquals(LocalDate.of(2026, 3, 29), LocalDay.today(before, zone))
        assertEquals(LocalDate.of(2026, 3, 29), LocalDay.today(after, zone))
    }

    @Test
    fun dateChangesAtLocalMidnight() {
        val justBeforeMidnight = Clock.fixed(Instant.parse("2026-10-16T21:59:59Z"), ZoneOffset.UTC)
        val atMidnight = Clock.fixed(Instant.parse("2026-10-16T22:00:00Z"), ZoneOffset.UTC)
        val madrid = ZoneId.of("Europe/Madrid")

        assertEquals(LocalDate.of(2026, 10, 16), LocalDay.today(justBeforeMidnight, madrid))
        assertEquals(LocalDate.of(2026, 10, 17), LocalDay.today(atMidnight, madrid))
    }

    @Test
    fun localCalendarHandlesLeapDayAndYearBoundary() {
        val leapDay = Clock.fixed(Instant.parse("2024-02-29T12:00:00Z"), ZoneOffset.UTC)
        val newYear = Clock.fixed(Instant.parse("2025-01-01T00:30:00Z"), ZoneOffset.UTC)

        assertEquals(LocalDate.of(2024, 2, 29), LocalDay.today(leapDay, ZoneOffset.UTC))
        assertEquals(LocalDate.of(2024, 12, 31), LocalDay.today(newYear, ZoneId.of("America/Los_Angeles")))
    }
}
