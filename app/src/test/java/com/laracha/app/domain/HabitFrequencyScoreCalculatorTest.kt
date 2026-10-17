package com.laracha.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitFrequencyScoreCalculatorTest {
    private val monday = LocalDate.of(2026, 1, 5)

    @Test
    fun fixedDaysEvaluateOnlyScheduledOpportunitiesAndKeepFreeDayMarksOutOfScore() {
        val rule = HabitFrequency.FixedDays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        val history = setOf(monday, monday.plusDays(1))
        val score = calculate(rule, history, monday.plusWeeks(1))

        assertEquals(score, calculate(rule, history - monday.plusDays(1), monday.plusWeeks(1)), 0.0)
        assertEquals(percent((1.0 - alpha) * alpha), HabitScoreCalculator.percentage(score))
    }

    @Test
    fun everyNDaysUsesStableReferenceAndIgnoresIntermediateMarks() {
        val rule = HabitFrequency.EveryNDays(intervalDays = 3, referenceDate = monday)
        val history = setOf(monday, monday.plusDays(1))
        val score = calculate(rule, history, monday.plusDays(7))

        assertEquals(score, calculate(rule, history - monday.plusDays(1), monday.plusDays(7)), 0.0)
        assertEquals(percent((1.0 - alpha) * alpha * alpha), HabitScoreCalculator.percentage(score))
    }

    @Test
    fun weeklyQuotaScoresClosedWeeksAsFractionsAndIgnoresOpenWeek() {
        val rule = HabitFrequency.WeeklyQuota(2)
        val history = setOf(
            monday.plusDays(1), // one of two in the first closed week
            monday.plusWeeks(1),
            monday.plusWeeks(1).plusDays(2),
            monday.plusWeeks(1).plusDays(4), // excess is capped at target
            monday.plusWeeks(2), // open week; does not affect score
        )
        val periods = listOf(FrequencyPeriod(monday, rule))
        val scoreWithOpenWeekMark = HabitScoreCalculator.calculate(monday, history, monday.plusWeeks(2), periods)
        val scoreWithoutOpenWeekMark = HabitScoreCalculator.calculate(monday, history - monday.plusWeeks(2), monday.plusWeeks(2), periods)
        val exactHistory = history - monday.plusWeeks(1).plusDays(4) - monday.plusWeeks(2)
        val scoreAtExactQuota = HabitScoreCalculator.calculate(monday, exactHistory, monday.plusWeeks(2), periods)

        assertEquals(scoreWithoutOpenWeekMark, scoreWithOpenWeekMark, 0.0)
        assertEquals(scoreAtExactQuota, scoreWithOpenWeekMark, 0.0)
        assertEquals(percent(0.5 * (1.0 - alpha) * alpha + (1.0 - alpha)), HabitScoreCalculator.percentage(scoreWithOpenWeekMark))
        assertTrue(scoreWithOpenWeekMark in 0.0..1.0)
        assertNotEquals(100, HabitScoreCalculator.percentage(scoreWithOpenWeekMark))
    }

    @Test
    fun oneDayIntervalIsEquivalentToDaily() {
        val history = setOf(monday, monday.plusDays(2))
        val daily = listOf(FrequencyPeriod(monday, HabitFrequency.Daily))
        val everyDay = listOf(FrequencyPeriod(monday, HabitFrequency.EveryNDays(1, monday)))

        assertEquals(
            HabitScoreCalculator.calculate(monday, history, monday.plusDays(4), daily),
            HabitScoreCalculator.calculate(monday, history, monday.plusDays(4), everyDay),
            0.0,
        )
    }

    @Test
    fun frequencyChangesBeginOnTheNextCompatiblePeriod() {
        val today = LocalDate.of(2026, 1, 7) // Wednesday
        assertEquals(today.plusDays(1), HabitFrequencyRules.effectiveFrom(HabitFrequency.Daily, HabitFrequency.FixedDays(setOf(DayOfWeek.FRIDAY)), today))
        assertEquals(LocalDate.of(2026, 1, 12), HabitFrequencyRules.effectiveFrom(HabitFrequency.Daily, HabitFrequency.WeeklyQuota(3), today))
        assertEquals(LocalDate.of(2026, 1, 12), HabitFrequencyRules.effectiveFrom(HabitFrequency.WeeklyQuota(3), HabitFrequency.Daily, today))
    }

    @Test
    fun historicalTrackingStartPrecedesCreationWithoutChangingEarlierRule() {
        val created = monday.plusWeeks(2)
        val historical = monday
        val history = setOf(historical, created)
        val periods = listOf(FrequencyPeriod(created, HabitFrequency.FixedDays(setOf(DayOfWeek.MONDAY))))

        assertEquals(
            HabitScoreCalculator.calculate(historical, history, created),
            HabitScoreCalculator.calculate(created, history, created, periods),
            0.0,
        )
    }

    private fun calculate(rule: HabitFrequency, history: Set<LocalDate>, today: LocalDate): Double =
        HabitScoreCalculator.calculate(monday, history, today, listOf(FrequencyPeriod(monday, rule)))

    private fun percent(score: Double) = (score * 100.0 + 0.5).toInt()

    private val alpha = Math.pow(0.5, 1.0 / 13.0)
}
