package com.laracha.app.calendar

import com.laracha.app.domain.HabitFrequency
import java.time.DayOfWeek.*
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CalendarRecurrenceTest {
    private val monday = LocalDate.of(2026, 10, 19)

    @Test fun dailyKeepsRequestedStart() {
        val plan = CalendarRecurrence.plan(HabitFrequency.Daily, monday)
        assertEquals(monday, plan.firstDate)
        assertEquals("FREQ=DAILY", plan.rule)
        assertEquals("DAILY", plan.frequencyKey)
    }

    @Test fun fixedDaysAreCanonicalAndFirstDateActuallyMatchesTheRule() {
        val frequency = HabitFrequency.FixedDays(setOf(FRIDAY, MONDAY, WEDNESDAY))
        val plan = CalendarRecurrence.plan(frequency, monday.plusDays(1))
        assertEquals(monday.plusDays(2), plan.firstDate)
        assertEquals("FREQ=WEEKLY;BYDAY=MO,WE,FR;WKST=MO", plan.rule)
        assertEquals("FIXED_DAYS:1,3,5", plan.frequencyKey)
    }

    @Test fun singleFixedDayWrapsAcrossYear() {
        val plan = CalendarRecurrence.plan(HabitFrequency.FixedDays(setOf(MONDAY)), LocalDate.of(2026, 12, 31))
        assertEquals(LocalDate.of(2027, 1, 4), plan.firstDate)
        assertEquals("FREQ=WEEKLY;BYDAY=MO;WKST=MO", plan.rule)
    }

    @Test fun intervalKeepsOriginalPhaseInsteadOfRestartingAtRequestedDate() {
        val frequency = HabitFrequency.EveryNDays(3, monday)
        val plan = CalendarRecurrence.plan(frequency, monday.plusDays(4))
        assertEquals(monday.plusDays(6), plan.firstDate)
        assertEquals("FREQ=DAILY;INTERVAL=3", plan.rule)
    }

    @Test fun intervalReferenceCanBeAfterTheRequestedStartWithoutChangingPhase() {
        val plan = CalendarRecurrence.plan(HabitFrequency.EveryNDays(3, monday.plusDays(6)), monday.plusDays(1))
        assertEquals(monday.plusDays(3), plan.firstDate)
    }

    @Test fun intervalCrossesLeapDayByCalendarDate() {
        val plan = CalendarRecurrence.plan(
            HabitFrequency.EveryNDays(2, LocalDate.of(2028, 2, 28)), LocalDate.of(2028, 2, 29),
        )
        assertEquals(LocalDate.of(2028, 3, 1), plan.firstDate)
    }

    @Test fun quotaUsesOnlyExplicitlyChosenDaysWithoutChangingTheHabitTarget() {
        val frequency = HabitFrequency.WeeklyQuota(3)
        val plan = CalendarRecurrence.plan(frequency, monday, setOf(TUESDAY, THURSDAY, SATURDAY))
        assertEquals(monday.plusDays(1), plan.firstDate)
        assertEquals("FREQ=WEEKLY;BYDAY=TU,TH,SA;WKST=MO", plan.rule)
        assertEquals("2,4,6", plan.quotaDaysCode)
        assertEquals(3, frequency.timesPerWeek)
    }

    @Test(expected = IllegalArgumentException::class)
    fun quotaCannotExportWithoutChosenDays() {
        CalendarRecurrence.plan(HabitFrequency.WeeklyQuota(3), monday)
    }

    @Test(expected = IllegalArgumentException::class)
    fun quotaCannotExportWithTheWrongNumberOfDays() {
        CalendarRecurrence.plan(HabitFrequency.WeeklyQuota(2), monday, setOf(MONDAY, TUESDAY, WEDNESDAY))
    }

    @Test fun sevenDayQuotaIsStillExplicitWeeklyPlanning() {
        val plan = CalendarRecurrence.plan(HabitFrequency.WeeklyQuota(7), monday, java.time.DayOfWeek.values().toSet())
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR,SA,SU;WKST=MO", plan.rule)
        assertEquals("WEEKLY_QUOTA:7", plan.frequencyKey)
    }

    @Test fun frequencyKeyIsStableForSetsButDetectsIntervalReferenceAndTargetChanges() {
        assertEquals(
            CalendarRecurrence.frequencyKey(HabitFrequency.FixedDays(setOf(MONDAY, FRIDAY))),
            CalendarRecurrence.frequencyKey(HabitFrequency.FixedDays(setOf(FRIDAY, MONDAY))),
        )
        assertNotEquals(
            CalendarRecurrence.frequencyKey(HabitFrequency.EveryNDays(3, monday)),
            CalendarRecurrence.frequencyKey(HabitFrequency.EveryNDays(3, monday.plusDays(1))),
        )
        assertNotEquals(CalendarRecurrence.frequencyKey(HabitFrequency.WeeklyQuota(2)), CalendarRecurrence.frequencyKey(HabitFrequency.WeeklyQuota(3)))
    }

    @Test fun daySelectionRoundTripsInCalendarOrder() {
        val days = setOf(SUNDAY, MONDAY, WEDNESDAY)
        assertEquals("1,3,7", CalendarRecurrence.encodeDays(days))
        assertEquals(days, CalendarRecurrence.decodeDays("1,3,7"))
        assertEquals(emptySet<java.time.DayOfWeek>(), CalendarRecurrence.decodeDays("0,8,invalid"))
    }
}
