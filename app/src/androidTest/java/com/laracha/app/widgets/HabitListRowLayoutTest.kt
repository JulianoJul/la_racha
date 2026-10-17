package com.laracha.app.widgets

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.CompoundButton
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
    fun compactRowKeepsCheckboxOutOfScrollbarGutter() = verifyRow(widthDp = 234, compact = true)

    @Test
    fun regularRowKeepsCheckboxOutOfScrollbarGutter() = verifyRow(widthDp = 276, compact = false)

    private fun verifyRow(widthDp: Int, compact: Boolean) = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val habit = HabitToday(
            habit = HabitEntity("layout-test-only", "Leer", "📖", "#437A54", "2026-10-17"),
            completedToday = false,
            scorePercent = 82,
            currentStreak = 4,
            bestStreak = 4,
        )
        val result = GlanceRemoteViews().compose(context, DpSize(widthDp.dp, 64.dp)) {
            GlanceTheme { HabitListRow(habit, compact) }
        }
        instrumentation.runOnMainSync {
            val density = context.resources.displayMetrics.density
            val host = FrameLayout(context)
            val content = result.remoteViews.apply(context, host)
            host.addView(content)
            val width = (widthDp * density).roundToInt()
            val height = (64 * density).roundToInt()
            host.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            host.layout(0, 0, width, height)

            val views = descendants(host).toList()
            val checkbox = views.single { it.contentDescription == "Leer, pendiente hoy. Completar" }
            val bounds = Rect(0, 0, checkbox.width, checkbox.height)
            host.offsetDescendantRectToMyCoords(checkbox, bounds)
            assertTrue("Reserve at least 24dp inside the row, not on the scroll container", width - bounds.right >= (24 * density).toInt() - 1)
            assertTrue("Checkbox keeps a 48dp touch target", checkbox.width >= (48 * density).toInt() - 1)
            // Glance applies semantics/size to a wrapper; the native toggle is inside it.
            val nativeToggle = descendants(checkbox).filterIsInstance<CompoundButton>().single()
            assertTrue("Checkbox remains a separate native toggle", nativeToggle.isClickable)

            val name = views.filterIsInstance<TextView>().single { it.text.toString() == "Leer" }
            val navigationTargets = generateSequence(name as View) { it.parent as? View }
                .filter { it.hasOnClickListeners() }.toList()
            val detailTarget = navigationTargets.first()
            assertNotSame("Detail action is not the checkbox action", checkbox, detailTarget)
            assertTrue("Unused row space has an app action separate from detail", navigationTargets.size == 2)
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
