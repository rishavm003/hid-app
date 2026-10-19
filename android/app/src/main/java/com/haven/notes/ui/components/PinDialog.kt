package com.haven.notes.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haven.notes.ui.theme.Ink
import com.haven.notes.ui.theme.SageAccent

@Composable
fun PinDialog(
    correctPin: String,
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Enter Passcode",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter your 4-digit PIN to open safety controls.",
                    fontSize = 14.sp,
                    color = Ink.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = enteredPin,
                    onValueChange = {
                        if (it.length <= 4) {
                            enteredPin = it
                            errorText = ""
                            if (it == correctPin) {
                                onUnlocked()
                            }
                        }
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorText.isNotEmpty()
                )
                if (errorText.isNotEmpty()) {
                    Text(
                        text = errorText,
                        color = androidx.compose.ui.graphics.Color.Red,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (enteredPin == correctPin) {
                        onUnlocked()
                    } else {
                        errorText = "Incorrect passcode."
                        enteredPin = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SageAccent)
            ) {
                Text("Unlock")
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
