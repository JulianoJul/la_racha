package com.laracha.app.calendar

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarOccurrenceTimeTest {
    private val newYork = ZoneId.of("America/New_York")

    @Test
    fun nonexistentWallTimeMovesForwardByDstGap() {
        val start = CalendarOccurrenceTime.resolveStart(
            LocalDate.of(2026, 3, 8),
            LocalTime.of(2, 30),
            newYork,
        )

        assertEquals(LocalTime.of(3, 30), start.toLocalTime())
    }

    @Test
    fun ambiguousWallTimeUsesEarlierOffset() {
        val start = CalendarOccurrenceTime.resolveStart(
            LocalDate.of(2026, 11, 1),
            LocalTime.of(1, 30),
            newYork,
        )

        assertEquals("-04:00", start.offset.toString())
    }
}
