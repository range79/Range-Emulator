package com.range.rangeEmulator.ui.screen.addNewEmulator


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.range.rangeEmulator.data.enums.DesktopEnvironment

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
                    Text("Automated OS setup", style = MaterialTheme.typography.labelSmall)
                }
                Switch(checked = isEnabled, onCheckedChange = onToggle)
            }

            AnimatedVisibility(visible = isEnabled, enter = expandVertically(), exit = shrinkVertically()) {
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = username, onValueChange = onUsernameChange, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation()
                    )
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