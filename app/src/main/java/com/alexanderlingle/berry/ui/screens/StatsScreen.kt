package com.alexanderlingle.berry.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alexanderlingle.berry.ui.components.EmptyState
import com.alexanderlingle.berry.ui.components.rememberBerryFactory
import com.alexanderlingle.berry.util.TimeUtils
import com.alexanderlingle.berry.viewmodel.DaySummary
import com.alexanderlingle.berry.viewmodel.StatsViewModel

private enum class StatMetric(val label: String) {
    SLEEP("Sleep") {
        override fun value(day: DaySummary): Float = day.sleepMillis / 3_600_000f
        override fun format(value: Float): String =
            if (value >= 9.95f) "${value.toInt()}h" else "%.1fh".format(value)
    },
    FEEDS("Feeds") {
        override fun value(day: DaySummary): Float = day.feedCount.toFloat()
        override fun format(value: Float): String = value.toInt().toString()
    },
    DIAPERS("Diapers") {
        override fun value(day: DaySummary): Float = day.diaperCount.toFloat()
        override fun format(value: Float): String = value.toInt().toString()
    };

    abstract fun value(day: DaySummary): Float
    abstract fun format(value: Float): String
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen() {
    val factory = rememberBerryFactory()
    val vm: StatsViewModel = viewModel(factory = factory)
    val days by vm.last7Days.collectAsStateWithLifecycle()
    var metric by remember { mutableStateOf(StatMetric.SLEEP) }

    Scaffold { padding ->
        if (days.isEmpty() || days.all { it.sleepMillis == 0L && it.feedCount == 0 && it.diaperCount == 0 }) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                EmptyState("Log some activity to see stats", Icons.Filled.BarChart)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "totals") { TotalsCard(days) }
                item(key = "metric") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatMetric.values().forEach { m ->
                            FilterChip(
                                selected = metric == m,
                                onClick = { metric = m },
                                label = { Text(m.label) }
                            )
                        }
                    }
                }
                item(key = "chart") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "${metric.label} · last 7 days",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(12.dp))
                            BarChart(days = days, metric = metric)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalsCard(days: List<DaySummary>) {
    val totalSleep = days.sumOf { it.sleepMillis }
    val totalFeeds = days.sumOf { it.feedCount }
    val totalDiapers = days.sumOf { it.diaperCount }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Last 7 days", style = MaterialTheme.typography.titleMedium)
            TotalRow("Total sleep", TimeUtils.formatDurationShort(totalSleep))
            TotalRow("Average sleep / day", TimeUtils.formatDurationShort(totalSleep / 7))
            TotalRow("Feedings", totalFeeds.toString())
            TotalRow("Diapers", totalDiapers.toString())
        }
    }
}

@Composable
private fun TotalRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Simple bar chart built from plain Compose layout — no chart library needed. */
@Composable
private fun BarChart(days: List<DaySummary>, metric: StatMetric) {
    val max = days.maxOfOrNull { metric.value(it) }?.takeIf { it > 0f } ?: 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        days.forEach { day ->
            val value = metric.value(day)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    metric.format(value),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .height(120.dp)
                        .width(32.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight((value / max).coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(day.label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
