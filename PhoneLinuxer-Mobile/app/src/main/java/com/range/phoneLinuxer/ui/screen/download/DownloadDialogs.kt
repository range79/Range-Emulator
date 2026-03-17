package com.range.phoneLinuxer.ui.screen.download

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

@Composable
fun MobileDataWarningDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mobile Data Warning") },
        text = { Text("Mobile data downloads are restricted. Do you want to download this file using your cellular plan anyway?") },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Download Anyway") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}