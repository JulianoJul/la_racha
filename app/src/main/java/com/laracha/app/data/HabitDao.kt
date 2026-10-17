package com.laracha.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

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

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertHabit(habit: HabitEntity)

    @Update
    abstract suspend fun updateHabit(habit: HabitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putCompletion(completion: CompletionEntity)

    @Query("DELETE FROM completions WHERE habitId = :habitId AND localDate = :localDate")
    abstract suspend fun deleteCompletion(habitId: String, localDate: String)

    @Query("DELETE FROM habits WHERE id = :habitId")
    abstract suspend fun deleteHabit(habitId: String)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM habits WHERE archivedOn IS NULL")
    abstract suspend fun nextSortOrder(): Int

    @Transaction
    open suspend fun insertNewHabit(habit: HabitEntity) {
        insertHabit(habit.copy(sortOrder = nextSortOrder()))
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
