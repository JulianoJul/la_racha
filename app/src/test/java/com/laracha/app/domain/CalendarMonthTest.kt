package com.laracha.app.domain

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarMonthTest {
    @Test
    fun monthStartingOnMondayHasNoLeadingBlanks() {
        val weeks = CalendarMonth.weeks(YearMonth.of(2024, 1))

        assertEquals(LocalDate.of(2024, 1, 1), weeks.first().first())
        assertEquals(5, weeks.size)
        assertEquals(LocalDate.of(2024, 1, 31), weeks.last()[2])
    }

    @Test
    fun leapMonthPlacesFebruaryTwentyNinthInCorrectWeek() {
        val weeks = CalendarMonth.weeks(YearMonth.of(2024, 2))

        assertEquals(5, weeks.size)
        assertNull(weeks.first()[0])
        assertNull(weeks.first()[1])
        assertNull(weeks.first()[2])
        assertEquals(LocalDate.of(2024, 2, 1), weeks.first()[3])
        assertEquals(LocalDate.of(2024, 2, 29), weeks.last()[3])
    }

    @Test
    fun allWeeksHaveSevenCellsAndRepresentEveryDateExactlyOnce() {
        val weeks = CalendarMonth.weeks(YearMonth.of(2026, 10))

        assertTrue(weeks.all { it.size == 7 })
        assertEquals((1..31).toList(), weeks.flatten().filterNotNull().map(LocalDate::getDayOfMonth))
    }
}
