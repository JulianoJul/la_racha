package com.laracha.app.domain

import java.time.LocalDate
import java.time.YearMonth

/** A Monday-first month grid. Nulls are only the leading/trailing empty cells. */
object CalendarMonth {
    fun weeks(month: YearMonth): List<List<LocalDate?>> {
        val leading = month.atDay(1).dayOfWeek.value - 1
        val cellCount = ((leading + month.lengthOfMonth() + 6) / 7) * 7
        return (0 until cellCount)
            .map { index ->
                val day = index - leading + 1
                if (day in 1..month.lengthOfMonth()) month.atDay(day) else null
            }
            .chunked(7)
    }
}
