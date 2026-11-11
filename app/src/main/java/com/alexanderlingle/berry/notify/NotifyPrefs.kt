package com.alexanderlingle.berry.notify

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.notifyPrefs by preferencesDataStore(name = "notify_prefs")

/**
 * Tiny persistent store for Phase 3 predictive notifications:
 * - the anchor event time each prediction kind was last notified about, so a
 *   learned event triggers at most one notification (a new log = a new anchor);
 * - whether the POST_NOTIFICATIONS runtime prompt has been shown, so we ask once.
 */
class NotifyPrefs(private val context: Context) {
    private fun anchorKey(kind: String) = longPreferencesKey("anchor_$kind")
    private val permAskedKey = longPreferencesKey("perm_asked")

    suspend fun lastNotifiedAnchor(kind: String): Long =
        context.notifyPrefs.data.map { it[anchorKey(kind)] ?: -1L }.first()

    suspend fun setNotifiedAnchor(kind: String, anchorMillis: Long) {
        context.notifyPrefs.edit { it[anchorKey(kind)] = anchorMillis }
    }

    suspend fun wasPermissionAsked(): Boolean =
        context.notifyPrefs.data.map { it[permAskedKey] ?: 0L }.first() == 1L

    suspend fun setPermissionAsked() {
        context.notifyPrefs.edit { it[permAskedKey] = 1L }
    }
}
