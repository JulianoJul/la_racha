package com.laracha.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.laracha.app.calendar.AndroidCalendarGateway
import com.laracha.app.calendar.CalendarExportCoordinator
import com.laracha.app.calendar.PublicationOutcome
import com.laracha.app.calendar.requireValidDuration
import com.laracha.app.calendar.WritableCalendar
import com.laracha.app.data.CalendarDao
import com.laracha.app.data.CalendarPreferences
import com.laracha.app.data.HabitDao
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.Clock

/**
 * Standalone Calendar settings destination. Embed in app navigation as:
 * `CalendarIntegrationScreen(habitDao, calendarDao, calendarPreferences, coordinator, onBack)`.
 * The owning screen remains responsible for app-bar/navigation chrome and theme wrapper.
 */
@Composable
fun CalendarIntegrationScreen(
    habitDao: HabitDao,
    calendarDao: CalendarDao,
    preferences: CalendarPreferences,
    coordinator: CalendarExportCoordinator,
    onBack: () -> Unit,
    clock: Clock = Clock.systemDefaultZone(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled by preferences.integrationEnabled.collectAsState(false)
    val defaultCalendar by preferences.defaultCalendarId.collectAsState(null)
    val habits by habitDao.observeAllHabits().collectAsState(emptyList())
    val schedules by calendarDao.observeSchedules().collectAsState(emptyList())
    val bindings by calendarDao.observeBindings().collectAsState(emptyList())
    val blockedOperations by calendarDao.observeBlockedOperations().collectAsState(emptyList())
    var calendars by remember { mutableStateOf<List<WritableCalendar>>(emptyList()) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var showDisableDialog by remember { mutableStateOf(false) }
    var deleteOrphanHabitId by remember { mutableStateOf<String?>(null) }
    val gateway = remember(context) { AndroidCalendarGateway(context) }

    fun hasPermissions() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
    suspend fun refreshCalendars() {
        if (!hasPermissions()) {
            calendars = emptyList()
            feedback = "Sin permisos de lectura y escritura. Vuelve a activar la integración para concederlos."
            return
        }
        loading = true
        try { calendars = gateway.listWritableGoogleCalendars(); feedback = if (calendars.isEmpty()) "No hay calendarios Google sincronizados y escribibles disponibles. Configúralos en Android y vuelve a comprobar." else null }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { feedback = e.message ?: "No se pudieron leer los calendarios." }
        finally { loading = false }
    }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.READ_CALENDAR] == true && grants[Manifest.permission.WRITE_CALENDAR] == true) {
            scope.launch { preferences.setIntegrationEnabled(true); refreshCalendars() }
        } else {
            scope.launch { preferences.setIntegrationEnabled(false) }
            feedback = "Permiso denegado. Los hábitos y widgets siguen funcionando sin Calendar."
        }
    }
    LaunchedEffect(enabled) {
        if (enabled && hasPermissions()) {
            refreshCalendars()
            coordinator.retryPending()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) { safePadding ->
      Column(
        Modifier.fillMaxSize().padding(safePadding).imePadding()
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Google Calendar", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onBack) { Text("Volver") }
        }
        Text("Exporta únicamente los horarios que elijas. Los completados, rachas y puntuación no se exportan; los cambios hechos en Calendar no se importan. La sincronización con Google depende de Android y puede tardar.")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Activar integración", style = MaterialTheme.typography.titleMedium)
                Text("Se solicitará acceso al calendario solo al activar esta función.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = enabled, onCheckedChange = { checked ->
                if (checked) permissionRequest.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                else if (bindings.any { it.state != "DETACHED" && it.state != "DELETED" }) showDisableDialog = true
                else scope.launch { preferences.setIntegrationEnabled(false) }
            })
        }
        if (enabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !loading, onClick = { scope.launch { refreshCalendars() } }) { Text(if (loading) "Comprobando…" else "Volver a comprobar") }
                OutlinedButton(onClick = { scope.launch { coordinator.retryPending(); feedback = "Se procesaron las operaciones pendientes disponibles." } }) { Text("Reintentar pendientes") }
            }
            if (calendars.isNotEmpty()) {
                Text("Destino para nuevas exportaciones", style = MaterialTheme.typography.titleMedium)
                calendars.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        RadioButton(selected = defaultCalendar == item.id, onClick = { scope.launch { preferences.setDefaultCalendarId(item.id) } })
                        Text("●", color = Color(item.color))
                        Column {
                            Text(item.name)
                            Text("${item.accountName} · ${item.accountType}" + if (item.supportsAlertReminders) " · recordatorios disponibles" else " · sin alertas compatibles", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        feedback?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
        if (enabled) {
            Text("Planificación por hábito", style = MaterialTheme.typography.titleLarge)
            if (habits.isEmpty()) Text("Crea un hábito antes de planificarlo.")
            habits.forEach { habit -> key(habit.id) {
                val existing = schedules.firstOrNull { it.habitId == habit.id }
                val binding = bindings.firstOrNull { it.habitId == habit.id }
                val blocked = blockedOperations.lastOrNull { it.habitId == habit.id }
                CalendarScheduleCard(
                    habitId = habit.id,
                    habitName = "${habit.emoji} ${habit.name}",
                    archived = habit.archivedOn != null,
                    initialDate = existing?.startDate ?: nextStartDate(existing?.localTime ?: "09:00", clock),
                    initialTime = existing?.localTime ?: "09:00",
                    initialDuration = existing?.durationMinutes?.toString() ?: "30",
                    initialReminder = existing?.reminderMinutes ?: -1,
                    initialZone = existing?.zoneId ?: ZoneId.systemDefault().id,
                    status = when {
                        blocked?.lastError?.let { it.contains("Evento no encontrado") || it.contains("Resultado externo incierto") } == true -> "Evento no encontrado o resultado incierto; no se recreará sin confirmación"
                        blocked != null -> "Requiere atención: ${blocked.lastError}"
                        binding?.state == "PUBLISHED_LOCAL" -> "Publicado en calendario del dispositivo (subida a Google no verificada)"
                        binding?.state == "DETACHED" -> "Serie conservada sin gestionar"
                        existing?.enabled == true -> "Pendiente o requiere reintento"
                        else -> "Desactivado"
                    },
                    enabled = enabled && (calendars.isNotEmpty() || binding != null),
                    onPublish = { date, time, duration, reminder, zone ->
                        val target = binding?.calendarId ?: defaultCalendar
                        val targetCalendar = calendars.firstOrNull { it.id == target }
                        if (target == null || targetCalendar == null) feedback = "Elige un calendario Google disponible antes de publicar."
                        else {
                             try {
                                feedback = when (coordinator.requestPublication(habit.id, target, targetCalendar.accountName, LocalDate.parse(date), LocalTime.parse(time), duration, ZoneId.of(zone), reminder)) {
                                    PublicationOutcome.PUBLISHED_LOCAL -> "La serie quedó publicada en el calendario del dispositivo. La sincronización con Google no está confirmada."
                                    PublicationOutcome.PENDING -> "La publicación quedó pendiente; Calendar todavía no confirmó el resultado."
                                    PublicationOutcome.BLOCKED -> "La publicación requiere atención; no se confirmó en Calendar."
                                }
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { feedback = e.message ?: "No se pudo guardar la planificación." }
                        }
                    },
                    onDisable = { delete -> scope.launch { coordinator.requestDisable(habit.id, delete); feedback = if (delete) "Se solicitó eliminar la serie gestionada." else "La serie se conservó y quedó desvinculada." } },
                    onRecreate = { date, time, duration, reminder, zone ->
                        scope.launch {
                            try {
                                coordinator.requestRecreate(habit.id, LocalDate.parse(date), LocalTime.parse(time), duration.toInt(), ZoneId.of(zone), reminder)
                                feedback = "Se solicitó recrear una serie nueva con identidad propia. La sincronización remota no está verificada."
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { feedback = e.message ?: "No se pudo recrear la serie." }
                        }
                    },
                )
            } }
            bindings.filter { binding -> habits.none { it.id == binding.habitId } && binding.state != "DELETED" && binding.state != "DETACHED" }.forEach { binding ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Serie de un hábito eliminado", style = MaterialTheme.typography.titleMedium)
                        Text("La serie de Calendar se conserva hasta que elijas qué hacer.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(enabled = enabled, onClick = { scope.launch { coordinator.requestDisable(binding.habitId, false) } }) { Text("Conservar serie") }
                            Button(enabled = enabled, onClick = { deleteOrphanHabitId = binding.habitId }) { Text("Eliminar serie…") }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
      }
    }
    if (showDisableDialog) {
        AlertDialog(
            onDismissRequest = { showDisableDialog = false },
            title = { Text("Desactivar Calendar") },
            text = { Text("¿Qué hacemos con las series creadas por La Racha? Eliminar borra las series completas y puede afectar apariciones pasadas. Si faltan permisos, la limpieza quedará pendiente y no se afirmará que ya se eliminó.") },
            confirmButton = {
                Row {
                    OutlinedButton(onClick = {
                        showDisableDialog = false
                        scope.launch {
                            bindings.filter { it.state != "DETACHED" && it.state != "DELETED" }.forEach { coordinator.requestDisable(it.habitId, false) }
                            preferences.setIntegrationEnabled(false)
                            feedback = "Integración desactivada; las series seleccionadas se conservaron sin gestionar."
                        }
                    }) { Text("Conservar") }
                    Button(onClick = {
                        showDisableDialog = false
                        scope.launch {
                            bindings.filter { it.state != "DETACHED" && it.state != "DELETED" }.forEach { coordinator.requestDisable(it.habitId, true) }
                            preferences.setIntegrationEnabled(false)
                            feedback = "Integración desactivada; se solicitaron las limpiezas autorizadas."
                        }
                    }) { Text("Eliminar series") }
                }
            },
            dismissButton = { OutlinedButton(onClick = { showDisableDialog = false }) { Text("Cancelar") } },
        )
    }
    deleteOrphanHabitId?.let { orphanId ->
        AlertDialog(
            onDismissRequest = { deleteOrphanHabitId = null },
            title = { Text("Eliminar serie de Calendar") },
            text = { Text("Se eliminará la serie completa y sus apariciones pasadas del calendario. El historial de La Racha no se modifica.") },
            confirmButton = { Button(onClick = {
                deleteOrphanHabitId = null
                scope.launch { coordinator.requestDisable(orphanId, true); feedback = "Se solicitó la eliminación de la serie." }
            }) { Text("Eliminar serie") } },
            dismissButton = { OutlinedButton(onClick = { deleteOrphanHabitId = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun CalendarScheduleCard(
    habitId: String,
    habitName: String,
    archived: Boolean,
    initialDate: String,
    initialTime: String,
    initialDuration: String,
    initialReminder: Int,
    initialZone: String,
    status: String,
    enabled: Boolean,
    onPublish: suspend (String, String, Int, Int, String) -> Unit,
    onDisable: (Boolean) -> Unit,
    onRecreate: (String, String, Int, Int, String) -> Unit,
) {
    var date by rememberSaveable(habitId) { mutableStateOf(initialDate) }
    var time by rememberSaveable(habitId) { mutableStateOf(initialTime) }
    var duration by rememberSaveable(habitId) { mutableStateOf(initialDuration) }
    var reminder by rememberSaveable(habitId) { mutableStateOf(initialReminder) }
    var zone by rememberSaveable(habitId) { mutableStateOf(initialZone) }
    var saving by remember(habitId) { mutableStateOf(false) }
    val cardScope = rememberCoroutineScope()
    var confirmRemoval by remember { mutableStateOf(false) }
    val editable = enabled && !archived
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(habitName + if (archived) " · Archivado" else "", style = MaterialTheme.typography.titleMedium)
            Text(status, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(date, { date = it }, Modifier.fillMaxWidth(), enabled = editable, label = { Text("Fecha inicial (AAAA-MM-DD)") }, singleLine = true)
            OutlinedTextField(time, { time = it }, Modifier.fillMaxWidth(), enabled = editable, label = { Text("Hora local (HH:MM)") }, singleLine = true)
            OutlinedTextField(duration, { duration = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), enabled = editable, label = { Text("Duración en minutos") }, singleLine = true)
            OutlinedTextField(zone, { zone = it }, Modifier.fillMaxWidth(), enabled = editable, label = { Text("Zona horaria IANA") }, singleLine = true)
            Text("Recordatorio")
            listOf(-1 to "Ninguno", 0 to "A la hora", 10 to "10 min antes", 30 to "30 min antes").chunked(2).forEach { options ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { (value, label) ->
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            enabled = editable,
                            onClick = { reminder = value },
                        ) { Text(if (reminder == value) "✓ $label" else label) }
                    }
                }
            }
            Text("Diario · se conserva la hora de pared en esta zona incluso al viajar. Los cambios externos no se importan; editar o borrar la serie completa puede afectar ocurrencias pasadas.", style = MaterialTheme.typography.bodySmall)
            Button(enabled = enabled && !archived && !saving && runCatching { LocalDate.parse(date); LocalTime.parse(time); requireValidDuration(duration.toInt()); ZoneId.of(zone); true }.getOrDefault(false), onClick = {
                duration.toIntOrNull()?.let { validDuration ->
                    saving = true
                    cardScope.launch {
                        try { onPublish(date, time, validDuration, reminder, zone) }
                        finally { saving = false }
                    }
                }
            }) { Text(if (saving) "Guardando…" else "Publicar planificación") }
            if (status != "Desactivado") {
                if (status.startsWith("Evento no encontrado") || status.startsWith("Evento no encontrado o resultado incierto")) {
                    Button(enabled = editable, onClick = { onRecreate(date, time, duration.toIntOrNull() ?: 30, reminder, zone) }) { Text("Confirmar y recrear serie") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = enabled, onClick = { onDisable(false) }) { Text("Conservar y desvincular") }
                    OutlinedButton(enabled = enabled, onClick = { confirmRemoval = !confirmRemoval }) { Text("Eliminar serie…") }
                }
                if (confirmRemoval) {
                    Text("Esto elimina la serie completa y sus apariciones del calendario. El historial de La Racha no cambia.")
                    Button(enabled = enabled, onClick = { confirmRemoval = false; onDisable(true) }) { Text("Confirmar eliminación") }
                }
            }
        }
    }
}

private fun nextStartDate(time: String, clock: Clock): String {
    val now = java.time.ZonedDateTime.now(clock)
    val localTime = runCatching { LocalTime.parse(time) }.getOrDefault(LocalTime.of(9, 0))
    val today = now.toLocalDate()
    return if (now.toLocalTime().isBefore(localTime)) today.toString() else today.plusDays(1).toString()
}
