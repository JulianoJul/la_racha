package com.laracha.app.widgets

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
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
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
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

internal fun singleWidgetMetric(currentStreak: Int, scorePercent: Int): String? = when {
    currentStreak > 0 -> "🔥 $currentStreak"
    scorePercent > 0 -> "$scorePercent% constancia"
    else -> null
}

internal data class SingleWidgetCarouselLayout(
    val visibleDayCount: Int,
    val todayCircleDp: Float,
    val todayCellDp: Float,
    val neighborCircleDp: Float,
)

internal fun singleWidgetCarouselLayout(contentWidthDp: Float, compact: Boolean): SingleWidgetCarouselLayout {
    val todayCircle = if (compact) 44f else 56f
    val todayCell = todayCircle + 8f
    // Exactly two past dates and two future dates, with today's large control centered.
    val visibleDays = 5
    val neighborCell = (contentWidthDp - todayCell) / (visibleDays - 1)
    return SingleWidgetCarouselLayout(
        visibleDays,
        todayCircle,
        todayCell,
        minOf(if (compact) 28f else 36f, (neighborCell - 4f).coerceAtLeast(1f)),
    )
}

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
    val carouselLayout = singleWidgetCarouselLayout(size.width.value - 2 * padding.value, compact)
    val openDetail = actionStartActivityWithIntent(
        Intent(context, MainActivity::class.java).putExtra("habit_id", habit.habit.id)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
    )
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .clickable(openDetail)
            .padding(padding),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        SingleHabitTitle(
            habit,
            openDetail,
            GlanceModifier.fillMaxWidth(),
            when {
                compact -> 16.sp
                expanded -> 24.sp
                else -> 20.sp
            },
            if (tall && !largeFont) 2 else 1,
        )
        Spacer(GlanceModifier.size(if (compact) 2.dp else 6.dp))
        SingleHabitDayCarousel(habit, carouselLayout)
        val metric = singleWidgetMetric(habit.currentStreak, habit.scorePercent)
        if (metric != null) {
            Spacer(GlanceModifier.size(2.dp))
            Text(
                metric,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = if (compact) 10.sp else 12.sp),
                maxLines = 1,
            )
        }
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
private fun SingleHabitDayCarousel(habit: HabitToday, layout: SingleWidgetCarouselLayout) {
    val days = habit.daysAroundToday
    val firstVisible = (days.size - layout.visibleDayCount).coerceAtLeast(0) / 2
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        days.drop(firstVisible).take(layout.visibleDayCount).forEach { day ->
            // Only today is blue; neighbors remain chronological across week boundaries.
            val color = if (day.isToday) WidgetBlue else WidgetGray
            val todayAction = if (day.isToday) {
                actionRunCallback<SetHabitCompletionAction>(
                    actionParametersOf(
                        HabitIdKey to habit.habit.id,
                        CompletionValueKey to singleWidgetCompletionTarget(day.completed),
                    ),
                )
            } else null
            Column(
                modifier = if (todayAction != null) {
                    GlanceModifier.width(layout.todayCellDp.dp).clickable(todayAction)
                } else GlanceModifier.defaultWeight(),
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            ) {
                Text(day.initial, style = TextStyle(color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Spacer(GlanceModifier.size(2.dp))
                Box(
                    modifier = GlanceModifier.fillMaxWidth().height(layout.todayCircleDp.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        provider = ImageProvider(if (day.completed) R.drawable.widget_check_done else R.drawable.widget_day_outline),
                        contentDescription = (if (day.isToday) "${habit.habit.name}, " else "") + day.date.format(WidgetDayFormat) +
                            (if (day.isToday) ", hoy" else "") + ", " + when {
                            day.completed -> "completado"
                            !day.available -> "no disponible"
                            day.isToday -> "pendiente"
                            else -> "sin completar"
                        } + (if (day.isToday) {
                            if (day.completed) ". Desmarcar" else ". Completar"
                        } else ""),
                        modifier = GlanceModifier.size((if (day.isToday) layout.todayCircleDp else layout.neighborCircleDp).dp),
                        colorFilter = ColorFilter.tint(color),
                    )
                }
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
