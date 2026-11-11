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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restaurant
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alexanderlingle.berry.data.FeedingEntry
import com.alexanderlingle.berry.data.FeedingType
import com.alexanderlingle.berry.ui.components.DateTimeInput
import com.alexanderlingle.berry.ui.components.DeleteConfirmDialog
import com.alexanderlingle.berry.ui.components.ElapsedTimeText
import com.alexanderlingle.berry.ui.components.EmptyState
import com.alexanderlingle.berry.ui.components.TypeSelector
import com.alexanderlingle.berry.ui.components.label
import com.alexanderlingle.berry.ui.components.rememberBerryFactory
import com.alexanderlingle.berry.util.TimeUtils
import com.alexanderlingle.berry.viewmodel.FeedingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedingScreen() {
    val factory = rememberBerryFactory()
    val vm: FeedingViewModel = viewModel(factory = factory)
    val entries by vm.entries.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()

    var showNursingEditor by remember { mutableStateOf(false) }
    var showBottleDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<FeedingEntry?>(null) }
    var deleting by remember { mutableStateOf<FeedingEntry?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showBottleDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Log bottle")
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
                    RunningNursingCard(entry = active, onStop = vm::stopRunning)
                }
            } else {
                item(key = "start") {
                    val suggested by vm.suggestedNursingSide.collectAsStateWithLifecycle()
                    StartNursingCard(suggestedSide = suggested, onStart = vm::startNursing)
                }
            }

            val past = entries.filter { it.id != active?.id }
            if (past.isEmpty() && active == null) {
                item(key = "empty") {
                    EmptyState("No feedings logged yet", Icons.Filled.Restaurant)
                }
            }
            items(past, key = { it.id }) { entry ->
                FeedingRow(
                    entry = entry,
                    onEdit = {
                        editing = entry
                        if (entry.isBottle) showBottleDialog = true else showNursingEditor = true
                    },
                    onDelete = { deleting = entry }
                )
            }
        }
    }

    if (showNursingEditor) {
        NursingEditorDialog(
            initial = editing,
            onDismiss = {
                showNursingEditor = false
                editing = null
            },
            onSave = {
                vm.save(it)
                showNursingEditor = false
                editing = null
            },
            onDelete = editing?.let { target ->
                {
                    vm.delete(target)
                    showNursingEditor = false
                    editing = null
                }
            }
        )
    }

    if (showBottleDialog) {
        BottleDialog(
            initial = editing?.takeIf { it.isBottle },
            onDismiss = {
                showBottleDialog = false
                editing = null
            },
            onSave = { amountMl, timeMillis, note ->
                val target = editing
                if (target != null) {
                    vm.save(target.copy(amountMl = amountMl, startTimeMillis = timeMillis, endTimeMillis = timeMillis, note = note))
                } else {
                    vm.logBottle(amountMl, timeMillis, note)
                }
                showBottleDialog = false
                editing = null
            },
            onDelete = editing?.let { target ->
                {
                    vm.delete(target)
                    showBottleDialog = false
                    editing = null
                }
            }
        )
    }

    deleting?.let { target ->
        DeleteConfirmDialog(
            label = "this ${target.type.label().lowercase()} feeding",
            onDismiss = { deleting = null },
            onConfirm = {
                vm.delete(target)
                deleting = null
            }
        )
    }
}

@Composable
private fun StartNursingCard(suggestedSide: FeedingType, onStart: (FeedingType) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Start nursing timer", style = MaterialTheme.typography.titleMedium)
            Text(
                "Suggested: ${if (suggestedSide == FeedingType.NURSING_LEFT) "left" else "right"} side next",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (suggestedSide == FeedingType.NURSING_LEFT) {
                    Button(
                        onClick = { onStart(FeedingType.NURSING_LEFT) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Left") }
                    OutlinedButton(
                        onClick = { onStart(FeedingType.NURSING_RIGHT) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Right") }
                } else {
                    OutlinedButton(
                        onClick = { onStart(FeedingType.NURSING_LEFT) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Left") }
                    Button(
                        onClick = { onStart(FeedingType.NURSING_RIGHT) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Right") }
                }
            }
            Text(
                "Or tap + to log a bottle.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RunningNursingCard(entry: FeedingEntry, onStop: () -> Unit) {
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
            Icon(Icons.Filled.Restaurant, contentDescription = null, tint = onContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Nursing (${entry.type.label()})",
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
private fun FeedingRow(
    entry: FeedingEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val headline = if (entry.isBottle && entry.amountMl != null) {
        "Bottle · ${TimeUtils.formatAmountMl(entry.amountMl)}"
    } else {
        val duration = entry.endTimeMillis?.let { TimeUtils.formatDurationShort(it - entry.startTimeMillis) }
        "Nursing (${entry.type.label()})" + (duration?.let { " · $it" } ?: "")
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(headline) },
            supportingContent = {
                Column {
                    Text(TimeUtils.formatDateTime(entry.startTimeMillis))
                    if (entry.note.isNotBlank()) Text(entry.note)
                }
            },
            leadingContent = {
                Icon(Icons.Filled.Restaurant, contentDescription = null)
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
fun NursingEditorDialog(
    initial: FeedingEntry?,
    onDismiss: () -> Unit,
    onSave: (FeedingEntry) -> Unit,
    onDelete: (() -> Unit)?
) {
    val now = remember { System.currentTimeMillis() }
    var type by remember {
        mutableStateOf(
            initial?.type?.takeIf { it != FeedingType.BOTTLE } ?: FeedingType.NURSING_LEFT
        )
    }
    var startMillis by remember { mutableStateOf(initial?.startTimeMillis ?: (now - 15 * 60_000)) }
    var endMillis by remember {
        mutableStateOf<Long?>(if (initial == null) now else initial.endTimeMillis)
    }
    var note by remember { mutableStateOf(initial?.note ?: "") }

    val valid = endMillis == null || endMillis!! > startMillis

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Log nursing" else "Edit nursing") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeSelector(
                    options = listOf(
                        FeedingType.NURSING_LEFT to "Left",
                        FeedingType.NURSING_RIGHT to "Right"
                    ),
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
                    TextButton(onClick = { endMillis = null }) { Text("Clear end (still nursing)") }
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
                        FeedingEntry(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottleDialog(
    initial: FeedingEntry?,
    onDismiss: () -> Unit,
    onSave: (amountMl: Double, timeMillis: Long, note: String) -> Unit,
    onDelete: (() -> Unit)?
) {
    val now = remember { System.currentTimeMillis() }
    var useOz by remember {
        mutableStateOf(false)
    }
    var amountText by remember {
        mutableStateOf(
            initial?.amountMl?.let {
                if (it >= 100) it.toInt().toString() else "%.1f".format(it)
            } ?: ""
        )
    }
    var timeMillis by remember { mutableStateOf(initial?.startTimeMillis ?: now) }
    var note by remember { mutableStateOf(initial?.note ?: "") }

    val amount = amountText.toDoubleOrNull()?.takeIf { it > 0 }
    val valid = amount != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Log bottle" else "Edit bottle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeSelector(
                    options = listOf(false to "Milliliters", true to "Ounces"),
                    selected = useOz,
                    onSelect = { useOz = it }
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(if (useOz) "Amount (oz)" else "Amount (ml)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    isError = amountText.isNotBlank() && !valid
                )
                DateTimeInput(label = "Time", valueMillis = timeMillis, onValueChange = { timeMillis = it })
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
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
                    val ml = if (useOz) TimeUtils.ozToMl(amount!!) else amount!!
                    onSave(ml, timeMillis, note.trim())
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
