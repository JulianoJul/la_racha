package com.laracha.app.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.content.ComponentName
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.laracha.app.HabitApplication
import com.laracha.app.data.HabitEntity
import com.laracha.app.ui.theme.HabitAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.CancellationException

class HabitListWidgetConfigurationActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val configurationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var saving by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider
        if (provider != ComponentName(this, HabitListWidgetReceiver::class.java)) {
            finish()
            return
        }
        setContent {
            HabitAppTheme((application as HabitApplication).preferences) {
                ConfigurationContent(
                    appWidgetId = appWidgetId,
                    onSave = ::saveConfiguration,
                    onCancel = { finish() },
                    isSaving = saving,
                )
            }
        }
    }

    private fun saveConfiguration(config: ListWidgetConfig) {
        if (saving) return
        saving = true
        configurationScope.launch {
            try {
                val active = (application as HabitApplication).habitRepository.observeHabits().first()
                    .filter { it.archivedOn == null }.map(HabitEntity::id).toSet()
                require(config.mode != ListWidgetMode.SELECTED ||
                    config.selectedHabitIds.size in 1..5 && config.selectedHabitIds.distinct().size == config.selectedHabitIds.size &&
                    config.selectedHabitIds.all(active::contains)) { "La selección ya no es válida" }
                WidgetConfigStore.saveListConfig(this@HabitListWidgetConfigurationActivity, appWidgetId, config)
                WidgetUpdater.updateAll(this@HabitListWidgetConfigurationActivity)
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                finish()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                saving = false
                Toast.makeText(this@HabitListWidgetConfigurationActivity, "No se pudo guardar. Inténtalo de nuevo.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        configurationScope.cancel()
        super.onDestroy()
    }
}

@Composable
private fun HabitListWidgetConfigurationActivity.ConfigurationContent(
    appWidgetId: Int,
    onSave: (ListWidgetConfig) -> Unit,
    onCancel: () -> Unit,
    isSaving: Boolean,
) {
    val repository = (application as HabitApplication).habitRepository
    var habits by remember { mutableStateOf<List<HabitEntity>>(emptyList()) }
    var mode by rememberSaveable(appWidgetId) { mutableStateOf(ListWidgetMode.ALL) }
    var initialized by rememberSaveable(appWidgetId) { mutableStateOf(false) }
    var loading by rememberSaveable(appWidgetId) { mutableStateOf(true) }
    var loadFailed by rememberSaveable(appWidgetId) { mutableStateOf(false) }
    val selectedIds = rememberSaveable(saver = listSaver(
        save = { it.toList() },
        restore = { mutableStateListOf<String>().apply { addAll(it) } },
    )) { mutableStateListOf<String>() }

    LaunchedEffect(appWidgetId) {
        try {
            repository.observeHabits().collect { all ->
                habits = all.filter { it.archivedOn == null }
                if (!initialized) {
                    val current = WidgetConfigStore.listConfig(this@ConfigurationContent, appWidgetId)
                    mode = current.mode
                    selectedIds.clear()
                    current.selectedHabitIds.filter { id -> habits.any { it.id == id } }.forEach(selectedIds::add)
                    initialized = true
                } else {
                    selectedIds.removeAll { id -> habits.none { it.id == id } }
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text("Configurar lista", style = MaterialTheme.typography.headlineSmall)
        Text("Elige todos los hábitos o selecciona de uno a cinco para esta instancia.")
        if (loading) Text("Cargando hábitos…")
        if (loadFailed) Text("No se pudo cargar la configuración. Cierra y vuelve a abrir esta pantalla.")
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          item(key = "mode_all") {
        Card(Modifier.fillMaxWidth().clickable { mode = ListWidgetMode.ALL }) {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = mode == ListWidgetMode.ALL, onClick = { mode = ListWidgetMode.ALL })
                Column {
                    Text("Todos")
                    Text("Incluye automáticamente los hábitos activos nuevos.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
          }
          item(key = "mode_selected") {
        Card(Modifier.fillMaxWidth().clickable { mode = ListWidgetMode.SELECTED }) {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = mode == ListWidgetMode.SELECTED, onClick = { mode = ListWidgetMode.SELECTED })
                Column {
                    Text("Elegidos (${selectedIds.size}/5)")
                    Text("Toca hábitos para añadirlos y usa ↑/↓ para ordenarlos.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
          }

        if (loading || loadFailed) {
          item { Text(if (loading) "Cargando…" else "No se muestran hábitos hasta recuperar la lectura.") }
        } else if (habits.isEmpty()) {
          item {
            Text("Aún no tienes hábitos. Puedes guardar el modo Todos y se mostrarán cuando crees alguno.")
          }
        } else if (mode == ListWidgetMode.SELECTED) {
                items(habits, key = HabitEntity::id) { habit ->
                    val selected = habit.id in selectedIds
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (selected) selectedIds.remove(habit.id)
                            else if (selectedIds.size < 5) selectedIds.add(habit.id)
                        }.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = selected,
                            enabled = selected || selectedIds.size < 5,
                            modifier = Modifier.semantics { contentDescription = "${habit.name}, ${if (selected) "elegido" else "no elegido"} para esta lista" },
                            onCheckedChange = { checked ->
                                if (checked && selectedIds.size < 5) selectedIds.add(habit.id)
                                else if (!checked) selectedIds.remove(habit.id)
                            },
                        )
                        Text("${habit.emoji}  ${habit.name}", modifier = Modifier.weight(1f))
                    }
                }
        }

        if (mode == ListWidgetMode.SELECTED) {
          item {
            Text("Orden de la lista", style = MaterialTheme.typography.titleSmall)
            selectedIds.forEachIndexed { index, id ->
                val habit = habits.firstOrNull { it.id == id } ?: return@forEachIndexed
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}. ${habit.emoji} ${habit.name}", modifier = Modifier.weight(1f))
                    TextButton(enabled = index > 0, onClick = {
                        val selected = selectedIds.removeAt(index)
                        selectedIds.add(index - 1, selected)
                    }, modifier = Modifier.semantics { contentDescription = "Subir ${habit.name} en el orden" }) { Text("↑") }
                    TextButton(enabled = index < selectedIds.lastIndex, onClick = {
                        val selected = selectedIds.removeAt(index)
                        selectedIds.add(index + 1, selected)
                    }, modifier = Modifier.semantics { contentDescription = "Bajar ${habit.name} en el orden" }) { Text("↓") }
                }
            }
          }
        }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = !isSaving && !loading && !loadFailed && (mode == ListWidgetMode.ALL || selectedIds.isNotEmpty()),
                onClick = { onSave(ListWidgetConfig(mode, selectedIds.toList())) },
            ) { Text(if (isSaving) "Guardando…" else "Guardar") }
            TextButton(onClick = onCancel, enabled = !isSaving) { Text("Cancelar") }
        }
      }
    }
}
