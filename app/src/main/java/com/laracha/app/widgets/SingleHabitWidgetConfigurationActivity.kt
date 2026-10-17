package com.laracha.app.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import com.laracha.app.HabitApplication
import com.laracha.app.data.HabitEntity
import com.laracha.app.ui.theme.HabitAppTheme
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import android.widget.Toast
import android.content.ComponentName
import kotlinx.coroutines.CancellationException

class SingleHabitWidgetConfigurationActivity : ComponentActivity() {
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private val configurationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var configuring by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(
            RESULT_CANCELED,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider
        if (provider != ComponentName(this, SingleHabitWidgetReceiver::class.java)) {
            finish()
            return
        }

        setContent {
            HabitAppTheme((application as HabitApplication).preferences) {
                ConfigurationContent(
                    appWidgetId = appWidgetId,
                    onConfigured = { habitId -> configure(habitId) },
                    onCancel = { finish() },
                    isSaving = configuring,
                )
            }
        }
    }

    private fun configure(habitId: String) {
        if (configuring) return
        configuring = true
        configurationScope.launch {
            try {
                val activeIds = (application as HabitApplication).habitRepository.observeHabits().first()
                    .filter { it.archivedOn == null }.map(HabitEntity::id)
                require(habitId in activeIds) { "El hábito ya no está disponible" }
                WidgetConfigStore.saveSingleHabit(this@SingleHabitWidgetConfigurationActivity, appWidgetId, habitId)
                SingleHabitWidget().updateAll(this@SingleHabitWidgetConfigurationActivity)
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                finish()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                configuring = false
                Toast.makeText(this@SingleHabitWidgetConfigurationActivity, "No se pudo guardar. Inténtalo de nuevo.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        configurationScope.cancel()
        super.onDestroy()
    }
}

@Composable
private fun SingleHabitWidgetConfigurationActivity.ConfigurationContent(
    appWidgetId: Int,
    onConfigured: (String) -> Unit,
    onCancel: () -> Unit,
    isSaving: Boolean,
) {
    val repository = (application as HabitApplication).habitRepository
    var habits by remember { mutableStateOf<List<HabitEntity>>(emptyList()) }
    var selectedId by rememberSaveable(appWidgetId) { mutableStateOf<String?>(null) }
    var initialized by rememberSaveable(appWidgetId) { mutableStateOf(false) }
    var loading by rememberSaveable(appWidgetId) { mutableStateOf(true) }
    var loadFailed by rememberSaveable(appWidgetId) { mutableStateOf(false) }

    LaunchedEffect(appWidgetId) {
        try {
            repository.observeHabits().collect { all ->
                habits = all.filter { it.archivedOn == null }
                if (!initialized) {
                    selectedId = WidgetConfigStore.singleHabitId(this@ConfigurationContent, appWidgetId)
                        ?.takeIf { selected -> habits.any { it.id == selected } }
                    initialized = true
                } else if (selectedId != null && habits.none { it.id == selectedId }) {
                    selectedId = null
                }
                loading = false
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            loadFailed = true
            loading = false
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
      Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Text("Configurar widget", style = MaterialTheme.typography.headlineSmall)
        Text("Elige el hábito que aparecerá en esta instancia.")
        if (loading) {
            Text("Cargando hábitos…")
        } else if (loadFailed) {
            Text("No se pudieron cargar los hábitos. Cierra y vuelve a abrir esta configuración.")
        } else if (habits.isEmpty()) {
            Text("Aún no tienes hábitos. Crea uno en la app y vuelve a añadir el widget.")
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(habits, key = HabitEntity::id) { habit ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { selectedId = habit.id }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selectedId == habit.id, onClick = { selectedId = habit.id })
                            Text("${habit.emoji}  ${habit.name}")
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                enabled = selectedId != null && !isSaving && !loading && !loadFailed,
                onClick = { selectedId?.let(onConfigured) },
            ) { Text(if (isSaving) "Guardando…" else "Guardar selección") }
            androidx.compose.material3.TextButton(onClick = onCancel, enabled = !isSaving) { Text("Cancelar") }
        }
        if (habits.isEmpty()) {
            androidx.compose.material3.TextButton(onClick = onCancel) { Text("Cerrar") }
        }
      }
    }
}
