package com.range.phoneLinuxer.ui.screen

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
import com.range.phoneLinuxer.viewModel.EmulatorViewModel

@Composable
fun EngineDownloadDialog(
    emulatorVm: EmulatorViewModel,
    onDismiss: () -> Unit
) {
    val isDownloading by emulatorVm.isEngineDownloading.collectAsState()
    val isPaused by emulatorVm.isEnginePaused.collectAsState()
    val status by emulatorVm.engineDownloadStatus.collectAsState()
    val speed by emulatorVm.engineDownloadSpeed.collectAsState()
    val remaining by emulatorVm.engineRemainingTime.collectAsState()
    val progress by emulatorVm.engineDownloadProgress.collectAsState()
    val showMobileWarning by emulatorVm.showEngineMobileDataWarning.collectAsState()
    val targetSizeMB by emulatorVm.engineTargetSizeMB.collectAsState()

    var showConfirmDialog by remember { mutableStateOf(true) }

    if (showMobileWarning) {
        AlertDialog(
            onDismissRequest = { emulatorVm.dismissEngineMobileDataWarning() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mobile Data Restricted")
                }
            },
            text = { Text("Downloading QEMU Engine ($targetSizeMB MB) over mobile data is forbidden by your settings. Download anyway?") },
            confirmButton = {
                Button(onClick = {
                    emulatorVm.dismissEngineMobileDataWarning()
                    emulatorVm.downloadQemuEngine(forceMobileData = true)
                }) { Text("Download Anyway") }
            },
            dismissButton = {
                TextButton(onClick = {
                    emulatorVm.dismissEngineMobileDataWarning()
                    if (!isDownloading) onDismiss()
                }) { Text("Cancel") }
            }
        )
    } else if (showConfirmDialog && !isDownloading && !isPaused && progress == 0) {
        AlertDialog(
            onDismissRequest = {
                showConfirmDialog = false
                onDismiss()
            },
            title = { Text("Download Virtual Engine") },
            text = { Text("This will download the QEMU Virtual Machine Engine (approx. $targetSizeMB MB). Are you sure you want to proceed?") },
            confirmButton = {
                Button(onClick = {
                    showConfirmDialog = false
                    emulatorVm.downloadQemuEngine()
                }) { Text("Start Download") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    onDismiss()
                }) { Text("Cancel") }
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
                    Button(onClick = { emulatorVm.toggleEnginePauseResume() }) {
                        Text(if (isPaused) "Resume" else "Pause")
                    }
                } else {
                    Button(onClick = {}, enabled = false) {
                        Text("Please Wait")
                    }
                }
            },
            dismissButton = {
                if (!status.contains("Extracting")) {
                    TextButton(onClick = {
                        emulatorVm.cancelEngineDownload()
                        onDismiss()
                    }) { Text("Cancel") }
                }
            }
        )
    }
}
