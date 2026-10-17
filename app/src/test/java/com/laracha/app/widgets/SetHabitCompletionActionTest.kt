package com.laracha.app.widgets

import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ToggleableStateKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SetHabitCompletionActionTest {
    @Test
    fun iconButtonCanExplicitlyComplete() {
        val parameters = actionParametersOf(CompletionValueKey to true)

        assertEquals(true, requestedCompletion(parameters))
    }

    @Test
    fun iconButtonCanExplicitlyUncheck() {
        val parameters = actionParametersOf(CompletionValueKey to false)

        assertEquals(false, requestedCompletion(parameters))
    }

    @Test
    fun repeatedIconActionKeepsTheSameTargetRatherThanToggling() {
        val parameters = actionParametersOf(CompletionValueKey to true)

        assertEquals(true, requestedCompletion(parameters))
        assertEquals(true, requestedCompletion(parameters))
    }

    @Test
    fun listCheckboxKeepsNativeToggleStateAsTheAuthority() {
        val parameters = actionParametersOf(ToggleableStateKey to false, CompletionValueKey to true)

        assertEquals(false, requestedCompletion(parameters))
    }

    @Test
    fun missingStateDoesNotGuessACompletion() {
        assertNull(requestedCompletion(actionParametersOf()))
    }
}
