package com.laracha.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HabitFrequencyMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        HabitDatabase::class.java,
    )

    @Test
    fun migrationFromV2KeepsHabitAndCompletionAndCreatesDailyBaseline() {
        val name = "frequency-migration-test"
        val old = helper.createDatabase(name, 2)
        old.execSQL(
            "INSERT INTO habits (id, name, emoji, color, createdOn, archivedOn, sortOrder) VALUES (?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>("habit", "Leer", "📖", "#437A54", "2026-01-01", null, 0),
        )
        old.execSQL("INSERT INTO completions (habitId, localDate) VALUES (?, ?)", arrayOf("habit", "2025-12-30"))
        old.close()

        val migrated = helper.runMigrationsAndValidate(name, 3, true, MIGRATION_2_3)
        migrated.query("SELECT frequencyType, createdOn FROM habits WHERE id = 'habit'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("DAILY", cursor.getString(0))
            assertEquals("2026-01-01", cursor.getString(1))
        }
        migrated.query("SELECT localDate FROM completions WHERE habitId = 'habit'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("2025-12-30", cursor.getString(0))
        }
        migrated.query("SELECT effectiveFrom, frequencyType FROM habit_frequency_periods WHERE habitId = 'habit'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("2026-01-01", cursor.getString(0))
            assertEquals("DAILY", cursor.getString(1))
            assertTrue(!cursor.moveToNext())
        }
        migrated.close()
    }
}
