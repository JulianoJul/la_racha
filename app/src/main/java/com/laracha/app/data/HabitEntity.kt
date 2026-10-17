package com.laracha.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String,
    val color: String,
    val createdOn: String,
    val archivedOn: String? = null,
    val sortOrder: Int = 0,
)
