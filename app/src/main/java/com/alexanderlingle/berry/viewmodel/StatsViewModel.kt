package com.alexanderlingle.berry.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexanderlingle.berry.data.BerryRepository
import com.alexanderlingle.berry.data.DiaperEntry
import com.alexanderlingle.berry.data.FeedingEntry
import com.alexanderlingle.berry.data.SleepEntry
import com.alexanderlingle.berry.util.TimeUtils
import kotlinx.coroutines.flow.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class DaySummary(
    val dayStartMillis: Long,
    val label: String,
    val sleepMillis: Long,
    val feedCount: Int,
    val diaperCount: Int
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(repo: BerryRepository) : ViewModel() {
    /** Oldest → newest, one entry per day for the last 7 days, for the selected child. */
    val last7Days = repo.selectedChildId.flatMapLatest { id ->
        if (id == -1L) {
            flowOf(emptyList())
        } else {
            combine(
                repo.sleepEntries(id), repo.feedingEntries(id), repo.diaperEntries(id)
            ) { sleeps: List<SleepEntry>, feeds: List<FeedingEntry>, diapers: List<DiaperEntry> ->
                val now = System.currentTimeMillis()
                (6 downTo 0).map { daysAgo ->
                    val dayStart = TimeUtils.startOfDayMillisDaysAgo(daysAgo.toLong())
                    val dayEnd = TimeUtils.startOfDayMillisDaysAgo(daysAgo.toLong() - 1)
                    var sleepMillis = 0L
                    for (s in sleeps) {
                        sleepMillis += TimeUtils.overlapMillis(
                            s.startTimeMillis, s.endTimeMillis ?: now, dayStart, dayEnd
                        )
                    }
                    DaySummary(
                        dayStartMillis = dayStart,
                        label = TimeUtils.formatDayLabel(dayStart),
                        sleepMillis = sleepMillis,
                        feedCount = feeds.count { it.startTimeMillis in dayStart until dayEnd },
                        diaperCount = diapers.count { it.timeMillis in dayStart until dayEnd }
                    )
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
