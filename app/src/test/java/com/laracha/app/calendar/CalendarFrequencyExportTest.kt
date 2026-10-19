package com.laracha.app.calendar

import com.laracha.app.data.CalendarDao
import com.laracha.app.data.CalendarEventBindingEntity
import com.laracha.app.data.CalendarExportOperationEntity
import com.laracha.app.data.CompletionEntity
import com.laracha.app.data.HabitCalendarScheduleEntity
import com.laracha.app.data.HabitDao
import com.laracha.app.data.HabitEntity
import com.laracha.app.data.HabitFrequencyPeriodEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Tests durable intent/consent with fakes. These do not validate SQLite or the Android provider. */
class CalendarFrequencyExportTest {
    private val today = LocalDate.of(2026, 10, 19)
    private val baseline = HabitCalendarScheduleEntity("h", true, "2026-10-19", "08:30", 45, "America/New_York", 10, 7)
    private val owned = CalendarEventBindingEntity("h", 2, "qa", "com.google", 99, "existing-token", 7, "PUBLISHED_LOCAL", 10)

    @Test fun newFixedDayExportPersistsRuleBeforeGatewayCall() = runBlocking {
        val habits = FakeHabits(habit().copy(frequencyType = "FIXED_DAYS", frequencyDays = "3,5"))
        val calendar = FakeCalendar()
        val gateway = FakeGateway { schedule -> assertEquals(schedule, calendar.schedule) }
        assertEquals(PublicationOutcome.PUBLISHED_LOCAL, publish(CalendarExportCoordinator(habits, calendar, gateway)))
        assertEquals("2026-10-21", calendar.schedule?.startDate)
        assertEquals("FREQ=WEEKLY;BYDAY=WE,FR;WKST=MO", gateway.published.single().recurrenceRule)
        assertEquals("FIXED_DAYS:3,5", calendar.schedule?.frequencyKey)
    }

    @Test fun quotaWithoutExactSelectionDoesNotWriteIntentOrCallProvider() = runBlocking {
        val habits = FakeHabits(habit().copy(frequencyType = "WEEKLY_QUOTA", frequencyWeeklyTarget = 3))
        val calendar = FakeCalendar()
        val gateway = FakeGateway()
        val coordinator = CalendarExportCoordinator(habits, calendar, gateway)
        rejected { publish(coordinator) }
        rejected { publish(coordinator, setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)) }
        assertEquals(0, calendar.writes)
        assertTrue(gateway.published.isEmpty())
    }

    @Test fun quotaSelectionBelongsToCalendarAndDoesNotChangeHabitFrequency() = runBlocking {
        val original = habit().copy(frequencyType = "WEEKLY_QUOTA", frequencyWeeklyTarget = 2)
        val habits = FakeHabits(original)
        val calendar = FakeCalendar()
        val gateway = FakeGateway()
        publish(CalendarExportCoordinator(habits, calendar, gateway), setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY))
        assertEquals("2,4", calendar.schedule?.quotaDays)
        assertEquals("FREQ=WEEKLY;BYDAY=TU,TH;WKST=MO", calendar.schedule?.recurrenceRule)
        assertEquals(original, habits.habit)
    }

    @Test fun replacingPublishedDailyRuleRequiresConsentBeforeAnyWrite() = runBlocking {
        val habits = FakeHabits(habit().copy(frequencyType = "FIXED_DAYS", frequencyDays = "1,3,5"))
        val calendar = FakeCalendar(baseline, owned)
        val gateway = FakeGateway()
        rejected { publish(CalendarExportCoordinator(habits, calendar, gateway)) }
        assertEquals(baseline, calendar.schedule)
        assertEquals(0, calendar.writes)
        assertTrue(gateway.published.isEmpty())
    }

    @Test fun confirmedChangeUpdatesSameOwnedSeriesAndKeepsTimeAndReminder() = runBlocking {
        val habits = FakeHabits(habit().copy(frequencyType = "EVERY_N_DAYS", frequencyIntervalDays = 3, frequencyReferenceDate = today.minusDays(1).toString()))
        val calendar = FakeCalendar(baseline, owned)
        val gateway = FakeGateway()
        publish(CalendarExportCoordinator(habits, calendar, gateway), confirmed = true)
        val schedule = gateway.published.single()
        assertEquals("FREQ=DAILY;INTERVAL=3", schedule.recurrenceRule)
        assertEquals(today.plusDays(2).toString(), schedule.startDate)
        assertEquals("08:30", schedule.localTime)
        assertEquals(10, schedule.reminderMinutes)
        assertEquals("America/New_York", schedule.zoneId)
        assertEquals(99L, calendar.binding?.eventId)
        assertEquals("existing-token", calendar.binding?.ownershipToken)
        assertEquals(8L, calendar.binding?.publishedRevision)
    }

    @Test fun changingQuotaWeekdaysAlsoRequiresConsentEvenIfTargetIsUnchanged() = runBlocking {
        val habits = FakeHabits(habit().copy(frequencyType = "WEEKLY_QUOTA", frequencyWeeklyTarget = 2))
        val previous = baseline.copy(recurrenceRule = "FREQ=WEEKLY;BYDAY=MO,FR;WKST=MO", frequencyKey = "WEEKLY_QUOTA:2", quotaDays = "1,5")
        val calendar = FakeCalendar(previous, owned)
        val gateway = FakeGateway()
        rejected { publish(CalendarExportCoordinator(habits, calendar, gateway), setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)) }
        assertEquals(0, calendar.writes)
        assertEquals(previous, calendar.schedule)
    }

    @Test fun renamingAfterDeclinedFrequencyChangePreservesAuthorizedDailyRule() = runBlocking {
        val habits = FakeHabits(habit().copy(name = "Nombre nuevo", frequencyType = "FIXED_DAYS", frequencyDays = "1,3,5"))
        val calendar = FakeCalendar(baseline, owned)
        val gateway = FakeGateway()
        CalendarExportCoordinator(habits, calendar, gateway).refreshPublishedName("h")
        assertEquals(listOf("Nombre nuevo"), gateway.titles)
        assertEquals(baseline.copy(revision = 8), gateway.published.single())
    }

    @Test fun inFlightRecoveryReappliesFrozenQuotaRuleNotNewUnconfirmedHabitTarget() = runBlocking {
        val habits = FakeHabits(habit().copy(frequencyType = "WEEKLY_QUOTA", frequencyWeeklyTarget = 2))
        val snapshot = baseline.copy(recurrenceRule = "FREQ=WEEKLY;BYDAY=MO,WE,FR;WKST=MO", frequencyKey = "WEEKLY_QUOTA:3", quotaDays = "1,3,5")
        val calendar = FakeCalendar(snapshot, owned)
        calendar.operations["op"] = CalendarExportOperationEntity("op", "h", 7, "UPSERT", 2, 99, "existing-token", "IN_FLIGHT", null, 0)
        val gateway = FakeGateway()
        CalendarExportCoordinator(habits, calendar, gateway).retryPending()
        assertEquals(snapshot, gateway.published.single())
        assertEquals(1, gateway.reconciliations)
        assertTrue(calendar.operations.isEmpty())
    }

    @Test fun pendingRecoveryAlsoPreservesStoredIntervalReferencePhase() = runBlocking {
        val habits = FakeHabits(habit())
        val snapshot = baseline.copy(startDate = "2026-10-21", recurrenceRule = "FREQ=DAILY;INTERVAL=3", frequencyKey = "EVERY_N_DAYS:3:2026-10-18")
        val calendar = FakeCalendar(snapshot, owned)
        calendar.operations["op"] = CalendarExportOperationEntity("op", "h", 7, "UPSERT", 2, 99, "existing-token", "PENDING", null, 0)
        val gateway = FakeGateway()
        CalendarExportCoordinator(habits, calendar, gateway).retryPending()
        assertEquals(snapshot, gateway.published.single())
    }

    private suspend fun publish(coordinator: CalendarExportCoordinator, days: Set<DayOfWeek> = emptySet(), confirmed: Boolean = false) =
        coordinator.requestPublication("h", 2, "qa", today, LocalTime.of(8, 30), 45, ZoneId.of("America/New_York"), 10, days, confirmed)

    private suspend fun rejected(block: suspend () -> Unit) {
        try { block(); fail("Expected a validation/consent rejection") }
        catch (_: IllegalArgumentException) { /* Expected before any write. */ }
    }

    private fun habit() = HabitEntity("h", "Leer", "x", "#437A54", "2026-10-01")

    private class FakeGateway(val beforePublish: (HabitCalendarScheduleEntity) -> Unit = {}) : CalendarGateway {
        val published = mutableListOf<HabitCalendarScheduleEntity>()
        val titles = mutableListOf<String>()
        var reconciliations = 0
        override suspend fun listWritableGoogleCalendars() = emptyList<WritableCalendar>()
        override suspend fun publish(calendarId: Long, habitName: String, schedule: HabitCalendarScheduleEntity, token: String, binding: CalendarEventBindingEntity?): GatewayResult {
            beforePublish(schedule)
            published += schedule
            titles += habitName
            return GatewayResult.Published(ManagedSeries(binding?.eventId ?: 99, token))
        }
        override suspend fun reconcile(calendarId: Long, token: String, binding: CalendarEventBindingEntity?): GatewayResult {
            reconciliations++
            return GatewayResult.Published(ManagedSeries(99, token))
        }
        override suspend fun delete(calendarId: Long, eventId: Long?, token: String, binding: CalendarEventBindingEntity?) = error("Unexpected delete")
    }

    private class FakeCalendar(var schedule: HabitCalendarScheduleEntity? = null, var binding: CalendarEventBindingEntity? = null) : CalendarDao() {
        val operations = mutableMapOf<String, CalendarExportOperationEntity>()
        var writes = 0
        override fun observeSchedules() = flowOf(listOfNotNull(schedule))
        override fun observeBindings() = flowOf(listOfNotNull(binding))
        override fun observeBlockedOperations() = flowOf(operations.values.filter { it.state == "BLOCKED" })
        override suspend fun getSchedule(habitId: String) = schedule
        override suspend fun getBinding(habitId: String) = binding
        override suspend fun getOperation(id: String) = operations[id]
        override suspend fun pendingOperations() = operations.values.filter { it.state in setOf("PENDING", "RETRY", "IN_FLIGHT") }
        override suspend fun putSchedule(schedule: HabitCalendarScheduleEntity) { writes++; this.schedule = schedule }
        override suspend fun putBinding(binding: CalendarEventBindingEntity) { writes++; this.binding = binding }
        override suspend fun putOperation(operation: CalendarExportOperationEntity) { writes++; operations[operation.operationId] = operation }
        override suspend fun deleteOperation(id: String) { writes++; operations.remove(id) }
        override suspend fun removeSupersededOperations(habitId: String, currentId: String) {
            operations.entries.removeAll { it.value.habitId == habitId && it.key != currentId && it.value.state != "IN_FLIGHT" }
        }
        override suspend fun markInFlight(id: String) { operations[id] = requireNotNull(operations[id]).copy(state = "IN_FLIGHT") }
        override suspend fun markBlocked(id: String, error: String) { operations[id] = requireNotNull(operations[id]).copy(state = "BLOCKED", lastError = error) }
        override suspend fun markRetry(id: String, error: String) { operations[id] = requireNotNull(operations[id]).copy(state = "RETRY", lastError = error) }
        override suspend fun unblockRecoverableFailures() = Unit
    }

    private class FakeHabits(var habit: HabitEntity) : HabitDao() {
        override suspend fun getHabit(habitId: String) = habit.takeIf { it.id == habitId }
        override fun observeActiveHabits() = flowOf(listOf(habit))
        override fun observeAllHabits() = flowOf(listOf(habit))
        override fun observeHabit(habitId: String) = flowOf(habit)
        override fun observeCompletions() = flowOf(emptyList<CompletionEntity>())
        override fun observeCompletionsForHabit(habitId: String) = flowOf(emptyList<CompletionEntity>())
        override suspend fun getCompletions(habitId: String) = emptyList<CompletionEntity>()
        override fun observeFrequencyPeriods(habitId: String) = flowOf(emptyList<HabitFrequencyPeriodEntity>())
        override fun observeAllFrequencyPeriods() = flowOf(emptyList<HabitFrequencyPeriodEntity>())
        override suspend fun getFrequencyPeriods(habitId: String) = emptyList<HabitFrequencyPeriodEntity>()
        override suspend fun insertHabit(habit: HabitEntity) = error("Unexpected habit write")
        override suspend fun updateHabit(habit: HabitEntity) = error("Unexpected habit write")
        override suspend fun putCompletion(completion: CompletionEntity) = error("Calendar must not write history")
        override suspend fun deleteCompletion(habitId: String, localDate: String) = error("Calendar must not write history")
        override suspend fun insertFrequencyPeriod(period: HabitFrequencyPeriodEntity) = error("Calendar must not change habit frequency")
        override suspend fun deleteFrequencyPeriodsFrom(habitId: String, effectiveFrom: String) = error("Calendar must not change habit frequency")
        override suspend fun deleteHabit(habitId: String) = error("Unexpected habit write")
        override suspend fun nextSortOrder() = 0
    }
}
