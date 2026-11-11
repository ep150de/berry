package com.alexanderlingle.berry.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexanderlingle.berry.data.BerryRepository
import com.alexanderlingle.berry.data.Child
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class ChildViewModel(private val repo: BerryRepository) : ViewModel() {
    val children: StateFlow<List<Child>> = repo.children
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedChild: StateFlow<Child?> = repo.selectedChild

    init {
        viewModelScope.launch { repo.ensureDefaultChild() }
    }

    fun select(id: Long) {
        viewModelScope.launch { repo.selectChild(id) }
    }

    /**
     * Creates or updates a child. When [photoSource] is non-null the image is
     * copied into app-private storage (no storage permission needed) and the
     * private path is stored on the child.
     */
    fun saveChild(
        existing: Child?,
        name: String,
        birthdateMillis: Long?,
        photoSource: Uri?,
        context: Context
    ) {
        viewModelScope.launch {
            val photoPath = photoSource?.let { copyPhotoToPrivateStorage(context, it) }
            if (existing == null) {
                repo.addChild(
                    Child(
                        name = name.trim(),
                        photoPath = photoPath,
                        birthdateMillis = birthdateMillis
                    )
                )
            } else {
                repo.updateChild(
                    existing.copy(
                        name = name.trim(),
                        photoPath = photoPath ?: existing.photoPath,
                        birthdateMillis = birthdateMillis
                    )
                )
            }
        }
    }

    fun clearPhoto(child: Child) {
        viewModelScope.launch {
            child.photoPath?.let { runCatching { File(it).delete() } }
            repo.updateChild(child.copy(photoPath = null))
        }
    }

    fun delete(child: Child) {
        viewModelScope.launch { repo.deleteChild(child) }
    }

    private fun copyPhotoToPrivateStorage(context: Context, uri: Uri): String? {
        return try {
            val dir = File(context.filesDir, "child_photos").apply { mkdirs() }
            val dest = File(dir, "${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            dest.absolutePath
        } catch (_: Exception) {
            null
        }
    }
}
