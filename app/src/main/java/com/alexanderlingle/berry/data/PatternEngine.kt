package com.alexanderlingle.berry.data

import kotlin.math.abs

/**
 * On-device pattern learning for Berry v2 (Phase 2).
 *
 * Pure Kotlin, no Android dependencies: given a child's log entries it learns
 * median feed/diaper intervals and wake windows, then predicts when the next
 * nap, feed, or diaper change is usually due. Everything runs locally —
 * no server, no account, no Huckleberry-style $12/mo paywall.
 *
 * Phase 3 (predictive notifications) will schedule against these predictions
 * with WorkManager/AlarmManager.
 */
enum class PredictionKind { NAP, FEED, DIAPER }

enum class Confidence { HIGH, MEDIUM, LOW }

data class Prediction(
    val kind: PredictionKind,
    /** When the next event is usually due, epoch millis. */
    val expectedAtMillis: Long,
    /** The learned median interval behind the prediction, millis. */
    val intervalMillis: Long,
    val confidence: Confidence,
    /** Distinct days of logs the interval was learned from. */
    val basedOnDays: Int
)

object PatternEngine {
    private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    private const val MINUTE_MILLIS = 60_000L

    /** Days of history the engine looks back over. */
    const val LOOKBACK_DAYS = 14

    /** Wake-window learning needs logs spanning at least this many distinct days. */
    const val MIN_WAKE_DAYS = 5

    /** Sensible bounds for a single gap; anything outside is a logging artifact. */
    private val FEED_GAP_BOUNDS = 30 * MINUTE_MILLIS..12 * 60 * MINUTE_MILLIS
    private val DIAPER_GAP_BOUNDS = 30 * MINUTE_MILLIS..12 * 60 * MINUTE_MILLIS
    private val WAKE_GAP_BOUNDS = 20 * MINUTE_MILLIS..14 * 60 * MINUTE_MILLIS

    fun median(values: List<Long>): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid]
        else (sorted[mid - 1] + sorted[mid]) / 2
    }

    /** Gaps between consecutive feed starts, newest-first or oldest-first input. */
    fun feedGaps(feeds: List<FeedingEntry>, now: Long): List<Long> {
        val cutoff = now - LOOKBACK_DAYS * DAY_MILLIS
        val starts = feeds
            .filter { !it.isRunning && it.startTimeMillis >= cutoff }
            .map { it.startTimeMillis }
            .sorted()
        return starts.zipWithNext { a, b -> b - a }.filter { it in FEED_GAP_BOUNDS }
    }

    /** Gaps between consecutive diaper changes. */
    fun diaperGaps(diapers: List<DiaperEntry>, now: Long): List<Long> {
        val cutoff = now - LOOKBACK_DAYS * DAY_MILLIS
        val times = diapers
            .filter { it.timeMillis >= cutoff }
            .map { it.timeMillis }
            .sorted()
        return times.zipWithNext { a, b -> b - a }.filter { it in DIAPER_GAP_BOUNDS }
    }

    /**
     * Wake windows: end of one completed sleep -> start of the next.
     * Returns the learned gaps plus the distinct-day span of the underlying logs.
     */
    fun wakeWindows(sleeps: List<SleepEntry>, now: Long): Pair<List<Long>, Int> {
        val cutoff = now - LOOKBACK_DAYS * DAY_MILLIS
        val done = sleeps
            .filter { !it.isRunning && it.endTimeMillis != null && it.startTimeMillis >= cutoff }
            .sortedBy { it.startTimeMillis }
        val gaps = done.zipWithNext { a, b -> b.startTimeMillis - (a.endTimeMillis ?: b.startTimeMillis) }
            .filter { it in WAKE_GAP_BOUNDS }
        val days = done.map { it.startTimeMillis / DAY_MILLIS }.toSet().size
        return gaps to days
    }

    /**
     * Age-appropriate wake-window clamp (min, max), millis, by age in weeks.
     * Keeps a thin-data median from predicting something biologically absurd.
     */
    fun wakeWindowBounds(ageWeeks: Long): Pair<Long, Long> {
        val h = 60 * MINUTE_MILLIS
        val m = MINUTE_MILLIS
        return when {
            ageWeeks < 4 -> 35 * m to 60 * m
            ageWeeks < 8 -> 45 * m to 75 * m
            ageWeeks < 13 -> 60 * m to 105 * m
            ageWeeks < 18 -> 75 * m to 2 * h
            ageWeeks < 27 -> 2 * h to (2.5 * h).toLong()
            ageWeeks < 40 -> (2.5 * h).toLong() to (3.5 * h).toLong()
            ageWeeks < 53 -> 3 * h to (4.5 * h).toLong()
            else -> 4 * h to 6 * h
        }
    }

    private fun confidenceFor(gapCount: Int): Confidence? = when {
        gapCount >= 10 -> Confidence.HIGH
        gapCount >= 5 -> Confidence.MEDIUM
        gapCount >= 2 -> Confidence.LOW
        else -> null
    }

    private fun distinctDays(times: List<Long>, now: Long): Int {
        val cutoff = now - LOOKBACK_DAYS * DAY_MILLIS
        return times.filter { it >= cutoff }.map { it / DAY_MILLIS }.toSet().size
    }

    /**
     * Learns from the child's logs and returns up to three predictions.
     * A prediction is only returned when there is enough history AND the anchor
     * event is recent enough that "due in X" is meaningful — otherwise it is
     * silently skipped (the banner simply won't show).
     */
    fun predictAll(
        sleeps: List<SleepEntry>,
        feeds: List<FeedingEntry>,
        diapers: List<DiaperEntry>,
        birthdateMillis: Long?,
        now: Long
    ): List<Prediction> {
        val out = mutableListOf<Prediction>()

        // Nap: anchor on the end of the last completed sleep. Skip while a nap runs.
        if (sleeps.none { it.isRunning }) {
            val (windows, days) = wakeWindows(sleeps, now)
            val medianWindow = median(windows)
            val confidence = confidenceFor(windows.size)
            val lastEnd = sleeps
                .filter { !it.isRunning && it.endTimeMillis != null }
                .maxOfOrNull { it.endTimeMillis!! }
            if (medianWindow != null && confidence != null && lastEnd != null && days >= MIN_WAKE_DAYS) {
                val clamped = birthdateMillis
                    ?.let { wakeWindowBounds(((now - it) / (7 * DAY_MILLIS)).coerceAtLeast(0)) }
                    ?.let { (min, max) -> medianWindow.coerceIn(min, max) }
                    ?: medianWindow
                // Stale anchor (nothing logged in 2+ windows) makes "due in X" meaningless.
                if (now - lastEnd < 2 * clamped) {
                    out += Prediction(PredictionKind.NAP, lastEnd + clamped, clamped, confidence, days)
                }
            }
        }

        // Feed: anchor on the last completed feed start. Skip while nursing.
        if (feeds.none { it.isRunning }) {
            val gaps = feedGaps(feeds, now)
            val medianGap = median(gaps)
            val confidence = confidenceFor(gaps.size)
            val lastStart = feeds.filter { !it.isRunning }.maxOfOrNull { it.startTimeMillis }
            if (medianGap != null && confidence != null && lastStart != null &&
                now - lastStart < 2 * medianGap
            ) {
                val days = distinctDays(feeds.filter { !it.isRunning }.map { it.startTimeMillis }, now)
                out += Prediction(PredictionKind.FEED, lastStart + medianGap, medianGap, confidence, days)
            }
        }

        // Diaper: anchor on the last change.
        val diaperGaps = diaperGaps(diapers, now)
        val medianDiaperGap = median(diaperGaps)
        val diaperConfidence = confidenceFor(diaperGaps.size)
        val lastChange = diapers.maxOfOrNull { it.timeMillis }
        if (medianDiaperGap != null && diaperConfidence != null && lastChange != null &&
            now - lastChange < 2 * medianDiaperGap
        ) {
            val days = distinctDays(diapers.map { it.timeMillis }, now)
            out += Prediction(
                PredictionKind.DIAPER, lastChange + medianDiaperGap,
                medianDiaperGap, diaperConfidence, days
            )
        }

        return out
    }

    /** Human, rounded, no false precision: "in ~20 min", "in ~2 h", "~10 min ago". */
    fun relativeText(expectedAtMillis: Long, now: Long): String {
        val diff = expectedAtMillis - now
        val absDiff = abs(diff)
        val text = when {
            absDiff < 45 * MINUTE_MILLIS -> "~${(absDiff / MINUTE_MILLIS).coerceAtLeast(1)} min"
            absDiff < 20 * 60 * MINUTE_MILLIS -> "~${absDiff / (60 * MINUTE_MILLIS)} h"
            else -> "a while"
        }
        return if (diff >= 0) "in $text" else "$text ago"
    }
}
