package com.thao_soft.alarmer.provider

import androidx.room.TypeConverter
import com.thao_soft.alarmer.data.Weekdays

class WeekdaysConverter {
    @TypeConverter
    fun fromWeekdays(weekdays: Weekdays): Int {
        return weekdays.bits // Save the ordinal value of the enum
    }

    @TypeConverter
    fun toWeekdays(ordinal: Int): Weekdays {
        return Weekdays.fromBits(ordinal)
    }
}