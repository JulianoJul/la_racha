package com.laracha.app.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.laracha.app.R
import com.laracha.app.domain.HabitWeekDay
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HabitIdKey = ActionParameters.Key<String>("habit_id")
private val WidgetBlue = ColorProvider(day = Color(0xFF2563EB), night = Color(0xFF8AB4FF))
private val WidgetGray = ColorProvider(day = Color(0xFF6B7280), night = Color(0xFF9CA3AF))
private val WidgetDayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.forLanguageTag("es-ES"))

/** A single shared presentation and completion action for day indicators in both widgets. */
@Composable
internal fun HabitDayIndicator(
    habitId: String,
    habitName: String,
    day: HabitWeekDay,
    modifier: GlanceModifier,
    circleSize: Dp,
    circleAreaHeight: Dp = circleSize,
) {
    val color = if (day.isToday) WidgetBlue else WidgetGray
    val todayAction = if (day.isToday) {
        actionRunCallback<SetHabitCompletionAction>(
            actionParametersOf(
                HabitIdKey to habitId,
                CompletionValueKey to singleWidgetCompletionTarget(day.completed),
            ),
        )
    } else null
    Column(
        modifier = if (todayAction != null) modifier.clickable(todayAction) else modifier,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Text(day.initial, style = TextStyle(color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        Spacer(GlanceModifier.size(2.dp))
        Box(
            modifier = GlanceModifier.fillMaxWidth().height(circleAreaHeight),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(if (day.completed) R.drawable.widget_check_done else R.drawable.widget_day_outline),
                contentDescription = "$habitName, " + day.date.format(WidgetDayFormat) +
                    (if (day.isToday) ", hoy" else "") + ", " + when {
                    day.completed -> "completado"
                    !day.available -> "no disponible"
                    day.isToday -> "pendiente"
                    else -> "sin completar"
                } + (if (day.isToday) {
                    if (day.completed) ". Desmarcar" else ". Completar"
                } else ""),
                modifier = GlanceModifier.size(circleSize),
                colorFilter = ColorFilter.tint(color),
            )
        }
    }
}
