package com.alexanderlingle.berry.notify

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules the 30-minute prediction checks. WorkManager persists the schedule
 * across reboots on its own, so no boot receiver is needed.
 */
object PredictionScheduler {
    private const val WORK_NAME = "berry_prediction_checks"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<PredictionWorker>(30, TimeUnit.MINUTES)
            .addTag("berry-predictions")
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
