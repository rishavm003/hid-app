package com.haven.notes.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haven.notes.ui.theme.BackgroundLight
import com.haven.notes.ui.theme.CardBackground
import com.haven.notes.ui.theme.CrimsonSOS
import com.haven.notes.ui.theme.Ink
import com.haven.notes.ui.theme.SageAccent
import com.haven.notes.ui.theme.TextPrimary
import com.haven.notes.ui.theme.TextSecondary
import com.haven.notes.viewmodel.FamilyViewModel
import com.haven.notes.viewmodel.SafetyViewModel
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyScreen(
    familyViewModel: FamilyViewModel,
    safetyViewModel: SafetyViewModel,
    onLockToDecoy: () -> Unit
) {
    val session by familyViewModel.session.collectAsState()
    val message by familyViewModel.message.collectAsState()
    val busy by familyViewModel.busy.collectAsState()
    val lastUpload by familyViewModel.lastUpload.collectAsState()
    val reviewedController by familyViewModel.reviewedController.collectAsState()

    val context = LocalContext.current

    // Join form inputs
    var setupCodeInput by remember { mutableStateOf("") }
    var originInput by remember { mutableStateOf("") }
    var codeInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var joinConsent by remember { mutableStateOf(false) }

    // Dialogs
    var showEnableConfirm by remember { mutableStateOf(false) }
    var showEraseConfirm by remember { mutableStateOf(false) }
    var showNoteConfirm by remember { mutableStateOf(false) }
    var showReconnectConfirm by remember { mutableStateOf(false) }

    // Call note inputs
    var noteText by remember { mutableStateOf("") }

    // Reconnect input
    var newServerOrigin by remember { mutableStateOf("") }

    // Enable sharing confirmation dialog
    if (showEnableConfirm) {
        AlertDialog(
            onDismissRequest = { showEnableConfirm = false },
            title = { Text("Enable Location Sharing?", color = Ink, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Share your location with ${session?.controller ?: "your controller"} approximately hourly, including in the background?",
                    color = Ink
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEnableConfirm = false
                        familyViewModel.enable(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SageAccent)
                ) {
                    Text("Enable Sharing")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEnableConfirm = false }) { Text("Cancel", color = Ink) }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Erase confirmation dialog
    if (showEraseConfirm) {
        AlertDialog(
            onDismissRequest = { showEraseConfirm = false },
            title = { Text("Erase Shared Data?", color = CrimsonSOS, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Stop sharing and erase your last family location and all manual notes from the server?",
                    color = Ink
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEraseConfirm = false
                        familyViewModel.erase(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonSOS)
                ) {
                    Text("Erase Shared Data")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEraseConfirm = false }) { Text("Cancel", color = Ink) }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Share note confirmation dialog
    if (showNoteConfirm) {
        AlertDialog(
            onDismissRequest = { showNoteConfirm = false },
            title = { Text("Share Call Note?", color = Ink, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Share this manually entered note with ${session?.controller ?: "your controller"}?",
                    color = Ink
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNoteConfirm = false
                        familyViewModel.shareNote(noteText, Date()) { success ->
                            if (success) noteText = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SageAccent)
                ) {
                    Text("Share Note")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoteConfirm = false }) { Text("Cancel", color = Ink) }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Reconnect confirmation dialog
    if (showReconnectConfirm) {
        AlertDialog(
            onDismissRequest = { showReconnectConfirm = false },
            title = { Text("Reconnect to Controller?", color = Ink, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Only continue if this address came from your own controller. Your member credential will be sent to it to reconnect.",
                    color = Ink
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showReconnectConfirm = false
                        familyViewModel.reconnect(newServerOrigin)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SageAccent)
                ) {
                    Text("Reconnect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReconnectConfirm = false }) { Text("Cancel", color = Ink) }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Family sharing",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onLockToDecoy) {
                        Icon(Icons.Default.Lock, contentDescription = "Lock to Notes", tint = Ink)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundLight)
            )
        },
        containerColor = BackgroundLight
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            val currentSession = session

            if (currentSession != null) {
                // Already joined: Connected view
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Your family connection",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Controller", color = TextSecondary, fontSize = 14.sp)
                            Text(currentSession.controller, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Sharing", color = TextSecondary, fontSize = 14.sp)
                            Text(
                                if (currentSession.enabled) "Enabled on this phone" else "Paused on this phone",
                                fontWeight = FontWeight.SemiBold,
                                color = if (currentSession.enabled) SageAccent else TextSecondary
                            )
                        }

                        if (lastUpload != null && lastUpload!! > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val uploadDate = Date(lastUpload!!)
                            val formatted = DateFormat.getDateFormat(context).format(uploadDate) + " " +
                                    DateFormat.getTimeFormat(context).format(uploadDate)
                            Text(
                                text = "Last confirmed upload: $formatted",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (currentSession.enabled) {
                            Button(
                                onClick = { familyViewModel.shareNow(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = SageAccent),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Share my location now")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { familyViewModel.stop(context) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Stop location sharing", color = CrimsonSOS)
                            }
                        } else {
                            Button(
                                onClick = { showEnableConfirm = true },
                                enabled = !busy,
                                colors = ButtonDefaults.buttonColors(containerColor = SageAccent),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Enable hourly location sharing…")
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Android foreground location service runs with a discreet persistent indicator in the notification drawer. Updates upload at most once per hour to minimize battery impact.",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 15.sp
                        )
                    }
                }

                // Connect to Safety/SOS
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Use this connection for SOS",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Each family member has separate SOS and check-in records.",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                safetyViewModel.saveSettings(
                                    newEndpoint = currentSession.origin,
                                    newToken = currentSession.token,
                                    newConsent = false
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SageAccent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Connect Home and Circle to my family account")
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Then review contacts in Circle and enable alert consent in Setup.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                // Share a call note
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Share a call note — optional",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Share details of a phone call or check-in note with your controller. Only typed notes are shared.",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = noteText,
                            onValueChange = { if (it.length <= 1000) noteText = it },
                            placeholder = { Text("Details you choose to share", color = TextSecondary) },
                            minLines = 3,
                            maxLines = 6,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${noteText.length}/1000 characters", fontSize = 11.sp, color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { showNoteConfirm = true },
                            enabled = !busy && noteText.trim().isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = SageAccent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Review and share note…")
                        }
                    }
                }

                // Your data: Erase
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Your data",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { showEraseConfirm = true },
                            enabled = !busy,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Erase shared family location and notes…", color = CrimsonSOS)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Pausing keeps the last shared data visible. Erasing removes family location and notes from the server.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                // Reconnect after PC restart
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Reconnect after PC restart",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "If your Cloudflare tunnel address changes, stop sharing first, paste the new address from your controller, and reconnect.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = newServerOrigin,
                            onValueChange = { newServerOrigin = it },
                            placeholder = { Text("https://temporary-tunnel.trycloudflare.com") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { showReconnectConfirm = true },
                            enabled = !busy && !currentSession.enabled && newServerOrigin.trim().isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = SageAccent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Reconnect to my controller…")
                        }
                    }
                }

            } else {
                // Not joined: Join form
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Join your family",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = setupCodeInput,
                            onValueChange = { setupCodeInput = it },
                            label = { Text("Paste invitation from your controller") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val parsed = familyViewModel.parseInvitation(setupCodeInput)
                                if (parsed != null) {
                                    originInput = parsed.first
                                    codeInput = parsed.second
                                    setupCodeInput = ""
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Use invitation", color = SageAccent)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = originInput,
                            onValueChange = {
                                originInput = it
                                joinConsent = false
                            },
                            label = { Text("HTTPS server origin") },
                            placeholder = { Text("https://safety.example.com") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = codeInput,
                            onValueChange = {
                                codeInput = it
                                joinConsent = false
                            },
                            label = { Text("One-use invitation code") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Your display name") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = { familyViewModel.preview(originInput, codeInput) },
                            enabled = !busy && originInput.isNotBlank() && codeInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = SageAccent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Review invitation")
                        }

                        if (reviewedController != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Controller: $reviewedController",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Ink
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = joinConsent,
                                    onCheckedChange = { joinConsent = it },
                                    colors = CheckboxDefaults.colors(checkedColor = SageAccent)
                                )
                                Text(
                                    text = "I know this controller and choose to join. Location remains off until I enable it.",
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    familyViewModel.join(originInput, codeInput, nameInput)
                                },
                                enabled = !busy && joinConsent && nameInput.trim().isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = SageAccent),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Accept and join")
                            }
                        }
                    }
                }
            }

            // Status message Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Status",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = message,
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { familyViewModel.reconcile() },
                        enabled = !busy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Refresh family connection", color = SageAccent)
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
