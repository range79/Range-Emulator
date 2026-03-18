package com.range.phoneLinuxer.ui.screen.addNewEmulator

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.range.phoneLinuxer.data.enums.CpuModel

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
            text = if (hasKvm) "✅ KVM Supported" else "⚠️ KVM Not Detected",
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.labelSmall
        )
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
        "KVM Passthrough: Native hardware access. | Performance: 95-99% (Native Speed)"

    CpuModel.CORTEX_A76 ->
        "Modern ARM64: Optimized for 64-bit distros. | Performance: ~45-55%"

    CpuModel.CORTEX_A72 ->
        "Standard ARM64: High stability, broad support. | Performance: ~35-40% (Stable Execution)"

    CpuModel.CORTEX_A53 ->
        "Power Efficient: Low thermal profile. | Performance: ~25-30% (Best for Background)"

    CpuModel.MAX ->
        "Full Feature Emulation: Software-based features. | Performance: ~15-20% (Heavy Load)"

    CpuModel.QEMU64 ->
        "Binary Translation (x86_64): Cross-arch overhead. | Performance: ~5-10% (Usable for CLI)"

    CpuModel.HASWELL ->
        "Complex x86_64: AVX2 translation stress. | Performance: ~2-5% (High Heat)"

    CpuModel.NEOVERSE_N1 ->
        "Cloud-Class ARM: Multi-threaded workloads. | Performance: ~40-45% (High Throughput)"

    else -> "Generic Model: Standard software emulation. | Performance: ~20-25% (Basic Use)"
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