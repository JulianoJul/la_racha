package com.laracha.app.domain

import java.time.LocalDate
import kotlin.math.roundToInt

/** Calculates a daily habit score from its history; it does not persist derived values. */
object HabitScoreCalculator {
    private val decay = Math.pow(0.5, 1.0 / 13.0)

    /** Returns a fraction in [0, 1]. A pending current day is deliberately not scored. */
    fun calculate(
        createdOn: LocalDate,
        completedDays: Set<LocalDate>,
        today: LocalDate,
    ): Double {
        if (createdOn.isAfter(today)) return 0.0

        var score = 0.0
        var day = createdOn
        val yesterday = today.minusDays(1)
        while (!day.isAfter(yesterday)) {
            score = update(score, day in completedDays)
            day = day.plusDays(1)
        }

        if (today in completedDays) score = update(score, completed = true)
        return score.coerceIn(0.0, 1.0)
    }

    fun percentage(score: Double): Int = (score.coerceIn(0.0, 1.0) * 100.0).roundToInt()

    private fun update(previous: Double, completed: Boolean): Double =
        decay * previous + (1.0 - decay) * if (completed) 1.0 else 0.0

}
