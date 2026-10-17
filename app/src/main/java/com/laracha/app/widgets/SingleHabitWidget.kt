package com.laracha.app.widgets

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.ColorFilter
import androidx.glance.LocalSize
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.actionStartActivity as actionStartActivityWithIntent
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.color.ColorProvider
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.text.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laracha.app.HabitApplication
import com.laracha.app.MainActivity
import com.laracha.app.R
import com.laracha.app.data.HabitToday
import com.laracha.app.domain.HabitWeekDay
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.first

private val HabitIdKey = ActionParameters.Key<String>("habit_id")
private val WidgetBlue = ColorProvider(day = Color(0xFF2563EB), night = Color(0xFF8AB4FF))
private val WidgetGray = ColorProvider(day = Color(0xFF6B7280), night = Color(0xFF9CA3AF))
private val WidgetDayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.forLanguageTag("es-ES"))

class SingleHabitWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 110.dp),
            DpSize(150.dp, 150.dp),
            DpSize(180.dp, 200.dp),
            DpSize(250.dp, 110.dp),
            DpSize(250.dp, 250.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val habitId = WidgetConfigStore.singleHabitId(context, appWidgetId)
        val app = context.applicationContext as HabitApplication
        val habit = habitId?.let { selectedId ->
            app.habitRepository.observeCurrentToday().first().firstOrNull { it.habit.id == selectedId }
        }

        provideContent {
            GlanceTheme {
                val observedHabitId by WidgetConfigStore.observeSingleHabitId(context, appWidgetId)
                    .collectAsState(initial = habitId)
                val currentHabits by app.habitRepository.observeCurrentToday().collectAsState(initial = habit?.let(::listOf).orEmpty())
                val selectedHabit = currentHabits.firstOrNull { it.habit.id == observedHabitId }
                if (selectedHabit == null) EmptySingleWidget(context, appWidgetId)
                else SingleHabitContent(context, selectedHabit, LocalSize.current)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun SingleHabitContent(context: Context, habit: HabitToday, size: DpSize) {
    val largeFont = LocalContext.current.resources.configuration.fontScale > 1.3f
    val compact = size.height < 150.dp || (largeFont && size.height < 200.dp)
    val expanded = size.height >= 220.dp && size.width >= 220.dp
    val tall = size.height >= 200.dp
    val padding = if (size.height < 150.dp) 4.dp else if (expanded) 12.dp else 8.dp
    val buttonSize = if (compact) 48.dp else if (expanded) 72.dp else if (tall) 64.dp else 56.dp
    val daySize = ((size.width.value - 2 * padding.value) / 7 - 2).coerceIn(10f, 24f).dp
    val openDetail = actionStartActivityWithIntent(
        Intent(context, MainActivity::class.java).putExtra("habit_id", habit.habit.id)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
    )
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(padding),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        if (compact) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                SingleHabitTitle(habit, openDetail, GlanceModifier.defaultWeight(), 16.sp, if (largeFont) 1 else 2)
                Spacer(GlanceModifier.size(4.dp))
                SingleHabitCompletionButton(context, habit, buttonSize)
            }
        } else {
            SingleHabitTitle(habit, openDetail, GlanceModifier.fillMaxWidth(), if (expanded) 26.sp else 22.sp, if (tall && !largeFont) 2 else 1)
            Spacer(GlanceModifier.size(8.dp))
            SingleHabitCompletionButton(context, habit, buttonSize)
        }
        Spacer(GlanceModifier.size(if (compact) 4.dp else 8.dp))
        SingleHabitWeek(habit.week, daySize)
    }
}

@androidx.compose.runtime.Composable
private fun SingleHabitTitle(habit: HabitToday, openDetail: Action, modifier: GlanceModifier, fontSize: TextUnit, maxLines: Int) {
    Text(
        text = "${habit.habit.emoji}  ${habit.habit.name}",
        modifier = modifier.clickable(openDetail),
        maxLines = maxLines,
        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = fontSize, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
    )
}

@androidx.compose.runtime.Composable
private fun SingleHabitCompletionButton(context: Context, habit: HabitToday, size: Dp) {
    Image(
        provider = ImageProvider(if (habit.completedToday) R.drawable.widget_check_done else R.drawable.widget_check_pending),
        contentDescription = context.getString(
            if (habit.completedToday) R.string.single_widget_uncheck else R.string.single_widget_check,
            habit.habit.name,
        ),
        modifier = GlanceModifier.size(size).clickable(
            actionRunCallback<SetHabitCompletionAction>(
                actionParametersOf(HabitIdKey to habit.habit.id, CompletionValueKey to !habit.completedToday),
            ),
        ),
        colorFilter = ColorFilter.tint(WidgetBlue),
    )
}

@androidx.compose.runtime.Composable
private fun SingleHabitWeek(days: List<HabitWeekDay>, daySize: Dp) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        days.forEach { day ->
            // Only today is blue, even when earlier days in this week were completed.
            val color = if (day.isToday) WidgetBlue else WidgetGray
            Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                Text(day.initial, style = TextStyle(color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Spacer(GlanceModifier.size(2.dp))
                Image(
                    provider = ImageProvider(if (day.completed) R.drawable.widget_check_done else R.drawable.widget_day_outline),
                    contentDescription = day.date.format(WidgetDayFormat) +
                        (if (day.isToday) ", hoy" else "") + ", " + when {
                            day.completed -> "completado"
                            !day.available -> "no disponible"
                            day.isToday -> "pendiente"
                            else -> "sin completar"
                        },
                    modifier = GlanceModifier.size(daySize),
                    colorFilter = ColorFilter.tint(color),
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun EmptySingleWidget(context: Context, appWidgetId: Int) {
    val configIntent = Intent(context, SingleHabitWidgetConfigurationActivity::class.java)
        .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(16.dp)
            .clickable(actionStartActivityWithIntent(configIntent)),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        Text("Elige un hábito", style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Bold))
        Text("Toca para configurar", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
    }
}
