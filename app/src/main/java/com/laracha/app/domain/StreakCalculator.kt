package com.laracha.app.domain

import java.time.LocalDate

data class Streaks(val current: Int, val best: Int)

/** Calculates consecutive completed calendar days independently from the habit score. */
object StreakCalculator {
    fun calculate(
        createdOn: LocalDate,
        completedDays: Set<LocalDate>,
        today: LocalDate,
    ): Streaks {
        if (createdOn.isAfter(today)) return Streaks(current = 0, best = 0)

        val currentEndpoint = if (today in completedDays) today else today.minusDays(1)
        var current = 0
        var day = currentEndpoint
        while (!day.isBefore(createdOn) && day in completedDays) {
            current++
            day = day.minusDays(1)
        }

        var best = 0
        var run = 0
        day = createdOn
        while (!day.isAfter(today)) {
            if (day in completedDays) {
                run++
                best = maxOf(best, run)
            } else {
                run = 0
            }
            day = day.plusDays(1)
        }

        return Streaks(current = current, best = best)
    }
}
