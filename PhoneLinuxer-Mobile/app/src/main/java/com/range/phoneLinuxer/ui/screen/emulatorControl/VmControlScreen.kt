package com.range.phoneLinuxer.ui.screen.emulator

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.range.phoneLinuxer.data.enums.ScreenType
import com.range.phoneLinuxer.data.enums.VmState
import com.range.phoneLinuxer.util.NetworkUtils
import com.range.phoneLinuxer.viewModel.EmulatorViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VmControlScreen(
    vmId: String,
    viewModel: EmulatorViewModel,
    onNavigateToLogs: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val deviceIp = remember { NetworkUtils.getLocalIpAddress() }
    val vms by viewModel.vms.collectAsState()
    val currentVm = vms.find { it.id == vmId }

    val isSpice = currentVm?.screenType == ScreenType.SPICE
    val protocolName = if (isSpice) "SPICE" else "VNC"

    val portValue = if (isSpice) {
        currentVm.spicePort.toString()
    } else {
        currentVm?.vncPort.toString()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("VM Dashboard", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(100.dp),
                shape = CircleShape,
                color = if (currentVm?.state == VmState.RUNNING) Color(0xFF4CAF50) else Color.Gray,
                shadowElevation = 6.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isSpice) Icons.Default.Monitor else Icons.Default.SettingsInputComponent,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.White
                    )
                }
            }

            Text(
                text = currentVm?.vmName ?: "Unknown Machine",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 16.dp)
            )

            Text(
                text = "State: ${currentVm?.state ?: "Unknown"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "$protocolName Connection Details",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    ControlInfoRow(
                        label = "Local IP",
                        value = deviceIp,
                        onCopy = {
                            scope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("IP Address", deviceIp)))
                                Toast.makeText(context, "IP Copied", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    ControlInfoRow(
                        label = "$protocolName Port",
                        value = portValue,
                        onCopy = {
                            scope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Port", portValue)))
                                Toast.makeText(context, "Port Copied", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    ControlInfoRow(
                        label = "Protocol",
                        value = if (isSpice) "SPICE (Simple Protocol for IDM)" else "VNC (RFB Protocol)"
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateToLogs,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Terminal, null)
                    Spacer(Modifier.width(8.dp))
                    Text("View Live Logs")
                }

                Button(
                    onClick = {
                        viewModel.stopVm(vmId)
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Stop, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Terminate VM")
                }
            }
        }
    }
}

@Composable
fun ControlInfoRow(label: String, value: String, onCopy: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (onCopy != null) {
                IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}