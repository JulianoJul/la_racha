package com.laracha.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CalendarDao {
    @Query("SELECT * FROM habit_calendar_schedules ORDER BY habitId")
    abstract fun observeSchedules(): Flow<List<HabitCalendarScheduleEntity>>

    @Query("SELECT * FROM habit_calendar_schedules WHERE habitId = :habitId LIMIT 1")
    abstract suspend fun getSchedule(habitId: String): HabitCalendarScheduleEntity?

    @Query("SELECT * FROM calendar_event_bindings ORDER BY habitId")
    abstract fun observeBindings(): Flow<List<CalendarEventBindingEntity>>

    @Query("SELECT * FROM calendar_event_bindings WHERE habitId = :habitId LIMIT 1")
    abstract suspend fun getBinding(habitId: String): CalendarEventBindingEntity?

    @Query("SELECT * FROM calendar_export_operations WHERE state IN ('PENDING','RETRY','IN_FLIGHT') ORDER BY revision, operationId")
    abstract suspend fun pendingOperations(): List<CalendarExportOperationEntity>

    @Query("SELECT * FROM calendar_export_operations WHERE operationId = :id LIMIT 1")
    abstract suspend fun getOperation(id: String): CalendarExportOperationEntity?

    @Query("SELECT * FROM calendar_export_operations WHERE state = 'BLOCKED' ORDER BY revision, operationId")
    abstract fun observeBlockedOperations(): Flow<List<CalendarExportOperationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putSchedule(schedule: HabitCalendarScheduleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putBinding(binding: CalendarEventBindingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putOperation(operation: CalendarExportOperationEntity)

    @Query("DELETE FROM calendar_export_operations WHERE operationId = :id")
    abstract suspend fun deleteOperation(id: String)

    @Query("DELETE FROM calendar_export_operations WHERE habitId = :habitId AND operationId != :currentId")
    abstract suspend fun removeSupersededOperations(habitId: String, currentId: String)

    @Transaction
    open suspend fun saveDesired(schedule: HabitCalendarScheduleEntity, operation: CalendarExportOperationEntity, binding: CalendarEventBindingEntity? = null) {
        putSchedule(schedule)
        if (binding != null) putBinding(binding)
        putOperation(operation)
        removeSupersededOperations(operation.habitId, operation.operationId)
    }

    @Transaction
    open suspend fun finish(operationId: String, binding: CalendarEventBindingEntity?, terminalState: String) {
        val operation = getOperation(operationId) ?: return
        if (getSchedule(operation.habitId)?.revision != operation.revision) {
            deleteOperation(operationId)
            return
        }
        if (binding != null) putBinding(binding)
        if (terminalState == "DONE") deleteOperation(operationId)
        else putOperation(operation.copy(state = terminalState))
    }

    @Query("UPDATE calendar_export_operations SET state = 'RETRY', lastError = :error, attempts = attempts + 1 WHERE operationId = :id")
    abstract suspend fun markRetry(id: String, error: String)

    @Query("UPDATE calendar_export_operations SET state = 'BLOCKED', lastError = :error WHERE operationId = :id")
    abstract suspend fun markBlocked(id: String, error: String)

    @Query("UPDATE calendar_export_operations SET state = 'IN_FLIGHT' WHERE operationId = :id")
    abstract suspend fun markInFlight(id: String)

    @Query("UPDATE calendar_export_operations SET state = 'RETRY', lastError = NULL WHERE state = 'BLOCKED' AND (lastError LIKE 'Sin permiso%' OR lastError LIKE 'El calendario Google seleccionado%')")
    abstract suspend fun unblockRecoverableFailures()
}
