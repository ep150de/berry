package com.alexanderlingle.berry.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alexanderlingle.berry.data.SleepEntry
import com.alexanderlingle.berry.data.SleepType
import com.alexanderlingle.berry.ui.components.DateTimeInput
import com.alexanderlingle.berry.ui.components.DeleteConfirmDialog
import com.alexanderlingle.berry.ui.components.ElapsedTimeText
import com.alexanderlingle.berry.ui.components.EmptyState
import com.alexanderlingle.berry.ui.components.TypeSelector
import com.alexanderlingle.berry.ui.components.label
import com.alexanderlingle.berry.ui.components.rememberBerryFactory
import com.alexanderlingle.berry.util.TimeUtils
import com.alexanderlingle.berry.viewmodel.SleepViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepScreen() {
    val factory = rememberBerryFactory()
    val vm: SleepViewModel = viewModel(factory = factory)
    val entries by vm.entries.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()

    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SleepEntry?>(null) }
    var deleting by remember { mutableStateOf<SleepEntry?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = null
                showEditor = true
            }) {
                Icon(Icons.Filled.Add, contentDescription = "Log past sleep")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val active = running
            if (active != null) {
                item(key = "running") {
                    RunningSleepCard(entry = active, onStop = vm::stopRunning)
                }
            } else {
                item(key = "start") {
                    StartSleepCard(onStart = vm::startSleep)
                }
            }

            val past = entries.filter { it.id != active?.id }
            if (past.isEmpty() && active == null) {
                item(key = "empty") {
                    EmptyState("No sleep logged yet", Icons.Filled.Bedtime)
                }
            }
            items(past, key = { it.id }) { entry ->
                SleepRow(
                    entry = entry,
                    onEdit = {
                        editing = entry
                        showEditor = true
                    },
                    onDelete = { deleting = entry }
                )
            }
        }
    }

    if (showEditor) {
        SleepEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = {
                vm.save(it)
                showEditor = false
            },
            onDelete = editing?.let { target ->
                {
                    vm.delete(target)
                    showEditor = false
                }
            }
        )
    }
    deleting?.let { target ->
        DeleteConfirmDialog(
            label = "this ${target.type.label().lowercase()} entry",
            onDismiss = { deleting = null },
            onConfirm = {
                vm.delete(target)
                deleting = null
            }
        )
    }
}

@Composable
private fun StartSleepCard(onStart: (SleepType) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Start a timer", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onStart(SleepType.NAP) },
                    modifier = Modifier.weight(1f)
                ) { Text("Start nap") }
                OutlinedButton(
                    onClick = { onStart(SleepType.NIGHT) },
                    modifier = Modifier.weight(1f)
                ) { Text("Start night") }
            }
        }
    }
}

@Composable
private fun RunningSleepCard(entry: SleepEntry, onStop: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
            Icon(Icons.Filled.Bedtime, contentDescription = null, tint = onContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${entry.type.label()} in progress",
                    style = MaterialTheme.typography.titleMedium,
                    color = onContainer
                )
                Text(
                    "Started ${TimeUtils.formatTime(entry.startTimeMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer
                )
            }
            ElapsedTimeText(
                startTimeMillis = entry.startTimeMillis,
                style = MaterialTheme.typography.titleLarge.copy(color = onContainer)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = onStop) { Text("Stop") }
        }
    }
}

@Composable
private fun SleepRow(
    entry: SleepEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val end = entry.endTimeMillis ?: return // running entries are shown separately
    Card(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = {
                Text("${entry.type.label()} · ${TimeUtils.formatDurationShort(end - entry.startTimeMillis)}")
            },
            supportingContent = {
                Column {
                    Text("${TimeUtils.formatDateTime(entry.startTimeMillis)} → ${TimeUtils.formatTime(end)}")
                    if (entry.note.isNotBlank()) Text(entry.note)
                }
            },
            leadingContent = {
                Icon(Icons.Filled.Bedtime, contentDescription = null)
            },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepEditorDialog(
    initial: SleepEntry?,
    onDismiss: () -> Unit,
    onSave: (SleepEntry) -> Unit,
    onDelete: (() -> Unit)?
) {
    val now = remember { System.currentTimeMillis() }
    var type by remember { mutableStateOf(initial?.type ?: SleepType.NAP) }
    var startMillis by remember { mutableStateOf(initial?.startTimeMillis ?: (now - 3_600_000)) }
    var endMillis by remember {
        mutableStateOf<Long?>(if (initial == null) now else initial.endTimeMillis)
    }
    var note by remember { mutableStateOf(initial?.note ?: "") }

    val valid = endMillis == null || endMillis!! > startMillis

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Log sleep" else "Edit sleep") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeSelector(
                    options = listOf(SleepType.NAP to "Nap", SleepType.NIGHT to "Night"),
                    selected = type,
                    onSelect = { type = it }
                )
                DateTimeInput(label = "Start", valueMillis = startMillis, onValueChange = { startMillis = it })
                if (endMillis != null) {
                    DateTimeInput(
                        label = "End",
                        valueMillis = endMillis!!,
                        onValueChange = { endMillis = it }
                    )
                    TextButton(onClick = { endMillis = null }) { Text("Clear end (still sleeping)") }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Still in progress",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { endMillis = System.currentTimeMillis() }) {
                            Text("Set end time")
                        }
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (!valid) {
                    Text(
                        "End time must be after the start time.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete entry", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        SleepEntry(
                            id = initial?.id ?: 0,
                            childId = initial?.childId ?: 0,
                            type = type,
                            startTimeMillis = startMillis,
                            endTimeMillis = endMillis,
                            note = note.trim()
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
