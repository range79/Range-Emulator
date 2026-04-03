package com.range.rangeEmulator.ui.screen.emulatorList

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.range.rangeEmulator.data.enums.ScreenType
import com.range.rangeEmulator.data.enums.VmState
import com.range.rangeEmulator.data.model.VirtualMachineSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmulatorListScreen(
    onBack: () -> Unit,
    onAddEmulator: () -> Unit,
    onEditVM: (VirtualMachineSettings) -> Unit,
    onStartVM: (VirtualMachineSettings) -> Unit,
    onNavigateToEmulator: (String) -> Unit,
    onDeleteVM: (String) -> Unit,
    vms: List<VirtualMachineSettings> = emptyList()
) {
    var vmToDelete by remember { mutableStateOf<VirtualMachineSettings?>(null) }

    LaunchedEffect(vms) {
        val runningVm = vms.find { it.state == VmState.RUNNING }
        runningVm?.let {
            onNavigateToEmulator(it.id)
        }
    }

    vmToDelete?.let { vm ->
        AlertDialog(
            onDismissRequest = { vmToDelete = null },
            title = { Text("Delete VM") },
            text = { Text("Are you sure you want to delete '${vm.vmName}'?") },
            confirmButton = {
                TextButton(
                    onClick = { onDeleteVM(vm.id); vmToDelete = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { vmToDelete = null }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Virtual Machines", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = onAddEmulator,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Text("New VM", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            )
        }
    ) { padding ->
        if (vms.isEmpty()) {
            EmptyVMState(onAdd = onAddEmulator, modifier = Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(vms, key = { it.id }) { vm ->
                    VMCard(
                        vm = vm,
                        onStart = { onStartVM(vm) },
                        onEdit = { onEditVM(vm) },
                        onDelete = { vmToDelete = vm },
                        onCardClick = {
                            if (vm.state == VmState.RUNNING) onNavigateToEmulator(vm.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun VMCard(
    vm: VirtualMachineSettings,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCardClick: () -> Unit
) {
    val isRunning = vm.state == VmState.RUNNING
    val isTransitioning = vm.state == VmState.STARTING || vm.state == VmState.STOPPING
    val canModify = !isRunning && !isTransitioning

    val statusColor by animateColorAsState(
        targetValue = when (vm.state) {
            VmState.RUNNING -> Color(0xFF4CAF50)
            VmState.STARTING, VmState.STOPPING -> Color(0xFFFFC107)
            VmState.ERROR -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        }, label = "statusColor"
    )

    Card(
        onClick = onCardClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = if (isRunning) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) else null
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).background(statusColor, CircleShape))

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(vm.vmName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    val port = if (vm.screenType == ScreenType.VNC) vm.vncPort else vm.spicePort
                    Text(
                        "${vm.state.name} • ${vm.screenType.name} : $port",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    if (canModify) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                        }
                    }

                    FilledIconButton(
                        onClick = onStart,
                        enabled = !isTransitioning,
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isRunning) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isTransitioning) {
                            CircularProgressIndicator(
                                Modifier.size(24.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(if (isRunning) Icons.Default.PowerSettingsNew else Icons.Default.PlayArrow, null)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactInfoChip(Icons.Default.Memory, "${vm.ramMB}MB")
                CompactInfoChip(Icons.Default.DeveloperBoard, "${vm.cpuCores} Core")
                CompactInfoChip(Icons.Default.Tv, "${vm.screenWidth}x${vm.screenHeight}")
                if (vm.isGpuEnabled) CompactInfoChip(Icons.Default.FlashOn, "GPU")
            }
        }
    }
}

@Composable
fun CompactInfoChip(icon: ImageVector, label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        shape = CircleShape,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                label,
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun EmptyVMState(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Terminal, null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
            Spacer(Modifier.height(16.dp))
            Text("No Virtual Machines Found", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAdd) {
                Icon(Icons.Default.Add, null)
                Text("Create VM", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}