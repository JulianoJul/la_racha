package com.laracha.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.calendarDataStore by preferencesDataStore(name = "calendar_preferences")
private val DefaultCalendarId = longPreferencesKey("default_google_calendar_id")
private val IntegrationEnabled = booleanPreferencesKey("calendar_integration_enabled")

/** Stores only the selected future-export destination; existing bindings keep their calendar. */
class CalendarPreferences(private val context: Context) {
    val defaultCalendarId: Flow<Long?> = context.calendarDataStore.data.map { it[DefaultCalendarId] }
    val integrationEnabled: Flow<Boolean> = context.calendarDataStore.data.map { it[IntegrationEnabled] ?: false }
    suspend fun setIntegrationEnabled(enabled: Boolean) {
        context.calendarDataStore.edit { it[IntegrationEnabled] = enabled }
    }
    suspend fun setDefaultCalendarId(id: Long?) {
        context.calendarDataStore.edit { prefs -> if (id == null) prefs.remove(DefaultCalendarId) else prefs[DefaultCalendarId] = id }
    }
}
