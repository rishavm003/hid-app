package com.haven.notes.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haven.notes.data.DailyNote
import com.haven.notes.ui.components.PinDialog
import com.haven.notes.ui.theme.CardBackground
import com.haven.notes.ui.theme.DecoyBg
import com.haven.notes.ui.theme.DecoyHeader
import com.haven.notes.ui.theme.DecoySearchBorder
import com.haven.notes.ui.theme.Ink
import com.haven.notes.ui.theme.NotePalettes
import com.haven.notes.ui.theme.PastelRose
import com.haven.notes.ui.theme.TextPrimary
import com.haven.notes.ui.theme.TextSecondary
import com.haven.notes.ui.theme.getNotePalette
import com.haven.notes.viewmodel.DecoyNotesViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DecoyNotesScreen(
    viewModel: DecoyNotesViewModel,
    onUnlock: () -> Unit
) {
    val notes by viewModel.notes.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
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
            onSave = { title, content, colorIndex ->
                val current = editingNote
                if (current != null) {
                    viewModel.updateNote(current.id, title, content, colorIndex)
                } else {
                    viewModel.addNote(title, content, colorIndex)
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Notes",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = DecoyHeader
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "🌸",
                                fontSize = 20.sp
                            )
                        }
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
                containerColor = PastelRose,
                contentColor = Color.White,
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Note", modifier = Modifier.size(26.dp))
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
                placeholder = { Text("Search notes or recipes... 🎀", color = TextSecondary) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondary)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("", onUnlock) }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    containerColor = CardBackground,
                    unfocusedBorderColor = DecoySearchBorder,
                    focusedBorderColor = PastelRose
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )

            // Category Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                item {
                    val isAll = selectedCategory == null
                    FilterChip(
                        selected = isAll,
                        onClick = { viewModel.setSelectedCategory(null) },
                        label = { Text("All 🌸", fontSize = 12.sp, fontWeight = if (isAll) FontWeight.Bold else FontWeight.Normal) },
                        shape = RoundedCornerShape(16.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PastelRose.copy(alpha = 0.2f),
                            selectedLabelColor = DecoyHeader,
                            containerColor = CardBackground,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (isAll) PastelRose else DecoySearchBorder
                        )
                    )
                }
                items(NotePalettes) { palette ->
                    val isSelected = selectedCategory == palette.id
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            viewModel.setSelectedCategory(if (isSelected) null else palette.id)
                        },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(palette.emoji, fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    palette.name,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.bg,
                            selectedLabelColor = palette.accent,
                            containerColor = CardBackground,
                            labelColor = TextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (isSelected) palette.accent else DecoySearchBorder
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (filteredNotes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = if (searchQuery.isEmpty()) "🌸" else "🔍",
                            fontSize = 42.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) "Your pastel journal is empty." else "No matching notes found.",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) "Tap + to write down recipes, thoughts, or checklists!" else "Try a different search word.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
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
    val palette = getNotePalette(note.colorIndex)

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = palette.bg),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.2.dp, palette.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cute vertical accent strip
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(palette.accent)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = note.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(text = palette.emoji, fontSize = 13.sp)
                }

                if (note.content.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = note.content,
                        fontSize = 13.sp,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 17.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Pill date tag
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.accent.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = formattedDate,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.accent
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = palette.name,
                        fontSize = 11.sp,
                        color = TextSecondary
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
    onSave: (String, String, Int) -> Unit
) {
    var title by remember { mutableStateOf(note?.title ?: "") }
    var content by remember { mutableStateOf(note?.content ?: "") }
    var selectedColorIndex by remember { mutableIntStateOf(note?.colorIndex ?: 0) }

    val currentPalette = getNotePalette(selectedColorIndex)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (note == null) "New Note 🌸" else "Edit Note 🎀",
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    fontSize = 20.sp
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Color theme selector
                Text(
                    text = "Pick a palette style:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Ink
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    NotePalettes.forEach { palette ->
                        val isSelected = selectedColorIndex == palette.id
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(palette.bg)
                                .border(
                                    BorderStroke(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) palette.accent else palette.border
                                    ),
                                    CircleShape
                                )
                                .clickable { selectedColorIndex = palette.id }
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = palette.name,
                                    tint = palette.accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Text(palette.emoji, fontSize = 14.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    placeholder = { Text("e.g., Weekend plans 🌸") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = currentPalette.accent,
                        unfocusedBorderColor = currentPalette.border
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Note content") },
                    placeholder = { Text("Write your thoughts, lists, or recipes here...") },
                    minLines = 4,
                    maxLines = 8,
                    shape = RoundedCornerShape(14.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = currentPalette.accent,
                        unfocusedBorderColor = currentPalette.border
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, content, selectedColorIndex) },
                colors = ButtonDefaults.buttonColors(containerColor = PastelRose),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Save Note 🌸", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Ink)
            }
        },
        containerColor = CardBackground,
        shape = RoundedCornerShape(24.dp)
    )
}
