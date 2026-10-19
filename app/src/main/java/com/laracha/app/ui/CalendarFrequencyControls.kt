package com.laracha.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.laracha.app.calendar.CalendarRecurrence
import com.laracha.app.domain.HabitFrequency
import java.time.DayOfWeek
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CalendarQuotaDaysPicker(target: Int, selected: Set<DayOfWeek>, enabled: Boolean, onChange: (Set<DayOfWeek>) -> Unit) {
    Text("Elige $target días para Calendar (${selected.size}/$target)")
    Text("Solo organizan tus eventos. El hábito sigue siendo una cuota semanal, sin días obligatorios.", style = MaterialTheme.typography.bodySmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.values().forEach { day ->
            FilterChip(
                selected = day in selected,
                enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp),
                onClick = { onChange(if (day in selected) selected - day else selected + day) },
                label = { Text(listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")[day.value - 1]) },
            )
        }
    }
}

@Composable
internal fun CalendarSeriesUpdateDialog(
    habitName: String,
    frequency: HabitFrequency,
    initialQuotaDays: Set<DayOfWeek>,
    onDismiss: () -> Unit,
    onConfirm: suspend (Set<DayOfWeek>) -> Unit,
) {
    var dayCode by rememberSaveable(habitName, CalendarRecurrence.frequencyKey(frequency)) {
        mutableStateOf(CalendarRecurrence.encodeDays(initialQuotaDays))
    }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val days = if (frequency is HabitFrequency.WeeklyQuota) CalendarRecurrence.decodeDays(dayCode) else emptySet()
    val valid = frequency !is HabitFrequency.WeeklyQuota || days.size == frequency.timesPerWeek
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("¿Actualizar también Calendar?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$habitName · ${frequency.label()}")
                Text("Se actualizará la serie completa con esta frecuencia, incluidas sus apariciones pasadas. Si mantienes Calendar como estaba, tus eventos no cambiarán. El historial, la racha y la constancia de La Racha no se modifican.")
                if (frequency is HabitFrequency.WeeklyQuota) {
                    CalendarQuotaDaysPicker(frequency.timesPerWeek, days, !saving) { dayCode = CalendarRecurrence.encodeDays(it) }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = valid && !saving, onClick = {
                saving = true
                error = null
                scope.launch {
                    try { onConfirm(days) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { error = failure.message ?: "No se pudo actualizar Calendar." }
                    finally { saving = false }
                }
            }) { Text(if (saving) "Actualizando…" else "Actualizar serie") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Mantener Calendar") } },
    )
}
