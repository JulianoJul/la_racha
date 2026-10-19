package com.laracha.app.data

import com.laracha.app.domain.HabitScoreCalculator
import com.laracha.app.domain.LocalDay
import com.laracha.app.domain.StreakCalculator
import com.laracha.app.domain.HabitWeek
import com.laracha.app.domain.HabitWeekDay
import com.laracha.app.domain.HabitFrequency
import com.laracha.app.domain.FrequencyPeriod
import com.laracha.app.domain.HabitFrequencyRules
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class HabitToday(
    val habit: HabitEntity,
    val completedToday: Boolean,
    val scorePercent: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val week: List<HabitWeekDay> = emptyList(),
    val frequency: HabitFrequency = HabitFrequency.Daily,
    val daysAroundToday: List<HabitWeekDay> = emptyList(),
    val recentProgressDays: List<HabitWeekDay> = emptyList(),
)

data class HabitDetail(
    val habit: HabitEntity,
    val completedDays: Set<LocalDate>,
    val scorePercent: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val frequency: HabitFrequency = HabitFrequency.Daily,
    val frequencyPeriods: List<FrequencyPeriod> = emptyList(),
    val nextFrequency: FrequencyPeriod? = null,
)

class HabitRepository(
    private val dao: HabitDao,
    private val clock: Clock,
    private val zoneId: () -> ZoneId,
    private val afterMutation: suspend () -> Unit = {},
) {
    constructor(dao: HabitDao, clock: Clock, zoneId: ZoneId, afterMutation: suspend () -> Unit = {}) :
        this(dao, clock, { zoneId }, afterMutation)

    private suspend fun refreshWidgetsAfterCommit() = withContext(NonCancellable) { afterMutation() }

    fun today(): LocalDate = LocalDay.today(clock, zoneId())

    fun observeToday(forDate: LocalDate = today()): Flow<List<HabitToday>> =
        combine(dao.observeActiveHabits(), dao.observeCompletions()) { habits, completions -> habits to completions }
            .combine(dao.observeAllFrequencyPeriods()) { (habits, completions), periods ->
                calculateToday(habits, completions, periods, forDate)
        }.flowOn(Dispatchers.Default)

    /** Live day-key source for long-lived Glance sessions; updates on local date rollover. */
    fun observeCurrentToday(): Flow<List<HabitToday>> = combine(
        dao.observeActiveHabits(),
        dao.observeCompletions(),
        dao.observeAllFrequencyPeriods(),
        localDayFlow(),
    ) { habits, completions, periods, currentDay -> calculateToday(habits, completions, periods, currentDay) }
        .flowOn(Dispatchers.Default)

    private fun localDayFlow(): Flow<LocalDate> = flow {
        while (coroutineContext.isActive) {
            val currentZone = zoneId()
            val now = clock.instant()
            val localNow = now.atZone(currentZone)
            emit(localNow.toLocalDate())
            val nextDay = localNow.toLocalDate().plusDays(1).atStartOfDay(currentZone).toInstant()
            val untilMidnight = Duration.between(now, nextDay).toMillis().coerceAtLeast(1L)
            delay(untilMidnight)
        }
    }

    private fun calculateToday(
        habits: List<HabitEntity>,
        completions: List<CompletionEntity>,
        frequencyRows: List<HabitFrequencyPeriodEntity>,
        forDate: LocalDate,
    ): List<HabitToday> {
        val daysByHabit = completions.groupBy(CompletionEntity::habitId)
            .mapValues { (_, rows) -> rows.mapTo(mutableSetOf()) { LocalDate.parse(it.localDate) } }
        val periodsByHabit = frequencyRows.groupBy(HabitFrequencyPeriodEntity::habitId)
            .mapValues { (_, rows) -> rows.map(HabitFrequencyPeriodEntity::toDomain) }
        return habits.map { habit ->
            val completedDays = daysByHabit[habit.id].orEmpty()
            val createdOn = LocalDate.parse(habit.createdOn)
            val periods = periodsByHabit[habit.id].orEmpty()
            val frequency = periods.lastOrNull { !it.effectiveFrom.isAfter(forDate) }?.frequency
                ?: HabitFrequency.Daily
            val score = HabitScoreCalculator.calculate(createdOn, completedDays, forDate, periods)
            val streaks = StreakCalculator.calculate(createdOn, completedDays, forDate)
            HabitToday(
                habit = habit,
                completedToday = forDate in completedDays,
                scorePercent = HabitScoreCalculator.percentage(score),
                currentStreak = streaks.current,
                bestStreak = streaks.best,
                week = HabitWeek.days(createdOn, completedDays, forDate),
                daysAroundToday = HabitWeek.daysAroundToday(createdOn, completedDays, forDate),
                recentProgressDays = HabitWeek.recentProgressDays(createdOn, completedDays, forDate, frequency),
                frequency = frequency,
            )
        }
    }

    fun observeCompletionCountToday(forDate: LocalDate = today()): Flow<Pair<Int, Int>> =
        combine(dao.observeActiveHabits(), dao.observeCompletions()) { habits, completions ->
            val dateKey = forDate.toString()
            val activeIds = habits.mapTo(hashSetOf(), HabitEntity::id)
            val completed = completions.count { it.habitId in activeIds && it.localDate == dateKey }
            completed to habits.size
        }.flowOn(Dispatchers.Default)

    fun observeDetail(habitId: String, forDate: LocalDate = today()): Flow<HabitDetail?> =
        combine(dao.observeHabit(habitId), dao.observeCompletionsForHabit(habitId)) { habit, rows -> habit to rows }
            .combine(dao.observeFrequencyPeriods(habitId)) { (habit, rows), frequencyRows ->
            habit?.let {
                val completedDays = rows.mapTo(mutableSetOf()) { LocalDate.parse(it.localDate) }
                val createdOn = LocalDate.parse(it.createdOn)
                val periods = frequencyRows.map(HabitFrequencyPeriodEntity::toDomain)
                val score = HabitScoreCalculator.calculate(createdOn, completedDays, forDate, periods)
                val streaks = StreakCalculator.calculate(createdOn, completedDays, forDate)
                HabitDetail(
                    habit = it,
                    completedDays = completedDays,
                    scorePercent = HabitScoreCalculator.percentage(score),
                    currentStreak = streaks.current,
                    bestStreak = streaks.best,
                    frequency = periods.lastOrNull { period -> !period.effectiveFrom.isAfter(forDate) }?.frequency
                        ?: HabitFrequency.Daily,
                    frequencyPeriods = periods,
                    nextFrequency = periods.firstOrNull { period -> period.effectiveFrom.isAfter(forDate) },
                )
            }
        }.flowOn(Dispatchers.Default)

    suspend fun createHabit(
        name: String,
        emoji: String,
        color: String,
        frequency: HabitFrequency = HabitFrequency.Daily,
    ): String {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "El nombre no puede estar vacío" }
        val id = UUID.randomUUID().toString()
        dao.insertNewHabit(
            HabitEntity(
                id = id,
                name = trimmedName,
                emoji = emoji,
                color = color,
                createdOn = today().toString(),
            ).withFrequency(frequency),
        )
        refreshWidgetsAfterCommit()
        return id
    }

    suspend fun renameHabit(id: String, name: String, emoji: String, color: String) {
        val current = requireNotNull(dao.getHabit(id)) { "Hábito no encontrado" }
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "El nombre no puede estar vacío" }
        dao.updateHabit(current.copy(name = trimmedName, emoji = emoji, color = color))
        refreshWidgetsAfterCommit()
    }

    suspend fun updateFrequency(habitId: String, frequency: HabitFrequency): Boolean {
        val current = dao.getHabit(habitId) ?: return false
        val currentFrequency = current.frequency()
        if (currentFrequency == frequency) return false
        val effectiveFrom = HabitFrequencyRules.effectiveFrom(currentFrequency, frequency, today())
        val changed = dao.updateFrequency(habitId, frequency, effectiveFrom)
        if (changed) refreshWidgetsAfterCommit()
        return changed
    }

    suspend fun setCompleted(habitId: String, date: LocalDate = today(), completed: Boolean): Boolean {
        val habit = dao.getHabit(habitId) ?: return false
        val currentToday = today()
        if (date.isAfter(currentToday)) return false
        val changed = dao.setCompleted(habitId, date.toString(), completed)
        if (changed) refreshWidgetsAfterCommit()
        return changed
    }

    suspend fun setArchived(habitId: String, archived: Boolean): Boolean {
        val changed = dao.setArchived(habitId, if (archived) today().toString() else null)
        if (changed) refreshWidgetsAfterCommit()
        return changed
    }

    suspend fun deleteHabit(habitId: String) {
        dao.deleteHabit(habitId)
        refreshWidgetsAfterCommit()
    }

    suspend fun reorderHabits(orderedIds: List<String>) {
        dao.reorderHabits(orderedIds)
        refreshWidgetsAfterCommit()
    }

    fun observeHabits(): Flow<List<HabitEntity>> = dao.observeAllHabits()
}

private fun HabitEntity.withFrequency(frequency: HabitFrequency): HabitEntity = copy(
    frequencyType = frequency.typeCode(),
    frequencyDays = frequency.daysCode(),
    frequencyIntervalDays = (frequency as? HabitFrequency.EveryNDays)?.intervalDays ?: 1,
    frequencyReferenceDate = (frequency as? HabitFrequency.EveryNDays)?.referenceDate?.toString(),
    frequencyWeeklyTarget = (frequency as? HabitFrequency.WeeklyQuota)?.timesPerWeek ?: 1,
)
