package com.laracha.app.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SingleWidgetCarouselLayoutTest {
    @Test
    fun twoByTwoShowsTwoNeighborsOnEachSideOfLargeToday() {
        val layout = singleWidgetCarouselLayout(contentWidthDp = 164f, compact = false)

        assertEquals(5, layout.visibleDayCount)
        assertTrue(layout.todayCircleDp > layout.neighborCircleDp)
        assertTrue(layout.todayCircleDp >= 48f)
        assertTrue(layout.neighborCircleDp >= 20f)
    }

    @Test
    fun narrowLayoutKeepsFiveDatesAndTodaysTouchTargetWithinWidth() {
        val layout = singleWidgetCarouselLayout(contentWidthDp = 102f, compact = true)

        assertEquals(5, layout.visibleDayCount)
        assertTrue(layout.todayCellDp >= 48f)
        assertTrue(layout.todayCircleDp > layout.neighborCircleDp)
        assertTrue(layout.todayCellDp + 4 * (layout.neighborCircleDp + 4f) <= 102f)
    }

    @Test
    fun widerLayoutEnlargesNeighborsWithoutChangingTodayOrDateCount() {
        val normal = singleWidgetCarouselLayout(contentWidthDp = 164f, compact = false)
        val wide = singleWidgetCarouselLayout(contentWidthDp = 226f, compact = false)

        assertEquals(5, wide.visibleDayCount)
        assertEquals(normal.todayCircleDp, wide.todayCircleDp, 0f)
        assertTrue(wide.neighborCircleDp > normal.neighborCircleDp)
    }
}
