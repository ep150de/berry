package com.alexanderlingle.berry.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alexanderlingle.berry.data.Child
import com.alexanderlingle.berry.ui.components.ChildAvatar
import com.alexanderlingle.berry.ui.components.DateInput
import com.alexanderlingle.berry.ui.components.DeleteConfirmDialog
import com.alexanderlingle.berry.ui.components.rememberBerryFactory
import com.alexanderlingle.berry.util.TimeUtils
import com.alexanderlingle.berry.viewmodel.ChildViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildrenScreen(onBack: () -> Unit) {
    val factory = rememberBerryFactory()
    val vm: ChildViewModel = viewModel(factory = factory)
    val children by vm.children.collectAsStateWithLifecycle()
    val selected by vm.selectedChild.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<Child?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Child?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Children") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add child")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(children, key = { it.id }) { child ->
                ChildRow(
                    child = child,
                    isSelected = child.id == selected?.id,
                    canDelete = children.size > 1,
                    onSelect = { vm.select(child.id) },
                    onEdit = { editing = child },
                    onDelete = { deleting = child }
                )
            }
            item {
                Text(
                    "Tap a child to switch. Each child keeps their own logs, history, and stats.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }

    if (showAdd) {
        ChildEditDialog(
            existing = null,
            onDismiss = { showAdd = false },
            onSave = { name, birthdate, photoUri, context ->
                vm.saveChild(null, name, birthdate, photoUri, context)
                showAdd = false
            }
        )
    }
    editing?.let { child ->
        ChildEditDialog(
            existing = child,
            onDismiss = { editing = null },
            onSave = { name, birthdate, photoUri, context ->
                vm.saveChild(child, name, birthdate, photoUri, context)
                editing = null
            },
            onRemovePhoto = { vm.clearPhoto(child) }
        )
    }
    deleting?.let { child ->
        DeleteConfirmDialog(
            label = "${child.name}'s profile and all of their logs",
            onDismiss = { deleting = null },
            onConfirm = {
                vm.delete(child)
                deleting = null
            }
        )
    }
}

@Composable
private fun ChildRow(
    child: Child,
    isSelected: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChildAvatar(photoPath = child.photoPath, name = child.name, size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(child.name, style = MaterialTheme.typography.titleMedium)
                    if (isSelected) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    child.birthdateMillis?.let {
                        "${TimeUtils.formatAge(it)} · Born ${TimeUtils.formatBirthdate(it)}"
                    } ?: "Birthdate not set",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit ${child.name}")
            }
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete ${child.name}")
                }
            }
        }
    }
}

@Composable
private fun ChildEditDialog(
    existing: Child?,
    onDismiss: () -> Unit,
    onSave: (name: String, birthdateMillis: Long?, photoUri: Uri?, context: android.content.Context) -> Unit,
    onRemovePhoto: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var birthdate by remember { mutableStateOf(existing?.birthdateMillis) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var photoRemoved by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) photoUri = uri }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add child" else "Edit child") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (photoUri != null) {
                        coil.compose.AsyncImage(
                            model = photoUri,
                            contentDescription = "New photo",
                            modifier = Modifier
                                .width(56.dp)
                                .height(56.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        ChildAvatar(
                            photoPath = if (photoRemoved) null else existing?.photoPath,
                            name = name.ifBlank { "?" },
                            size = 56.dp
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        TextButton(
                            onClick = {
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        ) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (photoUri != null) "Photo selected ✓" else "Choose photo")
                        }
                        if (existing?.photoPath != null && !photoRemoved && onRemovePhoto != null) {
                            TextButton(onClick = {
                                onRemovePhoto()
                                photoRemoved = true
                            }) { Text("Remove photo") }
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                DateInput(
                    label = "Birthdate",
                    valueMillis = birthdate,
                    onValueChange = { birthdate = it }
                )
                if (existing == null) {
                    Text(
                        "Logs you already have will stay with your first child.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, birthdate, photoUri, context) },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
