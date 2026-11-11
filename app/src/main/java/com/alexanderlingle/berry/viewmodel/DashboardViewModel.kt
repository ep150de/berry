package com.alexanderlingle.berry.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexanderlingle.berry.data.BerryRepository
import com.alexanderlingle.berry.data.DiaperEntry
import com.alexanderlingle.berry.data.FeedingEntry
import com.alexanderlingle.berry.data.PatternEngine
import com.alexanderlingle.berry.data.Prediction
import com.alexanderlingle.berry.data.SleepEntry
import com.alexanderlingle.berry.util.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val todaySleepMillis: Long = 0,
    val todayFeedCount: Int = 0,
    val todayDiaperCount: Int = 0,
    val runningSleep: SleepEntry? = null,
    val runningFeeding: FeedingEntry? = null,
    val lastFeeding: FeedingEntry? = null,
    val lastDiaper: DiaperEntry? = null,
    val predictions: List<Prediction> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(private val repo: BerryRepository) : ViewModel() {
    /** Ticks every second so running timers and "today" totals stay live. */
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1000)
        }
    }

    val uiState = repo.selectedChildId.flatMapLatest { id ->
        if (id == -1L) {
            flowOf(DashboardUiState())
        } else {
            combine(
                repo.sleepEntries(id),
                repo.feedingEntries(id),
                repo.diaperEntries(id),
                repo.selectedChild,
                ticker
            ) { sleeps, feeds, diapers, child, now ->
                val dayStart = TimeUtils.startOfDayMillis(now)
                val dayEnd = TimeUtils.startOfNextDayMillis(now)

                var sleepMillis = 0L
                for (s in sleeps) {
                    // Clip each session to today so night sleep crossing midnight counts correctly.
                    sleepMillis += TimeUtils.overlapMillis(
                        s.startTimeMillis, s.endTimeMillis ?: now, dayStart, dayEnd
                    )
                }

                DashboardUiState(
                    todaySleepMillis = sleepMillis,
                    todayFeedCount = feeds.count { it.startTimeMillis in dayStart until dayEnd },
                    todayDiaperCount = diapers.count { it.timeMillis in dayStart until dayEnd },
                    runningSleep = sleeps.firstOrNull { it.isRunning },
                    runningFeeding = feeds.firstOrNull { it.isRunning },
                    lastFeeding = feeds.filter { !it.isRunning }.maxByOrNull { it.startTimeMillis },
                    lastDiaper = diapers.maxByOrNull { it.timeMillis },
                    predictions = PatternEngine.predictAll(
                        sleeps, feeds, diapers, child?.birthdateMillis, now
                    ),
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    fun stopSleep() {
        viewModelScope.launch {
            uiState.value.runningSleep?.let {
                repo.updateSleep(it.copy(endTimeMillis = System.currentTimeMillis()))
            }
        }
    }

    fun stopFeeding() {
        viewModelScope.launch {
            uiState.value.runningFeeding?.let {
                repo.updateFeeding(it.copy(endTimeMillis = System.currentTimeMillis()))
            }
        }
    }
}
