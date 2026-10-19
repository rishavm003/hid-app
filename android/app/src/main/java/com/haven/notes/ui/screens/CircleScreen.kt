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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.haven.notes.ui.theme.TextPrimary
import com.haven.notes.ui.theme.TextSecondary
import com.haven.notes.viewmodel.SafetyViewModel
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleScreen(
    viewModel: SafetyViewModel,
    onLockToDecoy: () -> Unit
) {
    val status by viewModel.status.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Your circle",
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
            // Section: Trusted Contacts
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Trusted contacts",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (status == null || status?.contacts.isNullOrEmpty()) {
                        Text(
                            text = "Connect a server in Setup to load your contacts.",
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                    } else {
                        status?.contacts?.forEach { contact ->
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Text(
                                    text = contact.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = contact.phone,
                                    fontSize = 14.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Contacts are configured by your pilot server administrator. Review this list before enabling alerts.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }

            // Section: Recent Alerts
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Recent alerts",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (status?.alerts.isNullOrEmpty()) {
                        Text(
                            text = "No alerts loaded.",
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                    } else {
                        status?.alerts?.forEach { alert ->
                            val alertDate = Date((alert.created * 1000).toLong())
                            val formattedDate = DateFormat.getDateFormat(context).format(alertDate) + " " +
                                    DateFormat.getTimeFormat(context).format(alertDate)

                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                Text(
                                    text = if (alert.kind == "sos") "SOS alert" else "Missed check-in",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (alert.kind == "sos") CrimsonSOS else Ink
                                )
                                Text(
                                    text = formattedDate,
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                alert.deliveries.forEach { delivery ->
                                    val stateClean = delivery.state.replace("_", " ")
                                    Text(
                                        text = "${delivery.phone}: $stateClean",
                                        fontSize = 13.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Provider accepted means the SMS service accepted the request. It does not confirm delivery or that someone has read it.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
