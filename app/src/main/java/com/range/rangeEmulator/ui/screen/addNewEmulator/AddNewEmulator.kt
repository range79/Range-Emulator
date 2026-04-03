package com.range.rangeEmulator.ui.screen.addNewEmulator

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.range.rangeEmulator.data.enums.*
import com.range.rangeEmulator.data.model.*
import com.range.rangeEmulator.util.HardwareUtil
import com.range.rangeEmulator.viewModel.EmulatorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddNewEmulatorScreen(
    viewModel: EmulatorViewModel,
    onBack: () -> Unit,
    onSave: (VirtualMachineSettings) -> Unit
) {
    val context = LocalContext.current
    val isSavingLocked by viewModel.isSavingVm.collectAsState()

    val deviceMaxRam = remember { HardwareUtil.getTotalRamMB(context) }
    val deviceMaxCores = remember { HardwareUtil.getTotalCores() }
    val hasKvmSupport = remember { HardwareUtil.isKvmSupported() }
    val isGpuSupported = remember { HardwareUtil.isGpuAccelerationSupported(context) }
    val safeLimit = remember { HardwareUtil.getSafeStorageLimitGB(context) }
    val availableSpace = remember { HardwareUtil.getAvailableInternalStorageGB(context) }

    var vmName by remember { mutableStateOf("") }
    var selectedCpu by remember { mutableStateOf(if (hasKvmSupport) CpuModel.HOST else CpuModel.MAX) }
    var ramAmount by remember { mutableStateOf((deviceMaxRam / 4f).coerceIn(512f, deviceMaxRam.toFloat())) }
    var cpuCores by remember { mutableStateOf((deviceMaxCores / 2f).coerceAtLeast(1f)) }
    val maxTbSize = remember { (deviceMaxRam - 512).coerceAtLeast(256) }
    val tbSafeLimit = remember { deviceMaxRam / 3 }
    val combinedSafeLimitMB = remember { 
        if (deviceMaxRam > 8000) (deviceMaxRam * 0.90f).toInt() 
        else (deviceMaxRam * 0.85f).toInt() 
    }
    var tbSize by remember { mutableStateOf(512f) }
    var showTbWarning by remember { mutableStateOf(false) }

    var diskSizeGb by remember { mutableStateOf(8f) }
    var selectedDiskFormat by remember { mutableStateOf(DiskFormat.QCOW2) }
    var customDiskPath by remember { mutableStateOf(context.filesDir.absolutePath) }
    val isStorageDangerouslyHigh = diskSizeGb > safeLimit

    var selectedIsos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var screenWidth by remember { mutableStateOf("1280") }
    var screenHeight by remember { mutableStateOf("720") }

    var selectedScreenType by remember { mutableStateOf(ScreenType.VNC) }
    var vncPort by remember { mutableStateOf("5900") }
    var spicePort by remember { mutableStateOf("5901") }
    var isGpuEnabled by remember { mutableStateOf(true) }

    var isEasyInstallEnabled by remember { mutableStateOf(false) }
    var ezUsername by remember { mutableStateOf("arch") }
    var ezPassword by remember { mutableStateOf("1234") }
    var selectedDE by remember { mutableStateOf(DesktopEnvironment.XFCE) }

    val isoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri -> context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        selectedIsos = (selectedIsos + uris).distinct()
    }
    val directoryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { resolvedUri ->
            val pathStr = resolvedUri.path ?: ""
            if (pathStr.startsWith("/tree/primary:")) {
                val folder = pathStr.substringAfter("/tree/primary:")
                customDiskPath = if (folder.isNotEmpty()) "/storage/emulated/0/$folder" else "/storage/emulated/0"
            } else if (pathStr.startsWith("/tree/") && pathStr.contains(":")) {
                val volumeId = pathStr.substringAfter("/tree/").substringBefore(":")
                val folder = pathStr.substringAfter(":")
                val volPath = "/storage/$volumeId"
                customDiskPath = if (folder.isNotEmpty()) "$volPath/$folder" else volPath
            } else {
                customDiskPath = context.filesDir.absolutePath
            }
        }
    }

    fun performSave() {
        onSave(VirtualMachineSettings(
            vmName = vmName,
            cpuModel = selectedCpu,
            ramMB = ramAmount.toInt(),
            cpuCores = cpuCores.toInt(),
            isoUris = selectedIsos.map { it.toString() },
            diskImgPath = "$customDiskPath/$vmName.${selectedDiskFormat.name.lowercase()}",
            diskFormat = selectedDiskFormat,
            diskSizeGB = diskSizeGb.toInt(),
            screenWidth = if (selectedScreenType == ScreenType.SPICE) 0 else (screenWidth.toIntOrNull() ?: 1280),
            screenHeight = if (selectedScreenType == ScreenType.SPICE) 0 else (screenHeight.toIntOrNull() ?: 720),
            isGpuEnabled = isGpuEnabled,
            screenType = selectedScreenType,
            vncPort = vncPort.toIntOrNull() ?: 5900,
            spicePort = spicePort.toIntOrNull() ?: 5901,
            tbSizeMB = tbSize.toInt(),
            easyInstall = isEasyInstallEnabled,
            easyInstallSettings = if (isEasyInstallEnabled) {
                EasyInstallSettings(ezUsername, ezPassword, selectedDE)
            } else null
        ))
    }

    LaunchedEffect(isEasyInstallEnabled) {
        selectedScreenType = if (isEasyInstallEnabled) ScreenType.SPICE else ScreenType.VNC
    }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create New VM", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    Button(
                        onClick = {
                            if (vmName.isNotBlank() && !isSavingLocked && !isStorageDangerouslyHigh) {
                                if (selectedCpu == CpuModel.HOST && !hasKvmSupport) {
                                    Toast.makeText(context, "Cannot save: KVM is not supported!", Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                                if (tbSize > tbSafeLimit) {
                                    showTbWarning = true
                                } else {
                                    performSave()
                                }
                            }
                        },
                        enabled = vmName.isNotBlank() && !isSavingLocked && !isStorageDangerouslyHigh
                    ) {
                        if (isSavingLocked) CircularProgressIndicator(Modifier.size(16.dp)) else Text("Save")
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
                    TextButton(onClick = { showTbWarning = false; performSave() }) { Text("Proceed Anyway") }
                },
                dismissButton = {
                    TextButton(onClick = { showTbWarning = false }) { Text("Cancel") }
                }
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = vmName,
                onValueChange = { vmName = it },
                label = { Text("VM Name") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Label, null) }
            )

            SectionHeader("Storage & Security")
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(
                    width = if (isStorageDangerouslyHigh) 2.dp else 1.dp,
                    color = if (isStorageDangerouslyHigh) Color.Red else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    TextButton(onClick = { directoryPicker.launch(null) }) {
                        Icon(Icons.Default.Folder, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Path: $customDiskPath", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DiskFormat.entries.forEach { format ->
                            FilterChip(selected = selectedDiskFormat == format, onClick = { selectedDiskFormat = format }, label = { Text(format.name) }, modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    SettingSlider("Disk Size: ${diskSizeGb.toInt()} GB", diskSizeGb, { diskSizeGb = it }, 2f..availableSpace.toFloat(), if(availableSpace > 2) (availableSpace - 2).toInt() else 0, Icons.Default.SdCard)

                    Text(
                        if (isStorageDangerouslyHigh) "Critical: Storage limit exceeded! Max ${safeLimit.toInt()} GB recommended." else "Safe limit: ${safeLimit.toInt()} GB",
                        color = if (isStorageDangerouslyHigh) Color.Red else MaterialTheme.colorScheme.outline,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            SectionHeader("Optical Drives (ISOs)")
            Button(onClick = { isoPicker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add ISO Image")
            }
            selectedIsos.forEach { uri -> ISOListItem(uri = uri, onRemove = { selectedIsos = selectedIsos - uri }) }

            SectionHeader("Display & Graphics")
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable GPU Acceleration")
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScreenType.entries.forEach { type ->
                    FilterChip(selected = selectedScreenType == type, onClick = { if (!isEasyInstallEnabled) selectedScreenType = type }, label = { Text(type.name) }, modifier = Modifier.weight(1f), enabled = !isEasyInstallEnabled)
                }
            }

            EasyInstallSection(isEasyInstallEnabled, { isEasyInstallEnabled = it }, ezUsername, { ezUsername = it }, ezPassword, { ezPassword = it }, selectedDE, { selectedDE = it })

            SectionHeader("Hardware Resources")
            KvmStatusCard(hasKvmSupport)

            var cpuExpanded by remember { mutableStateOf(false) }
            val isKvmMissingForHost = selectedCpu == CpuModel.HOST && !hasKvmSupport

            ExposedDropdownMenuBox(
                expanded = cpuExpanded,
                onExpandedChange = { cpuExpanded = !cpuExpanded }
            ) {
                OutlinedTextField(
                    value = if (isKvmMissingForHost) "${selectedCpu.name} (Unsupported)" else selectedCpu.name,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("CPU Model") },
                    supportingText = {
                        if (isKvmMissingForHost) {
                            Text("KVM is not supported on this device!", color = MaterialTheme.colorScheme.error)
                        } else {
                            Text(selectedCpu.getModeDescription())
                        }
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cpuExpanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                    isError = isKvmMissingForHost,
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenu(
                    expanded = cpuExpanded,
                    onDismissRequest = { cpuExpanded = false }
                ) {
                    CpuModel.entries.forEach { cpu ->
                        val requiresKvm = cpu == CpuModel.HOST
                        val isSupported = !requiresKvm || hasKvmSupport

                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = if (!isSupported) "${cpu.name} (KVM Required)" else cpu.name,
                                        fontWeight = FontWeight.Bold,
                                        color = if (!isSupported) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(cpu.getModeDescription(), style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            onClick = {
                                if (isSupported) {
                                    selectedCpu = cpu
                                    cpuExpanded = false
                                } else {
                                    Toast.makeText(context, "Your device lacks KVM support for HOST mode.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = isSupported
                        )
                    }
                }
            }

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
                title = "CPU Cores: ${cpuCores.toInt()}", 
                value = cpuCores, 
                onValueChange = { cpuCores = it }, 
                valueRange = 1f..deviceMaxCores.toFloat(), 
                steps = (deviceMaxCores - 1).coerceAtLeast(1).toInt(), 
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
                "Info: Larger cache is faster but uses more device RAM. Max allowed: ${maxTbSize}MB.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}