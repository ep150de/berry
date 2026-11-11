package com.alexanderlingle.berry.data

import androidx.room.TypeConverter

enum class SleepType { NAP, NIGHT }

enum class FeedingType { NURSING_LEFT, NURSING_RIGHT, BOTTLE }

enum class DiaperType { WET, DIRTY, MIXED }

/** Room stores the enums as their names; conversion is total and cannot fail for our own writes. */
class Converters {
    @TypeConverter
    fun sleepTypeToString(value: SleepType): String = value.name

    @TypeConverter
    fun stringToSleepType(value: String): SleepType = SleepType.valueOf(value)

    @TypeConverter
    fun feedingTypeToString(value: FeedingType): String = value.name

    @TypeConverter
    fun stringToFeedingType(value: String): FeedingType = FeedingType.valueOf(value)

    @TypeConverter
    fun diaperTypeToString(value: DiaperType): String = value.name

    @TypeConverter
    fun stringToDiaperType(value: String): DiaperType = DiaperType.valueOf(value)
}
