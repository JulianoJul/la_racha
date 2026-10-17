package com.laracha.app.domain

import java.time.LocalDate

data class HabitWeekDay(
    val date: LocalDate,
    val initial: String,
    val isToday: Boolean,
    val completed: Boolean,
    val available: Boolean,
)

/** A local-calendar week, Monday through Sunday; no derived state is persisted. */
object HabitWeek {
    private val initials = listOf("L", "M", "M", "J", "V", "S", "D")

    fun days(createdOn: LocalDate, completedDays: Set<LocalDate>, today: LocalDate): List<HabitWeekDay> {
        val monday = today.minusDays(today.dayOfWeek.value - 1L)
        return initials.mapIndexed { index, initial ->
            val date = monday.plusDays(index.toLong())
            val available = !date.isBefore(createdOn) && !date.isAfter(today)
            HabitWeekDay(
                date = date,
                initial = initial,
                isToday = date == today,
                completed = available && date in completedDays,
                available = available,
            )
        }
    }
}
