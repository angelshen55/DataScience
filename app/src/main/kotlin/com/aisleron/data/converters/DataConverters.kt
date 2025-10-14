package com.aisleron.data.converters

import androidx.room.TypeConverter
import java.util.Date

class DateConverters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? = date?.time
}