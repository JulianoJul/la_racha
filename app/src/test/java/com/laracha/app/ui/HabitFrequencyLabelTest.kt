package com.laracha.app.ui

import com.laracha.app.domain.HabitFrequency
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class HabitFrequencyLabelTest {
    @Test
    fun intervalUsesRequestedEveryXDaysWording() {
        assertEquals("Cada 3 días", HabitFrequency.EveryNDays(3, LocalDate.of(2026, 10, 17)).label())
    }

    @Test
    fun weeklyQuotaStatesDaysPerWeekWithoutSuggestingAFixedInterval() {
        assertEquals("3 días por semana", HabitFrequency.WeeklyQuota(3).label())
    }

    @Test
    fun oneDayWeeklyTargetUsesSingular() {
        assertEquals("1 día por semana", HabitFrequency.WeeklyQuota(1).label())
    }

    @Test
    fun sevenDayWeeklyTargetStillDescribesAWeeklyQuota() {
        assertEquals("7 días por semana", HabitFrequency.WeeklyQuota(7).label())
    }
}
