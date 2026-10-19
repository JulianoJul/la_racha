package com.laracha.app

import android.app.Application
import androidx.room.Room
import com.laracha.app.data.AppPreferences
import com.laracha.app.data.HabitDatabase
import com.laracha.app.data.HabitRepository
import com.laracha.app.data.CalendarPreferences
import com.laracha.app.calendar.AndroidCalendarGateway
import com.laracha.app.calendar.CalendarExportCoordinator
import com.laracha.app.calendar.CalendarWorkScheduler
import com.laracha.app.widgets.WidgetRefreshScheduler
import com.laracha.app.widgets.WidgetUpdater
import java.time.Clock
import java.time.ZoneId

class HabitApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WidgetRefreshScheduler.ensureScheduled(applicationContext)
    }

    val preferences: AppPreferences by lazy { AppPreferences(applicationContext) }
    val calendarPreferences: CalendarPreferences by lazy { CalendarPreferences(applicationContext) }

    val database: HabitDatabase by lazy {
        Room.databaseBuilder(applicationContext, HabitDatabase::class.java, "la_racha.db")
            .addMigrations(com.laracha.app.data.MIGRATION_1_2, com.laracha.app.data.MIGRATION_2_3, com.laracha.app.data.MIGRATION_3_4)
            .build()
    }

    val habitRepository: HabitRepository by lazy {
        HabitRepository(
            database.habitDao(),
            Clock.systemUTC(),
            { ZoneId.systemDefault() },
            afterMutation = { WidgetUpdater.updateAll(applicationContext) },
        )
    }

    val calendarCoordinator: CalendarExportCoordinator by lazy {
        CalendarExportCoordinator(
            database.habitDao(),
            database.calendarDao(),
            AndroidCalendarGateway(applicationContext),
            enqueueRetry = { CalendarWorkScheduler.enqueue(applicationContext) },
            clock = Clock.systemDefaultZone(),
        )
    }
}
