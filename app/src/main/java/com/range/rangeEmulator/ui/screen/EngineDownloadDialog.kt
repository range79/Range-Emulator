package com.range.rangeEmulator.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.range.rangeEmulator.viewModel.EngineViewModel

@Composable
fun EngineDownloadDialog(
    engineVm: EngineViewModel,
    onDismiss: () -> Unit
) {
    val isDownloading by engineVm.isEngineDownloading.collectAsState()
    val isPaused by engineVm.isEnginePaused.collectAsState()
    val status by engineVm.engineDownloadStatus.collectAsState()
    val speed by engineVm.engineDownloadSpeed.collectAsState()
    val remaining by engineVm.engineRemainingTime.collectAsState()
    val progress by engineVm.engineDownloadProgress.collectAsState()
    val showMobileWarning by engineVm.showMobileDataWarning.collectAsState()
    val targetSizeMB by engineVm.engineTargetSizeMB.collectAsState()

    var showConfirmDialog by remember { mutableStateOf(true) }

    if (showMobileWarning) {
        AlertDialog(
            onDismissRequest = { engineVm.dismissMobileDataWarning() },
            title = { Text("Mobile Data Warning") },
            text = { Text("Mobile data downloads are restricted. Do you want to download this file using your cellular plan anyway?") },
            confirmButton = {
                Button(onClick = {
                    engineVm.dismissMobileDataWarning()
                    engineVm.downloadEngine(forceMobileData = true)
                }) { Text("Download Anyway") }
            },
            dismissButton = {
                TextButton(onClick = {
                    engineVm.dismissMobileDataWarning()
                    if (!isDownloading) onDismiss()
                }) { Text("Cancel") }
            }
        )
    }
 else if (showConfirmDialog && !isDownloading && !isPaused && progress == 0) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false; onDismiss() },
            title = { Text("Download Virtual Engine") },
            text = { Text("This will download the QEMU Virtual Machine Engine (approx. $targetSizeMB MB). Are you sure you want to proceed?") },
            confirmButton = {
                Button(onClick = { showConfirmDialog = false; engineVm.downloadEngine() }) { Text("Start Download") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false; onDismiss() }) { Text("Cancel") }
            }
        )
    } else if (isDownloading || isPaused || progress > 0) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Engine Setup") },
            text = {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(text = status, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = speed, style = MaterialTheme.typography.labelMedium)
                        Text(text = remaining, style = MaterialTheme.typography.labelMedium)
                    }
                }
            },
            confirmButton = {
                if (!status.contains("Extracting")) {
                    Button(onClick = { engineVm.togglePauseResume() }) {
                        Text(if (isPaused) "Resume" else "Pause")
                    }
                } else {
                    Button(onClick = {}, enabled = false) { Text("Please Wait") }
                }
            },
            dismissButton = {
                if (!status.contains("Extracting")) {
                    TextButton(onClick = { engineVm.cancelDownload(); onDismiss() }) { Text("Cancel") }
                }
            }
        )
    }
}
