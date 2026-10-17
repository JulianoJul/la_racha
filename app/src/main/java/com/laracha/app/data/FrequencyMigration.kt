package com.laracha.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds frequency metadata and a daily baseline for every existing v2 habit without altering history. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE habits ADD COLUMN frequencyType TEXT NOT NULL DEFAULT 'DAILY'")
        db.execSQL("ALTER TABLE habits ADD COLUMN frequencyDays TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE habits ADD COLUMN frequencyIntervalDays INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE habits ADD COLUMN frequencyReferenceDate TEXT")
        db.execSQL("ALTER TABLE habits ADD COLUMN frequencyWeeklyTarget INTEGER NOT NULL DEFAULT 1")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `habit_frequency_periods` (
                `habitId` TEXT NOT NULL,
                `effectiveFrom` TEXT NOT NULL,
                `frequencyType` TEXT NOT NULL,
                `frequencyDays` TEXT NOT NULL,
                `frequencyIntervalDays` INTEGER NOT NULL,
                `frequencyReferenceDate` TEXT,
                `frequencyWeeklyTarget` INTEGER NOT NULL,
                PRIMARY KEY(`habitId`, `effectiveFrom`),
                FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )""".trimIndent(),
        )
        db.execSQL(
            """INSERT INTO `habit_frequency_periods`
                (`habitId`, `effectiveFrom`, `frequencyType`, `frequencyDays`, `frequencyIntervalDays`, `frequencyReferenceDate`, `frequencyWeeklyTarget`)
                SELECT `id`, `createdOn`, 'DAILY', '', 1, NULL, 1 FROM `habits`""".trimIndent(),
        )
    }
}
