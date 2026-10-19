package com.laracha.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        HabitEntity::class,
        CompletionEntity::class,
        HabitFrequencyPeriodEntity::class,
        HabitCalendarScheduleEntity::class,
        CalendarEventBindingEntity::class,
        CalendarExportOperationEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(LocalDateConverters::class)
abstract class HabitDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun calendarDao(): CalendarDao
}
