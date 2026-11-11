package com.alexanderlingle.berry.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.alexanderlingle.berry.BerryApp
import com.alexanderlingle.berry.MainActivity
import com.alexanderlingle.berry.data.PredictionKind
import com.alexanderlingle.berry.data.SleepEntry
import com.alexanderlingle.berry.data.SleepType
import com.alexanderlingle.berry.ui.navigation.Routes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Handles notification action buttons. "Start nap" writes a running NAP entry
 * (same semantics as the Sleep tab's Start nap: ignored when a timer already
 * runs), dismisses the notification, and opens Berry on the Sleep tab.
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BerryNotifier.ACTION_START_NAP) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as BerryApp
                val childId = intent.getLongExtra(BerryNotifier.EXTRA_CHILD_ID, -1L)
                if (childId != -1L && app.repository.runningSleep(childId).first() == null) {
                    app.repository.insertSleep(
                        SleepEntry(
                            childId = childId,
                            type = SleepType.NAP,
                            startTimeMillis = System.currentTimeMillis()
                        )
                    )
                }
                BerryNotifier.cancel(context, PredictionKind.NAP)
                context.startActivity(
                    Intent(context, MainActivity::class.java).apply {
                        putExtra(BerryNotifier.EXTRA_START_ROUTE, Routes.SLEEP)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                )
            } catch (e: Exception) {
                Log.w("BerryNotify", "Start-nap action failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}
