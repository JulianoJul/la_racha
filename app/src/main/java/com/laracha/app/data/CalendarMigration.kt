package com.laracha.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds opt-in scheduling and durable provider-operation records; legacy habits remain untouched. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS `habit_calendar_schedules` (
            `habitId` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `startDate` TEXT NOT NULL,
            `localTime` TEXT NOT NULL, `durationMinutes` INTEGER NOT NULL, `zoneId` TEXT NOT NULL,
            `reminderMinutes` INTEGER NOT NULL, `revision` INTEGER NOT NULL, PRIMARY KEY(`habitId`))""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `calendar_event_bindings` (
            `habitId` TEXT NOT NULL, `calendarId` INTEGER NOT NULL, `accountName` TEXT NOT NULL,
            `accountType` TEXT NOT NULL, `eventId` INTEGER, `ownershipToken` TEXT NOT NULL,
            `publishedRevision` INTEGER NOT NULL, `state` TEXT NOT NULL, PRIMARY KEY(`habitId`))""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `calendar_export_operations` (
            `operationId` TEXT NOT NULL, `habitId` TEXT NOT NULL, `revision` INTEGER NOT NULL,
            `kind` TEXT NOT NULL, `calendarId` INTEGER, `eventId` INTEGER,
            `ownershipToken` TEXT NOT NULL, `state` TEXT NOT NULL, `lastError` TEXT,
            `attempts` INTEGER NOT NULL, PRIMARY KEY(`operationId`))""")
        db.execSQL("ALTER TABLE `calendar_event_bindings` ADD COLUMN `managedReminderMinutes` INTEGER")
    }
}
