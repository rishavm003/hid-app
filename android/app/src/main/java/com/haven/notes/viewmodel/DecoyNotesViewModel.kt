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

    private val _selectedCategory = MutableStateFlow<Int?>(null)
    val selectedCategory: StateFlow<Int?> = _selectedCategory.asStateFlow()

    fun setSelectedCategory(index: Int?) {
        _selectedCategory.value = index
    }

    fun getFilteredNotes(): List<DailyNote> {
        val query = _searchQuery.value.trim().lowercase()
        val cat = _selectedCategory.value
        return _notes.value.filter { note ->
            val matchesQuery = query.isEmpty() ||
                note.title.lowercase().contains(query) ||
                note.content.lowercase().contains(query)
            val matchesCategory = (cat == null || note.colorIndex == cat)
            matchesQuery && matchesCategory
        }
    }

    fun addNote(title: String, content: String, colorIndex: Int = 0) {
        val newNote = DailyNote(
            title = title.ifBlank { "Untitled Note" },
            content = content,
            colorIndex = colorIndex
        )
        val updated = listOf(newNote) + _notes.value
        _notes.value = updated
        storage.saveDailyNotes(updated)
    }

    fun updateNote(id: String, title: String, content: String, colorIndex: Int = 0) {
        val updated = _notes.value.map {
            if (it.id == id) it.copy(title = title, content = content, colorIndex = colorIndex) else it
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
