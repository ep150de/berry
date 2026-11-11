package com.alexanderlingle.berry.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Central place for all date/time math and display formatting (device timezone). */
object TimeUtils {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    private val dateFmt = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    private val dateTimeFmt = DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a", Locale.getDefault())
    private val dayFmt = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())

    fun zoned(millis: Long): ZonedDateTime =
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)

    fun formatTime(millis: Long): String = timeFmt.format(zoned(millis))

    fun formatDate(millis: Long): String = dateFmt.format(zoned(millis))

    fun formatDateTime(millis: Long): String = dateTimeFmt.format(zoned(millis))

    fun formatDayLabel(millis: Long): String = dayFmt.format(zoned(millis))

    /** Midnight at the start of the day containing [millis]. */
    fun startOfDayMillis(millis: Long): Long {
        val date: LocalDate = zoned(millis).toLocalDate()
        return date.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** Midnight [daysAgo] days before today (calendar days, so DST transitions
     *  don't shift the bucket boundaries). */
    fun startOfDayMillisDaysAgo(daysAgo: Long): Long {
        val date: LocalDate = zoned(System.currentTimeMillis()).toLocalDate().minusDays(daysAgo)
        return date.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** Midnight at the start of the day after the one containing [millis]. */
    fun startOfNextDayMillis(millis: Long): Long {
        val date: LocalDate = zoned(millis).toLocalDate().plusDays(1)
        return date.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** "2h 15m", "12m 30s", or "45s". */
    fun formatDuration(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }

    /** "7h 30m" or "45m" — for daily totals where seconds add noise. */
    fun formatDurationShort(millis: Long): String {
        val totalMinutes = (millis / 60_000).coerceAtLeast(0)
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    /** Milliseconds of overlap between [aStart, aEnd) and [bStart, bEnd). */
    fun overlapMillis(aStart: Long, aEnd: Long, bStart: Long, bEnd: Long): Long =
        (minOf(aEnd, bEnd) - maxOf(aStart, bStart)).coerceAtLeast(0)

    const val ML_PER_OZ: Double = 29.5735

    fun mlToOz(ml: Double): Double = ml / ML_PER_OZ

    fun ozToMl(oz: Double): Double = oz * ML_PER_OZ

    fun formatAmountMl(ml: Double): String =
        if (ml >= 100) "${ml.toInt()} ml"
        else "${"%.1f".format(Locale.getDefault(), ml)} ml"

    private val birthdateFmt = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

    /** "Jan 12, 2026" style, for birthdates. */
    fun formatBirthdate(millis: Long): String = birthdateFmt.format(zoned(millis))

    /** "3 mo, 2 wk", "5 d", "1 y, 4 mo", or "Newborn". */
    fun formatAge(birthdateMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val birth = zoned(birthdateMillis).toLocalDate()
        val now = zoned(nowMillis).toLocalDate()
        val period = java.time.Period.between(birth, now)
        if (period.isNegative) return ""
        val totalDays = java.time.temporal.ChronoUnit.DAYS.between(birth, now)
        return when {
            period.years > 0 -> "${period.years} y, ${period.months} mo"
            period.months > 0 -> {
                val weeks = (period.days / 7)
                if (weeks > 0) "${period.months} mo, ${weeks} wk" else "${period.months} mo"
            }
            totalDays >= 7 -> "${totalDays / 7} wk, ${totalDays % 7} d"
            totalDays >= 1 -> "$totalDays d"
            else -> "Newborn"
        }
    }

    /** Warm time-of-day greeting for tired parents. */
    fun greeting(nowMillis: Long = System.currentTimeMillis()): String {
        return when (zoned(nowMillis).hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Up late"
        }
    }
}
