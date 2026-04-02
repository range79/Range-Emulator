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
import androidx.compose.ui.text.font.FontWeight
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
    val isGpuSupported = remember { HardwareUtil.isGpuAccelerationSupported(context) }

    var vmName by remember { mutableStateOf("") }
    var selectedCpu by remember { mutableStateOf(CpuModel.MAX) }
    var ramAmount by remember { mutableFloatStateOf(1024f) }
    var cpuCores by remember { mutableFloatStateOf(1f) }
    var isGpuEnabled by remember { mutableStateOf(true) }

    var screenWidth by remember { mutableStateOf("1280") }
    var screenHeight by remember { mutableStateOf("720") }

    var vncPort by remember { mutableStateOf("5900") }
    var spicePort by remember { mutableStateOf("5901") }
    var selectedAioMode by remember { mutableStateOf(DiskAioMode.THREADS) }
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
            spicePort = vm.spicePort.toString()
            selectedScreenType = vm.screenType
            selectedAioMode = vm.diskAioMode
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
                                    screenWidth = if (selectedScreenType == ScreenType.SPICE) 0 else (screenWidth.toIntOrNull() ?: 1280),
                                    screenHeight = if (selectedScreenType == ScreenType.SPICE) 0 else (screenHeight.toIntOrNull() ?: 720),
                                    vncPort = vncPort.toIntOrNull() ?: 5900,
                                    spicePort = spicePort.toIntOrNull() ?: 5901,
                                    diskAioMode = selectedAioMode
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

            if (selectedScreenType == ScreenType.SPICE) {
                Text(
                    "English Warning: Manual resolution is disabled for SPICE. System will use optimized dynamic resizing.",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = if (selectedScreenType == ScreenType.SPICE) "Auto" else screenWidth,
                    onValueChange = { screenWidth = it },
                    label = { Text("Width") },
                    modifier = Modifier.weight(1f),
                    enabled = selectedScreenType != ScreenType.SPICE
                )
                OutlinedTextField(
                    value = if (selectedScreenType == ScreenType.SPICE) "Auto" else screenHeight,
                    onValueChange = { screenHeight = it },
                    label = { Text("Height") },
                    modifier = Modifier.weight(1f),
                    enabled = selectedScreenType != ScreenType.SPICE
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
                    value = spicePort,
                    onValueChange = { spicePort = it },
                    label = { Text("Spice Port") },
                    modifier = Modifier.weight(1f),
                    enabled = selectedScreenType == ScreenType.SPICE
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Terminal, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Enable Virtio-GPU")
                    if (!isGpuSupported) {
                        Text(
                            "⚠️ Your device might not support Virtio-GPU acceleration. High-performance graphics may not work.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            "Caution: Virtio-GPU requires host support. Disable if VM fails to start.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                    }
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

            Spacer(Modifier.height(8.dp))
            Text("Disk Performance (AIO Mode)", style = MaterialTheme.typography.labelMedium)
            Text(
                "Threads: High compatibility, recommended for older devices.\n" +
                "IO_URING: The fastest performance mode (Android 11+).\n" +
                "Native: High speed Linux AIO, second choice after io_uring.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            var aioExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = aioExpanded,
                onExpandedChange = { aioExpanded = !aioExpanded }
            ) {
                OutlinedTextField(
                    value = selectedAioMode.name,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = aioExpanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = aioExpanded,
                    onDismissRequest = { aioExpanded = false }
                ) {
                    DiskAioMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(mode.name, fontWeight = FontWeight.Bold)
                                    val desc = when(mode) {
                                        DiskAioMode.THREADS -> "Default compatible mode (Standard for older Android)."
                                        DiskAioMode.IO_URING -> "Modern high-speed mode (Recommended for Android 12+)."
                                        DiskAioMode.NATIVE -> "Direct Linux AIO (Use if io_uring fails)."
                                    }
                                    Text(desc, style = MaterialTheme.typography.labelSmall)
                                }
                            },
                            onClick = {
                                selectedAioMode = mode
                                aioExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}