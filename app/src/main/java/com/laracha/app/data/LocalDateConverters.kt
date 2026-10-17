package com.laracha.app.data

import androidx.room.TypeConverter
import java.time.LocalDate

class LocalDateConverters {
    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
}
