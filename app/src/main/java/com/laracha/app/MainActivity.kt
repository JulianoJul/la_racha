package com.laracha.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.laracha.app.widgets.WidgetUpdater
import com.laracha.app.ui.LaRachaApp
import com.laracha.app.ui.theme.HabitAppTheme
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job

class MainActivity : ComponentActivity() {
    private var today by mutableStateOf(LocalDate.now())
    private var openedHabitId by mutableStateOf<String?>(null)
    private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var dayCheckJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openedHabitId = intent?.getStringExtra(EXTRA_HABIT_ID)
        today = (application as HabitApplication).habitRepository.today()
        setContent {
            val app = application as HabitApplication
            HabitAppTheme(app.preferences) {
                LaRachaApp(
                    repository = (application as HabitApplication).habitRepository,
                    preferences = app.preferences,
                    today = today,
                    onDayChanged = { today = app.habitRepository.today() },
                    openedHabitId = openedHabitId,
                    onOpenedHabitIdHandled = {
                        openedHabitId = null
                        intent?.removeExtra(EXTRA_HABIT_ID)
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openedHabitId = intent.getStringExtra(EXTRA_HABIT_ID)
    }

    override fun onResume() {
        super.onResume()
        val repository = (application as HabitApplication).habitRepository
        today = repository.today()
        updateScope.launch { WidgetUpdater.updateAll(applicationContext) }
        dayCheckJob?.cancel()
        dayCheckJob = updateScope.launch {
            while (isActive) {
                delay(15_000)
                val current = repository.today()
                if (today != current) today = current
            }
        }
    }

    override fun onPause() {
        dayCheckJob?.cancel()
        dayCheckJob = null
        super.onPause()
    }

    override fun onDestroy() {
        updateScope.cancel()
        super.onDestroy()
    }
}

private const val EXTRA_HABIT_ID = "habit_id"
