package com.range.phoneLinuxer.ui.screen

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.range.phoneLinuxer.viewModel.EmulatorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(
    emulatorVm: EmulatorViewModel,
    onDownloadDistro: () -> Unit,
    onStartDistro: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToLogs: () -> Unit
) {
    val context = LocalContext.current
    var showSupportDialog by remember { mutableStateOf(false) }
    var showEngineDialog by remember { mutableStateOf(false) }

    val isEngineDownloaded by emulatorVm.isEngineDownloaded.collectAsState()
    val isEngineDownloading by emulatorVm.isEngineDownloading.collectAsState()
    val isEnginePaused by emulatorVm.isEnginePaused.collectAsState()
    val downloadProgress by emulatorVm.engineDownloadProgress.collectAsState()

    LaunchedEffect(Unit) {
        if (!isEngineDownloaded) {
            emulatorVm.prepareLatestEngineOTA()
        }
    }

    if (showEngineDialog) {
        EngineDownloadDialog(emulatorVm = emulatorVm, onDismiss = { showEngineDialog = false })
    }

    if (showSupportDialog) {
        AlertDialog(
            onDismissRequest = { showSupportDialog = false },
            title = { Text("Support the Project") },
            text = {
                Text("This project is developed with love by range79. Would you like to visit the support page to help keep it alive?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSupportDialog = false
                        val intent = Intent(Intent.ACTION_VIEW, "https://buymeacoffee.com/darkrange6s".toUri())
                        context.startActivity(intent)
                    }
                ) {
                    Text("Visit Page")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSupportDialog = false }) {
                    Text("Not now")
                }
            }
        )
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToSettings,
                    label = { Text("Settings") },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToLogs,
                    label = { Text("Logs") },
                    icon = { Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = null) }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 80.dp)
            ) {
                Text(
                    text = "PhoneLinuxer",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Linux in your pocket",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onDownloadDistro,
                    modifier = Modifier.fillMaxWidth(0.8f).height(64.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(Icons.Default.Download, null)
                    Spacer(Modifier.width(12.dp))
                    Text("Download Distro", fontSize = 18.sp)
                }

                if (!isEngineDownloaded) {
                    if (isEngineDownloading || isEnginePaused) {
                        Button(
                            onClick = { showEngineDialog = true },
                            modifier = Modifier.fillMaxWidth(0.8f).height(64.dp),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text("Downloading Engine... $downloadProgress%", fontSize = 18.sp)
                        }
                    } else {
                        Button(
                            onClick = { showEngineDialog = true },
                            modifier = Modifier.fillMaxWidth(0.8f).height(64.dp),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Icon(Icons.Default.Download, null)
                            Spacer(Modifier.width(12.dp))
                            Text("Download QEMU Engine", fontSize = 18.sp)
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onStartDistro,
                        modifier = Modifier.fillMaxWidth(0.8f).height(64.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(12.dp))
                        Text("Manage Emulators", fontSize = 18.sp)
                    }
                }
            }

            Card(
                modifier = Modifier
                    .padding(bottom = 48.dp, start = 24.dp, end = 24.dp)
                    .fillMaxWidth()
                    .clickable { showSupportDialog = true },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = null,
                        tint = Color(0xFFE91E63),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Support development",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}