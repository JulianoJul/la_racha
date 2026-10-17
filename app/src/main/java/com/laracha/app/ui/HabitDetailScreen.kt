package com.laracha.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laracha.app.data.HabitDetail
import com.laracha.app.data.HabitRepository
import com.laracha.app.domain.CalendarMonth
import java.time.LocalDate
import java.time.YearMonth
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlinx.coroutines.launch

private val DetailLocale = Locale.forLanguageTag("es-ES")
private val Weekdays = listOf("L", "M", "X", "J", "V", "S", "D")

@Composable
fun HabitDetailScreen(
    repository: HabitRepository,
    habitId: String,
    today: LocalDate,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val detailFlow = remember(repository, habitId, today) { repository.observeDetail(habitId, today) }
    val detail by detailFlow.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var monthOffset by rememberSaveable(habitId) { mutableIntStateOf(0) }
    val shownMonth = remember(today, monthOffset) { YearMonth.from(today).plusMonths(monthOffset.toLong()) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver a Hoy")
                    }
                    Text("Detalle del hábito", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
            }

            val current = detail
            if (current == null) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Este hábito ya no está disponible.")
                        TextButton(onClick = onBack) { Text("Volver") }
                    }
                }
            } else {
                item { DetailHeader(current, today) }
                item {
                    CalendarCard(
                        detail = current,
                        today = today,
                        shownMonth = shownMonth,
                        // The calendar must be able to reach dates before both creation and
                        // the first historical completion, otherwise that first mark is impossible.
                        canGoBack = shownMonth.isAfter(YearMonth.of(1, 1)),
                        canGoForward = monthOffset < 0,
                        onPreviousMonth = { monthOffset-- },
                        onNextMonth = { if (monthOffset < 0) monthOffset++ },
                        onToggleDay = { date, completed ->
                            scope.launch {
                                repository.setCompleted(current.habit.id, date, completed)
                            }
                        },
                    )
                }
                item {
                    Text(
                        "Toca hoy o cualquier día pasado para corregir el historial, incluso antes de crear el hábito. Hoy sigue pendiente hasta que termine el día.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailHeader(detail: HabitDetail, today: LocalDate) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(58.dp).background(parseHabitColor(detail.habit.color).copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(detail.habit.emoji, fontSize = 30.sp) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(detail.habit.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text("Creado el ${detail.habit.createdOn}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Constancia", "${detail.scorePercent}%", Modifier.fillMaxWidth())
            MetricCard("Racha actual", "${detail.currentStreak} días", Modifier.fillMaxWidth())
            MetricCard("Mejor racha", "${detail.bestStreak} días", Modifier.fillMaxWidth())
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Frecuencia: ${detail.frequency.label()}", style = MaterialTheme.typography.bodyMedium)
            val nextFrequency = detail.nextFrequency
            if (nextFrequency != null) {
                Text(
                    "${nextFrequency.frequency.label()} desde ${nextFrequency.effectiveFrom.format(DateTimeFormatter.ofPattern("d MMM", DetailLocale))}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            (detail.frequency as? com.laracha.app.domain.HabitFrequency.WeeklyQuota)?.let { quota ->
                val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val completed = detail.completedDays.count { !it.isBefore(weekStart) && !it.isAfter(today) }
                Text(
                    "Esta semana: ${completed.coerceAtMost(quota.timesPerWeek)} / ${quota.timesPerWeek} días completados",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CalendarCard(
    detail: HabitDetail,
    today: LocalDate,
    shownMonth: YearMonth,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToggleDay: (LocalDate, Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onPreviousMonth, enabled = canGoBack) {
                    Text("‹", modifier = Modifier.semantics { contentDescription = "Mes anterior" }, fontSize = 28.sp)
                }
                Text(
                    shownMonth.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", DetailLocale))
                        .replaceFirstChar { it.titlecase(DetailLocale) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = onNextMonth, enabled = canGoForward) {
                    Text("›", modifier = Modifier.semantics { contentDescription = "Mes siguiente" }, fontSize = 28.sp)
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Weekdays.forEach { weekday ->
                    Text(
                        weekday,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }

            CalendarMonth.weeks(shownMonth).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    week.forEach { date ->
                        CalendarDay(
                            date = date,
                            detail = detail,
                            today = today,
                            modifier = Modifier.weight(1f),
                            onClick = { selected -> onToggleDay(selected, selected !in detail.completedDays) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(
    date: LocalDate?,
    detail: HabitDetail,
    today: LocalDate,
    modifier: Modifier,
    onClick: (LocalDate) -> Unit,
) {
    if (date == null) {
        Spacer(modifier.aspectRatio(1f))
        return
    }

    val enabled = !date.isAfter(today)
    val completed = date in detail.completedDays
    val isToday = date == today
    val monthDate = date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", DetailLocale))
    val description = "$monthDate, ${if (completed) "completado" else "pendiente"}" +
        if (isToday) ", hoy" else ""

    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clickable(enabled = enabled) { onClick(date) }
            .semantics { contentDescription = "$description. ${if (enabled) "Tocar para cambiar" else "No editable"}" },
        shape = CircleShape,
        color = if (completed) MaterialTheme.colorScheme.primary else Color.Transparent,
        border = if (isToday && !completed) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                date.dayOfMonth.toString(),
                color = when {
                    completed -> MaterialTheme.colorScheme.onPrimary
                    enabled -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
