package com.haven.notes.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.haven.notes.data.DailyNote
import com.haven.notes.data.Storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DecoyNotesViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = Storage(application)

    private val _notes = MutableStateFlow<List<DailyNote>>(emptyList())
    val notes: StateFlow<List<DailyNote>> = _notes.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val secretPin: String
        get() = storage.decoyPin

    init {
        loadNotes()
    }

    fun loadNotes() {
        _notes.value = storage.getDailyNotes()
    }

    fun onSearchQueryChanged(query: String, onPinMatched: () -> Unit) {
        _searchQuery.value = query
        if (query.trim() == storage.decoyPin) {
            _searchQuery.value = ""
            onPinMatched()
        }
    }

    fun getFilteredNotes(): List<DailyNote> {
        val query = _searchQuery.value.trim().lowercase()
        if (query.isEmpty()) return _notes.value
        return _notes.value.filter {
            it.title.lowercase().contains(query) || it.content.lowercase().contains(query)
        }
    }

    fun addNote(title: String, content: String) {
        val newNote = DailyNote(
            title = title.ifBlank { "Untitled Note" },
            content = content
        )
        val updated = listOf(newNote) + _notes.value
        _notes.value = updated
        storage.saveDailyNotes(updated)
    }

    fun updateNote(id: String, title: String, content: String) {
        val updated = _notes.value.map {
            if (it.id == id) it.copy(title = title, content = content) else it
        }
        _notes.value = updated
        storage.saveDailyNotes(updated)
    }

    fun deleteNote(id: String) {
        val updated = _notes.value.filter { it.id != id }
        _notes.value = updated
        storage.saveDailyNotes(updated)
    }
}
