package com.alexanderlingle.berry.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A child profile. Every log entry belongs to exactly one child. */
@Entity(tableName = "children")
data class Child(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Absolute path of the profile photo in app-private storage, if set. */
    val photoPath: String? = null,
    val birthdateMillis: Long? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
)

/** A single sleep session. `endTimeMillis == null` means the timer is still running. */
@Entity(tableName = "sleep_entries")
data class SleepEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val childId: Long = 0,
    val type: SleepType,
    val startTimeMillis: Long,
    val endTimeMillis: Long? = null,
    val note: String = ""
) {
    val isRunning: Boolean get() = endTimeMillis == null
}

/**
 * A feeding event. Nursing sessions have a start and (once stopped) an end time.
 * Bottle feeds are point-in-time logs with an amount; their end time equals the start time.
 */
@Entity(tableName = "feeding_entries")
data class FeedingEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val childId: Long = 0,
    val type: FeedingType,
    val startTimeMillis: Long,
    val endTimeMillis: Long? = null,
    /** Amount in milliliters; only set for [FeedingType.BOTTLE]. */
    val amountMl: Double? = null,
    val note: String = ""
) {
    val isBottle: Boolean get() = type == FeedingType.BOTTLE
    val isRunning: Boolean get() = !isBottle && endTimeMillis == null
}

/** A diaper change, logged at a single point in time. */
@Entity(tableName = "diaper_entries")
data class DiaperEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val childId: Long = 0,
    val type: DiaperType,
    val timeMillis: Long,
    val note: String = ""
)
