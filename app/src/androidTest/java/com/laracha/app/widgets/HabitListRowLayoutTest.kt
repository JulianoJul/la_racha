package com.laracha.app.widgets

import android.graphics.Rect
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
import com.laracha.app.domain.HabitFrequency
import com.laracha.app.domain.HabitWeek
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

/** Real RemoteViews geometry, without a launcher or writes to the app's habit database. */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@RunWith(AndroidJUnit4::class)
class HabitListRowLayoutTest {
    @Test
    fun compactRowKeepsTodayOutOfScrollbarGutter() = verifyRow(widthDp = 234, compact = true, previousDays = 0)

    @Test
    fun regularRowKeepsTodayOutOfScrollbarGutter() = verifyRow(widthDp = 276, compact = false, previousDays = 2)

    @Test
    fun compactSevenDayStripPreservesNameAndTouchTarget() = verifyRow(widthDp = 234, compact = true, previousDays = 6)

    @Test
    fun regularSevenDayStripPreservesNameAndTouchTarget() = verifyRow(widthDp = 276, compact = false, previousDays = 6)

    private fun verifyRow(widthDp: Int, compact: Boolean, previousDays: Int) = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val today = LocalDate.of(2026, 10, 17)
        val habit = HabitToday(
            habit = HabitEntity("layout-test-only", "Leer", "📖", "#437A54", "2026-10-17"),
            completedToday = false,
            scorePercent = 82,
            currentStreak = 4,
            bestStreak = 4,
            recentProgressDays = HabitWeek.recentProgressDays(
                today.minusWeeks(1), (1..previousDays).mapTo(mutableSetOf()) { today.minusDays(it.toLong()) },
                today, HabitFrequency.Daily,
            ),
        )
        val heightDp = if (previousDays >= 3) 120 else 64
        val result = GlanceRemoteViews().compose(context, DpSize(widthDp.dp, heightDp.dp)) {
            GlanceTheme { HabitListRow(habit, compact) }
        }
        instrumentation.runOnMainSync {
            val density = context.resources.displayMetrics.density
            val host = FrameLayout(context)
            val content = result.remoteViews.apply(context, host)
            host.addView(content)
            val width = (widthDp * density).roundToInt()
            val height = (heightDp * density).roundToInt()
            host.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            host.layout(0, 0, width, height)

            val views = descendants(host).toList()
            val todayImage = views.single { it.contentDescription?.toString()?.endsWith(". Completar") == true }
            val todayTarget = generateSequence(todayImage) { it.parent as? View }.first { it.hasOnClickListeners() }
            val bounds = Rect(0, 0, todayTarget.width, todayTarget.height)
            host.offsetDescendantRectToMyCoords(todayTarget, bounds)
            assertTrue("Reserve at least 24dp inside the row, not on the scroll container", width - bounds.right >= (24 * density).toInt() - 1)
            assertTrue("Today keeps a 48dp-wide touch target", todayTarget.width >= (48 * density).toInt() - 1)
            assertTrue("Today keeps a 48dp-tall touch target", todayTarget.height >= (48 * density).toInt() - 1)

            val name = views.filterIsInstance<TextView>().single { it.text.toString() == "Leer" }
            val navigationTargets = generateSequence(name as View) { it.parent as? View }
                .filter { it.hasOnClickListeners() }.toList()
            val detailTarget = navigationTargets.first()
            assertNotSame("Detail action is not the completion action", todayTarget, detailTarget)
            assertTrue("Unused row space has an app action separate from detail", navigationTargets.size == 2)
            assertTrue("Only background, detail and today have actions", views.count { it.hasOnClickListeners() } == 3)
            val nameBounds = Rect(0, 0, name.width, name.height)
            host.offsetDescendantRectToMyCoords(name, nameBounds)
            assertFalse("Name and today must not overlap", Rect.intersects(nameBounds, bounds))
            assertTrue("Habit name retains usable width", name.width >= (48 * density).toInt() - 1)
            assertFalse("State label must not be visible beside the checkbox", views.filterIsInstance<TextView>().any { it.text.contains("pendiente") })
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) {
            repeat(view.childCount) { yieldAll(descendants(view.getChildAt(it))) }
        }
    }
}
