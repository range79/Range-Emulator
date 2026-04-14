package com.range.rangeEmulator.ui.screen.emulatorList

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import com.range.rangeEmulator.data.enums.*
import com.range.rangeEmulator.data.model.*
import com.range.rangeEmulator.ui.screen.addNewEmulator.CpuModelDropdown
import com.range.rangeEmulator.ui.screen.addNewEmulator.ISOListItem
import com.range.rangeEmulator.ui.screen.addNewEmulator.KvmStatusCard
import com.range.rangeEmulator.ui.screen.addNewEmulator.OptimizationType
import com.range.rangeEmulator.ui.screen.addNewEmulator.PerformanceTuningCard
import com.range.rangeEmulator.ui.screen.addNewEmulator.SectionHeader
import com.range.rangeEmulator.ui.screen.addNewEmulator.SystemConfigPanel
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
    val isEngineGpuSupported = remember { HardwareUtil.isEngineVirglSupported(context) }

    var vmName by remember { mutableStateOf("") }
    var selectedCpu by remember { mutableStateOf(CpuModel.MAX) }
    var ramAmount by remember { mutableFloatStateOf((deviceMaxRam / 4f).coerceIn(512f, deviceMaxRam.toFloat())) }
    var cpuCores by remember { mutableFloatStateOf((deviceMaxCores / 2f).coerceAtLeast(1f)) }
    var isGpuEnabled by remember { mutableStateOf(true) }
    var selectedOsType by remember { mutableStateOf(OsType.LINUX) }
    var selectedArch by remember { mutableStateOf(Architecture.AARCH64) }
    var selectedIsos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val diskList = remember { mutableStateListOf<DiskConfig>() }
    var showAddDiskDialog by remember { mutableStateOf(false) }

    var isCacheUnsafe by remember { mutableStateOf(false) }
    var isMemPreallocEnabled by remember { mutableStateOf(false) }
    var is4kAlignmentEnabled by remember { mutableStateOf(false) }
    var isDiscardEnabled by remember { mutableStateOf(true) }
    var isDetectZeroesEnabled by remember { mutableStateOf(true) }
    var isGicV3Enabled by remember { mutableStateOf(true) }
    var isIoThreadEnabled by remember { mutableStateOf(true) }

    var newDiskSize by remember { mutableStateOf(10f) }
    var newDiskFormat by remember { mutableStateOf(DiskFormat.QCOW2) }
    var newDiskLabel by remember { mutableStateOf("") }
    var customDiskPath by remember { mutableStateOf(context.filesDir.absolutePath) }
    val availableSpace = remember { HardwareUtil.getAvailableInternalStorageGB(context) }
    
    val existingDiskPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { resolvedUri ->
            val path = resolvedUri.toString()
            val fileName = resolvedUri.path?.substringAfterLast("/")?.substringBeforeLast(".") ?: "Existing Disk"
            diskList.add(DiskConfig(label = fileName, path = path, format = DiskFormat.QCOW2, sizeGB = 0)) 
            showAddDiskDialog = false
        }
    }
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

    var screenWidth by remember { mutableStateOf("1280") }
    var screenHeight by remember { mutableStateOf("720") }

    var vncPort by remember { mutableStateOf("5900") }
    var spicePort by remember { mutableStateOf("5901") }
    var selectedScreenType by remember { mutableStateOf(ScreenType.VNC) }
    var isTitanModeEnabled by remember { mutableStateOf(false) }
    var selectedDiskInterface by remember { mutableStateOf(DiskInterface.NVME) }
    var isTpmEnabled by remember { mutableStateOf(false) }
    var showTitanWarning by remember { mutableStateOf(false) }
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
            screenWidth = if (vm.screenWidth == 0) "1280" else vm.screenWidth.toString()
            screenHeight = if (vm.screenHeight == 0) "720" else vm.screenHeight.toString()
            vncPort = vm.vncPort.toString()
            spicePort = vm.spicePort.toString()
            selectedScreenType = vm.screenType
            tbSize = vm.tbSizeMB.toFloat()
            selectedOsType = vm.osType
            selectedIsos = vm.isoUris.map { Uri.parse(it) }
            isTitanModeEnabled = vm.isTitanModeEnabled
            selectedDiskInterface = vm.diskInterface
            isTpmEnabled = vm.isTpmEnabled
            selectedArch = vm.arch
            diskList.clear()
            diskList.addAll(vm.disks)

            isCacheUnsafe = vm.isCacheUnsafe
            isMemPreallocEnabled = vm.isMemPreallocEnabled
            is4kAlignmentEnabled = vm.is4kAlignmentEnabled
            isDiscardEnabled = vm.isDiscardEnabled
            isDetectZeroesEnabled = vm.isDetectZeroesEnabled
            isGicV3Enabled = vm.isGicV3Enabled
            isIoThreadEnabled = vm.isIoThreadEnabled
        }
    }

    fun performUpdate() {
        isSavingLocked = true
        editingVm?.copy(
            vmName = vmName,
            osType = selectedOsType,
            cpuModel = selectedCpu,
            ramMB = ramAmount.toInt(),
            cpuCores = cpuCores.toInt(),
            isGpuEnabled = isGpuEnabled,
            screenType = selectedScreenType,
            screenWidth = if (selectedScreenType == ScreenType.SPICE) 0 else (screenWidth.toIntOrNull() ?: 1280),
            screenHeight = if (selectedScreenType == ScreenType.SPICE) 0 else (screenHeight.toIntOrNull() ?: 720),
            vncPort = vncPort.toIntOrNull() ?: 5900,
            spicePort = spicePort.toIntOrNull() ?: 5901,
            tbSizeMB = tbSize.toInt(),
            isoUris = selectedIsos.map { it.toString() },
            disks = diskList.toList(),
            diskInterface = selectedDiskInterface,
            isTitanModeEnabled = isTitanModeEnabled,
            isTpmEnabled = isTpmEnabled,
            arch = selectedArch,
            isCacheUnsafe = isCacheUnsafe,
            isMemPreallocEnabled = isMemPreallocEnabled,
            is4kAlignmentEnabled = is4kAlignmentEnabled,
            isDiscardEnabled = isDiscardEnabled,
            isDetectZeroesEnabled = isDetectZeroesEnabled,
            isGicV3Enabled = isGicV3Enabled,
            isIoThreadEnabled = isIoThreadEnabled
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
        if (showAddDiskDialog) {
            AlertDialog(
                onDismissRequest = { showAddDiskDialog = false },
                title = { Text("Add Virtual Disk") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = newDiskLabel,
                            onValueChange = { newDiskLabel = it },
                            label = { Text("Disk Label (e.g. System, Games)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Text("Or create a new blank disk image:", style = MaterialTheme.typography.labelSmall)
                        
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DiskFormat.entries.forEach { format ->
                                FilterChip(
                                    selected = newDiskFormat == format,
                                    onClick = { newDiskFormat = format },
                                    label = { Text(format.name) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        SettingSlider(
                            title = "Size: ${newDiskSize.toInt()} GB",
                            value = newDiskSize,
                            onValueChange = { newDiskSize = it },
                            valueRange = 2f..availableSpace.coerceAtLeast(10f).toFloat(),
                            steps = 0,
                            icon = Icons.Default.SdCard
                        )
                        
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { directoryPicker.launch(null) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Folder, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Location", style = MaterialTheme.typography.labelSmall)
                            }
                            Button(onClick = { existingDiskPicker.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1.5f)) {
                                Icon(Icons.Default.FileUpload, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Select Existing", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Text("Current Path: $customDiskPath", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val finalLabel = newDiskLabel.ifBlank { "Disk ${diskList.size + 1}" }
                        val fileName = "${vmName}_extra_${System.currentTimeMillis()}.${newDiskFormat.name.lowercase()}"
                        diskList.add(DiskConfig(label = finalLabel, path = "$customDiskPath/$fileName", format = newDiskFormat, sizeGB = newDiskSize.toInt()))
                        showAddDiskDialog = false
                        newDiskLabel = ""
                    }) { Text("Create & Add") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDiskDialog = false }) { Text("Cancel") }
                }
            )
        }

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

        if (showTitanWarning) {
            AlertDialog(
                onDismissRequest = { showTitanWarning = false },
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = Color.Red)
                        Spacer(Modifier.width(8.dp))
                        Text("Titan Mode: Extreme Risk", color = Color.Red)
                    }
                },
                text = { 
                    Column {
                        Text("Titan Mode enables dangerous performance optimizations:", fontWeight = FontWeight.Bold)
                        Text("• cache=unsafe: Writes are not flushed to disk. A crash WILL result in data corruption.")
                        Text("• packed=on: Experimental VirtIO optimizations.")
                        Text("\nDo not use this for important data. Only for speed tests and throwaway VMs.")
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { 
                            isTitanModeEnabled = true
                            showTitanWarning = false 
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) { Text("I Accept the Risk") }
                },
                dismissButton = {
                    TextButton(onClick = { showTitanWarning = false }) { Text("Cancel") }
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = vmName,
                onValueChange = { vmName = it },
                label = { Text("VM Name") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Label, null) }
            )

            SectionHeader("Core Configuration")
            SystemConfigPanel(
                selectedOs = selectedOsType,
                selectedArch = selectedArch,
                selectedCpu = selectedCpu,
                isTpmEnabled = isTpmEnabled,
                hasKvm = hasKvmSupport,
                onOsSelected = { selectedOsType = it },
                onArchSelected = { arch ->
                    selectedArch = arch
                },
                onCpuSelected = { selectedCpu = it },
                onTpmSelected = { isTpmEnabled = it }
            )

            SectionHeader("Storage Management")
            
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Disk Controller", fontWeight = FontWeight.Bold)
                    Text(
                        text = if (selectedDiskInterface == DiskInterface.NVME) 
                            "NVMe: Compatible & Easy. No extra drivers needed." 
                            else "VirtIO: Maximum Speed. Requires loading drivers manually.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DiskInterface.entries.forEach { iface ->
                            FilterChip(
                                selected = selectedDiskInterface == iface,
                                onClick = { selectedDiskInterface = iface },
                                label = { Text(iface.name) },
                                modifier = Modifier.weight(1f)
                             )
                        }
                    }
                }
            }

            Button(onClick = { showAddDiskDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add Virtual Disk")
            }

            diskList.forEach { disk ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SdCard, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            val displayName = disk.label.ifBlank { disk.path.substringAfterLast("/") }
                            Text(displayName, fontWeight = FontWeight.Bold)
                            Text(
                                if (disk.sizeGB > 0) "${disk.sizeGB} GB • ${disk.format.name} • ${disk.path.substringBeforeLast("/")}"
                                else "Existing Disk • ${disk.path}", 
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        IconButton(onClick = { 
                            if (diskList.size > 1) diskList.remove(disk)
                            else Toast.makeText(context, "Cannot remove the only disk!", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.Delete, null, tint = Color.Red)
                        }
                    }
                }
            }

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

            SectionHeader("Optical Drives (ISOs)")
            Button(onClick = { isoPicker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add ISO Image")
            }
            selectedIsos.forEach { uri -> ISOListItem(uri = uri, onRemove = { selectedIsos = selectedIsos - uri }) }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Terminal, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Enable Virtio-GPU")
                    if (!isGpuSupported) {
                        Text(
                            "Hardware Alert: This phone does not support OpenGL ES 3.0+. 3D acceleration will crash or fail to start.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            "Status: Supported. Virtio-GPU uses your phone's GPU. Disable if the VM becomes unstable.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Switch(checked = isGpuEnabled, onCheckedChange = { isGpuEnabled = it })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Resources",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = {
                        isTitanModeEnabled = true
                        cpuCores = deviceMaxCores.toFloat()
                        val targetTb = (deviceMaxRam * 0.20f).coerceIn(1024f, 4096f).coerceAtMost(maxTbSize.toFloat())
                        tbSize = targetTb
                        ramAmount = (combinedSafeLimitMB - targetTb).coerceIn(512f, deviceMaxRam.toFloat())
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Bolt, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Max Performance Preset")
                }
            }
            PerformanceTuningCard(
                isTitanEnabled = isTitanModeEnabled,
                isCacheUnsafe = isCacheUnsafe,
                isMemPrealloc = isMemPreallocEnabled,
                isGicV3 = isGicV3Enabled,
                isIoThread = isIoThreadEnabled,
                is4kAlignment = is4kAlignmentEnabled,
                osType = selectedOsType,
                onTitanToggled = { enabled: Boolean -> 
                    if (enabled) {
                        showTitanWarning = true 
                    } else {
                        isTitanModeEnabled = false
                    }
                },
                onGranularChange = { type: OptimizationType, value: Boolean ->
                    when (type) {
                        OptimizationType.CACHE_UNSAFE -> isCacheUnsafe = value
                        OptimizationType.MEM_PREALLOC -> isMemPreallocEnabled = value
                        OptimizationType.DISCARD -> isDiscardEnabled = value
                        OptimizationType.IOTHREAD -> isIoThreadEnabled = value
                        OptimizationType.GIC_V3 -> isGicV3Enabled = value
                        OptimizationType.DETECT_ZEROES -> isDetectZeroesEnabled = value
                        OptimizationType.ALIGN_4K -> is4kAlignmentEnabled = value
                    }
                }
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
                steps = (deviceMaxCores - 1).coerceAtLeast(1),
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
