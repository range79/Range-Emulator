package com.range.phoneLinuxer.ui.screen.settings

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun LinuxSettingHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(16.dp, 8.dp)
    )
}

@Composable
fun LinuxSettingItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    var isRotated by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (isRotated) 360f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "icon_spin"
    )

    Surface(
        onClick = {
            if (title == "Developer") isRotated = !isRotated
            onClick?.invoke()
        },
        enabled = onClick != null
    ) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(subtitle) },
            leadingContent = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer(rotationZ = rotation)
                )
            },
            trailingContent = trailing
        )
    }
}

@Composable
fun LinuxSettingSwitchItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) }
    )
}