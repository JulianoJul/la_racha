package com.laracha.app.data

import com.laracha.app.domain.HabitScoreCalculator
import com.laracha.app.domain.HabitFrequency
import com.laracha.app.domain.FrequencyPeriod
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
        assertEquals("DAILY", created?.frequencyType)
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
    fun widgetCarouselReadsPreviousWeekCompletionsAndUpdatesAfterEdits() = runBlocking {
        val monday = LocalDate.of(2026, 10, 19)
        val dao = FakeHabitDao()
        dao.insertHabit(habit(createdOn = monday))
        dao.putCompletion(CompletionEntity("habit", monday.minusDays(1).toString()))
        val mondayClock = Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"), ZoneOffset.UTC)
        val repository = HabitRepository(dao, mondayClock, ZoneOffset.UTC)

        val before = repository.observeToday().first().single().daysAroundToday
        assertEquals(monday, before[3].date)
        assertTrue(before[2].completed)
        assertTrue(before[2].available)
        assertTrue(before.drop(4).none { it.completed || it.available })

        repository.setCompleted("habit", monday.minusDays(1), false)
        val after = repository.observeToday().first().single().daysAroundToday
        assertFalse(after[2].completed)
        assertFalse(after[2].available)
        assertEquals(before.map { it.date }, after.map { it.date })
    }

    @Test
    fun repositoryAllowsCompletionBeforeCreationButRejectsFuture() = runBlocking {
        val dao = FakeHabitDao()
        dao.insertHabit(habit(createdOn = today.minusDays(1)))
        val repository = HabitRepository(dao, clock, ZoneOffset.UTC)

        assertTrue(repository.setCompleted("habit", today.minusDays(2), true))
        assertFalse(repository.setCompleted("habit", today.plusDays(1), true))
        assertEquals(listOf(today.minusDays(2).toString()), dao.completions.value.map(CompletionEntity::localDate))
    }

    @Test
    fun recentDailyProgressReactsToTodayAndHistoricalEdits() = runBlocking {
        val dao = FakeHabitDao()
        dao.insertHabit(habit(createdOn = today.minusWeeks(1)))
        listOf(today.minusDays(2), today.minusDays(1), today).forEach {
            dao.putCompletion(CompletionEntity("habit", it.toString()))
        }
        val repository = HabitRepository(dao, clock, ZoneOffset.UTC)
        val before = repository.observeToday().first().single()
        assertEquals(listOf(today.minusDays(2), today.minusDays(1), today), before.recentProgressDays.map { it.date })

        repository.setCompleted("habit", today, false)
        val pending = repository.observeToday().first().single()
        assertEquals(before.recentProgressDays.dropLast(1), pending.recentProgressDays.dropLast(1))
        assertFalse(pending.recentProgressDays.last().completed)

        repository.setCompleted("habit", today.minusDays(1), false)
        val tolerated = repository.observeToday().first().single()
        assertEquals(listOf(today.minusDays(2), today), tolerated.recentProgressDays.map { it.date })
        assertEquals(0, tolerated.currentStreak)
        val broken = repository.observeToday(today.plusDays(1)).first().single()
        assertEquals(listOf(today.plusDays(1)), broken.recentProgressDays.map { it.date })
        assertEquals(setOf(today.minusDays(2).toString()), dao.completions.value.map { it.localDate }.toSet())
    }

    @Test
    fun recentProgressUsesEffectiveFrequencyRatherThanPendingChange() = runBlocking {
        val dao = FakeHabitDao()
        dao.insertHabit(habit(createdOn = today.minusWeeks(1)))
        dao.putCompletion(CompletionEntity("habit", today.minusDays(3).toString()))
        val repository = HabitRepository(dao, clock, ZoneOffset.UTC)
        val frequency = HabitFrequency.FixedDays(setOf(java.time.DayOfWeek.MONDAY))
        repository.updateFrequency("habit", frequency)

        val current = repository.observeToday().first().single()
        assertEquals(HabitFrequency.Daily, current.frequency)
        assertEquals(listOf(today), current.recentProgressDays.map { it.date })

        val nextDay = repository.observeToday(today.plusDays(1)).first().single()
        assertEquals(frequency, nextDay.frequency)
        assertEquals(listOf(today.minusDays(3), today.plusDays(1)), nextDay.recentProgressDays.map { it.date })
        assertEquals(0, nextDay.currentStreak) // The visual strip is not a new streak calculation.
    }

    @Test
    fun removingOldestHistoricalCompletionRecomputesDetailFromNextCompletion() = runBlocking {
        val createdOn = today.minusDays(1)
        val earliest = today.minusDays(3)
        val next = today.minusDays(2)
        val dao = FakeHabitDao()
        dao.insertHabit(habit(createdOn = createdOn))
        listOf(earliest, next, createdOn).forEach { date ->
            dao.putCompletion(CompletionEntity("habit", date.toString()))
        }
        val repository = HabitRepository(dao, clock, ZoneOffset.UTC)

        val before = repository.observeDetail("habit", today).first()!!
        assertTrue(repository.setCompleted("habit", earliest, false))
        val after = repository.observeDetail("habit", today).first()!!

        assertEquals(3, before.bestStreak)
        assertEquals(2, after.bestStreak)
        assertEquals(
            HabitScoreCalculator.percentage(
                HabitScoreCalculator.calculate(next, setOf(next, createdOn), today),
            ),
            after.scorePercent,
        )
    }

    @Test
    fun changingFrequencyKeepsPriorScoreAndPersistsEffectivePeriod() = runBlocking {
        val createdOn = today.minusDays(8)
        val dao = FakeHabitDao()
        dao.insertHabit(habit(createdOn = createdOn))
        (0L..7L).forEach { offset ->
            dao.putCompletion(CompletionEntity("habit", createdOn.plusDays(offset).toString()))
        }
        val repository = HabitRepository(dao, clock, ZoneOffset.UTC)
        val before = repository.observeDetail("habit", today).first()!!
        val updatedFrequency = HabitFrequency.FixedDays(setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.FRIDAY))

        repository.updateFrequency("habit", updatedFrequency)
        val after = repository.observeDetail("habit", today).first()!!

        assertEquals(before.scorePercent, after.scorePercent)
        assertEquals(HabitFrequency.Daily, after.frequency)
        assertEquals(updatedFrequency, after.nextFrequency?.frequency)
        assertEquals(today.plusDays(1), after.frequencyPeriods.last().effectiveFrom)
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
    private val frequencies = MutableStateFlow<List<HabitFrequencyPeriodEntity>>(emptyList())

    override fun observeActiveHabits() = habits.map { all -> all.filter { it.archivedOn == null } }

    override fun observeAllHabits() = habits

    override suspend fun getHabit(habitId: String) = habits.value.firstOrNull { it.id == habitId }

    override fun observeHabit(habitId: String) = habits.map { all -> all.firstOrNull { it.id == habitId } }

    override fun observeCompletions() = completions

    override suspend fun getCompletions(habitId: String) =
        completions.value.filter { it.habitId == habitId }.sortedBy(CompletionEntity::localDate)

    override fun observeCompletionsForHabit(habitId: String) =
        completions.map { rows -> rows.filter { it.habitId == habitId }.sortedBy(CompletionEntity::localDate) }

    override fun observeFrequencyPeriods(habitId: String) =
        frequencies.map { rows -> rows.filter { it.habitId == habitId }.sortedBy(HabitFrequencyPeriodEntity::effectiveFrom) }

    override fun observeAllFrequencyPeriods() = frequencies

    override suspend fun insertHabit(habit: HabitEntity) {
        habits.value = habits.value + habit
        frequencies.value = frequencies.value + HabitFrequencyPeriodEntity.from(habit.id, LocalDate.parse(habit.createdOn), HabitFrequency.Daily)
    }

    override suspend fun updateHabit(habit: HabitEntity) {
        habits.value = habits.value.map { if (it.id == habit.id) habit else it }
    }

    override suspend fun getFrequencyPeriods(habitId: String) =
        frequencies.value.filter { it.habitId == habitId }.sortedBy(HabitFrequencyPeriodEntity::effectiveFrom)

    override suspend fun deleteFrequencyPeriodsFrom(habitId: String, effectiveFrom: String) {
        frequencies.value = frequencies.value.filterNot {
            it.habitId == habitId && it.effectiveFrom >= effectiveFrom
        }
    }

    override suspend fun insertFrequencyPeriod(period: HabitFrequencyPeriodEntity) {
        frequencies.value = frequencies.value.filterNot {
            it.habitId == period.habitId && it.effectiveFrom == period.effectiveFrom
        } + period
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
