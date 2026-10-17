package com.laracha.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "completions",
    primaryKeys = ["habitId", "localDate"],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("localDate")],
)
data class CompletionEntity(
    val habitId: String,
    /** ISO-8601 local date (YYYY-MM-DD), never an epoch timestamp. */
    val localDate: String,
)
