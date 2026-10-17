package com.laracha.app.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity as actionStartDetailActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
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
import androidx.glance.layout.height
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.laracha.app.HabitApplication
import com.laracha.app.MainActivity
import com.laracha.app.data.HabitToday
import kotlinx.coroutines.flow.first

private val ListActionHabitIdKey = ActionParameters.Key<String>("habit_id")
private val ListWidgetIdKey = ActionParameters.Key<Int>(AppWidgetManager.EXTRA_APPWIDGET_ID)

class HabitListWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(250.dp, 170.dp), DpSize(300.dp, 250.dp)),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val config = WidgetConfigStore.listConfig(context, appWidgetId)
        val allHabits = (context.applicationContext as HabitApplication).habitRepository.observeCurrentToday().first()
        provideContent {
            GlanceTheme {
                val observedConfig by WidgetConfigStore.observeListConfig(context, appWidgetId)
                    .collectAsState(initial = config)
                val currentHabits by (context.applicationContext as HabitApplication)
                    .habitRepository.observeCurrentToday().collectAsState(initial = allHabits)
                val currentShown = when (observedConfig.mode) {
                    ListWidgetMode.ALL -> currentHabits
                    ListWidgetMode.SELECTED -> {
                        val byId = currentHabits.associateBy(HabitToday::habitId)
                        observedConfig.selectedHabitIds.mapNotNull(byId::get)
                    }
                }
                HabitListContent(appWidgetId, currentShown, currentHabits.size, observedConfig.mode, LocalSize.current)
            }
        }
    }
}

private val HabitToday.habitId: String get() = habit.id

@androidx.compose.runtime.Composable
private fun HabitListContent(
    appWidgetId: Int,
    habits: List<HabitToday>,
    totalActive: Int,
    mode: ListWidgetMode,
    size: DpSize,
) {
    val compact = size.height < 210.dp
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(if (compact) 8.dp else 12.dp),
        verticalAlignment = Alignment.Vertical.Top,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    "Mis hábitos",
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                )
                Text(
                    "${habits.count(HabitToday::completedToday)} / ${if (mode == ListWidgetMode.ALL) totalActive else habits.size} hoy",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                )
            }
            Text(
                text = "Editar",
                modifier = GlanceModifier.clickable(
                    actionStartActivity<HabitListWidgetConfigurationActivity>(
                        actionParametersOf(ListWidgetIdKey to appWidgetId),
                    ),
                ),
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold),
            )
        }
        Spacer(GlanceModifier.size(if (compact) 2.dp else 6.dp))

        if (habits.isEmpty()) {
            Column(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight().clickable(
                    actionStartActivity<HabitListWidgetConfigurationActivity>(
                        actionParametersOf(ListWidgetIdKey to appWidgetId),
                    ),
                ),
                verticalAlignment = Alignment.Vertical.CenterVertically,
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            ) {
                Text(
                    if (mode == ListWidgetMode.ALL && totalActive == 0) "Crea tu primer hábito en la app" else "Toca Editar para elegir hábitos",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                )
            }
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(habits) { habit ->
                    HabitListRow(habit, compact)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun HabitListRow(habit: HabitToday, compact: Boolean) {
    val openDetail = actionStartDetailActivity(
        Intent(androidx.glance.LocalContext.current, MainActivity::class.java)
            .putExtra("habit_id", habit.habit.id)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
    )
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = if (compact) 1.dp else 3.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Row(
            modifier = GlanceModifier.defaultWeight().height(52.dp).clickable(openDetail),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(habit.habit.emoji, style = TextStyle(fontSize = 18.sp))
            Spacer(GlanceModifier.size(8.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = habit.habit.name,
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                )
                Text(
                    text = if (compact) "${habit.scorePercent}%" else "${habit.scorePercent}% · 🔥 ${habit.currentStreak}",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                )
            }
        }
        CheckBox(
            checked = habit.completedToday,
            text = if (habit.completedToday) "Hecho" else "",
            onCheckedChange = actionRunCallback<SetHabitCompletionAction>(
                actionParametersOf(ListActionHabitIdKey to habit.habit.id),
            ),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 11.sp),
            maxLines = 1,
        )
    }
}
