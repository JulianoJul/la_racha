package com.laracha.app.widgets

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.ToggleableStateKey
import com.laracha.app.HabitApplication

internal val CompletionValueKey = ActionParameters.Key<Boolean>("completion_value")

internal fun singleWidgetCompletionTarget(completedToday: Boolean): Boolean = !completedToday

/** Native checkboxes supply their new state; icon buttons explicitly set the rendered target. */
internal fun requestedCompletion(parameters: ActionParameters): Boolean? =
    parameters[ToggleableStateKey] ?: parameters[CompletionValueKey]

class SetHabitCompletionAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val habitId = parameters[ActionParameters.Key<String>("habit_id")] ?: return
        val checked = requestedCompletion(parameters) ?: return
        val repository = (context.applicationContext as HabitApplication).habitRepository

        // Room owns the state change; no Activity is started by this action.
        repository.setCompleted(habitId, completed = checked)
    }
}
