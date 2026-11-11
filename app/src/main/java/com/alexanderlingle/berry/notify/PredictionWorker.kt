package com.alexanderlingle.berry.notify

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.alexanderlingle.berry.BerryApp
import com.alexanderlingle.berry.data.Confidence
import com.alexanderlingle.berry.data.PatternEngine
import kotlinx.coroutines.flow.first

/**
 * Phase 3 engine room. Every 30 minutes it re-learns the selected child's
 * patterns with PatternEngine and posts a gentle notification when a nap,
 * feed, or diaper change is due within the notify window.
 *
 * Conservative by design (mirrors the dashboard banner):
 * - needs real history (PatternEngine enforces minimum gaps + fresh anchors);
 * - only MEDIUM/HIGH confidence notifies — LOW stays on the banner's
 *   "still learning" note;
 * - one notification per learned event (dedupe by anchor event time, so a
 *   median that wobbles a minute between runs can't re-notify);
 * - silent when notifications are disabled; stays scheduled in case the user
 *   re-enables them later.
 */
class PredictionWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            return Result.success()
        }
        val app = applicationContext as BerryApp
        val repo = app.repository

        val childId = repo.selectedChildId.first()
        if (childId == -1L) return Result.success()
        val child = repo.selectedChild.first() ?: return Result.success()

        val now = System.currentTimeMillis()
        val predictions = PatternEngine.predictAll(
            sleeps = repo.sleepEntries(childId).first(),
            feeds = repo.feedingEntries(childId).first(),
            diapers = repo.diaperEntries(childId).first(),
            birthdateMillis = child.birthdateMillis,
            now = now
        )
        val prefs = NotifyPrefs(applicationContext)
        for (p in predictions) {
            if (p.confidence == Confidence.LOW) continue
            // Anchor = the last logged event this prediction is based on.
            val anchor = p.expectedAtMillis - p.intervalMillis
            val kindKey = p.kind.name
            if (prefs.lastNotifiedAnchor(kindKey) == anchor) continue
            val dueIn = p.expectedAtMillis - now
            if (dueIn in -OVERDUE_MILLIS..AHEAD_MILLIS) {
                BerryNotifier.notifyPrediction(
                    applicationContext, p.kind, child, p.expectedAtMillis, now
                )
                prefs.setNotifiedAnchor(kindKey, anchor)
            }
        }
        return Result.success()
    }

    companion object {
        /** Notify when the event is due within the next 30 minutes… */
        private const val AHEAD_MILLIS = 30 * 60_000L

        /** …or went due up to 45 minutes ago (catches a worker run that landed late). */
        private const val OVERDUE_MILLIS = 45 * 60_000L
    }
}
