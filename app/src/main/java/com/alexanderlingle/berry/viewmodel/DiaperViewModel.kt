package com.alexanderlingle.berry.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexanderlingle.berry.data.BerryRepository
import com.alexanderlingle.berry.data.DiaperEntry
import com.alexanderlingle.berry.data.DiaperType
import kotlinx.coroutines.flow.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DiaperViewModel(private val repo: BerryRepository) : ViewModel() {
    private val childId = repo.selectedChildId

    val entries: StateFlow<List<DiaperEntry>> = childId.flatMapLatest { id ->
        if (id == -1L) flowOf(emptyList()) else repo.diaperEntries(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** One-tap log: records a change of [type] right now. */
    fun quickLog(type: DiaperType, note: String = "") {
        val cid = childId.value
        if (cid == -1L) return
        viewModelScope.launch {
            repo.insertDiaper(
                DiaperEntry(childId = cid, type = type, timeMillis = System.currentTimeMillis(), note = note)
            )
        }
    }

    fun save(entry: DiaperEntry) {
        viewModelScope.launch {
            val cid = childId.value
            if (cid == -1L) return@launch
            val e = if (entry.childId == 0L) entry.copy(childId = cid) else entry
            if (e.id == 0L) repo.insertDiaper(e) else repo.updateDiaper(e)
        }
    }

    fun delete(entry: DiaperEntry) {
        viewModelScope.launch { repo.deleteDiaper(entry) }
    }
}
