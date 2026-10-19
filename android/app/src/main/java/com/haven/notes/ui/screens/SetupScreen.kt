package com.haven.notes.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haven.notes.ui.components.StatusPanel
import com.haven.notes.ui.theme.BackgroundLight
import com.haven.notes.ui.theme.CardBackground
import com.haven.notes.ui.theme.Ink
import com.haven.notes.ui.theme.SageAccent
import com.haven.notes.ui.theme.TextPrimary
import com.haven.notes.ui.theme.TextSecondary
import com.haven.notes.viewmodel.SafetyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    viewModel: SafetyViewModel,
    onLockToDecoy: () -> Unit
) {
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    val pending by viewModel.pending.collectAsState()

    var endpointInput by remember { mutableStateOf(viewModel.endpoint) }
    var tokenInput by remember { mutableStateOf(viewModel.token) }
    var consentInput by remember { mutableStateOf(viewModel.consent) }
    var pinInput by remember { mutableStateOf(viewModel.decoyPin) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Setup",
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
            // Disguise & Security
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Disguise & Security",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            if (it.length <= 4) {
                                pinInput = it
                                viewModel.decoyPin = it
                            }
                        },
                        label = { Text("4-digit Unlock PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "When Notes is shown, typing this PIN into the search bar or long-pressing the header title reveals safety controls.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = onLockToDecoy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Switch to Notes view now", color = SageAccent)
                    }
                }
            }

            // Connect your pilot server
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Connect your pilot server",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = endpointInput,
                        onValueChange = { endpointInput = it },
                        label = { Text("Server origin") },
                        placeholder = { Text("https://safety.example.com") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("API token") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = consentInput,
                            onCheckedChange = { consentInput = it },
                            colors = CheckboxDefaults.colors(checkedColor = SageAccent)
                        )
                        Text(
                            text = "I choose to share alerts and optional location with the contacts shown in Circle.",
                            fontSize = 13.sp,
                            color = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            viewModel.saveSettings(endpointInput, tokenInput, consentInput)
                        },
                        enabled = !busy && pending == null,
                        colors = ButtonDefaults.buttonColors(containerColor = SageAccent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save and connect")
                    }
                }
            }

            // Discreet by your choice
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Discreet by your choice",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "On Android, you can use Private Space (Android 15+), Samsung Secure Folder, or app hiding settings in your launcher. The app acts as a completely functional notepad until unlocked.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "The Home location toggle prepares a sample for an alert or timer. Family sharing is separate: it sends location in the background only after you explicitly join and enable it in Family. Notes does not automatically record audio or phone call conversations.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }

            // Before relying on this
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Before relying on this",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This is a safety development pilot. Practice with consenting contacts, verify SMS delivery, and use built-in Emergency SOS (pressing power 5 times) for 911/112/999 dispatch. A dead battery, lost network connection or SMS carrier outage can prevent alerts from reaching contacts.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }

            // Status panel
            StatusPanel(
                message = message,
                busy = busy,
                pending = pending,
                onRetry = { viewModel.retry() },
                onClearPending = { viewModel.clearPending() }
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
