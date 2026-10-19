package com.haven.notes.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haven.notes.ui.theme.CardBackground
import com.haven.notes.ui.theme.DecoySearchBorder
import com.haven.notes.ui.theme.Ink
import com.haven.notes.ui.theme.PastelRose
import com.haven.notes.ui.theme.TextSecondary

@Composable
fun NicknameDialog(
    initialName: String = "",
    isBusy: Boolean = false,
    errorMessage: String? = null,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Welcome to Notes",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
                Spacer(modifier = Modifier.padding(start = 4.dp))
                Text(text = "🌸", fontSize = 20.sp)
            }
        },
        text = {
            Column {
                Text(
                    text = "Choose a nickname for this phone so your family controller can identify your updates.",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 40) name = it },
                    placeholder = { Text("e.g., Aunt, Mom, Sarah 🌸", color = TextSecondary.copy(alpha = 0.7f)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PastelRose,
                        unfocusedBorderColor = DecoySearchBorder,
                        focusedContainerColor = CardBackground,
                        unfocusedContainerColor = CardBackground
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        color = Color(0xFFD32F2F),
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isNotEmpty()) {
                        onConfirm(trimmed)
                    }
                },
                enabled = !isBusy && name.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = PastelRose),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Connect & Start 🌸", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (!isBusy) {
                TextButton(onClick = onDismiss) {
                    Text("Skip for now", color = TextSecondary)
                }
            }
        },
        containerColor = CardBackground,
        shape = RoundedCornerShape(24.dp)
    )
}
