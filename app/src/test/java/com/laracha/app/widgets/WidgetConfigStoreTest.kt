package com.laracha.app.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WidgetConfigStoreTest {
    @Test
    fun configurationKeysAreStableAndIndependentPerWidgetInstance() {
        assertEquals("single_habit_17", singleHabitPreferenceKeyName(17))
        assertNotEquals(singleHabitPreferenceKeyName(17), singleHabitPreferenceKeyName(18))
        assertEquals("list_mode_17", listModePreferenceKeyName(17))
        assertEquals("list_selected_ids_17", selectedIdsPreferenceKeyName(17))
        assertNotEquals(listModePreferenceKeyName(17), listModePreferenceKeyName(18))
    }

    @Test
    fun selectedHabitIdsKeepOrderAndRemoveDuplicates() {
        val ids = listOf("habit-c", "habit-a", "habit-c", "habit-b")

        assertEquals(listOf("habit-c", "habit-a", "habit-b"), decodeHabitIds(encodeHabitIds(ids)))
    }

    @Test
    fun storedSelectionIsCappedAtFiveIds() {
        val ids = (1..7).map { "habit-$it" }

        assertEquals(ids.take(5), decodeHabitIds(encodeHabitIds(ids)))
    }
}
