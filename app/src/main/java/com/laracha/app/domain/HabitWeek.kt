package com.laracha.app.domain

import java.time.LocalDate

data class HabitWeekDay(
    val date: LocalDate,
    val initial: String,
    val isToday: Boolean,
    val completed: Boolean,
    val available: Boolean,
)

/** Local-calendar day indicators; no derived state is persisted. */
object HabitWeek {
    private val initials = listOf("L", "M", "M", "J", "V", "S", "D")

    fun days(createdOn: LocalDate, completedDays: Set<LocalDate>, today: LocalDate): List<HabitWeekDay> {
        val monday = today.minusDays(today.dayOfWeek.value - 1L)
        return dayIndicators(monday, createdOn, completedDays, today)
    }

    /** Rolling seven-day window, with today at index 3 even on Monday/Sunday. */
    fun daysAroundToday(createdOn: LocalDate, completedDays: Set<LocalDate>, today: LocalDate): List<HabitWeekDay> =
        dayIndicators(today.minusDays(3), createdOn, completedDays, today)

    private fun dayIndicators(
        firstDay: LocalDate,
        createdOn: LocalDate,
        completedDays: Set<LocalDate>,
        today: LocalDate,
    ): List<HabitWeekDay> {
        val trackingStart = completedDays.asSequence()
            .filterNot { it.isAfter(today) }
            .minOrNull()
            ?.let { minOf(createdOn, it) }
            ?: createdOn
        return (0..6).map { index ->
            val date = firstDay.plusDays(index.toLong())
            val available = !date.isBefore(trackingStart) && !date.isAfter(today)
            HabitWeekDay(
                date = date,
                initial = initials[date.dayOfWeek.value - 1],
                isToday = date == today,
                completed = available && date in completedDays,
                available = available,
            )
        }
    }
}
