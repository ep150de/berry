package com.alexanderlingle.berry.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BabyChangingStation
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alexanderlingle.berry.data.FeedingType
import com.alexanderlingle.berry.ui.components.ElapsedTimeText
import com.alexanderlingle.berry.ui.components.NextUpBanner
import com.alexanderlingle.berry.ui.components.label
import com.alexanderlingle.berry.ui.components.nextUp
import com.alexanderlingle.berry.ui.components.rememberBerryFactory
import com.alexanderlingle.berry.ui.navigation.Routes
import com.alexanderlingle.berry.util.TimeUtils
import com.alexanderlingle.berry.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onNavigate: (String) -> Unit) {
    val factory = rememberBerryFactory()
    val vm: DashboardViewModel = viewModel(factory = factory)
    val state by vm.uiState.collectAsStateWithLifecycle()

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "greeting") {
                Text(
                    TimeUtils.greeting(),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            nextUp(state.predictions, System.currentTimeMillis())?.let { prediction ->
                item(key = "nextup") {
                    NextUpBanner(prediction = prediction)
                }
            }
            state.runningSleep?.let { running ->
                item(key = "running-sleep") {
                    RunningTimerCard(
                        title = "${running.type.label()} in progress",
                        icon = Icons.Filled.Bedtime,
                        startTimeMillis = running.startTimeMillis,
                        onStop = vm::stopSleep
                    )
                }
            }
            state.runningFeeding?.let { running ->
                item(key = "running-feeding") {
                    RunningTimerCard(
                        title = "Nursing (${running.type.label()}) in progress",
                        icon = Icons.Filled.Restaurant,
                        startTimeMillis = running.startTimeMillis,
                        onStop = vm::stopFeeding
                    )
                }
            }

            item(key = "today") { TodaySummaryCard(state.todaySleepMillis, state.todayFeedCount, state.todayDiaperCount) }

            item(key = "last") {
                LastEventsCard(
                    lastFeedingText = state.lastFeeding?.let { feedingSummary(it.type, it.amountMl, it.startTimeMillis) },
                    lastDiaperText = state.lastDiaper?.let { "${it.type.label()} · ${TimeUtils.formatDateTime(it.timeMillis)}" }
                )
            }

            item(key = "quick") {
                QuickActionsRow(
                    onSleep = { onNavigate(Routes.SLEEP) },
                    onFeeding = { onNavigate(Routes.FEEDING) },
                    onDiaper = { onNavigate(Routes.DIAPER) }
                )
            }

            item(key = "stats-teaser") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(Routes.STATS) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.BarChart, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text("See the last 7 days", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            item(key = "ask-berry-teaser") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(Routes.ASK_BERRY) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Ask Berry", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "On-device AI tips, from your own patterns",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun feedingSummary(type: FeedingType, amountMl: Double?, timeMillis: Long): String {
    val what = if (type == FeedingType.BOTTLE && amountMl != null) {
        "Bottle ${TimeUtils.formatAmountMl(amountMl)}"
    } else {
        "Nursing (${type.label()})"
    }
    return "$what · ${TimeUtils.formatDateTime(timeMillis)}"
}

@Composable
private fun RunningTimerCard(
    title: String,
    icon: ImageVector,
    startTimeMillis: Long,
    onStop: () -> Unit
) {
    val container = MaterialTheme.colorScheme.primaryContainer
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = onContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = onContainer)
                Text(
                    "Started ${TimeUtils.formatTime(startTimeMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer
                )
            }
            ElapsedTimeText(
                startTimeMillis = startTimeMillis,
                style = MaterialTheme.typography.titleLarge.copy(color = onContainer)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = onStop) { Text("Stop") }
        }
    }
}

@Composable
private fun TodaySummaryCard(sleepMillis: Long, feeds: Int, diapers: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Today", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatColumn(TimeUtils.formatDurationShort(sleepMillis), "asleep")
                StatColumn(feeds.toString(), "feeds")
                StatColumn(diapers.toString(), "diapers")
            }
        }
    }
}

@Composable
private fun StatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LastEventsCard(lastFeedingText: String?, lastDiaperText: String?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Latest", style = MaterialTheme.typography.titleMedium)
            Text(
                lastFeedingText?.let { "Last feed: $it" } ?: "No feeds logged yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                lastDiaperText?.let { "Last diaper: $it" } ?: "No diapers logged yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuickActionsRow(
    onSleep: () -> Unit,
    onFeeding: () -> Unit,
    onDiaper: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilledTonalButton(onClick = onSleep, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Bedtime, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Sleep")
        }
        FilledTonalButton(onClick = onFeeding, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Restaurant, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Feed")
        }
        FilledTonalButton(onClick = onDiaper, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.BabyChangingStation, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Diaper")
        }
    }
}
