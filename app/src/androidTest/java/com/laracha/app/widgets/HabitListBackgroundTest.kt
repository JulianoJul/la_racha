package com.laracha.app.widgets

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ListView
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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Checks translated RemoteViews without starting activities or changing user data. */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@RunWith(AndroidJUnit4::class)
class HabitListBackgroundTest {
    @Test
    fun shortListLeavesEmptySpaceToClickableBackground() = verifyContent(hasHabit = true)

    @Test
    fun emptyWidgetStillHasClickableBackgroundAndEditAction() = verifyContent(hasHabit = false)

    private fun verifyContent(hasHabit: Boolean) = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val size = DpSize(300.dp, 250.dp)
        val habits = if (hasHabit) listOf(
            HabitToday(
                habit = HabitEntity("background-test-only", "Leer", "📖", "#437A54", "2026-10-17"),
                completedToday = false,
                scorePercent = 0,
                currentStreak = 0,
                bestStreak = 0,
            ),
        ) else emptyList()
        val result = GlanceRemoteViews().compose(context, size) {
            GlanceTheme { HabitListContent(42, habits, habits.size, ListWidgetMode.ALL, size) }
        }
        instrumentation.runOnMainSync {
            val host = FrameLayout(context)
            val content = result.remoteViews.apply(context, host)
            val views = descendants(content).toList()
            val title = views.filterIsInstance<TextView>().single { it.text.toString() == "Mis hábitos" }
            val titleActions = clickTargets(title)
            assertEquals("Title and widget background both open the app", 2, titleActions.size)
            val background = titleActions.last()
            val edit = views.filterIsInstance<TextView>().single { it.text.toString() == "Editar" }
            assertTrue("Edit retains its own action", clickTargets(edit).first() !== background)
            if (hasHabit) {
                val list = views.filterIsInstance<ListView>().single()
                assertEquals("Short lists must not consume the empty area below their items", ViewGroup.LayoutParams.WRAP_CONTENT, list.layoutParams.height)
            } else {
                val message = views.filterIsInstance<TextView>().single { it.text.toString() == "Crea tu primer hábito en la app" }
                assertTrue("Empty area has an action distinct from Edit", clickTargets(message).first() !== clickTargets(edit).first())
            }
        }
    }

    private fun clickTargets(view: View): List<View> =
        generateSequence(view) { it.parent as? View }.filter { it.hasOnClickListeners() }.toList()

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) {
            repeat(view.childCount) { yieldAll(descendants(view.getChildAt(it))) }
        }
    }
}
