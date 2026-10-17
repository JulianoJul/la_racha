package com.laracha.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(val storedValue: String, val label: String) {
    SYSTEM("system", "Sistema"),
    LIGHT("light", "Claro"),
    DARK("dark", "Oscuro"),
}

private val Context.appPreferencesDataStore by preferencesDataStore(name = "app_preferences")
private val ThemeModeKey = stringPreferencesKey("theme_mode")

class AppPreferences(private val context: Context) {
    val themeMode: Flow<ThemeMode> = context.appPreferencesDataStore.data.map { preferences ->
        themeModeFromStoredValue(preferences[ThemeModeKey])
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.appPreferencesDataStore.edit { it[ThemeModeKey] = mode.storedValue }
    }
}

internal fun themeModeFromStoredValue(value: String?): ThemeMode =
    ThemeMode.entries.firstOrNull { it.storedValue == value } ?: ThemeMode.SYSTEM
