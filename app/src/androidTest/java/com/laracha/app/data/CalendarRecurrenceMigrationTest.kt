package com.laracha.app.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarRecurrenceMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), HabitDatabase::class.java)

    @Test
    fun v3PreservesHistoryBindingAndInFlightDailyPlanThenReopensWithRoom() = runBlocking {
        val name = "calendar-recurrence-migration-test"
        helper.createDatabase(name, 3).apply {
            execSQL("INSERT INTO habits (id, name, emoji, color, createdOn, sortOrder, frequencyType, frequencyDays) VALUES ('h', 'Leer', 'x', '#437A54', '2026-10-01', 0, 'FIXED_DAYS', '1,3,5')")
            execSQL("INSERT INTO completions VALUES ('h', '2026-09-30')")
            execSQL("INSERT INTO habit_calendar_schedules VALUES ('h', 1, '2026-10-17', '08:30', 45, 'America/New_York', 10, 7)")
            execSQL("INSERT INTO calendar_event_bindings VALUES ('h', 2, 'qa', 'com.google', 99, 'existing-token', 6, 'PUBLISHED_LOCAL', 10)")
            execSQL("INSERT INTO calendar_export_operations VALUES ('op', 'h', 7, 'UPSERT', 2, 99, 'existing-token', 'IN_FLIGHT', NULL, 1)")
            close()
        }
        helper.runMigrationsAndValidate(name, 4, true, MIGRATION_3_4).close()
        val database = Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, HabitDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
        try {
            val dao = database.calendarDao()
            val schedule = requireNotNull(dao.getSchedule("h"))
            assertEquals("FREQ=DAILY", schedule.recurrenceRule) // Old exports were daily even for non-daily habits.
            assertEquals("DAILY", schedule.frequencyKey)
            assertEquals("", schedule.quotaDays)
            assertEquals("2026-10-17", schedule.startDate)
            assertEquals("08:30", schedule.localTime)
            assertEquals("America/New_York", schedule.zoneId)
            assertEquals(45, schedule.durationMinutes)
            assertEquals(10, schedule.reminderMinutes)
            assertEquals(7L, schedule.revision)
            assertEquals("existing-token", dao.getBinding("h")?.ownershipToken)
            assertEquals(99L, dao.getBinding("h")?.eventId)
            assertEquals("IN_FLIGHT", dao.getOperation("op")?.state)
            assertEquals(listOf("2026-09-30"), database.habitDao().getCompletions("h").map { it.localDate })
            dao.putSchedule(schedule.copy(recurrenceRule = "FREQ=WEEKLY;BYDAY=MO,WE,FR;WKST=MO", frequencyKey = "WEEKLY_QUOTA:3", quotaDays = "1,3,5"))
            assertEquals("1,3,5", dao.getSchedule("h")?.quotaDays)
            assertEquals("FIXED_DAYS", database.habitDao().getHabit("h")?.frequencyType)
        } finally { database.close() }
    }

    @Test
    fun v2ToV4KeepsExistingHabitAndCompletion() {
        val name = "calendar-recurrence-v2-chain-test"
        helper.createDatabase(name, 2).apply {
            execSQL("INSERT INTO habits VALUES ('h', 'Leer', 'x', '#437A54', '2026-10-01', NULL, 0)")
            execSQL("INSERT INTO completions VALUES ('h', '2026-10-01')")
            close()
        }
        val migrated = helper.runMigrationsAndValidate(name, 4, true, MIGRATION_2_3, MIGRATION_3_4)
        migrated.query("SELECT habitId, localDate FROM completions").use {
            assertTrue(it.moveToFirst())
            assertEquals("h", it.getString(0))
            assertEquals("2026-10-01", it.getString(1))
        }
        migrated.close()
    }
}
