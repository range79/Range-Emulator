package com.range.rangeEmulator.ui.screen.addNewEmulator

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.range.rangeEmulator.data.enums.Architecture
import com.range.rangeEmulator.data.enums.CpuModel
import com.range.rangeEmulator.data.enums.OsType
import com.range.rangeEmulator.util.HardwareUtil

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
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (hasKvm) Icons.Default.Verified else Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (hasKvm) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (hasKvm) "KVM Hardware Acceleration Active" else "KVM Acceleration Not Detected",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun SettingSlider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    icon: ImageVector
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.labelLarge)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps)
    }
}

fun getCpuDescription(model: CpuModel): String = when (model) {
    CpuModel.HOST ->
        "Native Hardware Access: Direct silicon speed. | Performance: Up to 100% (KVM)"

    CpuModel.MAX ->
        "Peak Software Speed: Fastest non-KVM mode. | Performance: Up to ~60% with Titan Mode"

    CpuModel.CORTEX_A76 ->
        "Modern ARM64: Optimized for AArch64 distros. | Performance: Up to ~40% with Titan Mode"

    CpuModel.CORTEX_A72 ->
        "Standard ARM64: Balanced stability and speed. | Performance: Up to ~30% with Titan Mode"

    CpuModel.CORTEX_A53 ->
        "Ultra Efficient: Lowest overhead, very slow. | Performance: Up to ~15% with Titan Mode"

    CpuModel.NEOVERSE_N1 ->
        "Cloud-Class ARM: Optimized for server loads. | Performance: Up to ~45% with Titan Mode"

    else -> "Generic Model: Standard software emulation. | Performance: ~20-25% with Titan Mode"
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CpuModelDropdown(
    selectedModel: CpuModel,
    hasKvm: Boolean,
    onModelSelected: (CpuModel) -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = if (selectedModel.requiresKvm() && !hasKvm) "${selectedModel.name} (Unsupported)" else selectedModel.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("CPU Architecture") },
            supportingText = { Text(getCpuDescription(selectedModel)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                .fillMaxWidth(),
            isError = selectedModel.requiresKvm() && !hasKvm
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            CpuModel.entries.forEach { model ->
                val isUnsupported = model.requiresKvm() && !hasKvm

                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = if (isUnsupported) "${model.name} (KVM Required)" else model.name,
                                color = if (isUnsupported) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = getCpuDescription(model),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isUnsupported) MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                                else MaterialTheme.colorScheme.outline
                            )
                        }
                    },
                    onClick = {
                        if (isUnsupported) {
                            Toast.makeText(context, "Device does not support KVM!", Toast.LENGTH_SHORT).show()
                        } else {
                            onModelSelected(model)
                            expanded = false
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SystemConfigPanel(
    selectedOs: OsType,
    selectedArch: Architecture,
    selectedCpu: CpuModel,
    isTpmEnabled: Boolean,
    hasKvm: Boolean,
    onOsSelected: (OsType) -> Unit,
    onArchSelected: (Architecture) -> Unit,
    onCpuSelected: (CpuModel) -> Unit,
    onTpmSelected: (Boolean) -> Unit
) {
    val context = LocalContext.current
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("System Configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OsType.entries.forEach { os ->
                    OsCard(
                        os = os,
                        isSelected = selectedOs == os,
                        onClick = { onOsSelected(os) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Target Architecture", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Architecture.entries.forEach { arch ->
                        val isSupported = arch == Architecture.AARCH64
                        val isSelected = selectedArch == arch
                        Surface(
                            onClick = { 
                                if (isSupported) onArchSelected(arch) 
                                else Toast.makeText(context, "x86_64 architecture is no longer supported.", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary 
                                    else if (!isSupported) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                    else Color.Transparent,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary 
                                          else if (!isSupported) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                          else MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(vertical = 10.dp)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (arch == Architecture.AARCH64) Icons.Default.Memory else Icons.Default.Computer,
                                        null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (!isSupported && !isSelected) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else Color.Unspecified
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        arch.toString().substringBefore(" ("),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Processor Model", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                CpuModelDropdown(
                    selectedModel = selectedCpu,
                    hasKvm = hasKvm,
                    onModelSelected = onCpuSelected
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                    Icon(Icons.Default.Info, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "x86_64 architecture is no longer supported.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("TPM 2.0 Security", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text("Required for Windows 11 features.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = isTpmEnabled,
                        onCheckedChange = onTpmSelected
                    )
                }
            }
        }
    }
}

@Composable
private fun OsCard(
    os: OsType,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            when (os) {
                OsType.LINUX -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                OsType.WINDOWS -> Color(0xFF0078D4).copy(alpha = 0.1f)
                OsType.ANDROID -> Color(0xFF3DDC84).copy(alpha = 0.1f)
                OsType.OTHER -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        } else MaterialTheme.colorScheme.surface,
        label = "bgColor"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            when (os) {
                OsType.LINUX -> MaterialTheme.colorScheme.secondary
                OsType.WINDOWS -> Color(0xFF0078D4)
                OsType.ANDROID -> Color(0xFF3DDC84)
                OsType.OTHER -> MaterialTheme.colorScheme.outline
            }
        } else MaterialTheme.colorScheme.outlineVariant,
        label = "borderColor"
    )

    val elevation by animateDpAsState(targetValue = if (isSelected) 4.dp else 0.dp, label = "elevation")

    OutlinedCard(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, borderColor),
        colors = CardDefaults.outlinedCardColors(containerColor = backgroundColor),
        elevation = CardDefaults.outlinedCardElevation(defaultElevation = elevation)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = when (os) {
                    OsType.LINUX -> Icons.Default.Terminal
                    OsType.WINDOWS -> Icons.Default.Window
                    OsType.ANDROID -> Icons.Default.Android
                    OsType.OTHER -> Icons.Default.MoreHoriz
                },
                contentDescription = null,
                tint = borderColor,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = os.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PerformanceTuningCard(
    isTitanEnabled: Boolean,
    isCacheUnsafe: Boolean,
    isMemPrealloc: Boolean,
    isGicV3: Boolean,
    isIoThread: Boolean,
    is4kAlignment: Boolean,
    onTitanToggled: (Boolean) -> Unit,
    onGranularChange: (OptimizationType, Boolean) -> Unit,
    osType: OsType = OsType.LINUX
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Power & Performance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ModeSelectionItem(
                    title = "Balanced",
                    subtitle = "Stable (100% CPU)",
                    icon = Icons.Default.Shield,
                    checked = !isTitanEnabled,
                    onCheckedChange = { if (it) onTitanToggled(false) },
                    activeColor = Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f)
                )
                ModeSelectionItem(
                    title = "Titan",
                    subtitle = "Up to 300% CPU Speed",
                    icon = Icons.Default.FlashOn,
                    checked = isTitanEnabled,
                    onCheckedChange = onTitanToggled,
                    activeColor = Color.Red,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            var showAdvanced by remember { mutableStateOf(false) }
            TextButton(
                onClick = { showAdvanced = !showAdvanced },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(if (showAdvanced) "Hide Advanced Controls" else "Show Advanced Controls", style = MaterialTheme.typography.labelSmall)
                Icon(if (showAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, Modifier.size(16.dp))
            }

            if (showAdvanced) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 2
                ) {
                    val titanLocked = isTitanEnabled

                    OptimizationToggle(
                        label = "Cache Unsafe",
                        description = "+150% CPU Boost",
                        checked = titanLocked || isCacheUnsafe,
                        onCheckedChange = { onGranularChange(OptimizationType.CACHE_UNSAFE, it) },
                        enabled = !titanLocked,
                        modifier = Modifier.weight(1f)
                    )
                    OptimizationToggle(
                        label = "Mem Prealloc",
                        description = "+50% Stability",
                        checked = titanLocked || isMemPrealloc,
                        onCheckedChange = { onGranularChange(OptimizationType.MEM_PREALLOC, it) },
                        enabled = !titanLocked,
                        modifier = Modifier.weight(1f)
                    )
                    OptimizationToggle(
                        label = "Discard/TRIM",
                        description = "Standard",
                        checked = true,
                        onCheckedChange = { },
                        enabled = false,
                        modifier = Modifier.weight(1f)
                    )
                    OptimizationToggle(
                        label = "IOThread",
                        description = "Standard",
                        checked = true,
                        onCheckedChange = { },
                        enabled = false,
                        modifier = Modifier.weight(1f)
                    )
                    OptimizationToggle(
                        label = "GIC v3",
                        description = "+30% IO Speed",
                        checked = titanLocked || isGicV3,
                        onCheckedChange = { onGranularChange(OptimizationType.GIC_V3, it) },
                        enabled = !titanLocked,
                        modifier = Modifier.weight(1f)
                    )

                    val isWindowsTitan = osType == OsType.WINDOWS && titanLocked
                    OptimizationToggle(
                        label = "4K Alignment",
                        description = if (isWindowsTitan) "Disabled (Stability)" else "Turbo IO",
                        checked = if (isWindowsTitan) false else (titanLocked || is4kAlignment),
                        onCheckedChange = { onGranularChange(OptimizationType.ALIGN_4K, it) },
                        enabled = !isWindowsTitan,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

enum class OptimizationType { CACHE_UNSAFE, MEM_PREALLOC, DISCARD, IOTHREAD, GIC_V3, DETECT_ZEROES, ALIGN_4K }

@Composable
private fun ModeSelectionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(20.dp),
        color = if (checked) activeColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.5.dp, if (checked) activeColor else Color.Transparent),
        modifier = modifier.height(80.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(if (checked) activeColor else MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = if (checked) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, color = if (checked) activeColor else MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ISOListItem(
    uri: Uri,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val fileName = remember(uri) {
        uri.path?.substringAfterLast("/") ?: "Selected ISO"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = Icons.Default.Album,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove ISO",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun OptimizationToggle(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = { if (enabled) onCheckedChange(!checked) },
        modifier = modifier.height(60.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (checked) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f) 
               else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.1f else 0.05f),
        border = BorderStroke(1.dp, if (checked) MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f) else Color.Transparent)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = Modifier.scale(0.7f)
            )
        }
    }
}