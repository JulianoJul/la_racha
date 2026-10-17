package com.laracha.app.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitScoreCalculatorTest {
    private val start = LocalDate.of(2026, 1, 1)

    @Test
    fun perfectHistoryMatchesSpecifiedMilestones() {
        assertEquals(31, percentageAfter(7))
        assertEquals(50, percentageAfter(13))
        assertEquals(80, percentageAfter(30))
        assertEquals(96, percentageAfter(60))
    }

    @Test
    fun pendingTodayDoesNotCauseDecayAndCompletionIsAppliedOnce() {
        val yesterday = start.plusDays(12)
        val today = yesterday.plusDays(1)
        val history = (0L..12L).map(start::plusDays).toSet()

        val pending = HabitScoreCalculator.calculate(start, history, today)
        val marked = HabitScoreCalculator.calculate(start, history + today, today)
        val markedAgain = HabitScoreCalculator.calculate(start, history + today, today)

        assertEquals(50, HabitScoreCalculator.percentage(pending))
        assertEquals(53, HabitScoreCalculator.percentage(marked))
        assertEquals(marked, markedAgain, 0.0)
    }

    @Test
    fun oneClosedMissLowersScoreWithoutResettingIt() {
        val completed = (0L..29L).map(start::plusDays).toSet()
        val score = HabitScoreCalculator.calculate(start, completed, start.plusDays(31))

        assertEquals(76, HabitScoreCalculator.percentage(score))
        assertTrue(score in 0.0..1.0)
    }

    @Test
    fun futureCreationAndCompletionsBeforeCreationAreIgnored() {
        val today = start.plusDays(1)
        assertEquals(0.0, HabitScoreCalculator.calculate(today.plusDays(1), setOf(start), today), 0.0)
        assertEquals(
            HabitScoreCalculator.calculate(today, emptySet(), today),
            HabitScoreCalculator.calculate(today, setOf(start), today),
            0.0,
        )
    }

    @Test
    fun historicalCorrectionRecalculatesFollowingDays() {
        val today = start.plusDays(20)
        val history = (0L..20L).map(start::plusDays).toSet()
        val before = HabitScoreCalculator.calculate(start, history, today)
        val after = HabitScoreCalculator.calculate(start, history - start.plusDays(5), today)

        assertTrue(after < before)
        assertTrue(after >= 0.0)
    }

    @Test
    fun longMissesRemainFiniteAndWithinBounds() {
        val score = HabitScoreCalculator.calculate(start, emptySet(), start.plusYears(10))
        assertTrue(score.isFinite())
        assertTrue(score in 0.0..1.0)
    }

    private fun percentageAfter(days: Long): Int {
        val completed = (0 until days).map { start.plusDays(it) }.toSet()
        return HabitScoreCalculator.percentage(
            HabitScoreCalculator.calculate(start, completed, start.plusDays(days - 1)),
        )
    }
}
