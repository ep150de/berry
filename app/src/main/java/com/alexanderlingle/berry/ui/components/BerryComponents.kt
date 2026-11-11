package com.alexanderlingle.berry.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.alexanderlingle.berry.BerryApp
import com.alexanderlingle.berry.data.BerryRepository
import com.alexanderlingle.berry.util.TimeUtils
import com.alexanderlingle.berry.viewmodel.DashboardViewModel
import com.alexanderlingle.berry.viewmodel.DiaperViewModel
import com.alexanderlingle.berry.viewmodel.FeedingViewModel
import com.alexanderlingle.berry.viewmodel.HistoryViewModel
import com.alexanderlingle.berry.viewmodel.SleepViewModel
import com.alexanderlingle.berry.viewmodel.StatsViewModel
import com.alexanderlingle.berry.viewmodel.AskBerryViewModel
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Creates every screen ViewModel from the app-wide repository. */
class BerryViewModelFactory(
    private val repo: BerryRepository,
    private val appContext: android.content.Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(DashboardViewModel::class.java) -> DashboardViewModel(repo)
            modelClass.isAssignableFrom(SleepViewModel::class.java) -> SleepViewModel(repo)
            modelClass.isAssignableFrom(FeedingViewModel::class.java) -> FeedingViewModel(repo)
            modelClass.isAssignableFrom(DiaperViewModel::class.java) -> DiaperViewModel(repo)
            modelClass.isAssignableFrom(HistoryViewModel::class.java) -> HistoryViewModel(repo)
            modelClass.isAssignableFrom(StatsViewModel::class.java) -> StatsViewModel(repo)
            modelClass.isAssignableFrom(ChildViewModel::class.java) -> ChildViewModel(repo)
            // v3: Ask Berry builds its own ModelManager + llama.cpp service from the app context.
            modelClass.isAssignableFrom(AskBerryViewModel::class.java) -> AskBerryViewModel(
                repo,
                com.alexanderlingle.berry.ai.ModelManager(appContext),
                com.alexanderlingle.berry.ai.LlamaCppInferenceService()
            )
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        } as T
    }
}

@Composable
fun rememberBerryFactory(): BerryViewModelFactory {
    val context = LocalContext.current
    return remember {
        BerryViewModelFactory(
            (context.applicationContext as BerryApp).repository,
            context.applicationContext
        )
    }
}

/** Text that ticks every second showing how long ago [startTimeMillis] was. */
@Composable
fun ElapsedTimeText(
    startTimeMillis: Long,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineMedium,
    modifier: Modifier = Modifier
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    Text(
        text = TimeUtils.formatDuration((now - startTimeMillis).coerceAtLeast(0)),
        style = style,
        modifier = modifier
    )
}

@Composable
fun DeleteConfirmDialog(
    label: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete entry?") },
        text = { Text("Delete $label? This cannot be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(vertical = 4.dp)
    )
}

@Composable
fun EmptyState(message: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp)
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** A row of chips acting as a single-choice segmented control. */
@Composable
fun <T> TypeSelector(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Date-then-time picker shown as stacked Material 3 dialogs.
 * Calls [onConfirm] with the combined epoch millis in the device timezone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var step by remember { mutableStateOf(0) } // 0 = date, 1 = time
    var chosenDateMillis by remember { mutableStateOf<Long?>(null) }

    if (step == 0) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    chosenDateMillis = dateState.selectedDateMillis
                    step = 1
                }) { Text("Next") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        ) {
            DatePicker(state = dateState)
        }
    } else {
        val initialDateTime = remember(initialMillis) {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(initialMillis), ZoneId.systemDefault())
        }
        val timeState = rememberTimePickerState(
            initialHour = initialDateTime.hour,
            initialMinute = initialDateTime.minute
        )
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    val dateMillis = chosenDateMillis ?: initialMillis
                    // DatePicker reports the selected day as UTC midnight; interpret it
                    // in UTC so the displayed calendar date survives the conversion.
                    val date = Instant.ofEpochMilli(dateMillis)
                        .atZone(ZoneOffset.UTC).toLocalDate()
                    val result = date.atTime(timeState.hour, timeState.minute)
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    onConfirm(result)
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state = timeState)
                }
            }
        )
    }
}

/** Read-only text field that opens [DateTimePickerDialog] on tap. */
@Composable
fun DateTimeInput(
    label: String,
    valueMillis: Long,
    onValueChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
    ) {
        OutlinedTextField(
            value = TimeUtils.formatDateTime(valueMillis),
            onValueChange = {},
            enabled = false,
            label = { Text(label) },
            trailingIcon = {
                Icon(Icons.Filled.CalendarMonth, contentDescription = "Pick date and time")
            },
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
    if (showDialog) {
        DateTimePickerDialog(
            initialMillis = valueMillis,
            onDismiss = { showDialog = false },
            onConfirm = {
                onValueChange(it)
                showDialog = false
            }
        )
    }
}

/** Circular child photo, or an initial-letter fallback when no photo is set. */
@Composable
fun ChildAvatar(
    photoPath: String?,
    name: String,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    if (photoPath != null) {
        coil.compose.AsyncImage(
            model = java.io.File(photoPath),
            contentDescription = name,
            modifier = modifier
                .size(size)
                .clip(androidx.compose.foundation.shape.CircleShape),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
    } else {
        androidx.compose.foundation.layout.Box(
            modifier = modifier
                .size(size)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/** Read-only date field that opens a [DatePickerDialog] on tap. Null = "not set". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateInput(
    label: String,
    valueMillis: Long?,
    onValueChange: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
    ) {
        OutlinedTextField(
            value = valueMillis?.let { TimeUtils.formatBirthdate(it) } ?: "",
            onValueChange = {},
            enabled = false,
            label = { Text(label) },
            placeholder = { Text("Not set") },
            trailingIcon = {
                Icon(Icons.Filled.CalendarMonth, contentDescription = "Pick date")
            },
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
    if (showDialog) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = valueMillis)
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    onValueChange(dateState.selectedDateMillis)
                    showDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = dateState)
        }
    }
}
