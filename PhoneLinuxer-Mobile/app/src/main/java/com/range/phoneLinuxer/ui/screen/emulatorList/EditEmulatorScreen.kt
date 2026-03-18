package com.range.phoneLinuxer.ui.screen.emulatorList

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.range.phoneLinuxer.data.enums.*
import com.range.phoneLinuxer.data.model.*
import com.range.phoneLinuxer.ui.screen.addNewEmulator.CpuModelDropdown
import com.range.phoneLinuxer.ui.screen.addNewEmulator.KvmStatusCard
import com.range.phoneLinuxer.ui.screen.addNewEmulator.SectionHeader
import com.range.phoneLinuxer.ui.screen.addNewEmulator.SettingSlider
import com.range.phoneLinuxer.util.HardwareUtil
import com.range.phoneLinuxer.viewModel.EmulatorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEmulatorScreen(
    viewModel: EmulatorViewModel,
    onBack: () -> Unit,
    onSave: (VirtualMachineSettings) -> Unit
) {
    val context = LocalContext.current
    val editingVm by viewModel.editingVm.collectAsState()
    var isSavingLocked by remember { mutableStateOf(false) }

    val deviceMaxRam = remember { HardwareUtil.getTotalRamMB(context) }
    val deviceMaxCores = remember { HardwareUtil.getTotalCores() }
    val hasKvmSupport = remember { HardwareUtil.isKvmSupported() }

    var vmName by remember { mutableStateOf("") }
    var selectedCpu by remember { mutableStateOf(CpuModel.MAX) }
    var ramAmount by remember { mutableFloatStateOf(1024f) }
    var cpuCores by remember { mutableFloatStateOf(1f) }
    var isGpuEnabled by remember { mutableStateOf(true) }

    var screenWidth by remember { mutableStateOf("1280") }
    var screenHeight by remember { mutableStateOf("720") }

    var vncPort by remember { mutableStateOf("5900") }
    var rdpPort by remember { mutableStateOf("3389") }
    var selectedScreenType by remember { mutableStateOf(ScreenType.VNC) }

    LaunchedEffect(editingVm) {
        editingVm?.let { vm ->
            vmName = vm.vmName
            selectedCpu = vm.cpuModel
            ramAmount = vm.ramMB.toFloat()
            cpuCores = vm.cpuCores.toFloat()
            isGpuEnabled = vm.isGpuEnabled
            screenWidth = vm.screenWidth.toString()
            screenHeight = vm.screenHeight.toString()
            vncPort = vm.vncPort.toString()
            rdpPort = vm.rdpPort.toString()
            selectedScreenType = vm.screenType
        }
    }

    BackHandler {
        viewModel.setEditingVm(null)
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit VM: $vmName") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.setEditingVm(null)
                        onBack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                actions = {
                    Button(
                        onClick = {
                            if (vmName.isNotBlank() && !isSavingLocked) {
                                isSavingLocked = true
                                editingVm?.copy(
                                    vmName = vmName,
                                    cpuModel = selectedCpu,
                                    ramMB = ramAmount.toInt(),
                                    cpuCores = cpuCores.toInt(),
                                    isGpuEnabled = isGpuEnabled,
                                    screenType = selectedScreenType,
                                    screenWidth = screenWidth.toIntOrNull() ?: 1280,
                                    screenHeight = screenHeight.toIntOrNull() ?: 720,
                                    vncPort = vncPort.toIntOrNull() ?: 5900,
                                    rdpPort = rdpPort.toIntOrNull() ?: 3389
                                )?.let { onSave(it) }
                            }
                        },
                        enabled = vmName.isNotBlank() && !isSavingLocked
                    ) {
                        if (isSavingLocked) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Update")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            OutlinedTextField(
                value = vmName,
                onValueChange = { vmName = it },
                label = { Text("VM Name") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Label, null) }
            )

            SectionHeader("Display & Graphics")

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScreenType.entries.forEach { type ->
                    FilterChip(
                        selected = selectedScreenType == type,
                        onClick = { selectedScreenType = type },
                        label = { Text(type.name) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = screenWidth,
                    onValueChange = { screenWidth = it },
                    label = { Text("Width") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = screenHeight,
                    onValueChange = { screenHeight = it },
                    label = { Text("Height") },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = vncPort,
                    onValueChange = { vncPort = it },
                    label = { Text("VNC Port") },
                    modifier = Modifier.weight(1f),
                    enabled = selectedScreenType == ScreenType.VNC
                )
                OutlinedTextField(
                    value = rdpPort,
                    onValueChange = { rdpPort = it },
                    label = { Text("RDP Port") },
                    modifier = Modifier.weight(1f),
                    enabled = selectedScreenType == ScreenType.RDP
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Terminal, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Enable Virtio-GPU")
                    Text("Hardware acceleration for graphics", style = MaterialTheme.typography.labelSmall)
                }
                Switch(checked = isGpuEnabled, onCheckedChange = { isGpuEnabled = it })
            }

            SectionHeader("Resources")
            KvmStatusCard(hasKvmSupport)

            CpuModelDropdown(
                selectedModel = selectedCpu,
                hasKvm = hasKvmSupport,
                onModelSelected = { selectedCpu = it }
            )

            SettingSlider(
                title = "RAM: ${ramAmount.toInt()} MB",
                value = ramAmount,
                onValueChange = { ramAmount = it },
                valueRange = 512f..deviceMaxRam.toFloat(),
                steps = 10,
                icon = Icons.Default.Memory
            )

            SettingSlider(
                title = "Cores: ${cpuCores.toInt()}",
                value = cpuCores,
                onValueChange = { cpuCores = it },
                valueRange = 1f..deviceMaxCores.toFloat(),
                steps = deviceMaxCores - 1,
                icon = Icons.Default.SettingsInputComponent
            )
        }
    }
}