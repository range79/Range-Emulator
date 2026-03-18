package com.range.phoneLinuxer.ui.screen.emulatorList

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.range.phoneLinuxer.data.enums.ScreenType
import com.range.phoneLinuxer.data.enums.VmState
import com.range.phoneLinuxer.data.model.VirtualMachineSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmulatorListScreen(
    onBack: () -> Unit,
    onAddEmulator: () -> Unit,
    onEditVM: (VirtualMachineSettings) -> Unit,
    onStartVM: (VirtualMachineSettings) -> Unit,
    onDeleteVM: (String) -> Unit,
    vms: List<VirtualMachineSettings> = emptyList()
) {
    var vmToDelete by remember { mutableStateOf<VirtualMachineSettings?>(null) }

    vmToDelete?.let { vm ->
        AlertDialog(
            onDismissRequest = { vmToDelete = null },
            title = { Text("Delete Virtual Machine") },
            text = { Text("Are you sure you want to delete '${vm.vmName}'? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteVM(vm.id)
                        vmToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { vmToDelete = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Virtual Machines", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = onAddEmulator,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New VM")
                    }
                }
            )
        }
    ) { padding ->
        if (vms.isEmpty()) {
            EmptyVMState(
                onAdd = onAddEmulator,
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 80.dp, top = 16.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(vms, key = { it.id }) { vm ->
                    VMCard(
                        vm = vm,
                        onStart = { onStartVM(vm) },
                        onEdit = { onEditVM(vm) },
                        onDelete = { vmToDelete = vm }
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
    onDelete: () -> Unit
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
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onEdit,
                    enabled = canModify,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = if (canModify) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = vm.vmName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            modifier = Modifier.size(8.dp),
                            shape = CircleShape,
                            color = statusColor,
                            border = if(vm.state == VmState.INACTIVE) BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f)) else null
                        ) {}
                    }
                    val displayPort = if (vm.screenType == ScreenType.VNC) vm.vncPort else vm.rdpPort
                    Text(
                        text = "${vm.state.name} • ${vm.screenType.name} Port $displayPort",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDelete, enabled = canModify) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = if (canModify) MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                    }

                    Spacer(Modifier.width(4.dp))

                    FilledIconButton(
                        onClick = onStart,
                        enabled = !isTransitioning,
                        modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isRunning) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        if (isTransitioning) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        } else {
                            Icon(
                                imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = "Action"
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoChip(Icons.Default.Memory, "${vm.ramMB} MB")
                InfoChip(Icons.Default.SettingsInputComponent, "${vm.cpuCores} Core")
                InfoChip(Icons.Default.Monitor, "${vm.screenWidth}x${vm.screenHeight}")
                if (vm.isGpuEnabled) InfoChip(Icons.Default.Bolt, "GPU")
                if (vm.easyInstall) InfoChip(Icons.Default.AutoFixHigh, "Easy")
            }
        }
    }
}

@Composable
fun InfoChip(icon: ImageVector, label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(icon, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun EmptyVMState(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Terminal,
                null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
            Spacer(Modifier.height(16.dp))
            Text("No Virtual Machines", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAdd) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Create First VM")
            }
        }
    }
}