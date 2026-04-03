package com.range.rangeEmulator.ui.screen.download

import android.content.Intent
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri

@Composable
fun LinuxDownloadButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun DownloadProgressPanel(
    progress: Int, status: String, isDownloading: Boolean, isPaused: Boolean,
    speed: String, eta: String, onTogglePause: () -> Unit, onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        text = if (progress >= 0) "$progress%" else "Wait",
                        fontSize = 32.sp, fontWeight = FontWeight.Black,
                        color = if (progress == -1) MaterialTheme.colorScheme.error else if (isPaused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
                    )
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!isPaused && isDownloading && progress > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(speed, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                        Text(eta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            LinearProgressIndicator(
                progress = { if (progress > 0) progress / 100f else 0f },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = if (isPaused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                FilledTonalIconButton(onClick = onTogglePause, enabled = progress != -1 && !status.contains("Success")) {
                    Icon(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, "Control")
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onCancel, colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Default.Close, "Cancel")
                }
            }
        }
    }
}

@Composable
fun SupportSmallCard() {
    val context = LocalContext.current
    val infiniteTransition = rememberInfiniteTransition(label = "heartbeat")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale"
    )

    Surface(
        onClick = {
            val intent = Intent(Intent.ACTION_VIEW, "https://buymeacoffee.com/darkrange6s".toUri())
            context.startActivity(intent)
        },
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(Icons.Default.Favorite, null, tint = Color(0xFFE91E63), modifier = Modifier.size(16.dp).graphicsLayer(scaleX = scale, scaleY = scale))
            Spacer(Modifier.width(12.dp))
            Text("Support the developer", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}