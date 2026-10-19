package com.haven.notes.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haven.notes.data.PendingRequest
import com.haven.notes.ui.theme.CrimsonSOS
import com.haven.notes.ui.theme.Ink
import com.haven.notes.ui.theme.SageAccent
import com.haven.notes.ui.theme.TextSecondary

@Composable
fun StatusPanel(
    message: String,
    busy: Boolean,
    pending: PendingRequest?,
    onRetry: () -> Unit,
    onClearPending: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear Local Retry", color = Ink) },
            text = {
                Text(
                    "Clear the saved retry? This cannot cancel a request already received by the server.",
                    color = Ink
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirm = false
                        onClearPending()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonSOS)
                ) {
                    Text("Clear local retry")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel", color = Ink)
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = if (pending == null) Icons.Default.Info else Icons.Default.Warning,
                contentDescription = null,
                tint = if (pending == null) SageAccent else CrimsonSOS,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                fontSize = 13.sp,
                color = TextSecondary,
                lineHeight = 18.sp
            )
        }

        if (busy) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = SageAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Waiting for server confirmation…",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
        }

        if (pending != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Row {
                Button(
                    onClick = onRetry,
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = SageAccent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Retry same request", fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { showClearConfirm = true },
                    enabled = !busy,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear retry", color = CrimsonSOS, fontSize = 13.sp)
                }
            }
        }
    }
}
