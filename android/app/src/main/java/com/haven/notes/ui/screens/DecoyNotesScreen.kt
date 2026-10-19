package com.haven.notes.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haven.notes.data.DailyNote
import com.haven.notes.ui.components.PinDialog
import com.haven.notes.ui.theme.CardBackground
import com.haven.notes.ui.theme.DecoyBg
import com.haven.notes.ui.theme.DecoyHeader
import com.haven.notes.ui.theme.Ink
import com.haven.notes.ui.theme.SageAccent
import com.haven.notes.ui.theme.TextSecondary
import com.haven.notes.viewmodel.DecoyNotesViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DecoyNotesScreen(
    viewModel: DecoyNotesViewModel,
    onUnlock: () -> Unit
) {
    val notes by viewModel.notes.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filteredNotes = viewModel.getFilteredNotes()

    var showPinDialog by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<DailyNote?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }

    if (showPinDialog) {
        PinDialog(
            correctPin = viewModel.secretPin,
            onDismiss = { showPinDialog = false },
            onUnlocked = {
                showPinDialog = false
                onUnlock()
            }
        )
    }

    if (isCreatingNew || editingNote != null) {
        NoteEditorDialog(
            note = editingNote,
            onDismiss = {
                editingNote = null
                isCreatingNew = false
            },
            onSave = { title, content ->
                val current = editingNote
                if (current != null) {
                    viewModel.updateNote(current.id, title, content)
                } else {
                    viewModel.addNote(title, content)
                }
                editingNote = null
                isCreatingNew = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(
                        modifier = Modifier
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { showPinDialog = true }
                            )
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = "Notes",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = DecoyHeader
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { isCreatingNew = true }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "New Note",
                            tint = DecoyHeader
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DecoyBg
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { isCreatingNew = true },
                containerColor = SageAccent,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Note")
            }
        },
        containerColor = DecoyBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Search Bar (Stealth PIN entry)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    viewModel.onSearchQueryChanged(it, onUnlock)
                },
                placeholder = { Text("Search notes", color = TextSecondary) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondary)
                },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    containerColor = CardBackground,
                    unfocusedBorderColor = Color(0xFFE8E4DC),
                    focusedBorderColor = SageAccent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredNotes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isEmpty()) "No notes yet. Tap + to write one." else "No matching notes found.",
                        color = TextSecondary,
                        fontSize = 15.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredNotes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            onClick = { editingNote = note },
                            onDelete = { viewModel.deleteNote(note.id) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun NoteCard(
    note: DailyNote,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val formattedDate = DateFormat.getDateFormat(context).format(note.date)

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formattedDate,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = note.content,
                        fontSize = 13.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = TextSecondary.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun NoteEditorDialog(
    note: DailyNote?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by remember { mutableStateOf(note?.title ?: "") }
    var content by remember { mutableStateOf(note?.content ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (note == null) "New Note" else "Edit Note",
                fontWeight = FontWeight.Bold,
                color = Ink
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Note content") },
                    minLines = 4,
                    maxLines = 8,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, content) },
                colors = ButtonDefaults.buttonColors(containerColor = SageAccent)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Ink)
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
