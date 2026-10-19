package com.laracha.app.calendar

import com.laracha.app.domain.HabitFrequency
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class CalendarRecurrencePlan(
    val firstDate: LocalDate,
    val rule: String,
    val frequencyKey: String,
    val quotaDaysCode: String,
)

/** Pure planning rules. A quota's chosen weekdays belong to Calendar, not the habit's history. */
object CalendarRecurrence {
    fun plan(frequency: HabitFrequency, notBefore: LocalDate, quotaDays: Set<DayOfWeek> = emptySet()): CalendarRecurrencePlan {
        val days = when (frequency) {
            is HabitFrequency.FixedDays -> frequency.days
            is HabitFrequency.WeeklyQuota -> quotaDays.also {
                require(it.size == frequency.timesPerWeek) { "Elige exactamente ${frequency.timesPerWeek} días para Calendar" }
            }
            else -> emptySet()
        }
        val firstDate = when (frequency) {
            is HabitFrequency.EveryNDays -> {
                val interval = frequency.intervalDays.toLong()
                val remainder = Math.floorMod(ChronoUnit.DAYS.between(frequency.referenceDate, notBefore), interval)
                notBefore.plusDays((interval - remainder) % interval)
            }
            is HabitFrequency.FixedDays, is HabitFrequency.WeeklyQuota ->
                (0L..6L).map(notBefore::plusDays).first { it.dayOfWeek in days }
            HabitFrequency.Daily -> notBefore
        }
        val rule = when (frequency) {
            HabitFrequency.Daily -> "FREQ=DAILY"
            is HabitFrequency.EveryNDays -> "FREQ=DAILY;INTERVAL=${frequency.intervalDays}"
            is HabitFrequency.FixedDays, is HabitFrequency.WeeklyQuota ->
                "FREQ=WEEKLY;BYDAY=${days.sortedBy { it.value }.joinToString(",") { weekdayCodes[it.value - 1] }};WKST=MO"
        }
        return CalendarRecurrencePlan(
            firstDate, rule, frequencyKey(frequency),
            if (frequency is HabitFrequency.WeeklyQuota) encodeDays(days) else "",
        )
    }

    fun frequencyKey(frequency: HabitFrequency): String = when (frequency) {
        HabitFrequency.Daily -> "DAILY"
        is HabitFrequency.FixedDays -> "FIXED_DAYS:${encodeDays(frequency.days)}"
        is HabitFrequency.EveryNDays -> "EVERY_N_DAYS:${frequency.intervalDays}:${frequency.referenceDate}"
        is HabitFrequency.WeeklyQuota -> "WEEKLY_QUOTA:${frequency.timesPerWeek}"
    }

    fun encodeDays(days: Set<DayOfWeek>): String = days.sortedBy { it.value }.joinToString(",") { it.value.toString() }

    fun decodeDays(code: String): Set<DayOfWeek> = code.split(',').mapNotNull { value ->
        value.toIntOrNull()?.takeIf { it in 1..7 }?.let(DayOfWeek::of)
    }.toSet()

    private val weekdayCodes = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
}
