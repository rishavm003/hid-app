package com.haven.notes.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haven.notes.ui.components.StatusPanel
import com.haven.notes.ui.theme.BackgroundLight
import com.haven.notes.ui.theme.CardBackground
import com.haven.notes.ui.theme.CrimsonSOS
import com.haven.notes.ui.theme.Ink
import com.haven.notes.ui.theme.SageAccent
import com.haven.notes.ui.theme.TextPrimary
import com.haven.notes.ui.theme.TextSecondary
import com.haven.notes.viewmodel.SafetyViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: SafetyViewModel,
    onLockToDecoy: () -> Unit
) {
    val status by viewModel.status.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    val locationText by viewModel.locationText.collectAsState()
    val sharingLocation by viewModel.sharingLocation.collectAsState()
    val pending by viewModel.pending.collectAsState()

    var selectedMinutes by remember { mutableStateOf(15) }
    var showConfirmSOS by remember { mutableStateOf(false) }

    val activeTimer = viewModel.activeTimer
    val context = LocalContext.current

    // Live countdown timer state
    var remainingSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(activeTimer) {
        if (activeTimer != null) {
            while (true) {
                val now = System.currentTimeMillis() / 1000L
                val left = Math.max(0L, (activeTimer.deadline - now).toLong())
                remainingSeconds = left
                delay(1000)
            }
        }
    }

    if (showConfirmSOS) {
        val isLive = status?.mode == "live"
        AlertDialog(
            onDismissRequest = { showConfirmSOS = false },
            title = {
                Text(
                    text = if (isLive) "Send SOS Alert?" else "Record Demo SOS?",
                    fontWeight = FontWeight.Bold,
                    color = CrimsonSOS
                )
            },
            text = {
                Text(
                    text = if (isLive) "Send an urgent SMS alert to your configured trusted contacts right now?"
                    else "Record a demo SOS? Nobody will be contacted.",
                    color = Ink
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmSOS = false
                        viewModel.send("/v1/sos")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonSOS)
                ) {
                    Text("Send SOS Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmSOS = false }) {
                    Text("Cancel", color = Ink)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "NOTES",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp,
                            color = Ink
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.85f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (status?.mode == "live") "LIVE PILOT" else "DEMO / NOT CONNECTED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (status?.mode == "live") CrimsonSOS else Ink
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onLockToDecoy) {
                        Icon(Icons.Default.Lock, contentDescription = "Lock to Notes", tint = Ink)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Ink)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundLight
                )
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
            // Hero section
            Column {
                Text(
                    text = "A little closer.\nEven from afar.",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    lineHeight = 38.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Your people, one alert away. Set a check-in before you head out.",
                    fontSize = 15.sp,
                    color = TextSecondary
                )
            }

            // SOS Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.PanTool,
                        contentDescription = null,
                        tint = CrimsonSOS,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Need your people?",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Send an SMS alert to your configured trusted contacts.",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = { showConfirmSOS = true },
                        enabled = !busy && pending == null && status != null && viewModel.consent,
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonSOS),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(
                            text = "Send SOS",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Requires internet and a working SMS server. This does not call emergency services.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Check-in timer Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = SageAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Check-in timer",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Ink
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (activeTimer != null) {
                        val deadlineDate = java.util.Date((activeTimer.deadline * 1000).toLong())
                        val formattedTime = DateFormat.getTimeFormat(context).format(deadlineDate)

                        Text(
                            text = "Check in by $formattedTime",
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Countdown display
                        val minutesLeft = remainingSeconds / 60
                        val secondsLeft = remainingSeconds % 60
                        Text(
                            text = String.format("%02d:%02d", minutesLeft, secondsLeft),
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (remainingSeconds < 60) CrimsonSOS else Ink
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.send("/v1/checkins/complete") },
                            enabled = !busy && pending == null,
                            colors = ButtonDefaults.buttonColors(containerColor = SageAccent),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Text("I’m safe — complete check-in", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Closing or deleting Notes does not cancel this server timer.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    } else {
                        // Duration selection chips
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(1, 5, 15, 30, 60).forEach { mins ->
                                FilterChip(
                                    selected = selectedMinutes == mins,
                                    onClick = { selectedMinutes = mins },
                                    label = { Text("$mins min", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SageAccent,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { viewModel.send("/v1/checkins", minutes = selectedMinutes) },
                            enabled = !busy && pending == null && status != null && viewModel.consent,
                            colors = ButtonDefaults.buttonColors(containerColor = SageAccent),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text("Start check-in", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Location Toggle Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Include current location",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = Ink
                        )
                        Switch(
                            checked = sharingLocation,
                            onCheckedChange = { viewModel.setLocation(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = SageAccent
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = locationText,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            // Status Panel
            StatusPanel(
                message = message,
                busy = busy,
                pending = pending,
                onRetry = { viewModel.retry() },
                onClearPending = { viewModel.clearPending() }
            )

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}
