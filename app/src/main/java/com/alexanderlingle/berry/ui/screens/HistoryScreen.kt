package com.alexanderlingle.berry.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BabyChangingStation
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alexanderlingle.berry.data.DiaperEntry
import com.alexanderlingle.berry.data.FeedingEntry
import com.alexanderlingle.berry.data.SleepEntry
import com.alexanderlingle.berry.ui.components.DeleteConfirmDialog
import com.alexanderlingle.berry.ui.components.EmptyState
import com.alexanderlingle.berry.ui.components.label
import com.alexanderlingle.berry.ui.components.rememberBerryFactory
import com.alexanderlingle.berry.util.TimeUtils
import com.alexanderlingle.berry.viewmodel.HistoryFilter
import com.alexanderlingle.berry.viewmodel.HistoryViewModel
import com.alexanderlingle.berry.viewmodel.TimelineItem

private fun HistoryFilter.label(): String = when (this) {
    HistoryFilter.ALL -> "All"
    HistoryFilter.SLEEP -> "Sleep"
    HistoryFilter.FEEDING -> "Feeding"
    HistoryFilter.DIAPER -> "Diaper"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen() {
    val factory = rememberBerryFactory()
    val vm: HistoryViewModel = viewModel(factory = factory)
    val items by vm.items.collectAsStateWithLifecycle()
    val filter by vm.filterState.collectAsStateWithLifecycle()

    var editingSleep by remember { mutableStateOf<SleepEntry?>(null) }
    var editingFeeding by remember { mutableStateOf<FeedingEntry?>(null) }
    var editingDiaper by remember { mutableStateOf<DiaperEntry?>(null) }
    var deleting by remember { mutableStateOf<TimelineItem?>(null) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HistoryFilter.values().forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { vm.setFilter(f) },
                        label = { Text(f.label()) }
                    )
                }
            }
            if (items.isEmpty()) {
                EmptyState("Nothing here yet", Icons.Filled.History)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.stableId }) { item ->
                        TimelineRow(
                            item = item,
                            onEdit = {
                                when (item) {
                                    is TimelineItem.SleepItem -> editingSleep = item.entry
                                    is TimelineItem.FeedingItem -> editingFeeding = item.entry
                                    is TimelineItem.DiaperItem -> editingDiaper = item.entry
                                }
                            },
                            onDelete = { deleting = item }
                        )
                    }
                }
            }
        }
    }

    editingSleep?.let { target ->
        SleepEditorDialog(
            initial = target,
            onDismiss = { editingSleep = null },
            onSave = {
                vm.saveSleep(it)
                editingSleep = null
            },
            onDelete = {
                vm.delete(TimelineItem.SleepItem(target))
                editingSleep = null
            }
        )
    }

    editingFeeding?.let { target ->
        if (target.isBottle) {
            BottleDialog(
                initial = target,
                onDismiss = { editingFeeding = null },
                onSave = { amountMl, timeMillis, note ->
                    vm.saveFeeding(
                        target.copy(
                            amountMl = amountMl,
                            startTimeMillis = timeMillis,
                            endTimeMillis = timeMillis,
                            note = note
                        )
                    )
                    editingFeeding = null
                },
                onDelete = {
                    vm.delete(TimelineItem.FeedingItem(target))
                    editingFeeding = null
                }
            )
        } else {
            NursingEditorDialog(
                initial = target,
                onDismiss = { editingFeeding = null },
                onSave = {
                    vm.saveFeeding(it)
                    editingFeeding = null
                },
                onDelete = {
                    vm.delete(TimelineItem.FeedingItem(target))
                    editingFeeding = null
                }
            )
        }
    }

    editingDiaper?.let { target ->
        DiaperEditorDialog(
            initial = target,
            onDismiss = { editingDiaper = null },
            onSave = {
                vm.saveDiaper(it)
                editingDiaper = null
            },
            onDelete = {
                vm.delete(TimelineItem.DiaperItem(target))
                editingDiaper = null
            }
        )
    }

    deleting?.let { target ->
        DeleteConfirmDialog(
            label = "this entry",
            onDismiss = { deleting = null },
            onConfirm = {
                vm.delete(target)
                deleting = null
            }
        )
    }
}

@Composable
private fun TimelineRow(
    item: TimelineItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val now = remember { System.currentTimeMillis() }
    val (icon, headline, supporting) = when (item) {
        is TimelineItem.SleepItem -> {
            val e = item.entry
            val end = e.endTimeMillis
            val headlineText = if (end == null) {
                "${e.type.label()} · in progress ${TimeUtils.formatDurationShort(now - e.startTimeMillis)}"
            } else {
                "${e.type.label()} · ${TimeUtils.formatDurationShort(end - e.startTimeMillis)}"
            }
            val timeText = if (end == null) {
                "Started ${TimeUtils.formatDateTime(e.startTimeMillis)}"
            } else {
                "${TimeUtils.formatDateTime(e.startTimeMillis)} → ${TimeUtils.formatTime(end)}"
            }
            Triple(
                Icons.Filled.Bedtime,
                headlineText,
                listOfNotNull(timeText, e.note.ifBlank { null })
            )
        }
        is TimelineItem.FeedingItem -> {
            val e = item.entry
            val headlineText = if (e.isBottle && e.amountMl != null) {
                "Bottle · ${TimeUtils.formatAmountMl(e.amountMl)}"
            } else {
                val dur = e.endTimeMillis?.let { TimeUtils.formatDurationShort(it - e.startTimeMillis) }
                "Nursing (${e.type.label()})" + (dur?.let { " · $it" } ?: " · in progress")
            }
            Triple(
                Icons.Filled.Restaurant,
                headlineText,
                listOfNotNull(TimeUtils.formatDateTime(e.startTimeMillis), e.note.ifBlank { null })
            )
        }
        is TimelineItem.DiaperItem -> {
            val e = item.entry
            Triple(
                Icons.Filled.BabyChangingStation,
                e.type.label(),
                listOfNotNull(TimeUtils.formatDateTime(e.timeMillis), e.note.ifBlank { null })
            )
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(headline) },
            supportingContent = {
                Column {
                    supporting.forEach { Text(it) }
                }
            },
            leadingContent = { Icon(icon, contentDescription = null) },
            trailingContent = {
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            }
        )
    }
}
