package com.laracha.app.ui

import com.laracha.app.domain.HabitFrequency
import java.time.DayOfWeek

internal fun HabitFrequency.label(): String = when (this) {
    HabitFrequency.Daily -> "Diario"
    is HabitFrequency.FixedDays -> "Días fijos: " + days.sortedBy { it.value }.joinToString(", ") { day ->
        when (day) {
            DayOfWeek.MONDAY -> "lunes"
            DayOfWeek.TUESDAY -> "martes"
            DayOfWeek.WEDNESDAY -> "miércoles"
            DayOfWeek.THURSDAY -> "jueves"
            DayOfWeek.FRIDAY -> "viernes"
            DayOfWeek.SATURDAY -> "sábado"
            DayOfWeek.SUNDAY -> "domingo"
        }
    }
    is HabitFrequency.EveryNDays -> "Cada $intervalDays días"
    is HabitFrequency.WeeklyQuota -> if (timesPerWeek == 1) "1 día por semana" else "$timesPerWeek días por semana"
}
