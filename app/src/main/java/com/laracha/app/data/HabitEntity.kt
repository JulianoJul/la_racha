package com.laracha.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String,
    val color: String,
    val createdOn: String,
    val archivedOn: String? = null,
    val sortOrder: Int = 0,
    @ColumnInfo(defaultValue = "'DAILY'") val frequencyType: String = "DAILY",
    @ColumnInfo(defaultValue = "''") val frequencyDays: String = "",
    @ColumnInfo(defaultValue = "1") val frequencyIntervalDays: Int = 1,
    val frequencyReferenceDate: String? = null,
    @ColumnInfo(defaultValue = "1") val frequencyWeeklyTarget: Int = 1,
)
