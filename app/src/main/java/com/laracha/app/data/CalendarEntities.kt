package com.laracha.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Desired agenda data. A row is created only after the user opts a habit in. */
@Entity(tableName = "habit_calendar_schedules")
data class HabitCalendarScheduleEntity(
    @PrimaryKey val habitId: String,
    val enabled: Boolean,
    val startDate: String,
    val localTime: String,
    val durationMinutes: Int,
    val zoneId: String,
    /** -1 means no reminder; otherwise provider alert minutes. */
    val reminderMinutes: Int,
    val revision: Long,
)

/** Provider ids are device-local. Ownership token is also embedded in event description. */
@Entity(tableName = "calendar_event_bindings")
data class CalendarEventBindingEntity(
    @PrimaryKey val habitId: String,
    val calendarId: Long,
    val accountName: String,
    val accountType: String,
    val eventId: Long?,
    val ownershipToken: String,
    val publishedRevision: Long,
    val state: String,
    val managedReminderMinutes: Int? = null,
)

/** No FK by design: delete/cleanup tombstones must outlive a deleted habit. */
@Entity(tableName = "calendar_export_operations")
data class CalendarExportOperationEntity(
    @PrimaryKey val operationId: String,
    val habitId: String,
    val revision: Long,
    val kind: String,
    val calendarId: Long?,
    val eventId: Long?,
    val ownershipToken: String,
    val state: String,
    val lastError: String?,
    val attempts: Int,
)
