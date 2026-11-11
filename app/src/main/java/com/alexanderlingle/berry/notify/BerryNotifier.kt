package com.alexanderlingle.berry.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.alexanderlingle.berry.MainActivity
import com.alexanderlingle.berry.R
import com.alexanderlingle.berry.data.Child
import com.alexanderlingle.berry.data.PredictionKind
import com.alexanderlingle.berry.ui.navigation.Routes
import kotlin.math.abs

/**
 * Phase 3: predictive notifications. Builds and posts the "usually due soon"
 * alerts learned by [com.alexanderlingle.berry.data.PatternEngine].
 *
 * Everything is local: no server, no account. Berry's first (and only)
 * runtime permission is POST_NOTIFICATIONS, approved 2026-10-17.
 */
object BerryNotifier {
    const val CHANNEL_ID = "berry_predictions"

    /** Intent extra carrying the tab route Berry should open to. */
    const val EXTRA_START_ROUTE = "com.alexanderlingle.berry.START_ROUTE"

    /** Intent extra carrying the child id a notification action applies to. */
    const val EXTRA_CHILD_ID = "com.alexanderlingle.berry.CHILD_ID"

    const val ACTION_START_NAP = "com.alexanderlingle.berry.ACTION_START_NAP"

    private const val NOTIF_NAP = 1001
    private const val NOTIF_FEED = 1002
    private const val NOTIF_DIAPER = 1003

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Smart reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description =
                    "Gentle heads-ups when a nap, feed, or diaper change is usually due — " +
                        "learned on-device from your own logs."
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    fun notifyPrediction(
        context: Context,
        kind: PredictionKind,
        child: Child,
        expectedAtMillis: Long,
        now: Long
    ) {
        val name = child.name.ifBlank { "Baby" }
        val (id, route, title) = when (kind) {
            PredictionKind.NAP -> Triple(NOTIF_NAP, Routes.SLEEP, "Nap SweetSpot")
            PredictionKind.FEED -> Triple(NOTIF_FEED, Routes.FEEDING, "Feeding reminder")
            PredictionKind.DIAPER -> Triple(NOTIF_DIAPER, Routes.DIAPER, "Diaper reminder")
        }
        val openPi = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_START_ROUTE, route)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(describe(kind, name, expectedAtMillis, now))
            .setContentIntent(openPi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        // One-tap actions. Nap start is unambiguous (same semantics as the
        // Sleep tab's "Start nap"); feed/diaper types need a choice, so those
        // actions open their tab where quick-log is one tap away.
        when (kind) {
            PredictionKind.NAP -> {
                val napPi = PendingIntent.getBroadcast(
                    context, id,
                    Intent(context, NotificationActionReceiver::class.java).apply {
                        action = ACTION_START_NAP
                        putExtra(EXTRA_CHILD_ID, child.id)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(0, "Start nap", napPi)
            }
            PredictionKind.FEED -> builder.addAction(0, "Log feed", openPi)
            PredictionKind.DIAPER -> builder.addAction(0, "Log diaper", openPi)
        }

        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    fun cancel(context: Context, kind: PredictionKind) {
        val id = when (kind) {
            PredictionKind.NAP -> NOTIF_NAP
            PredictionKind.FEED -> NOTIF_FEED
            PredictionKind.DIAPER -> NOTIF_DIAPER
        }
        NotificationManagerCompat.from(context).cancel(id)
    }

    /** Same rounding as PatternEngine.relativeText, so banner and notification agree. */
    private fun describe(kind: PredictionKind, name: String, expectedAt: Long, now: Long): String {
        val diff = expectedAt - now
        val mag = magnitude(diff)
        return if (diff >= 0) {
            when (kind) {
                PredictionKind.NAP -> "$name's nap usually due in $mag"
                PredictionKind.FEED -> "$name usually due for a feed in $mag"
                PredictionKind.DIAPER -> "$name usually due for a change in $mag"
            }
        } else {
            when (kind) {
                PredictionKind.NAP -> "$name's nap was due $mag ago"
                PredictionKind.FEED -> "$name was due for a feed $mag ago"
                PredictionKind.DIAPER -> "$name was due for a change $mag ago"
            }
        }
    }

    private fun magnitude(diffMillis: Long): String {
        val a = abs(diffMillis)
        val m = 60_000L
        return when {
            a < 45 * m -> "~${(a / m).coerceAtLeast(1)} min"
            a < 20 * 60 * m -> "~${a / (60 * m)} h"
            else -> "a while"
        }
    }
}
