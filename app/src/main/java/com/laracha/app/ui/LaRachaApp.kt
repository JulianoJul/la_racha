package com.laracha.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import com.laracha.app.data.AppPreferences
import com.laracha.app.data.HabitRepository
import com.laracha.app.data.ThemeMode
import com.laracha.app.HabitApplication
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun LaRachaApp(
    repository: HabitRepository,
    preferences: AppPreferences,
    today: LocalDate,
    onDayChanged: () -> Unit,
    openedHabitId: String? = null,
    onOpenedHabitIdHandled: () -> Unit = {},
) {
    val themeMode by preferences.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val scope = rememberCoroutineScope()
    val app = LocalContext.current.applicationContext as HabitApplication
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showCalendarSettings by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(openedHabitId) {
        if (openedHabitId != null) {
            showCalendarSettings = false
            showSettings = false
        }
    }

    if (showCalendarSettings) {
        BackHandler { showCalendarSettings = false }
        CalendarIntegrationScreen(
            habitDao = app.database.habitDao(),
            calendarDao = app.database.calendarDao(),
            preferences = app.calendarPreferences,
            coordinator = app.calendarCoordinator,
            onBack = { showCalendarSettings = false },
        )
    } else if (showSettings) {
        BackHandler { showSettings = false }
        SettingsScreen(
            themeMode = themeMode,
            onThemeModeChange = { mode -> scope.launch { preferences.setThemeMode(mode) } },
            onBack = { showSettings = false },
        )
    } else {
        TodayScreen(
            repository = repository,
            today = today,
            onOpenSettings = { showSettings = true },
            onOpenCalendar = { showCalendarSettings = true },
            onDayChanged = onDayChanged,
            openedHabitId = openedHabitId,
            onOpenedHabitIdHandled = onOpenedHabitIdHandled,
        )
    }
}
