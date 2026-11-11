package com.alexanderlingle.berry.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.childPrefs by preferencesDataStore(name = "child_prefs")

/** Persists which child is currently selected, across restarts. */
class ChildStore(context: Context) {
    private val dataStore = context.childPrefs

    /** -1 means "no explicit selection yet". */
    val selectedChildId: Flow<Long> =
        dataStore.data.map { prefs -> prefs[SELECTED_CHILD_ID] ?: -1L }

    suspend fun setSelectedChildId(id: Long) {
        dataStore.edit { prefs -> prefs[SELECTED_CHILD_ID] = id }
    }

    companion object {
        private val SELECTED_CHILD_ID = longPreferencesKey("selected_child_id")
    }
}
