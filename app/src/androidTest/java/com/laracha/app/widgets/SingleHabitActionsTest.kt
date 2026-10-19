package com.laracha.app.widgets

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.laracha.app.data.HabitEntity
import com.laracha.app.data.HabitToday
import com.laracha.app.domain.HabitWeek
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@RunWith(AndroidJUnit4::class)
class SingleHabitActionsTest {
    @Test
    fun smallWidgetHasOnlyBackgroundAndTodayActions() = verifyActions(completedToday = false)

    @Test
    fun checkedTodayRetainsIndependentUncheckAction() = verifyActions(completedToday = true)

    private fun verifyActions(completedToday: Boolean) = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val today = LocalDate.of(2026, 10, 17)
        val habit = HabitToday(
            habit = HabitEntity("single-actions-test-only", "Leer", "📖", "#437A54", today.toString()),
            completedToday = completedToday,
            scorePercent = 0,
            currentStreak = 0,
            bestStreak = 0,
            daysAroundToday = HabitWeek.daysAroundToday(today, if (completedToday) setOf(today) else emptySet(), today),
        )
        val size = DpSize(180.dp, 200.dp)
        val result = GlanceRemoteViews().compose(context, size) {
            GlanceTheme { SingleHabitContent(context, habit, size) }
        }
        instrumentation.runOnMainSync {
            val content = result.remoteViews.apply(context, FrameLayout(context))
            val views = descendants(content).toList()
            assertEquals(2, views.count { it.hasOnClickListeners() })
            val title = views.filterIsInstance<TextView>().single { it.text.contains("Leer") }
            assertFalse("The title has no separate click action", title.hasOnClickListeners())
            val background = generateSequence(title as View) { it.parent as? View }.single { it.hasOnClickListeners() }
            val action = if (completedToday) ". Desmarcar" else ". Completar"
            val todayImage = views.single { it.contentDescription?.toString()?.endsWith(action) == true }
            val todayTarget = generateSequence(todayImage) { it.parent as? View }.first { it.hasOnClickListeners() }
            assertNotSame(background, todayTarget)
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) {
            repeat(view.childCount) { yieldAll(descendants(view.getChildAt(it))) }
        }
    }
}
