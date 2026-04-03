package com.range.rangeEmulator.ui.screen.emulatorList

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
import com.range.rangeEmulator.data.enums.*
import com.range.rangeEmulator.data.model.*
import com.range.rangeEmulator.ui.screen.addNewEmulator.CpuModelDropdown
import com.range.rangeEmulator.ui.screen.addNewEmulator.KvmStatusCard
import com.range.rangeEmulator.ui.screen.addNewEmulator.SectionHeader
import com.range.rangeEmulator.ui.screen.addNewEmulator.SettingSlider
import com.range.rangeEmulator.util.HardwareUtil
import com.range.rangeEmulator.viewModel.EmulatorViewModel

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
    var selectedScreenType by remember { mutableStateOf(ScreenType.VNC) }
    val maxTbSize = remember { (deviceMaxRam - 512).coerceAtLeast(256) }
    val tbSafeLimit = remember { deviceMaxRam / 3 }
    val combinedSafeLimitMB = remember { 
        if (deviceMaxRam > 8000) (deviceMaxRam * 0.90f).toInt() 
        else (deviceMaxRam * 0.85f).toInt() 
    }
    var tbSize by remember { mutableFloatStateOf(512f) }
    var showTbWarning by remember { mutableStateOf(false) }

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
            tbSize = vm.tbSizeMB.toFloat()
        }
    }

    fun performUpdate() {
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
            tbSizeMB = tbSize.toInt()
        )?.let { onSave(it) }
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
                                if (tbSize > tbSafeLimit) {
                                    showTbWarning = true
                                } else {
                                    performUpdate()
                                }
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
        if (showTbWarning) {
            AlertDialog(
                onDismissRequest = { showTbWarning = false },
                title = { Text("High TB-Size Warning") },
                text = { Text("You have selected a very large TCG Cache (${tbSize.toInt()}MB). This exceeds the recommended safe limit (1/3 of RAM) and may cause your device to run out of memory or crash. Are you sure you want to proceed?") },
                confirmButton = {
                    TextButton(onClick = { showTbWarning = false; performUpdate() }) { Text("Proceed Anyway") }
                },
                dismissButton = {
                    TextButton(onClick = { showTbWarning = false }) { Text("Cancel") }
                }
            )
        }

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
                onValueChange = { newRam -> 
                    ramAmount = newRam
                    if (ramAmount + tbSize > combinedSafeLimitMB) {
                        tbSize = (combinedSafeLimitMB - ramAmount).coerceIn(128f, maxTbSize.toFloat())
                    }
                },
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
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (ramAmount + tbSize > combinedSafeLimitMB) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (ramAmount + tbSize > combinedSafeLimitMB) Icons.Default.Warning else Icons.Default.Info, 
                        null,
                        tint = if (ramAmount + tbSize > combinedSafeLimitMB) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Estimated Host Memory Usage", style = MaterialTheme.typography.labelSmall)
                        Text(
                            "${(ramAmount + tbSize).toInt()} MB / ${deviceMaxRam} MB",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (ramAmount + tbSize > combinedSafeLimitMB) {
                            Text("Warning: High memory pressure. System may kill the app.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("Status: Safe levels. Sliders will auto-balance.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("TCG Translation Cache (TB-Size)", style = MaterialTheme.typography.labelLarge)
            Text(
                "TB-Size stores translated guest code in RAM. A larger cache significantly improves emulation speed by reducing the need to re-translate code, but it increases the overall RAM consumption of the process.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SettingSlider(
                title = "TCG Cache (TB-Size): ${tbSize.toInt()} MB",
                value = tbSize,
                onValueChange = { newTb -> 
                    tbSize = newTb
                    if (ramAmount + tbSize > combinedSafeLimitMB) {
                        ramAmount = (combinedSafeLimitMB - tbSize).coerceIn(512f, deviceMaxRam.toFloat())
                    }
                },
                valueRange = 128f..maxTbSize.toFloat(),
                steps = 10,
                icon = Icons.Default.Speed
            )
            Text(
                "Info: Larger cache is faster but uses more device RAM. Max allowed for this device: ${maxTbSize}MB.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}