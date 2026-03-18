package com.range.phoneLinuxer.ui.screen.emulator

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import com.range.phoneLinuxer.util.HardwareUtil
import com.range.phoneLinuxer.util.NavDebouncer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddNewEmulatorScreen(
    onBack: () -> Unit,
    onSave: (VirtualMachineSettings) -> Unit
) {
    val context = LocalContext.current
    val deviceMaxRam = remember { HardwareUtil.getTotalRamMB(context) }
    val deviceMaxCores = remember { HardwareUtil.getTotalCores() }
    val hasKvmSupport = remember { HardwareUtil.isKvmSupported() }

    var vmName by remember { mutableStateOf("") }
    var selectedCpu by remember { mutableStateOf(CpuModel.MAX) }
    var ramAmount by remember { mutableFloatStateOf((deviceMaxRam / 4f).coerceIn(512f, deviceMaxRam.toFloat())) }
    var cpuCores by remember { mutableFloatStateOf((deviceMaxCores / 2f).coerceAtLeast(1f)) }
    var isGpuEnabled by remember { mutableStateOf(true) }
    var selectedScreenType by remember { mutableStateOf(ScreenType.VNC) }
    var selectedNetwork by remember { mutableStateOf(NetworkMode.USER) }

    var selectedIsoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedIsoName by remember { mutableStateOf("No ISO Selected") }

    val isoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            selectedIsoUri = it
            selectedIsoName = it.path?.substringAfterLast("/") ?: "Selected Image"
        }
    }

    var isEasyInstallEnabled by remember { mutableStateOf(false) }
    var ezUsername by remember { mutableStateOf("user") }
    var ezPassword by remember { mutableStateOf("1234") }
    var selectedDE by remember { mutableStateOf(DesktopEnvironment.XFCE) }

    BackHandler { if (NavDebouncer.canNavigate()) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create New VM") },
                navigationIcon = {
                    IconButton(onClick = { if (NavDebouncer.canNavigate()) onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            if (vmName.isNotBlank() && NavDebouncer.canNavigate()) {
                                val settings = VirtualMachineSettings(
                                    vmName = vmName,
                                    cpuModel = selectedCpu,
                                    ramMB = ramAmount.toInt(),
                                    cpuCores = cpuCores.toInt(),
                                    isoUri = selectedIsoUri?.toString(),
                                    isGpuEnabled = isGpuEnabled,
                                    screenType = if (isEasyInstallEnabled) ScreenType.RDP else selectedScreenType,
                                    networkMode = selectedNetwork,
                                    easyInstall = isEasyInstallEnabled,
                                    easyInstallSettings = if (isEasyInstallEnabled) {
                                        EasyInstallSettings(ezUsername, ezPassword, selectedDE)
                                    } else null
                                )
                                onSave(settings)
                            }
                        },
                        enabled = vmName.isNotBlank()
                    ) { Text("Save") }
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
                label = { Text("Virtual Machine Name") },
                placeholder = { Text("e.g. Arch Linux") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Card(
                onClick = { isoPickerLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FileOpen, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Boot Image (ISO/IMG)", style = MaterialTheme.typography.labelLarge)
                        Text(selectedIsoName, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                }
            }

            EasyInstallSection(
                isEnabled = isEasyInstallEnabled,
                onToggle = { isEasyInstallEnabled = it },
                username = ezUsername,
                onUsernameChange = { ezUsername = it },
                password = ezPassword,
                onPasswordChange = { ezPassword = it },
                selectedDE = selectedDE,
                onDEChange = { selectedDE = it }
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader("Hardware Resources")
                KvmStatusCard(hasKvmSupport)
                CpuModelDropdown(selectedCpu) { selectedCpu = it }

                AnimatedVisibility(visible = selectedCpu == CpuModel.HOST && !hasKvmSupport) {
                    Text(
                        "🛑 KVM not found. HOST model might fail.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val ramMax = (deviceMaxRam * 0.8f)
                SettingSlider(
                    title = "RAM: ${ramAmount.toInt()} MB",
                    value = ramAmount,
                    onValueChange = { ramAmount = it },
                    valueRange = 512f..ramMax,
                    steps = ((ramMax - 512f) / 128).toInt(),
                    icon = Icons.Default.Memory
                )
                SettingSlider(
                    title = "CPU Cores: ${cpuCores.toInt()}",
                    value = cpuCores,
                    onValueChange = { cpuCores = it },
                    valueRange = 1f..deviceMaxCores.toFloat(),
                    steps = deviceMaxCores - 1,
                    icon = Icons.Default.SettingsInputComponent
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SectionHeader("Display & Connectivity")

                AnimatedVisibility(visible = !isEasyInstallEnabled) {
                    Column {
                        Text("Display Protocol", style = MaterialTheme.typography.labelMedium)
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
                    }
                }

                Column {
                    Text("Network Mode", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NetworkMode.entries.forEach { mode ->
                            FilterChip(
                                selected = selectedNetwork == mode,
                                onClick = { selectedNetwork = mode },
                                label = { Text(mode.name) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("GPU Acceleration", style = MaterialTheme.typography.bodyLarge)
                        Text("VirGL support for 3D graphics", style = MaterialTheme.typography.labelSmall)
                    }
                    Switch(checked = isGpuEnabled, onCheckedChange = { isGpuEnabled = it })
                }
            }
        }
    }
}

@Composable
fun EasyInstallSection(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    selectedDE: DesktopEnvironment,
    onDEChange: (DesktopEnvironment) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoFixHigh, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Easy Install", style = MaterialTheme.typography.titleMedium)
                    Text("Automated OS setup & RDP config", style = MaterialTheme.typography.labelSmall)
                }
                Switch(checked = isEnabled, onCheckedChange = onToggle)
            }

            AnimatedVisibility(visible = isEnabled, enter = expandVertically(), exit = shrinkVertically()) {
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = username, onValueChange = onUsernameChange, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = password, onValueChange = onPasswordChange, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
                    Text("Desktop Environment", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DesktopEnvironment.entries.forEach { de ->
                            FilterChip(selected = selectedDE == de, onClick = { onDEChange(de) }, label = { Text(de.name) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)
    }
}

@Composable
fun KvmStatusCard(hasKvm: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (hasKvm) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = if (hasKvm) "✅ KVM Supported! Best performance with 'HOST'."
            else "⚠️ KVM Not Detected. Emulation will be slower.",
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun SettingSlider(title: String, value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>, steps: Int, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.labelLarge)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CpuModelDropdown(selectedModel: CpuModel, onModelSelected: (CpuModel) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selectedModel.name, onValueChange = {}, readOnly = true,
            label = { Text("CPU Architecture") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CpuModel.entries.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(model.name, style = MaterialTheme.typography.bodyLarge)
                            Text(getCpuInfo(model), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    },
                    onClick = { onModelSelected(model); expanded = false }
                )
            }
        }
    }
}

fun getCpuInfo(model: CpuModel): String = when (model) {
    CpuModel.HOST -> "Native Passthrough: Max performance (KVM required)."
    CpuModel.MAX -> "Best Software Emulation: All QEMU features enabled."
    CpuModel.CORTEX_A76 -> "ARMv8.2-A: Modern 64-bit performance core."
    CpuModel.CORTEX_A72 -> "ARMv8-A: Reliable and widely compatible."
    CpuModel.CORTEX_A53 -> "ARMv8-A: Power-efficient, lower overhead."
    CpuModel.QEMU64 -> "Standard x86_64: Best for common PC Linux distros."
    CpuModel.HASWELL -> "Intel Gen 4: Advanced features like AVX2."
    else -> "Generic architecture model."
}