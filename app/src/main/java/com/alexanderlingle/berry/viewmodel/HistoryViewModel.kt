package com.alexanderlingle.berry.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexanderlingle.berry.data.BerryRepository
import com.alexanderlingle.berry.data.DiaperEntry
import com.alexanderlingle.berry.data.FeedingEntry
import com.alexanderlingle.berry.data.SleepEntry
import kotlinx.coroutines.flow.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HistoryFilter { ALL, SLEEP, FEEDING, DIAPER }

/** One row in the unified chronological history list. */
sealed interface TimelineItem {
    val sortKey: Long
    val stableId: String

    data class SleepItem(val entry: SleepEntry) : TimelineItem {
        override val sortKey: Long = entry.startTimeMillis
        override val stableId: String = "sleep-${entry.id}"
    }

    data class FeedingItem(val entry: FeedingEntry) : TimelineItem {
        override val sortKey: Long = entry.startTimeMillis
        override val stableId: String = "feeding-${entry.id}"
    }

    data class DiaperItem(val entry: DiaperEntry) : TimelineItem {
        override val sortKey: Long = entry.timeMillis
        override val stableId: String = "diaper-${entry.id}"
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val repo: BerryRepository) : ViewModel() {
    private val filter = MutableStateFlow(HistoryFilter.ALL)
    val filterState = filter.asStateFlow()

    private data class ChildEntries(
        val sleeps: List<SleepEntry> = emptyList(),
        val feeds: List<FeedingEntry> = emptyList(),
        val diapers: List<DiaperEntry> = emptyList()
    )

    private val childEntries = repo.selectedChildId.flatMapLatest { id ->
        if (id == -1L) {
            flowOf(ChildEntries())
        } else {
            combine(
                repo.sleepEntries(id),
                repo.feedingEntries(id),
                repo.diaperEntries(id)
            ) { sleeps, feeds, diapers -> ChildEntries(sleeps, feeds, diapers) }
        }
    }

    val items = combine(
        childEntries, filter
    ) { entries, f ->
        val list = mutableListOf<TimelineItem>()
        if (f == HistoryFilter.ALL || f == HistoryFilter.SLEEP) {
            list += entries.sleeps.map { TimelineItem.SleepItem(it) }
        }
        if (f == HistoryFilter.ALL || f == HistoryFilter.FEEDING) {
            list += entries.feeds.map { TimelineItem.FeedingItem(it) }
        }
        if (f == HistoryFilter.ALL || f == HistoryFilter.DIAPER) {
            list += entries.diapers.map { TimelineItem.DiaperItem(it) }
        }
        list.sortedByDescending { it.sortKey }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(f: HistoryFilter) {
        filter.value = f
    }

    fun delete(item: TimelineItem) {
        viewModelScope.launch {
            when (item) {
                is TimelineItem.SleepItem -> repo.deleteSleep(item.entry)
                is TimelineItem.FeedingItem -> repo.deleteFeeding(item.entry)
                is TimelineItem.DiaperItem -> repo.deleteDiaper(item.entry)
            }
        }
    }

    /** Stamps the selected child on brand-new entries coming from the edit dialogs. */
    private fun withChildId(entryChildId: Long): Long? {
        if (entryChildId != 0L) return entryChildId
        val cid = repo.selectedChildId.value
        return if (cid == -1L) null else cid
    }

    fun saveSleep(entry: SleepEntry) {
        viewModelScope.launch {
            val cid = withChildId(entry.childId) ?: return@launch
            val e = entry.copy(childId = cid)
            if (e.id == 0L) repo.insertSleep(e) else repo.updateSleep(e)
        }
    }

    fun saveFeeding(entry: FeedingEntry) {
        viewModelScope.launch {
            val cid = withChildId(entry.childId) ?: return@launch
            val e = entry.copy(childId = cid)
            if (e.id == 0L) repo.insertFeeding(e) else repo.updateFeeding(e)
        }
    }

    fun saveDiaper(entry: DiaperEntry) {
        viewModelScope.launch {
            val cid = withChildId(entry.childId) ?: return@launch
            val e = entry.copy(childId = cid)
            if (e.id == 0L) repo.insertDiaper(e) else repo.updateDiaper(e)
        }
    }
}
