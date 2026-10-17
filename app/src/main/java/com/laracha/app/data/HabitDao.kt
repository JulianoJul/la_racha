package com.laracha.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import com.laracha.app.domain.HabitFrequency
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Dao
abstract class HabitDao {
    @Query("SELECT * FROM habits WHERE archivedOn IS NULL ORDER BY sortOrder, createdOn, id")
    abstract fun observeActiveHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits ORDER BY sortOrder, createdOn, id")
    abstract fun observeAllHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE id = :habitId LIMIT 1")
    abstract suspend fun getHabit(habitId: String): HabitEntity?

    @Query("SELECT * FROM habits WHERE id = :habitId LIMIT 1")
    abstract fun observeHabit(habitId: String): Flow<HabitEntity?>

    @Query("SELECT * FROM completions ORDER BY localDate")
    abstract fun observeCompletions(): Flow<List<CompletionEntity>>

    @Query("SELECT * FROM completions WHERE habitId = :habitId ORDER BY localDate")
    abstract suspend fun getCompletions(habitId: String): List<CompletionEntity>

    @Query("SELECT * FROM completions WHERE habitId = :habitId ORDER BY localDate")
    abstract fun observeCompletionsForHabit(habitId: String): Flow<List<CompletionEntity>>

    @Query("SELECT * FROM habit_frequency_periods WHERE habitId = :habitId ORDER BY effectiveFrom")
    abstract fun observeFrequencyPeriods(habitId: String): Flow<List<HabitFrequencyPeriodEntity>>

    @Query("SELECT * FROM habit_frequency_periods WHERE habitId = :habitId ORDER BY effectiveFrom")
    abstract suspend fun getFrequencyPeriods(habitId: String): List<HabitFrequencyPeriodEntity>

    @Query("SELECT * FROM habit_frequency_periods ORDER BY habitId, effectiveFrom")
    abstract fun observeAllFrequencyPeriods(): Flow<List<HabitFrequencyPeriodEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertHabit(habit: HabitEntity)

    @Update
    abstract suspend fun updateHabit(habit: HabitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putCompletion(completion: CompletionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertFrequencyPeriod(period: HabitFrequencyPeriodEntity)

    @Query("DELETE FROM habit_frequency_periods WHERE habitId = :habitId AND effectiveFrom >= :effectiveFrom")
    abstract suspend fun deleteFrequencyPeriodsFrom(habitId: String, effectiveFrom: String)

    @Query("DELETE FROM completions WHERE habitId = :habitId AND localDate = :localDate")
    abstract suspend fun deleteCompletion(habitId: String, localDate: String)

    @Query("DELETE FROM habits WHERE id = :habitId")
    abstract suspend fun deleteHabit(habitId: String)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM habits WHERE archivedOn IS NULL")
    abstract suspend fun nextSortOrder(): Int

    @Transaction
    open suspend fun insertNewHabit(habit: HabitEntity) {
        val created = habit.copy(sortOrder = nextSortOrder())
        insertHabit(created)
        val createdOn = LocalDate.parse(created.createdOn)
        if (created.frequencyType == "WEEKLY_QUOTA") {
            insertFrequencyPeriod(HabitFrequencyPeriodEntity.from(created.id, createdOn, HabitFrequency.Daily))
            insertFrequencyPeriod(
                HabitFrequencyPeriodEntity.from(
                    created.id,
                    createdOn.with(TemporalAdjusters.next(DayOfWeek.MONDAY)),
                    created.frequency(),
                ),
            )
        } else {
            insertFrequencyPeriod(HabitFrequencyPeriodEntity.from(created.id, createdOn, created.frequency()))
        }
    }

    @Transaction
    open suspend fun updateFrequency(
        habitId: String,
        frequency: HabitFrequency,
        effectiveFrom: LocalDate,
    ): Boolean {
        val current = getHabit(habitId) ?: return false
        val updated = current.copy(
            frequencyType = frequency.typeCode(),
            frequencyDays = frequency.daysCode(),
            frequencyIntervalDays = (frequency as? HabitFrequency.EveryNDays)?.intervalDays ?: 1,
            frequencyReferenceDate = (frequency as? HabitFrequency.EveryNDays)?.referenceDate?.toString(),
            frequencyWeeklyTarget = (frequency as? HabitFrequency.WeeklyQuota)?.timesPerWeek ?: 1,
        )
        updateHabit(updated)
        deleteFrequencyPeriodsFrom(habitId, effectiveFrom.toString())
        insertFrequencyPeriod(HabitFrequencyPeriodEntity.from(habitId, effectiveFrom, frequency))
        return true
    }

    /** Setting a value is idempotent; repeated taps cannot create duplicate days. */
    @Transaction
    open suspend fun setCompleted(habitId: String, localDate: String, completed: Boolean): Boolean {
        val habit = getHabit(habitId) ?: return false
        if (habit.archivedOn != null) return false
        if (completed) {
            putCompletion(CompletionEntity(habitId, localDate))
        } else {
            deleteCompletion(habitId, localDate)
        }
        return true
    }

    @Transaction
    open suspend fun setArchived(habitId: String, archivedOn: String?): Boolean {
        val habit = getHabit(habitId) ?: return false
        val restoring = habit.archivedOn != null && archivedOn == null
        updateHabit(habit.copy(archivedOn = archivedOn, sortOrder = if (restoring) nextSortOrder() else habit.sortOrder))
        return true
    }

    @Transaction
    open suspend fun reorderHabits(orderedIds: List<String>) {
        require(orderedIds.distinct().size == orderedIds.size) { "La lista de orden contiene IDs duplicados" }
        val currentIds = observeActiveHabits().first().map(HabitEntity::id)
        require(orderedIds.size == currentIds.size && orderedIds.toSet() == currentIds.toSet()) {
            "El orden debe incluir una sola vez todos los hábitos activos"
        }
        orderedIds.forEachIndexed { index, id ->
            val habit = requireNotNull(getHabit(id))
            check(habit.archivedOn == null) { "No se puede ordenar un hábito archivado" }
            updateHabit(habit.copy(sortOrder = index))
        }
    }
}
