package com.alexanderlingle.berry.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Child::class, SleepEntry::class, FeedingEntry::class, DiaperEntry::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class BerryDatabase : RoomDatabase() {
    abstract fun childDao(): ChildDao
    abstract fun sleepDao(): SleepDao
    abstract fun feedingDao(): FeedingDao
    abstract fun diaperDao(): DiaperDao
}
