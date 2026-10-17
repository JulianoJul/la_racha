package com.laracha.app.domain

import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/** Calculates a habit score from dated frequency periods and completion history; derived values are never persisted. */
object HabitScoreCalculator {
    private val decay = Math.pow(0.5, 1.0 / 13.0)

    /** Daily compatibility entry point; a pending current day is deliberately not scored. */
    fun calculate(
        createdOn: LocalDate,
        completedDays: Set<LocalDate>,
        today: LocalDate,
    ): Double = calculate(createdOn, completedDays, today, listOf(FrequencyPeriod(createdOn, HabitFrequency.Daily)))

    /** Calculates over opportunity units while retaining each dated frequency rule. */
    fun calculate(
        createdOn: LocalDate,
        completedDays: Set<LocalDate>,
        today: LocalDate,
        frequencyPeriods: List<FrequencyPeriod>,
    ): Double {
        if (createdOn.isAfter(today)) return 0.0

        var score = 0.0
        val trackingStart = completedDays.asSequence()
            .filterNot { it.isAfter(today) }
            .minOrNull()
            ?.let { minOf(createdOn, it) }
            ?: createdOn
        var day = trackingStart
        val yesterday = today.minusDays(1)
        while (!day.isAfter(today)) {
            val frequency = frequencyAt(day, frequencyPeriods)
            val weekStart = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val closedWeeklyFrequency = if (day.dayOfWeek == DayOfWeek.SUNDAY && !day.isAfter(yesterday)) {
                frequencyAt(weekStart, frequencyPeriods) as? HabitFrequency.WeeklyQuota
            } else null
            if (closedWeeklyFrequency != null) {
                val completed = completedDays.count { !it.isBefore(weekStart) && !it.isAfter(day) }
                score = updateValue(
                    score,
                    (completed.toDouble() / closedWeeklyFrequency.timesPerWeek).coerceAtMost(1.0),
                )
            } else if (frequency !is HabitFrequency.WeeklyQuota) {
                val isClosedOpportunity = day.isBefore(today)
                val completedToday = day == today && day in completedDays
                if ((isClosedOpportunity || completedToday) && isOpportunity(day, frequency)) {
                    score = update(score, day in completedDays)
                }
            }
            if (day == today) break
            day = day.plusDays(1)
        }

        return score.coerceIn(0.0, 1.0)
    }

    fun percentage(score: Double): Int = (score.coerceIn(0.0, 1.0) * 100.0).roundToInt()

    private fun update(previous: Double, completed: Boolean): Double =
        decay * previous + (1.0 - decay) * if (completed) 1.0 else 0.0

    private fun updateValue(previous: Double, value: Double): Double =
        decay * previous + (1.0 - decay) * value

    private fun frequencyAt(date: LocalDate, periods: List<FrequencyPeriod>): HabitFrequency =
        periods.asSequence()
            .filter { !it.effectiveFrom.isAfter(date) }
            .maxByOrNull(FrequencyPeriod::effectiveFrom)
            ?.frequency
            ?: HabitFrequency.Daily

    private fun isOpportunity(date: LocalDate, frequency: HabitFrequency): Boolean = when (frequency) {
        HabitFrequency.Daily -> true
        is HabitFrequency.FixedDays -> date.dayOfWeek in frequency.days
        is HabitFrequency.EveryNDays -> Math.floorMod(
            ChronoUnit.DAYS.between(frequency.referenceDate, date),
            frequency.intervalDays.toLong(),
        ) == 0L
        is HabitFrequency.WeeklyQuota -> false
    }

}
