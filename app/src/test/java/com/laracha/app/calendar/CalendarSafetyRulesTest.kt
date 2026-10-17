package com.laracha.app.calendar

import com.laracha.app.data.CalendarEventBindingEntity
import com.laracha.app.data.HabitCalendarScheduleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertNull
import org.junit.Test
import kotlinx.coroutines.runBlocking

class CalendarSafetyRulesTest {
    private val alert = 1

    @Test fun republishingSameManagedReminderDoesNotDeleteIt() {
        val change = reminderChange(listOf(ReminderRow(7, 10, alert)), 10, 10, alert)
        assertEquals(emptyList<Long>(), change.deleteIds)
        assertNull(change.insertMinutes)
    }

    @Test fun changingReminderReplacesOnlyManagedReminderAndPreservesExternal() {
        val change = reminderChange(
            listOf(ReminderRow(7, 10, alert), ReminderRow(8, 5, alert)), 10, 30, alert,
        )
        assertEquals(listOf(7L), change.deleteIds)
        assertEquals(30, change.insertMinutes)
    }

    @Test fun coincidentExternalReminderIsNotDuplicatedOrDeleted() {
        val change = reminderChange(
            listOf(ReminderRow(7, 10, alert), ReminderRow(8, 30, alert)), 10, 30, alert,
        )
        assertEquals(listOf(7L), change.deleteIds)
        assertNull(change.insertMinutes)
    }

    @Test fun durationAcceptsBoundsAndRejectsOutside() {
        requireValidDuration(1)
        requireValidDuration(1440)
        listOf(0, 1441).forEach { minutes ->
            try {
                requireValidDuration(minutes)
                fail("Expected invalid duration to be rejected: $minutes")
            } catch (_: IllegalArgumentException) {
                // Expected.
            }
        }
    }

    @Test fun recoveryReappliesDesiredScheduleWhenOldOwnedEventExists() = runBlocking {
        val fake = FakeCalendarGateway()
        val schedule = HabitCalendarScheduleEntity("h", true, "2026-10-17", "08:00", 45, "UTC", 10, 4)
        val binding = CalendarEventBindingEntity("h", 2, "account", "com.google", 99, "token", 3, "PUBLISHED_LOCAL", 30)
        val result = recoverPublication(fake, 2, "Hábito", schedule, "token", binding)
        assertEquals(listOf("reconcile", "publish"), fake.calls)
        assertEquals(schedule, fake.publishedSchedule)
        assertEquals(GatewayResult.Published(ManagedSeries(99, "token")), result)
    }

    private class FakeCalendarGateway : CalendarGateway {
        val calls = mutableListOf<String>()
        var publishedSchedule: HabitCalendarScheduleEntity? = null
        override suspend fun listWritableGoogleCalendars() = emptyList<WritableCalendar>()
        override suspend fun reconcile(calendarId: Long, token: String, binding: CalendarEventBindingEntity?): GatewayResult {
            calls += "reconcile"
            return GatewayResult.Published(ManagedSeries(99, token))
        }
        override suspend fun publish(calendarId: Long, habitName: String, schedule: HabitCalendarScheduleEntity, token: String, binding: CalendarEventBindingEntity?): GatewayResult {
            calls += "publish"
            publishedSchedule = schedule
            return GatewayResult.Published(ManagedSeries(99, token))
        }
        override suspend fun delete(calendarId: Long, eventId: Long?, token: String, binding: CalendarEventBindingEntity?) = GatewayResult.Missing
    }
}
