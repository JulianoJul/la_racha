package com.laracha.app.calendar

import com.laracha.app.data.CalendarDao
import com.laracha.app.data.CalendarEventBindingEntity
import com.laracha.app.data.CalendarExportOperationEntity
import com.laracha.app.data.HabitCalendarScheduleEntity
import com.laracha.app.data.HabitDao
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Durable intent first, then provider side effects. A provider call is never made by completion actions. */
class CalendarExportCoordinator(
    private val habits: HabitDao,
    private val calendar: CalendarDao,
    private val gateway: CalendarGateway,
    private val enqueueRetry: () -> Unit = {},
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val drainMutex = Mutex()
    suspend fun requestPublication(
        habitId: String,
        calendarId: Long,
        accountName: String,
        startDate: LocalDate,
        time: LocalTime,
        durationMinutes: Int,
        zoneId: ZoneId,
        reminderMinutes: Int = -1,
    ) {
        require(durationMinutes in 1..1440) { "La duración debe estar entre 1 y 1440 minutos" }
        require(reminderMinutes in setOf(-1, 0, 10, 30)) { "Recordatorio no permitido" }
        val habit = requireNotNull(habits.getHabit(habitId)) { "Hábito no encontrado" }
        require(habit.archivedOn == null) { "No se puede exportar un hábito archivado" }
        require(startDate >= LocalDate.parse(habit.createdOn)) { "La fecha inicial no puede preceder a la creación del hábito" }
        val previous = calendar.getSchedule(habitId)
        val binding = calendar.getBinding(habitId)
        val revision = (previous?.revision ?: 0L) + 1
        val token = binding?.ownershipToken ?: UUID.randomUUID().toString()
        val schedule = HabitCalendarScheduleEntity(habitId, true, startDate.toString(), time.toString(), durationMinutes, zoneId.id, reminderMinutes, revision)
        val operation = CalendarExportOperationEntity(UUID.randomUUID().toString(), habitId, revision, "UPSERT", binding?.calendarId ?: calendarId, binding?.eventId, token, "PENDING", null, 0)
        val destinationBinding = binding ?: CalendarEventBindingEntity(habitId, calendarId, accountName, "com.google", null, token, 0, "PENDING", null)
        calendar.saveDesired(schedule, operation, destinationBinding)
        enqueueRetry()
        drain()
    }

    suspend fun requestDisable(habitId: String, deleteSeries: Boolean) {
        val previous = calendar.getSchedule(habitId)
        val binding = calendar.getBinding(habitId) ?: return
        val revision = (previous?.revision ?: binding.publishedRevision) + 1
        val schedule = previous?.copy(enabled = false, revision = revision)
            ?: HabitCalendarScheduleEntity(habitId, false, LocalDate.now(clock).toString(), "09:00", 30, clock.zone.id, -1, revision)
        val operation = CalendarExportOperationEntity(
            UUID.randomUUID().toString(), habitId, revision, if (deleteSeries) "DELETE" else "DETACH",
            binding.calendarId, binding.eventId, binding.ownershipToken, "PENDING", null, 0,
        )
        calendar.saveDesired(schedule, operation)
        enqueueRetry()
        drain()
    }

    /** Called only after an explicit user confirmation when the linked series was removed externally. */
    suspend fun requestRecreate(habitId: String, startDate: LocalDate, time: LocalTime, durationMinutes: Int, zoneId: ZoneId, reminderMinutes: Int = -1) {
        val binding = requireNotNull(calendar.getBinding(habitId)) { "No existe un vínculo que recrear" }
        when (gateway.reconcile(binding.calendarId, binding.ownershipToken, binding)) {
            is GatewayResult.Published -> Unit // Existing managed series is found; update it, do not duplicate.
            GatewayResult.Missing -> calendar.putBinding(binding.copy(eventId = null, ownershipToken = UUID.randomUUID().toString(), state = "PENDING"))
            GatewayResult.Ambiguous -> error("Hay varias series coincidentes; no se creará otra")
        }
        requestPublication(habitId, binding.calendarId, binding.accountName, startDate, time, durationMinutes, zoneId, reminderMinutes)
    }

    suspend fun retryPending() {
        calendar.unblockRecoverableFailures()
        drain()
    }

    private suspend fun drain() = drainMutex.withLock {
        for (operation in calendar.pendingOperations()) {
            val schedule = calendar.getSchedule(operation.habitId)
            // Old desired-state revisions are superseded; never allow a delayed publish to beat disable/edit.
            if (schedule != null && operation.revision != schedule.revision) {
                calendar.deleteOperation(operation.operationId)
                continue
            }
            try {
                if (operation.state == "IN_FLIGHT") {
                    val binding = calendar.getBinding(operation.habitId)
                    if (operation.kind == "DETACH") {
                        calendar.finish(operation.operationId, binding?.copy(state = "DETACHED"), "DONE")
                        continue
                    }
                    when (val reconciled = gateway.reconcile(requireNotNull(operation.calendarId), operation.ownershipToken, binding)) {
                        is GatewayResult.Published -> {
                            if (operation.kind == "DELETE") {
                                when (gateway.delete(requireNotNull(operation.calendarId), reconciled.series.eventId, operation.ownershipToken, binding)) {
                                    is GatewayResult.Published, GatewayResult.Missing -> calendar.finish(operation.operationId, binding?.copy(eventId = null, state = "DELETED"), "DONE")
                                    GatewayResult.Ambiguous -> calendar.markBlocked(operation.operationId, "Varias series tienen el marcador; requiere reconciliación manual")
                                }
                            } else if (operation.kind == "DETACH") {
                                calendar.finish(operation.operationId, binding?.copy(state = "DETACHED"), "DONE")
                            } else {
                                calendar.finish(operation.operationId, binding?.copy(
                                    eventId = reconciled.series.eventId,
                                    publishedRevision = operation.revision,
                                    state = "PUBLISHED_LOCAL",
                                    managedReminderMinutes = calendar.getSchedule(operation.habitId)?.reminderMinutes?.takeIf { it >= 0 },
                                ), "DONE")
                            }
                        }
                        GatewayResult.Missing -> if (operation.kind == "DELETE") {
                            calendar.finish(operation.operationId, binding?.copy(eventId = null, state = "DELETED"), "DONE")
                        } else if (operation.kind == "DETACH") {
                            calendar.finish(operation.operationId, binding?.copy(state = "DETACHED"), "DONE")
                        } else {
                            calendar.markBlocked(operation.operationId, "Resultado externo incierto; no se encontró el marcador. Confirma la reconciliación antes de crear otra serie.")
                        }
                        GatewayResult.Ambiguous -> calendar.markBlocked(operation.operationId, "Varias series tienen el marcador; requiere reconciliación manual")
                    }
                    continue
                }
                calendar.markInFlight(operation.operationId)
                when (operation.kind) {
                    "UPSERT" -> {
                        val habit = habits.getHabit(operation.habitId) ?: throw IllegalStateException("Hábito ya no disponible")
                        val desired = schedule?.takeIf { it.enabled }
                        if (desired == null) {
                            calendar.deleteOperation(operation.operationId)
                            continue
                        }
                        when (val result = gateway.publish(requireNotNull(operation.calendarId), habit.name, desired, operation.ownershipToken, calendar.getBinding(operation.habitId))) {
                            is GatewayResult.Published -> calendar.finish(operation.operationId, CalendarEventBindingEntity(
                                operation.habitId, requireNotNull(operation.calendarId),
                                calendar.getBinding(operation.habitId)?.accountName.orEmpty(),
                                calendar.getBinding(operation.habitId)?.accountType ?: "com.google",
                                result.series.eventId, operation.ownershipToken, operation.revision, "PUBLISHED_LOCAL", desired.reminderMinutes.takeIf { it >= 0 },
                            ), "DONE")
                        GatewayResult.Missing -> calendar.markBlocked(operation.operationId, "Evento no encontrado; requiere confirmación explícita para recrear")
                            GatewayResult.Ambiguous -> calendar.markBlocked(operation.operationId, "Hay varias series con el marcador; requiere reconciliación manual")
                        }
                    }
                    "DELETE" -> when (gateway.delete(requireNotNull(operation.calendarId), operation.eventId, operation.ownershipToken, calendar.getBinding(operation.habitId))) {
                        is GatewayResult.Published, GatewayResult.Missing -> calendar.finish(
                            operation.operationId,
                            calendar.getBinding(operation.habitId)?.copy(eventId = null, state = "DELETED"),
                            "DONE",
                        )
                        GatewayResult.Ambiguous -> calendar.markBlocked(operation.operationId, "Identidad de serie ambigua")
                    }
                    "DETACH" -> {
                        val binding = calendar.getBinding(operation.habitId)
                        calendar.finish(operation.operationId, binding?.copy(state = "DETACHED"), "DONE")
                    }
                }
            } catch (security: SecurityException) {
                calendar.markBlocked(operation.operationId, "Sin permiso de Calendar")
            } catch (unavailable: CalendarUnavailableException) {
                calendar.markBlocked(operation.operationId, unavailable.message ?: "Calendario no disponible")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                calendar.markBlocked(operation.operationId, "Resultado del proveedor incierto; no se reintentará a ciegas (${failure.message ?: "error"})")
            }
        }
        if (calendar.pendingOperations().isNotEmpty()) enqueueRetry()
    }
}
