package com.laracha.app.data

import com.laracha.app.domain.HabitScoreCalculator
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitRepositoryTest {
    private val today = LocalDate.of(2026, 10, 16)
    private val clock = Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun repeatedSetCompletedIsIdempotentAndUncheckRemovesDay() = runBlocking {
        val dao = FakeHabitDao()
        dao.insertHabit(habit(createdOn = today.minusDays(1)))

        assertTrue(dao.setCompleted("habit", today.toString(), true))
        assertTrue(dao.setCompleted("habit", today.toString(), true))
        assertEquals(1, dao.completions.value.size)

        assertTrue(dao.setCompleted("habit", today.toString(), false))
        assertTrue(dao.completions.value.isEmpty())
    }

    @Test
    fun archivedOrMissingHabitCannotBeCompleted() = runBlocking {
        val dao = FakeHabitDao()
        dao.insertHabit(habit(archivedOn = today.toString()))

        assertFalse(dao.setCompleted("habit", today.toString(), true))
        assertFalse(dao.setCompleted("missing", today.toString(), true))
        assertTrue(dao.completions.value.isEmpty())
    }

    @Test
    fun repositoryDerivesScoreAndStreakFromRoomRows() = runBlocking {
        val dao = FakeHabitDao()
        dao.insertHabit(habit(createdOn = today.minusDays(1)))
        dao.putCompletion(CompletionEntity("habit", today.minusDays(1).toString()))
        dao.putCompletion(CompletionEntity("habit", today.toString()))
        val repository = HabitRepository(dao, clock, ZoneOffset.UTC)

        val result = repository.observeToday().first().single()

        assertTrue(result.completedToday)
        assertEquals(
            HabitScoreCalculator.percentage(
                HabitScoreCalculator.calculate(
                    today.minusDays(1),
                    setOf(today.minusDays(1), today),
                    today,
                ),
            ),
            result.scorePercent,
        )
        assertEquals(2, result.currentStreak)
        assertEquals(2, result.bestStreak)
    }

    @Test
    fun creatingHabitTrimsNameAndAssignsNextOrder() = runBlocking {
        val dao = FakeHabitDao()
        dao.insertHabit(habit(id = "first", sortOrder = 0))
        val repository = HabitRepository(dao, clock, ZoneOffset.UTC)

        val id = repository.createHabit("  Leer  ", "📖", "#437A54")
        val created = dao.getHabit(id)

        assertEquals("Leer", created?.name)
        assertEquals(1, created?.sortOrder)
        assertEquals(today.toString(), created?.createdOn)
    }

    @Test
    fun correctingHistoricalCompletionRecomputesScoreAndStreak() = runBlocking {
        val dao = FakeHabitDao()
        dao.insertHabit(habit(createdOn = today.minusDays(3)))
        listOf(today.minusDays(3), today.minusDays(2), today.minusDays(1), today).forEach {
            dao.putCompletion(CompletionEntity("habit", it.toString()))
        }
        val repository = HabitRepository(dao, clock, ZoneOffset.UTC)

        val before = repository.observeDetail("habit", today).first()!!
        repository.setCompleted("habit", today.minusDays(2), false)
        val after = repository.observeDetail("habit", today).first()!!

        assertEquals(4, before.currentStreak)
        assertEquals(2, after.currentStreak)
        assertTrue(after.scorePercent < before.scorePercent)
    }

    @Test
    fun widgetWeekReflectsHistoryEditsWithoutPersistingDerivedCounters() = runBlocking {
        val dao = FakeHabitDao()
        dao.insertHabit(habit(createdOn = today.minusDays(3)))
        val yesterday = today.minusDays(1)
        dao.putCompletion(CompletionEntity("habit", yesterday.toString()))
        val repository = HabitRepository(dao, clock, ZoneOffset.UTC)

        val before = repository.observeToday().first().single().week
        assertTrue(before.single { it.date == yesterday }.completed)
        assertFalse(before.single { it.date == yesterday }.isToday)
        assertFalse(before.single { it.isToday }.completed)

        repository.setCompleted("habit", yesterday, false)
        repository.setCompleted("habit", today, true)

        val after = repository.observeToday().first().single().week
        assertFalse(after.single { it.date == yesterday }.completed)
        assertTrue(after.single { it.isToday }.completed)
    }

    @Test
    fun repositoryRejectsCompletionBeforeCreationAndAfterToday() = runBlocking {
        val dao = FakeHabitDao()
        dao.insertHabit(habit(createdOn = today.minusDays(1)))
        val repository = HabitRepository(dao, clock, ZoneOffset.UTC)

        assertFalse(repository.setCompleted("habit", today.minusDays(2), true))
        assertFalse(repository.setCompleted("habit", today.plusDays(1), true))
        assertTrue(dao.completions.value.isEmpty())
    }

    @Test
    fun reorderPersistsNewActiveHabitOrder() = runBlocking {
        val dao = FakeHabitDao()
        dao.insertHabit(habit(id = "first", sortOrder = 0))
        dao.insertHabit(habit(id = "second", sortOrder = 1))
        dao.insertHabit(habit(id = "third", sortOrder = 2))
        val repository = HabitRepository(dao, clock, ZoneOffset.UTC)

        repository.reorderHabits(listOf("third", "first", "second"))

        assertEquals(0, dao.getHabit("third")?.sortOrder)
        assertEquals(1, dao.getHabit("first")?.sortOrder)
        assertEquals(2, dao.getHabit("second")?.sortOrder)
    }

    @Test
    fun restoringArchivedHabitPlacesItAtTheEnd() = runBlocking {
        val dao = FakeHabitDao()
        dao.insertHabit(habit(id = "first", sortOrder = 0))
        dao.insertHabit(habit(id = "archived", archivedOn = today.minusDays(1).toString(), sortOrder = 1))
        dao.insertHabit(habit(id = "last", sortOrder = 1))
        val repository = HabitRepository(dao, clock, ZoneOffset.UTC)

        repository.setArchived("archived", false)

        assertEquals(
            listOf("first", "last", "archived"),
            dao.observeActiveHabits().first().sortedBy(HabitEntity::sortOrder).map(HabitEntity::id),
        )
        assertEquals(2, dao.getHabit("archived")?.sortOrder)
    }

    @Test(expected = IllegalArgumentException::class)
    fun reorderRejectsPartialOrder() = runBlocking {
        val dao = FakeHabitDao()
        dao.insertHabit(habit(id = "first", sortOrder = 0))
        dao.insertHabit(habit(id = "second", sortOrder = 1))
        HabitRepository(dao, clock, ZoneOffset.UTC).reorderHabits(listOf("first"))
    }

    private fun habit(
        id: String = "habit",
        createdOn: LocalDate = today,
        archivedOn: String? = null,
        sortOrder: Int = 0,
    ) = HabitEntity(
        id = id,
        name = "Leer",
        emoji = "📖",
        color = "#437A54",
        createdOn = createdOn.toString(),
        archivedOn = archivedOn,
        sortOrder = sortOrder,
    )
}

private class FakeHabitDao : HabitDao() {
    private val habits = MutableStateFlow<List<HabitEntity>>(emptyList())
    val completions = MutableStateFlow<List<CompletionEntity>>(emptyList())

    override fun observeActiveHabits() = habits.map { all -> all.filter { it.archivedOn == null } }

    override fun observeAllHabits() = habits

    override suspend fun getHabit(habitId: String) = habits.value.firstOrNull { it.id == habitId }

    override fun observeHabit(habitId: String) = habits.map { all -> all.firstOrNull { it.id == habitId } }

    override fun observeCompletions() = completions

    override suspend fun getCompletions(habitId: String) =
        completions.value.filter { it.habitId == habitId }.sortedBy(CompletionEntity::localDate)

    override fun observeCompletionsForHabit(habitId: String) =
        completions.map { rows -> rows.filter { it.habitId == habitId }.sortedBy(CompletionEntity::localDate) }

    override suspend fun insertHabit(habit: HabitEntity) {
        habits.value = habits.value + habit
    }

    override suspend fun updateHabit(habit: HabitEntity) {
        habits.value = habits.value.map { if (it.id == habit.id) habit else it }
    }

    override suspend fun putCompletion(completion: CompletionEntity) {
        completions.value = (completions.value.filterNot {
            it.habitId == completion.habitId && it.localDate == completion.localDate
        } + completion)
    }

    override suspend fun deleteCompletion(habitId: String, localDate: String) {
        completions.value = completions.value.filterNot {
            it.habitId == habitId && it.localDate == localDate
        }
    }

    override suspend fun deleteHabit(habitId: String) {
        habits.value = habits.value.filterNot { it.id == habitId }
        completions.value = completions.value.filterNot { it.habitId == habitId }
    }

    override suspend fun nextSortOrder() =
        (habits.value.filter { it.archivedOn == null }.maxOfOrNull(HabitEntity::sortOrder) ?: -1) + 1
}
