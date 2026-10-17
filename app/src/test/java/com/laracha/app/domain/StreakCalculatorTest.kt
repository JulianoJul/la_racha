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
    fun neverCountsDaysBeforeCreationOrAfterToday() {
        val today = created.plusDays(1)
        val history = setOf(created.minusDays(3), created, today.plusDays(1))
        assertEquals(Streaks(1, 1), StreakCalculator.calculate(created, history, today))
    }

    @Test
    fun futureHabitHasNoStreak() {
        assertEquals(Streaks(0, 0), StreakCalculator.calculate(created.plusDays(1), setOf(created), created))
    }
}
