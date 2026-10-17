package com.laracha.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import java.time.DayOfWeek
import java.time.LocalDate
import com.laracha.app.domain.FrequencyPeriod
import com.laracha.app.domain.HabitFrequency

@Entity(
    tableName = "habit_frequency_periods",
    primaryKeys = ["habitId", "effectiveFrom"],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class HabitFrequencyPeriodEntity(
    val habitId: String,
    val effectiveFrom: String,
    val frequencyType: String,
    val frequencyDays: String,
    val frequencyIntervalDays: Int,
    val frequencyReferenceDate: String?,
    val frequencyWeeklyTarget: Int,
) {
    fun toDomain(): FrequencyPeriod = FrequencyPeriod(
        effectiveFrom = LocalDate.parse(effectiveFrom),
        frequency = frequencyFromColumns(
            frequencyType,
            frequencyDays,
            frequencyIntervalDays,
            frequencyReferenceDate,
            frequencyWeeklyTarget,
        ),
    )

    companion object {
        fun from(habitId: String, effectiveFrom: LocalDate, frequency: HabitFrequency) =
            HabitFrequencyPeriodEntity(
                habitId = habitId,
                effectiveFrom = effectiveFrom.toString(),
                frequencyType = frequency.typeCode(),
                frequencyDays = frequency.daysCode(),
                frequencyIntervalDays = (frequency as? HabitFrequency.EveryNDays)?.intervalDays ?: 1,
                frequencyReferenceDate = (frequency as? HabitFrequency.EveryNDays)?.referenceDate?.toString(),
                frequencyWeeklyTarget = (frequency as? HabitFrequency.WeeklyQuota)?.timesPerWeek ?: 1,
            )
    }
}

fun HabitEntity.frequency(): HabitFrequency = frequencyFromColumns(
    frequencyType,
    frequencyDays,
    frequencyIntervalDays,
    frequencyReferenceDate,
    frequencyWeeklyTarget,
)

private fun frequencyFromColumns(
    type: String,
    days: String,
    intervalDays: Int,
    referenceDate: String?,
    weeklyTarget: Int,
): HabitFrequency = when (type) {
    "FIXED_DAYS" -> HabitFrequency.FixedDays(
        days.split(',').mapNotNull { value -> value.toIntOrNull()?.let { DayOfWeek.of(it) } }.toSet(),
    )
    "EVERY_N_DAYS" -> HabitFrequency.EveryNDays(
        intervalDays = intervalDays,
        referenceDate = LocalDate.parse(requireNotNull(referenceDate) { "Falta la fecha de referencia del intervalo" }),
    )
    "WEEKLY_QUOTA" -> HabitFrequency.WeeklyQuota(weeklyTarget)
    "DAILY" -> HabitFrequency.Daily
    else -> error("Frecuencia no reconocida: $type")
}

internal fun HabitFrequency.typeCode(): String = when (this) {
    HabitFrequency.Daily -> "DAILY"
    is HabitFrequency.FixedDays -> "FIXED_DAYS"
    is HabitFrequency.EveryNDays -> "EVERY_N_DAYS"
    is HabitFrequency.WeeklyQuota -> "WEEKLY_QUOTA"
}

internal fun HabitFrequency.daysCode(): String =
    (this as? HabitFrequency.FixedDays)?.days?.sortedBy { it.value }?.joinToString(",") { it.value.toString() }.orEmpty()
