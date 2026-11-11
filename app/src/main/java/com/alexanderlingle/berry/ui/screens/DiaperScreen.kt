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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.alexanderlingle.berry.data.DiaperType
import com.alexanderlingle.berry.ui.components.DateTimeInput
import com.alexanderlingle.berry.ui.components.DeleteConfirmDialog
import com.alexanderlingle.berry.ui.components.EmptyState
import com.alexanderlingle.berry.ui.components.TypeSelector
import com.alexanderlingle.berry.ui.components.label
import com.alexanderlingle.berry.ui.components.rememberBerryFactory
import com.alexanderlingle.berry.util.TimeUtils
import com.alexanderlingle.berry.viewmodel.DiaperViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaperScreen() {
    val factory = rememberBerryFactory()
    val vm: DiaperViewModel = viewModel(factory = factory)
    val entries by vm.entries.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<DiaperEntry?>(null) }
    var deleting by remember { mutableStateOf<DiaperEntry?>(null) }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "quick-log") {
                QuickLogCard(onQuickLog = vm::quickLog)
            }

            if (entries.isEmpty()) {
                item(key = "empty") {
                    EmptyState("No diapers logged yet", Icons.Filled.BabyChangingStation)
                }
            }
            items(entries, key = { it.id }) { entry ->
                DiaperRow(
                    entry = entry,
                    onEdit = { editing = entry },
                    onDelete = { deleting = entry }
                )
            }
        }
    }

    editing?.let { target ->
        DiaperEditorDialog(
            initial = target,
            onDismiss = { editing = null },
            onSave = {
                vm.save(it)
                editing = null
            },
            onDelete = {
                vm.delete(target)
                editing = null
            }
        )
    }

    deleting?.let { target ->
        DeleteConfirmDialog(
            label = "this ${target.type.label().lowercase()} diaper",
            onDismiss = { deleting = null },
            onConfirm = {
                vm.delete(target)
                deleting = null
            }
        )
    }
}

@Composable
private fun QuickLogCard(onQuickLog: (DiaperType) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Quick log", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = { onQuickLog(DiaperType.WET) },
                    modifier = Modifier.weight(1f)
                ) { Text("Wet") }
                FilledTonalButton(
                    onClick = { onQuickLog(DiaperType.DIRTY) },
                    modifier = Modifier.weight(1f)
                ) { Text("Dirty") }
                FilledTonalButton(
                    onClick = { onQuickLog(DiaperType.MIXED) },
                    modifier = Modifier.weight(1f)
                ) { Text("Mixed") }
            }
            Text(
                "Logs right now. Tap an entry below to change its time or add a note.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DiaperRow(
    entry: DiaperEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(entry.type.label()) },
            supportingContent = {
                Column {
                    Text(TimeUtils.formatDateTime(entry.timeMillis))
                    if (entry.note.isNotBlank()) Text(entry.note)
                }
            },
            leadingContent = {
                Icon(Icons.Filled.BabyChangingStation, contentDescription = null)
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
fun DiaperEditorDialog(
    initial: DiaperEntry,
    onDismiss: () -> Unit,
    onSave: (DiaperEntry) -> Unit,
    onDelete: () -> Unit
) {
    var type by remember { mutableStateOf(initial.type) }
    var timeMillis by remember { mutableStateOf(initial.timeMillis) }
    var note by remember { mutableStateOf(initial.note) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit diaper") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeSelector(
                    options = listOf(
                        DiaperType.WET to "Wet",
                        DiaperType.DIRTY to "Dirty",
                        DiaperType.MIXED to "Mixed"
                    ),
                    selected = type,
                    onSelect = { type = it }
                )
                DateTimeInput(label = "Time", valueMillis = timeMillis, onValueChange = { timeMillis = it })
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = onDelete) {
                    Text("Delete entry", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(initial.copy(type = type, timeMillis = timeMillis, note = note.trim()))
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
