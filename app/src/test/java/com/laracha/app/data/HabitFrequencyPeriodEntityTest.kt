package com.laracha.app.data

import com.laracha.app.domain.HabitFrequency
import com.laracha.app.domain.FrequencyPeriod
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class HabitFrequencyPeriodEntityTest {
    private val date = LocalDate.of(2026, 1, 5)

    @Test
    fun allFrequencyConfigurationsRoundTripWithoutLosingReferenceOrWeekdays() {
        val rules = listOf(
            HabitFrequency.Daily,
            HabitFrequency.FixedDays(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)),
            HabitFrequency.EveryNDays(4, date.minusDays(2)),
            HabitFrequency.WeeklyQuota(3),
        )

        rules.forEach { frequency ->
            assertEquals(
                FrequencyPeriod(date, frequency),
                HabitFrequencyPeriodEntity.from("habit", date, frequency).toDomain(),
            )
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun fixedDaysCannotBeEmpty() {
        HabitFrequency.FixedDays(emptySet())
    }

    @Test(expected = IllegalArgumentException::class)
    fun intervalMustBePositive() {
        HabitFrequency.EveryNDays(0, date)
    }

    @Test(expected = IllegalArgumentException::class)
    fun weeklyTargetMustBeWithinOneAndSeven() {
        HabitFrequency.WeeklyQuota(8)
    }
}
