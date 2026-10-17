package com.laracha.app.domain

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitWeekTest {
    private val today = LocalDate.of(2026, 10, 17)

    @Test
    fun weekStartsOnMondayAndKeepsBothSpanishMInitials() {
        val days = HabitWeek.days(today.minusWeeks(1), emptySet(), today)

        assertEquals(listOf("L", "M", "M", "J", "V", "S", "D"), days.map { it.initial })
        assertEquals(LocalDate.of(2026, 10, 12), days.first().date)
        assertEquals(LocalDate.of(2026, 10, 18), days.last().date)
        assertEquals(listOf(today), days.filter { it.isToday }.map { it.date })
    }

    @Test
    fun yesterdayCompletedKeepsItsCheckButNeverUsesTodaysHighlight() {
        val yesterday = today.minusDays(1)
        val day = HabitWeek.days(today.minusWeeks(1), setOf(yesterday), today).single { it.date == yesterday }

        assertTrue(day.completed)
        assertFalse(day.isToday)
    }

    @Test
    fun completingAndUncheckingTodayChangesOnlyToday() {
        val yesterday = today.minusDays(1)
        val checked = HabitWeek.days(today.minusWeeks(1), setOf(yesterday, today), today)
        val unchecked = HabitWeek.days(today.minusWeeks(1), setOf(yesterday), today)

        assertTrue(checked.single { it.isToday }.completed)
        assertFalse(unchecked.single { it.isToday }.completed)
        assertEquals(checked.filterNot { it.isToday }, unchecked.filterNot { it.isToday })
    }

    @Test
    fun futureIsNeverCompletedAndOldCompletionExtendsVisibleHistory() {
        val days = HabitWeek.days(today, setOf(today.minusDays(1), today, today.plusDays(1)), today)

        assertEquals(listOf(today.minusDays(1), today), days.filter { it.completed }.map { it.date })
        assertTrue(days.single { it.date == today.minusDays(1) }.available)
        assertFalse(days.last().completed)
        assertFalse(days.last().available)
    }

    @Test
    fun weekCanCrossYearBoundary() {
        val newYear = LocalDate.of(2027, 1, 1)
        val days = HabitWeek.days(newYear.minusWeeks(1), emptySet(), newYear)

        assertEquals(LocalDate.of(2026, 12, 28), days.first().date)
        assertEquals(LocalDate.of(2027, 1, 3), days.last().date)
    }

    @Test
    fun mondayStartsANewWeekRatherThanReusingLastSundaysCompletion() {
        val monday = LocalDate.of(2026, 10, 19)
        val days = HabitWeek.days(monday.minusWeeks(1), setOf(monday.minusDays(1)), monday)

        assertEquals(monday, days.first().date)
        assertTrue(days.none { it.completed })
    }

    @Test
    fun weekUsesTheInjectedLocalDayAcrossDstNotTheUtcDate() {
        val clock = Clock.fixed(Instant.parse("2026-11-02T01:30:00Z"), ZoneOffset.UTC)
        val localToday = LocalDay.today(clock, ZoneId.of("America/New_York"))
        val days = HabitWeek.days(localToday.minusWeeks(1), emptySet(), localToday)

        assertEquals(LocalDate.of(2026, 11, 1), days.last().date)
        assertTrue(days.last().isToday)
    }

    @Test
    fun carouselKeepsTodayInTheMiddleForEveryWeekday() {
        val monday = LocalDate.of(2026, 10, 19)
        repeat(7) { offset ->
            val current = monday.plusDays(offset.toLong())
            val days = HabitWeek.daysAroundToday(current.minusWeeks(1), emptySet(), current)

            assertEquals(7, days.size)
            assertEquals(current, days[3].date)
            assertEquals(listOf(current), days.filter { it.isToday }.map { it.date })
            assertEquals(current.minusDays(3), days.first().date)
            assertEquals(current.plusDays(3), days.last().date)
        }
    }

    @Test
    fun mondayCarouselIncludesRealPreviousWeekHistoryAndInactiveFuture() {
        val monday = LocalDate.of(2026, 10, 19)
        val sunday = monday.minusDays(1)
        val days = HabitWeek.daysAroundToday(monday, setOf(sunday, monday.plusDays(1)), monday)

        assertEquals("D", days[2].initial)
        assertTrue(days[2].completed)
        assertTrue(days[2].available)
        assertFalse(days[2].isToday)
        assertEquals("L", days[3].initial)
        assertTrue(days.drop(4).none { it.completed || it.available })
    }

    @Test
    fun carouselCrossesYearBoundaryWithoutWrappingWeekdaysToTheWrongDate() {
        val newYear = LocalDate.of(2027, 1, 1)
        val days = HabitWeek.daysAroundToday(newYear.minusWeeks(1), emptySet(), newYear)

        assertEquals(LocalDate.of(2026, 12, 29), days.first().date)
        assertEquals(LocalDate.of(2027, 1, 4), days.last().date)
        assertEquals(listOf("M", "M", "J", "V", "S", "D", "L"), days.map { it.initial })
    }
}
