package com.alexanderlingle.berry.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alexanderlingle.berry.ui.components.rememberBerryFactory
import com.alexanderlingle.berry.viewmodel.AskBerryViewModel
import com.alexanderlingle.berry.viewmodel.ModelStatus

/**
 * Berry v3 — "Ask Berry" screen.
 *
 * States, in plain language:
 * - model not downloaded: explain the one-time Wi-Fi download, offer it.
 * - downloading: show progress; the file is ~1.1 GB, so this takes a while.
 * - ready: suggested questions + free text, answers generated on the phone.
 * - low RAM / engine missing / error: say exactly what is wrong and what
 *   would fix it. The app never pretends the AI works when it doesn't.
 */
@Composable
fun AskBerryScreen() {
    val factory = rememberBerryFactory()
    val vm: AskBerryViewModel = viewModel(factory = factory)
    val state by vm.uiState.collectAsStateWithLifecycle()
    var question by remember { mutableStateOf("") }
    var confirmCellular by remember { mutableStateOf(false) }

    // Free the ~1 GB model when the user leaves this screen.
    DisposableEffect(Unit) {
        onDispose { vm.unloadModel() }
    }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Ask Berry", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Answers are generated on your phone — nothing about " +
                            "${state.childName} or your logs ever leaves this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when (val status = state.status) {
                is ModelStatus.Checking -> {
                    item(key = "checking") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator()
                            Spacer(Modifier.width(12.dp))
                            Text("Checking the on-device model…")
                        }
                    }
                }

                is ModelStatus.NotDownloaded -> {
                    item(key = "download") {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Get the on-device brain",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "Ask Berry runs a small AI model (LFM3-1.7B, " +
                                        "about 1.1 GB) entirely on your phone. " +
                                        "Download it once over Wi-Fi; after that, " +
                                        "everything works offline, even in airplane mode.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(onClick = { vm.downloadModel(allowCellular = false) }) {
                                    androidx.compose.material3.Icon(
                                        Icons.Filled.Download,
                                        contentDescription = null
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Download over Wi-Fi")
                                }
                                if (confirmCellular) {
                                    Text(
                                        "Cellular download is about 1.1 GB and may " +
                                            "count against your data plan.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Button(onClick = { vm.downloadModel(allowCellular = true) }) {
                                        Text("Download anyway (cellular)")
                                    }
                                } else {
                                    TextButton(onClick = { confirmCellular = true }) {
                                        Text("Not on Wi-Fi? Use cellular")
                                    }
                                }
                            }
                        }
                    }
                }

                is ModelStatus.Downloading -> {
                    item(key = "downloading") {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Downloading the model…",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (status.progress >= 0) {
                                    LinearProgressIndicator(
                                        progress = status.progress,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        "${(status.progress * 100).toInt()}% — about 1.1 GB total. " +
                                            "Keep the app open; this takes a few minutes on Wi-Fi.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    Text(
                                        "Downloading… (size unknown, about 1.1 GB total)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                is ModelStatus.LowRam -> {
                    item(key = "lowram") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "This phone can't run the model",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(status.reason, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Everything else in Berry works normally — " +
                                        "only Ask Berry is unavailable here.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                is ModelStatus.EngineMissing -> {
                    item(key = "enginemissing") {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "AI engine not installed yet",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "The model downloaded, but the app build is missing " +
                                        "its native AI engine (libberry_llama.so). " +
                                        "This build of Berry was made without the " +
                                        "engine library — it needs to be added to the " +
                                        "app's jniLibs folder and the app rebuilt. " +
                                        "See app/src/main/cpp/README.md.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedButton(onClick = { vm.refreshStatus() }) {
                                    Text("Check again")
                                }
                            }
                        }
                    }
                }

                is ModelStatus.Error -> {
                    item(key = "error") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Something went wrong",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(status.message, style = MaterialTheme.typography.bodyMedium)
                                OutlinedButton(onClick = { vm.refreshStatus() }) {
                                    Text("Back")
                                }
                            }
                        }
                    }
                }

                is ModelStatus.Ready -> {
                    if (state.thinData) {
                        item(key = "thindata") {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "Tip: log a few more days of sleep, feeds, and " +
                                        "diapers and Berry's answers get personal — " +
                                        "right now it can only speak in general terms " +
                                        "because it hasn't learned ${state.childName}'s " +
                                        "patterns yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                    if (state.suggestedQuestions.isNotEmpty()) {
                        item(key = "suggested") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Try asking",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(state.suggestedQuestions) { suggested ->
                                        AssistChip(
                                            onClick = { vm.ask(suggested) },
                                            label = { Text(suggested) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item(key = "askbox") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = question,
                                onValueChange = { question = it },
                                label = { Text("Ask about ${state.childName}") },
                                placeholder = { Text("e.g. Any sleep tips for this age?") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = false,
                                maxLines = 3
                            )
                            Button(
                                onClick = {
                                    vm.ask(question)
                                    question = ""
                                },
                                enabled = question.isNotBlank() && !state.isGenerating,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                androidx.compose.material3.Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = null
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(if (state.isGenerating) "Thinking…" else "Ask Berry")
                            }
                        }
                    }
                    if (state.isGenerating) {
                        item(key = "generating") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator()
                                Spacer(Modifier.width(12.dp))
                                Text("Berry is thinking on-device…")
                            }
                        }
                    }
                    state.lastQuestion?.let { asked ->
                        state.answer?.let { answer ->
                            item(key = "answer") {
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            "Q: $asked",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            answer,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            "Generated on your phone. General " +
                                                "information only — not medical advice. " +
                                                "For health concerns, contact your pediatrician.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                    item(key = "freemodel") {
                        TextButton(
                            onClick = { vm.deleteModel() }
                        ) {
                            androidx.compose.material3.Icon(
                                Icons.Filled.Delete,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Delete downloaded model")
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Frees about 1.1 GB. Ask Berry will offer the " +
                                "download again afterwards.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
