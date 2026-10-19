package com.laracha.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laracha.app.R
import com.laracha.app.data.HabitEntity
import com.laracha.app.data.HabitRepository
import com.laracha.app.data.HabitToday
import com.laracha.app.HabitApplication
import com.laracha.app.data.CalendarEventBindingEntity
import com.laracha.app.data.HabitCalendarScheduleEntity
import com.laracha.app.calendar.CalendarRecurrence
import com.laracha.app.calendar.PublicationOutcome
import com.laracha.app.data.frequency
import com.laracha.app.domain.HabitFrequency
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

private val HabitColors = listOf("#437A54", "#D17C45", "#567DA8", "#9A6AA6", "#C35C62")
private val HabitEmojis = listOf("🌱", "📖", "💧", "🏃", "🧘", "✍️", "🎨", "🎸")
private val SpanishLocale = Locale.forLanguageTag("es-ES")

private data class CalendarFrequencyUpdateTarget(
    val habitId: String,
    val habitName: String,
    val frequency: HabitFrequency,
    val schedule: HabitCalendarScheduleEntity,
    val binding: CalendarEventBindingEntity,
)

@Composable
fun TodayScreen(
    repository: HabitRepository,
    today: LocalDate,
    onOpenSettings: () -> Unit,
    onOpenCalendar: () -> Unit,
    onDayChanged: () -> Unit,
    openedHabitId: String? = null,
    onOpenedHabitIdHandled: () -> Unit = {},
) {
    val todayFlow = remember(repository, today) { repository.observeToday(today) }
    val habits by todayFlow.collectAsState(initial = emptyList())
    val allHabits by repository.observeHabits().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = allHabits.firstOrNull { it.id == editingId }
    var detailHabitId by rememberSaveable { mutableStateOf<String?>(null) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var showingArchived by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<HabitEntity?>(null) }
    var calendarLifecycleTarget by remember { mutableStateOf<Pair<HabitEntity, Boolean>?>(null) }
    var calendarFrequencyTarget by remember { mutableStateOf<CalendarFrequencyUpdateTarget?>(null) }
    val calendarBindings by (context.applicationContext as HabitApplication).database.calendarDao()
        .observeBindings().collectAsState(initial = emptyList())

    androidx.compose.runtime.LaunchedEffect(openedHabitId) {
        if (openedHabitId != null) {
            detailHabitId = openedHabitId
            onOpenedHabitIdHandled()
        }
    }

    BackHandler(enabled = showEditor || deleteTarget != null) {
        if (deleteTarget != null) deleteTarget = null else showEditor = false
    }

    val active = habits
    val archived = allHabits.filter { it.archivedOn != null }
    val done = active.count(HabitToday::completedToday)
    val listState = rememberLazyListState()

    detailHabitId?.let { habitId ->
        HabitDetailScreen(
            repository = repository,
            habitId = habitId,
            today = today,
            onBack = { detailHabitId = null },
        )
        return
    }

    val dateLabel = remember(today) {
        today.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", SpanishLocale))
            .replaceFirstChar { it.titlecase(SpanishLocale) }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingId = null
                    showEditor = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Añadir hábito")
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { contentPadding ->
        Column(Modifier.fillMaxSize().padding(contentPadding)) {
          LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                top = 16.dp,
                end = 20.dp,
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(dateLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row {
                        IconButton(onClick = onOpenCalendar) {
                            Icon(
                                painter = painterResource(R.drawable.ic_calendar_today),
                                contentDescription = "Google Calendar",
                            )
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Mis hábitos", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Pequeños pasos, a tu ritmo.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(18.dp))
                TodayProgress(done, active.size)
            }

            if (active.isEmpty()) {
                item {
                    EmptyHabits(
                        onCreate = {
                            editingId = null
                            showEditor = true
                        },
                    )
                }
            } else {
                itemsIndexed(active, key = { _, item -> item.habit.id }) { index, item ->
                    HabitRow(
                        item = item,
                        onToggle = { checked ->
                            scope.launch {
                                if (repository.today() != today) {
                                    onDayChanged()
                                    return@launch
                                }
                                repository.setCompleted(item.habit.id, today, checked)
                            }
                        },
                        onEdit = {
                            editingId = item.habit.id
                            showEditor = true
                        },
                        onOpenDetail = { detailHabitId = item.habit.id },
                        canMoveUp = index > 0,
                        canMoveDown = index < active.lastIndex,
                        onMove = { delta ->
                            val orderedIds = active.map { it.habit.id }.toMutableList()
                            val target = index + delta
                            if (target in orderedIds.indices) {
                                val movedId = orderedIds.removeAt(index)
                                orderedIds.add(target, movedId)
                                scope.launch {
                                    repository.reorderHabits(orderedIds)
                                }
                            }
                        },
                        onArchive = {
                            if (hasManagedCalendarBinding(calendarBindings, item.habit.id)) {
                                calendarLifecycleTarget = item.habit to false
                            } else scope.launch {
                                repository.setArchived(item.habit.id, true)
                            }
                        },
                            onDelete = {
                                if (hasManagedCalendarBinding(calendarBindings, item.habit.id)) {
                                    calendarLifecycleTarget = item.habit to true
                                    deleteTarget = null
                                } else deleteTarget = item.habit
                            },
                    )
                }
            }

            if (archived.isNotEmpty()) {
                item {
                    TextButton(onClick = { showingArchived = !showingArchived }) {
                        Text(if (showingArchived) "Ocultar archivados (${archived.size})" else "Ver archivados (${archived.size})")
                    }
                }
                if (showingArchived) {
                    items(archived, key = { "archived-${it.id}" }) { habit ->
                        ArchivedHabitRow(
                            habit = habit,
                            onRestore = {
                                scope.launch {
                                    repository.setArchived(habit.id, false)
                                }
                            },
                            onDelete = {
                                if (hasManagedCalendarBinding(calendarBindings, habit.id)) {
                                    calendarLifecycleTarget = habit to true
                                } else deleteTarget = habit
                            },
                        )
                    }
                }
            }
          }
          if (listState.canScrollForward || listState.canScrollBackward) {
              ScrollHint(showDown = listState.canScrollForward)
          }
        }
    }

    if (showEditor && (editingId == null || editing != null)) {
        HabitEditorDialog(
            initial = editing,
            initialFrequency = editing?.frequency() ?: HabitFrequency.Daily,
            today = today,
            onDismiss = { showEditor = false },
            onSave = { name, emoji, color, frequency ->
                scope.launch {
                    val current = editing
                    if (current == null) repository.createHabit(name, emoji, color, frequency)
                    else {
                        repository.renameHabit(current.id, name, emoji, color)
                        val frequencyChanged = repository.updateFrequency(current.id, frequency)
                        val app = context.applicationContext as HabitApplication
                        val schedule = app.database.calendarDao().getSchedule(current.id)
                        val binding = app.database.calendarDao().getBinding(current.id)
                        if (schedule?.enabled == true && binding != null && binding.state !in setOf("DETACHED", "DELETED") &&
                            app.calendarPreferences.integrationEnabled.first()) {
                            if (frequencyChanged) {
                                calendarFrequencyTarget = CalendarFrequencyUpdateTarget(current.id, "$emoji $name", frequency, schedule, binding)
                            } else {
                                app.calendarCoordinator.refreshPublishedName(current.id)
                            }
                        }
                    }
                    showEditor = false
                }
            },
        )
    }

    deleteTarget?.let { habit ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("¿Eliminar ${habit.name}?") },
            text = { Text("También se borrará su historial. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    if (hasManagedCalendarBinding(calendarBindings, habit.id)) {
                        calendarLifecycleTarget = habit to true
                    } else {
                        scope.launch {
                            repository.deleteHabit(habit.id)
                        }
                    }
                    deleteTarget = null
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") } },
        )
    }

    calendarFrequencyTarget?.let { target ->
        CalendarSeriesUpdateDialog(
            habitName = target.habitName,
            frequency = target.frequency,
            initialQuotaDays = CalendarRecurrence.decodeDays(target.schedule.quotaDays),
            onDismiss = { calendarFrequencyTarget = null },
        ) { quotaDays ->
            val app = context.applicationContext as HabitApplication
            val schedule = target.schedule
            val result = app.calendarCoordinator.requestPublication(
                habitId = target.habitId,
                calendarId = target.binding.calendarId,
                accountName = target.binding.accountName,
                startDate = LocalDate.parse(schedule.startDate),
                time = java.time.LocalTime.parse(schedule.localTime),
                durationMinutes = schedule.durationMinutes,
                zoneId = java.time.ZoneId.of(schedule.zoneId),
                reminderMinutes = schedule.reminderMinutes,
                quotaDays = quotaDays,
                confirmedRecurrenceChange = true,
            )
            when (result) {
                PublicationOutcome.PUBLISHED_LOCAL -> calendarFrequencyTarget = null
                PublicationOutcome.PENDING -> error("La actualización quedó pendiente. Puedes revisar su estado en Calendar.")
                PublicationOutcome.BLOCKED -> error("No se confirmó la actualización. Revisa los permisos y el estado en Calendar.")
            }
        }
    }

    calendarLifecycleTarget?.let { (habit, deleting) ->
        AlertDialog(
            onDismissRequest = { calendarLifecycleTarget = null },
            title = { Text(if (deleting) "Hábito vinculado a Calendar" else "Hábito planificado en Calendar") },
            text = {
                Text(
                    "La serie de Calendar es independiente del historial de La Racha. Puedes conservarla sin que la app la gestione, eliminar la serie completa (incluidas apariciones pasadas) o cancelar.",
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        calendarLifecycleTarget = null
                        scope.launch {
                            val app = context.applicationContext as HabitApplication
                            app.calendarCoordinator.requestDisable(habit.id, deleteSeries = false)
                            if (deleting) repository.deleteHabit(habit.id) else repository.setArchived(habit.id, true)
                        }
                    }) { Text("Conservar") }
                    TextButton(onClick = {
                        calendarLifecycleTarget = null
                        scope.launch {
                            val app = context.applicationContext as HabitApplication
                            app.calendarCoordinator.requestDisable(habit.id, deleteSeries = true)
                            if (deleting) repository.deleteHabit(habit.id) else repository.setArchived(habit.id, true)
                        }
                    }) { Text("Eliminar serie", color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = { TextButton(onClick = { calendarLifecycleTarget = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ScrollHint(showDown: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            if (showDown) "Desliza hacia abajo" else "Desliza hacia arriba",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            if (showDown) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun hasManagedCalendarBinding(bindings: List<CalendarEventBindingEntity>, habitId: String): Boolean =
    bindings.any { it.habitId == habitId && it.state !in setOf("DETACHED", "DELETED") }

@Composable
private fun TodayProgress(done: Int, total: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Hoy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("$done / $total", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HabitRow(
    item: HabitToday,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onOpenDetail: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(46.dp).background(parseHabitColor(item.habit.color).copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(item.habit.emoji, fontSize = 23.sp) }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpenDetail)
                    .semantics { contentDescription = "Ver detalle de ${item.habit.name}" },
                verticalArrangement = Arrangement.Center,
            ) {
                Text(item.habit.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(
                    "${item.scorePercent}% constancia",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${item.currentStreak} ${if (item.currentStreak == 1) "día" else "días"} de racha",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Checkbox(
                checked = item.completedToday,
                onCheckedChange = onToggle,
                modifier = Modifier.semantics {
                    contentDescription = "${item.habit.name}, constancia ${item.scorePercent} por ciento, racha ${item.currentStreak} días, " +
                        if (item.completedToday) "completado hoy" else "pendiente hoy; completar"
                },
            )
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Opciones de ${item.habit.name}")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Editar") }, onClick = { menuExpanded = false; onEdit() })
                    DropdownMenuItem(
                        text = { Text("Subir") },
                        enabled = canMoveUp,
                        onClick = { menuExpanded = false; onMove(-1) },
                    )
                    DropdownMenuItem(
                        text = { Text("Bajar") },
                        enabled = canMoveDown,
                        onClick = { menuExpanded = false; onMove(1) },
                    )
                    DropdownMenuItem(text = { Text("Archivar") }, onClick = { menuExpanded = false; onArchive() })
                    DropdownMenuItem(text = { Text("Eliminar") }, onClick = { menuExpanded = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun ArchivedHabitRow(habit: HabitEntity, onRestore: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(habit.emoji, fontSize = 22.sp)
            Spacer(Modifier.width(12.dp))
            Text(habit.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = onRestore) { Text("Restaurar") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.MoreVert, contentDescription = "Eliminar ${habit.name}") }
        }
    }
}

@Composable
private fun EmptyHabits(onCreate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("🌿", fontSize = 38.sp)
            Text("Empieza con un hábito", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("No hace falta hacerlo perfecto. Solo empezar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onCreate) { Text("Crear hábito") }
        }
    }
}

@Composable
private fun HabitEditorDialog(
    initial: HabitEntity?,
    initialFrequency: HabitFrequency,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (String, String, String, HabitFrequency) -> Unit,
) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var emoji by rememberSaveable(initial?.id) { mutableStateOf(initial?.emoji ?: HabitEmojis.first()) }
    var color by rememberSaveable(initial?.id) { mutableStateOf(initial?.color ?: HabitColors.first()) }
    val initialFixedDays = initialFrequency as? HabitFrequency.FixedDays
    val initialInterval = initialFrequency as? HabitFrequency.EveryNDays
    val initialQuota = initialFrequency as? HabitFrequency.WeeklyQuota
    var frequencyType by rememberSaveable(initial?.id) {
        mutableStateOf(
            when (initialFrequency) {
                HabitFrequency.Daily -> "DAILY"
                is HabitFrequency.FixedDays -> "FIXED_DAYS"
                is HabitFrequency.EveryNDays -> "EVERY_N_DAYS"
                is HabitFrequency.WeeklyQuota -> "WEEKLY_QUOTA"
            },
        )
    }
    var selectedDays by rememberSaveable(initial?.id) {
        mutableStateOf(initialFixedDays?.days.orEmpty().joinToString(",") { it.value.toString() })
    }
    var intervalText by rememberSaveable(initial?.id) { mutableStateOf(initialInterval?.intervalDays?.toString() ?: "3") }
    var referenceDate by rememberSaveable(initial?.id) {
        mutableStateOf(
            initialInterval?.referenceDate?.toString()
                ?: when {
                    initial == null -> today
                    initialFrequency is HabitFrequency.WeeklyQuota -> today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                    else -> today.plusDays(1)
                }.toString(),
        )
    }
    var quotaText by rememberSaveable(initial?.id) { mutableStateOf(initialQuota?.timesPerWeek?.toString() ?: "3") }
    val nameFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val chosenDays = selectedDays.split(',').mapNotNull { value -> value.toIntOrNull()?.let { DayOfWeek.of(it) } }.toSet()
    val intervalValue = intervalText.toIntOrNull()
    val quotaValue = quotaText.toIntOrNull()
    val frequency: HabitFrequency? = when (frequencyType) {
        "FIXED_DAYS" -> chosenDays.takeIf { it.isNotEmpty() }?.let { HabitFrequency.FixedDays(it) }
        "EVERY_N_DAYS" -> intervalValue?.takeIf { it >= 1 }?.let { HabitFrequency.EveryNDays(it, LocalDate.parse(referenceDate)) }
        "WEEKLY_QUOTA" -> quotaValue?.takeIf { it in 1..7 }?.let { HabitFrequency.WeeklyQuota(it) }
        else -> HabitFrequency.Daily
    }
    val valid = name.trim().isNotEmpty() && frequency != null

    androidx.compose.runtime.LaunchedEffect(initial?.id) {
        if (initial == null) {
            nameFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuevo hábito" else "Editar hábito") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().focusRequester(nameFocusRequester),
                )
                Text("Frecuencia", style = MaterialTheme.typography.labelLarge)
                val frequencyOptions = listOf(
                    "DAILY" to "Diario",
                    "FIXED_DAYS" to "Días fijos",
                    "EVERY_N_DAYS" to "Cada X días",
                    "WEEKLY_QUOTA" to "Días por semana",
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    frequencyOptions.forEach { (code, label) ->
                        FilterChip(
                            selected = frequencyType == code,
                            onClick = { frequencyType = code },
                            label = { Text(label) },
                        )
                    }
                }
                when (frequencyType) {
                    "FIXED_DAYS" -> {
                        Text("Días de la semana", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            listOf(
                                DayOfWeek.MONDAY to "L",
                                DayOfWeek.TUESDAY to "M",
                                DayOfWeek.WEDNESDAY to "X",
                                DayOfWeek.THURSDAY to "J",
                                DayOfWeek.FRIDAY to "V",
                                DayOfWeek.SATURDAY to "S",
                                DayOfWeek.SUNDAY to "D",
                            ).forEach { (day, label) ->
                                val selected = day in chosenDays
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        val next = if (selected) chosenDays - day else chosenDays + day
                                        selectedDays = next.sortedBy { it.value }.joinToString(",") { it.value.toString() }
                                    },
                                    label = { Text(label) },
                                )
                            }
                        }
                        if (chosenDays.isEmpty()) Text("Selecciona al menos un día", color = MaterialTheme.colorScheme.error)
                    }
                    "EVERY_N_DAYS" -> {
                        OutlinedTextField(
                            value = intervalText,
                            onValueChange = { intervalText = it.filter(Char::isDigit).take(10) },
                            label = { Text("Cada cuántos días") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                        )
                        Text(
                            "Referencia del intervalo: $referenceDate",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    "WEEKLY_QUOTA" -> OutlinedTextField(
                        value = quotaText,
                        onValueChange = { quotaText = it.filter(Char::isDigit).take(1) },
                        label = { Text("Días distintos por semana (1–7)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }
                Text("Elige un icono", style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    HabitEmojis.forEach { choice ->
                        Text(
                            choice,
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    if (emoji == choice) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable { emoji = choice }
                                .semantics { contentDescription = "Icono $choice, ${if (emoji == choice) "seleccionado" else "no seleccionado"}" }
                                .padding(8.dp),
                            fontSize = 22.sp,
                        )
                    }
                }
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HabitColors.forEachIndexed { index, choice ->
                        val colorName = listOf("verde", "naranja", "azul", "violeta", "rojo")[index]
                        Surface(
                            modifier = Modifier
                                .size(48.dp)
                                .clickable { color = choice }
                                .semantics { contentDescription = "Color $colorName${if (color == choice) ", seleccionado" else ""}" },
                            shape = CircleShape,
                            color = parseHabitColor(choice),
                            border = if (color == choice) androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
                        ) {
                            if (color == choice) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(name.trim(), emoji, color, requireNotNull(frequency)) }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

internal fun parseHabitColor(value: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(Color(0xFF437A54))
