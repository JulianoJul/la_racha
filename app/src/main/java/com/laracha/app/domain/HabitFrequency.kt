package com.laracha.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

sealed interface HabitFrequency {
    data object Daily : HabitFrequency

    data class FixedDays(val days: Set<DayOfWeek>) : HabitFrequency {
        init { require(days.isNotEmpty()) { "Selecciona al menos un día" } }
    }

    data class EveryNDays(val intervalDays: Int, val referenceDate: LocalDate) : HabitFrequency {
        init { require(intervalDays >= 1) { "El intervalo debe ser al menos un día" } }
    }

    data class WeeklyQuota(val timesPerWeek: Int) : HabitFrequency {
        init { require(timesPerWeek in 1..7) { "La cuota semanal debe estar entre 1 y 7" } }
    }
}

data class FrequencyPeriod(
    val effectiveFrom: LocalDate,
    val frequency: HabitFrequency,
)

object HabitFrequencyRules {
    /** Preserve the in-progress unit when either side of a change is a weekly quota. */
    fun effectiveFrom(current: HabitFrequency, next: HabitFrequency, today: LocalDate): LocalDate =
        if (current is HabitFrequency.WeeklyQuota || next is HabitFrequency.WeeklyQuota) {
            today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        } else {
            today.plusDays(1)
        }
}
