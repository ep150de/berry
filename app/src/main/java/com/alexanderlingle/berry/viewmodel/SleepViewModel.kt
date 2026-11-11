package com.alexanderlingle.berry.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexanderlingle.berry.data.BerryRepository
import com.alexanderlingle.berry.data.SleepEntry
import com.alexanderlingle.berry.data.SleepType
import kotlinx.coroutines.flow.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SleepViewModel(private val repo: BerryRepository) : ViewModel() {
    private val childId = repo.selectedChildId

    val entries: StateFlow<List<SleepEntry>> = childId.flatMapLatest { id ->
        if (id == -1L) flowOf(emptyList()) else repo.sleepEntries(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val running: StateFlow<SleepEntry?> = childId.flatMapLatest { id ->
        if (id == -1L) flowOf(null) else repo.runningSleep(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Starts a sleep timer; ignored when one is already running. */
    fun startSleep(type: SleepType) {
        val cid = childId.value
        if (cid == -1L || running.value != null) return
        viewModelScope.launch {
            repo.insertSleep(
                SleepEntry(childId = cid, type = type, startTimeMillis = System.currentTimeMillis())
            )
        }
    }

    fun stopRunning() {
        viewModelScope.launch {
            running.value?.let {
                repo.updateSleep(it.copy(endTimeMillis = System.currentTimeMillis()))
            }
        }
    }

    fun save(entry: SleepEntry) {
        viewModelScope.launch {
            val cid = childId.value
            if (cid == -1L) return@launch
            val e = if (entry.childId == 0L) entry.copy(childId = cid) else entry
            // Never allow two running entries: saving one with no end time stops
            // any other running entry first.
            if (e.endTimeMillis == null) {
                running.value
                    ?.takeIf { it.id != e.id }
                    ?.let { repo.updateSleep(it.copy(endTimeMillis = System.currentTimeMillis())) }
            }
            if (e.id == 0L) repo.insertSleep(e) else repo.updateSleep(e)
        }
    }

    fun delete(entry: SleepEntry) {
        viewModelScope.launch { repo.deleteSleep(entry) }
    }
}
