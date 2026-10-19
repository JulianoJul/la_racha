package com.laracha.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitRecentProgressTest {
    private val today = LocalDate.of(2026, 10, 17) // Saturday
    private val createdOn = today.minusMonths(1)

    @Test
    fun noHistoryStillShowsTodayForEveryFrequency() {
        frequencies().forEach { frequency ->
            val days = HabitWeek.recentProgressDays(createdOn, emptySet(), today, frequency)
            assertEquals(listOf(today), days.map { it.date })
            assertTrue(days.single().isToday)
            assertFalse(days.single().completed)
        }
    }

    @Test
    fun dailyPendingTodayKeepsPreviousChecksAcrossOneMissingDay() {
        val days = HabitWeek.recentProgressDays(
            createdOn, setOf(today.minusDays(5), today.minusDays(3), today.minusDays(2), today.minusDays(1)),
            today, HabitFrequency.Daily,
        )
        assertEquals(listOf("L", "M", "J", "V", "S"), days.map { it.initial })
        assertEquals(listOf(today.minusDays(5), today.minusDays(3), today.minusDays(2), today.minusDays(1), today), days.map { it.date })
        assertTrue(days.dropLast(1).all { it.completed && !it.isToday })
        assertFalse(days.last().completed)
    }

    @Test
    fun checkingTodayDoesNotRemoveTheSingleGapTolerance() {
        val days = HabitWeek.recentProgressDays(createdOn, setOf(today.minusDays(2), today), today, HabitFrequency.Daily)
        assertEquals(listOf(today.minusDays(2), today), days.map { it.date })
        assertTrue(days.all { it.completed })
    }

    @Test
    fun mondayThroughThursdayStillShowsOnSaturdayWithoutInventingFriday() {
        val completed = (2L..5L).mapTo(mutableSetOf()) { today.minusDays(it) }
        val days = HabitWeek.recentProgressDays(createdOn, completed, today, HabitFrequency.Daily)
        assertEquals(listOf("L", "M", "M", "J", "S"), days.map { it.initial })
        assertFalse(days.last().completed)
        assertTrue(days.none { it.date == today.minusDays(1) })
        assertEquals(0, StreakCalculator.calculate(createdOn, completed, today).current)
    }

    @Test
    fun secondMissingDayStopsOlderChecksWhetherTheGapsAreAdjacentOrSeparate() {
        listOf(
            setOf(today.minusDays(3)),
            setOf(today.minusDays(6), today.minusDays(4), today.minusDays(2), today.minusDays(1)),
        ).zip(listOf(listOf(today), listOf(today.minusDays(4), today.minusDays(2), today.minusDays(1), today)))
            .forEach { (completed, expected) ->
                assertEquals(expected, HabitWeek.recentProgressDays(createdOn, completed, today, HabitFrequency.Daily).map { it.date })
            }
    }

    @Test
    fun longDailyStreakShowsAtMostSevenIncludingToday() {
        val completed = (0L..20L).mapTo(mutableSetOf()) { today.minusDays(it) }
        val days = HabitWeek.recentProgressDays(createdOn, completed, today, HabitFrequency.Daily)
        assertEquals((6L downTo 0L).map { today.minusDays(it) }, days.map { it.date })
        assertTrue(days.all { it.completed })
        assertEquals(1, days.count { it.isToday })
    }

    @Test
    fun fixedDaysIncludeExtraTuesdayAndSkipUncompletedScheduledMonday() {
        val frequency = HabitFrequency.FixedDays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        val days = HabitWeek.recentProgressDays(
            createdOn, setOf(today.minusDays(4), today.minusDays(3), today.minusDays(1)), today, frequency,
        )
        assertEquals(listOf("M", "M", "V", "S"), days.map { it.initial })
        assertTrue(days.dropLast(1).all { it.completed })
        assertFalse(days.last().completed) // Today is free but still has the completion control.
    }

    @Test
    fun intervalIncludesExtraDaysWithoutRequiringConsecutiveOpportunities() {
        val days = HabitWeek.recentProgressDays(
            createdOn, setOf(today.minusDays(6), today.minusDays(2)), today,
            HabitFrequency.EveryNDays(3, today.minusDays(6)),
        )
        assertEquals(listOf(today.minusDays(6), today.minusDays(2), today), days.map { it.date })
    }

    @Test
    fun weeklyQuotaShowsCheckedDatesAcrossWeekBoundaryButNeverOlderThanSevenDays() {
        val monday = LocalDate.of(2026, 10, 19)
        val days = HabitWeek.recentProgressDays(
            createdOn, setOf(monday.minusDays(7), monday.minusDays(6), monday.minusDays(2)), monday,
            HabitFrequency.WeeklyQuota(3),
        )
        assertEquals(listOf(monday.minusDays(6), monday.minusDays(2), monday), days.map { it.date })
        assertEquals(listOf("M", "S", "L"), days.map { it.initial })
    }

    @Test
    fun checkingAndUncheckingTodayNeverDuplicatesTodayOrChangesPreviousDays() {
        frequencies().forEach { frequency ->
            val completed = setOf(today.minusDays(2), today.minusDays(1))
            val pending = HabitWeek.recentProgressDays(createdOn, completed, today, frequency)
            val checked = HabitWeek.recentProgressDays(createdOn, completed + today, today, frequency)
            assertEquals(pending.dropLast(1), checked.dropLast(1))
            assertEquals(1, checked.count { it.isToday })
            assertTrue(checked.last().completed)
        }
    }

    @Test
    fun historicalCompletionBeforeCreationIsIncludedButFutureIsNeverShown() {
        val days = HabitWeek.recentProgressDays(today, setOf(today.minusDays(1), today.plusDays(1)), today, HabitFrequency.Daily)
        assertEquals(listOf(today.minusDays(1), today), days.map { it.date })
        assertTrue(days.first().completed && days.first().available)
    }

    @Test
    fun windowCrossesYearAndLeapDayUsingCalendarDates() {
        listOf(LocalDate.of(2027, 1, 1), LocalDate.of(2028, 3, 1)).forEach { current ->
            val completed = (1L..7L).mapTo(mutableSetOf()) { current.minusDays(it) }
            val days = HabitWeek.recentProgressDays(current, completed, current, HabitFrequency.WeeklyQuota(2))
            assertEquals((6L downTo 0L).map { current.minusDays(it) }, days.map { it.date })
        }
    }

    private fun frequencies() = listOf(
        HabitFrequency.Daily,
        HabitFrequency.FixedDays(setOf(DayOfWeek.MONDAY)),
        HabitFrequency.EveryNDays(3, today),
        HabitFrequency.WeeklyQuota(3),
    )
}
