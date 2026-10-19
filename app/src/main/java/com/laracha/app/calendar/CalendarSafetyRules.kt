package com.laracha.app.calendar

import com.laracha.app.data.CalendarEventBindingEntity
import com.laracha.app.data.HabitCalendarScheduleEntity

/** Provider reminder rows relevant to deciding which rows belong to this integration. */
internal data class ReminderRow(val id: Long, val minutes: Int, val method: Int)

internal data class ReminderChange(val deleteIds: List<Long>, val insertMinutes: Int?)

/** Avoid deleting and then mistaking the deleted managed reminder for one that still exists. */
internal fun reminderChange(
    existing: List<ReminderRow>,
    previouslyManagedMinutes: Int?,
    requestedMinutes: Int,
    alertMethod: Int,
): ReminderChange {
    val managed = existing.filter {
        previouslyManagedMinutes != null && it.minutes == previouslyManagedMinutes && it.method == alertMethod
    }
    if (requestedMinutes >= 0 && managed.any { it.minutes == requestedMinutes }) {
        return ReminderChange(emptyList(), null)
    }
    val externalMatch = existing.any {
        it !in managed && it.minutes == requestedMinutes && it.method == alertMethod
    }
    return ReminderChange(
        deleteIds = managed.map(ReminderRow::id),
        insertMinutes = requestedMinutes.takeIf { it >= 0 && !externalMatch },
    )
}

internal fun requireValidDuration(minutes: Int) {
    require(minutes in 1..1440) { "La duración debe estar entre 1 y 1440 minutos" }
}

internal fun recurrenceChangeNeedsConfirmation(
    previous: HabitCalendarScheduleEntity?,
    binding: CalendarEventBindingEntity?,
    plan: CalendarRecurrencePlan,
): Boolean = previous != null && binding?.eventId != null &&
    (previous.frequencyKey != plan.frequencyKey || previous.recurrenceRule != plan.rule)

/** A found marker only proves identity; reapply every owned field before committing a revision. */
internal suspend fun recoverPublication(
    gateway: CalendarGateway,
    calendarId: Long,
    habitName: String,
    schedule: HabitCalendarScheduleEntity,
    token: String,
    binding: CalendarEventBindingEntity?,
): GatewayResult = when (gateway.reconcile(calendarId, token, binding)) {
    is GatewayResult.Published -> gateway.publish(calendarId, habitName, schedule, token, binding)
    GatewayResult.Missing -> GatewayResult.Missing
    GatewayResult.Ambiguous -> GatewayResult.Ambiguous
}
