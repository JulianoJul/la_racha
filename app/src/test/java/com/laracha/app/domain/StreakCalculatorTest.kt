package com.laracha.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakCalculatorTest {
    private val created = LocalDate.of(2026, 10, 10)

    @Test
    fun pendingTodayKeepsStreakThatEndedYesterday() {
        val history = setOf(created, created.plusDays(1), created.plusDays(2))
        assertEquals(Streaks(3, 3), StreakCalculator.calculate(created, history, created.plusDays(3)))
    }

    @Test
    fun pendingTodayUsesTwoCompletionsFromYesterdayAndTheDayBefore() {
        val dayNine = LocalDate.of(2026, 10, 9)
        val dayTen = LocalDate.of(2026, 10, 10)
        val dayEleven = LocalDate.of(2026, 10, 11)

        assertEquals(Streaks(2, 2), StreakCalculator.calculate(dayNine, setOf(dayNine, dayTen), dayEleven))
    }

    @Test
    fun missingYesterdayEndsCurrentStreakButKeepsBest() {
        val history = setOf(created, created.plusDays(1))
        assertEquals(Streaks(0, 2), StreakCalculator.calculate(created, history, created.plusDays(4)))
    }

    @Test
    fun markingTodayExtendsCurrentStreak() {
        val history = setOf(created, created.plusDays(1), created.plusDays(2))
        assertEquals(Streaks(4, 4), StreakCalculator.calculate(created, history + created.plusDays(3), created.plusDays(3)))
    }

    @Test
    fun tracksFromEarliestCompletionAndIgnoresDaysAfterToday() {
        val today = created.plusDays(1)
        val history = setOf(created.minusDays(1), created, today.plusDays(1))
        assertEquals(Streaks(2, 2), StreakCalculator.calculate(created, history, today))
    }

    @Test
    fun removingEarliestHistoricalCompletionMovesTrackingStartForward() {
        val earliest = created.minusDays(2)
        val next = created.minusDays(1)
        val history = setOf(earliest, next, created)

        assertEquals(Streaks(3, 3), StreakCalculator.calculate(created, history, created))
        assertEquals(Streaks(2, 2), StreakCalculator.calculate(created, history - earliest, created))
    }

    @Test
    fun futureHabitHasNoStreak() {
        assertEquals(Streaks(0, 0), StreakCalculator.calculate(created.plusDays(1), setOf(created), created))
    }
}
