package com.alexanderlingle.berry.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexanderlingle.berry.data.BerryRepository
import com.alexanderlingle.berry.data.FeedingEntry
import com.alexanderlingle.berry.data.FeedingType
import kotlinx.coroutines.flow.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class FeedingViewModel(private val repo: BerryRepository) : ViewModel() {
    private val childId = repo.selectedChildId

    val entries: StateFlow<List<FeedingEntry>> = childId.flatMapLatest { id ->
        if (id == -1L) flowOf(emptyList()) else repo.feedingEntries(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val running: StateFlow<FeedingEntry?> = childId.flatMapLatest { id ->
        if (id == -1L) flowOf(null) else repo.runningFeeding(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Suggests the side opposite the last nursing session ("other side next"). */
    val suggestedNursingSide: StateFlow<FeedingType> = entries.map { list ->
        val last = list.filter { it.type != FeedingType.BOTTLE }
            .maxByOrNull { it.startTimeMillis }
        if (last?.type == FeedingType.NURSING_LEFT) FeedingType.NURSING_RIGHT
        else FeedingType.NURSING_LEFT
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FeedingType.NURSING_LEFT)

    /** Starts a nursing timer; ignored when one is already running. */
    fun startNursing(type: FeedingType) {
        val cid = childId.value
        if (type == FeedingType.BOTTLE || cid == -1L || running.value != null) return
        viewModelScope.launch {
            repo.insertFeeding(
                FeedingEntry(childId = cid, type = type, startTimeMillis = System.currentTimeMillis())
            )
        }
    }

    fun stopRunning() {
        viewModelScope.launch {
            running.value?.let {
                repo.updateFeeding(it.copy(endTimeMillis = System.currentTimeMillis()))
            }
        }
    }

    /** Logs a bottle feed at [timeMillis] with [amountMl] milliliters. */
    fun logBottle(amountMl: Double, timeMillis: Long, note: String = "") {
        val cid = childId.value
        if (cid == -1L) return
        viewModelScope.launch {
            repo.insertFeeding(
                FeedingEntry(
                    childId = cid,
                    type = FeedingType.BOTTLE,
                    startTimeMillis = timeMillis,
                    endTimeMillis = timeMillis,
                    amountMl = amountMl,
                    note = note
                )
            )
        }
    }

    fun save(entry: FeedingEntry) {
        viewModelScope.launch {
            val cid = childId.value
            if (cid == -1L) return@launch
            val e = if (entry.childId == 0L) entry.copy(childId = cid) else entry
            // Never allow two running nursing entries: saving one with no end
            // time stops any other running one first.
            if (!e.isBottle && e.endTimeMillis == null) {
                running.value
                    ?.takeIf { it.id != e.id }
                    ?.let { repo.updateFeeding(it.copy(endTimeMillis = System.currentTimeMillis())) }
            }
            if (e.id == 0L) repo.insertFeeding(e) else repo.updateFeeding(e)
        }
    }

    fun delete(entry: FeedingEntry) {
        viewModelScope.launch { repo.deleteFeeding(entry) }
    }
}
