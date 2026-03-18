package com.range.phoneLinuxer.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.range.phoneLinuxer.data.enums.DarkModeEnum

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinuxDarkModeSheet(
    currentMode: DarkModeEnum,
    onModeSelected: (DarkModeEnum) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            DarkModeEnum.entries.forEach { mode ->
                ListItem(
                    headlineContent = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    leadingContent = {
                        Icon(
                            imageVector = when(mode) {
                                DarkModeEnum.LIGHT -> Icons.Default.LightMode
                                DarkModeEnum.DARK -> Icons.Default.DarkMode
                                DarkModeEnum.SYSTEM -> Icons.Default.SettingsSuggest
                            },
                            contentDescription = null
                        )
                    },
                    trailingContent = { RadioButton(selected = currentMode == mode, onClick = null) },
                    modifier = Modifier.clickable { onModeSelected(mode) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinuxColorPickerSheet(
    selectedColorArgb: Int,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val colorOptions = listOf(
        Color(0xFF6750A4), Color(0xFF388E3C), Color(0xFF1976D2),
        Color(0xFFD32F2F), Color(0xFFFBC02D), Color(0xFF0097A7)
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 24.dp)) {
            Text("Theme Color", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                colorOptions.forEach { color ->
                    Box(
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(color)
                            .clickable { onColorSelected(color) }.padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedColorArgb == color.toArgb()) {
                            Icon(Icons.Default.Check, null, tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}