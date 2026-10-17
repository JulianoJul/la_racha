package com.laracha.app.widgets

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.widgetPreferences by preferencesDataStore(name = "widget_config")

enum class ListWidgetMode(val persistedValue: String) {
    ALL("all"),
    SELECTED("selected"),
}

data class ListWidgetConfig(
    val mode: ListWidgetMode = ListWidgetMode.ALL,
    val selectedHabitIds: List<String> = emptyList(),
)

object WidgetConfigStore {
    private fun singleHabitKey(appWidgetId: Int) = stringPreferencesKey(singleHabitPreferenceKeyName(appWidgetId))
    private fun listModeKey(appWidgetId: Int) = stringPreferencesKey(listModePreferenceKeyName(appWidgetId))
    private fun selectedIdsKey(appWidgetId: Int) = stringPreferencesKey(selectedIdsPreferenceKeyName(appWidgetId))

    suspend fun singleHabitId(context: Context, appWidgetId: Int): String? =
        context.widgetPreferences.data.first()[singleHabitKey(appWidgetId)]

    fun observeSingleHabitId(context: Context, appWidgetId: Int): Flow<String?> =
        context.widgetPreferences.data.map { it[singleHabitKey(appWidgetId)] }

    suspend fun saveSingleHabit(context: Context, appWidgetId: Int, habitId: String) {
        context.widgetPreferences.edit { it[singleHabitKey(appWidgetId)] = habitId }
    }

    suspend fun deleteSingleWidget(context: Context, appWidgetId: Int) {
        context.widgetPreferences.edit { it.remove(singleHabitKey(appWidgetId)) }
    }

    suspend fun listConfig(context: Context, appWidgetId: Int): ListWidgetConfig {
        val preferences = context.widgetPreferences.data.first()
        val mode = when (preferences[listModeKey(appWidgetId)]) {
            ListWidgetMode.SELECTED.persistedValue -> ListWidgetMode.SELECTED
            else -> ListWidgetMode.ALL
        }
        val ids = decodeHabitIds(preferences[selectedIdsKey(appWidgetId)].orEmpty())
        return ListWidgetConfig(mode, ids)
    }

    fun observeListConfig(context: Context, appWidgetId: Int): Flow<ListWidgetConfig> =
        context.widgetPreferences.data.map { preferences ->
            val mode = when (preferences[listModeKey(appWidgetId)]) {
                ListWidgetMode.SELECTED.persistedValue -> ListWidgetMode.SELECTED
                else -> ListWidgetMode.ALL
            }
            val ids = decodeHabitIds(preferences[selectedIdsKey(appWidgetId)].orEmpty())
            ListWidgetConfig(mode, ids)
        }

    suspend fun saveListConfig(context: Context, appWidgetId: Int, config: ListWidgetConfig) {
        val distinctIds = config.selectedHabitIds.distinct()
        require(config.mode != ListWidgetMode.SELECTED || distinctIds.size in 1..5) {
            "El modo seleccionado requiere entre uno y cinco hábitos"
        }
        context.widgetPreferences.edit { preferences ->
            preferences[listModeKey(appWidgetId)] = config.mode.persistedValue
            preferences[selectedIdsKey(appWidgetId)] = encodeHabitIds(distinctIds)
        }
    }

    suspend fun deleteListWidget(context: Context, appWidgetId: Int) {
        context.widgetPreferences.edit { preferences ->
            preferences.remove(listModeKey(appWidgetId))
            preferences.remove(selectedIdsKey(appWidgetId))
        }
    }
}

internal fun singleHabitPreferenceKeyName(appWidgetId: Int): String = "single_habit_$appWidgetId"
internal fun listModePreferenceKeyName(appWidgetId: Int): String = "list_mode_$appWidgetId"
internal fun selectedIdsPreferenceKeyName(appWidgetId: Int): String = "list_selected_ids_$appWidgetId"
internal fun encodeHabitIds(ids: List<String>): String = ids.joinToString(",")
internal fun decodeHabitIds(serialized: String): List<String> =
    serialized.split(',').filter(String::isNotBlank).distinct().take(5)
