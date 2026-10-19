package com.laracha.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Existing exports really were daily, regardless of the habit's current frequency. Do not republish them. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE habit_calendar_schedules ADD COLUMN recurrenceRule TEXT NOT NULL DEFAULT 'FREQ=DAILY'")
        db.execSQL("ALTER TABLE habit_calendar_schedules ADD COLUMN frequencyKey TEXT NOT NULL DEFAULT 'DAILY'")
        db.execSQL("ALTER TABLE habit_calendar_schedules ADD COLUMN quotaDays TEXT NOT NULL DEFAULT ''")
    }
}
