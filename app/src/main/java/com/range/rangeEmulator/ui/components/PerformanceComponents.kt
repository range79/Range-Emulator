package com.range.rangeEmulator.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.range.rangeEmulator.util.HardwareUtil
import java.io.File

@Composable
fun PerformanceDashboardCard(
    isTitanEnabled: Boolean,
    isIgnoringBattery: Boolean,
    isMonitoring: Boolean,
    cpuUsage: Int,
    ramUsage: Pair<Long, Long>,
    cpuTemp: Int,
    batteryTemp: Int,
    thermalStatus: String,
    onToggleMonitor: (Boolean) -> Unit,
    onRequestBatteryExemption: () -> Unit
) {
    val context = LocalContext.current
    val isKvmSupported = remember { File("/dev/kvm").exists() }
    val isGpuSupported = remember { HardwareUtil.isGpuAccelerationSupported(context) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "CORE Cockpit", 
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = if (isTitanEnabled) Modifier.clickable {
                                android.widget.Toast.makeText(context, "Titan Mode: Extreme performance may cause device heating and data risks.", android.widget.Toast.LENGTH_SHORT).show()
                            } else Modifier
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        if (isTitanEnabled) Color.Red else Color(0xFF4CAF50), 
                                        CircleShape
                                    )
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isTitanEnabled) "Titan: Extreme Power" else "Balanced Power", 
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isTitanEnabled) {
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Whatshot,
                                    contentDescription = null,
                                    tint = Color.Red,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                    
                    TextButton(onClick = { onToggleMonitor(!isMonitoring) }) {
                        Icon(
                            if (isMonitoring) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (isMonitoring) "Hide" else "Monitor", fontSize = 12.sp)
                    }
                }

                if (isMonitoring) {
                    Spacer(Modifier.height(20.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ThermalGauge(
                            modifier = Modifier.weight(1f),
                            label = if (cpuTemp > 0) "CPU" else "System",
                            value = if (cpuTemp > 0) "$cpuTemp°C" else (if (batteryTemp > 0) "$batteryTemp°C" else "Locked"),
                            progress = if (cpuTemp > 0) (cpuTemp / 100f).coerceIn(0f, 1f) else (batteryTemp / 100f).coerceIn(0f, 1f),
                            icon = Icons.Default.Thermostat,
                            color = when {
                                cpuTemp > 75 || batteryTemp > 45 -> MaterialTheme.colorScheme.error
                                cpuTemp > 50 || batteryTemp > 38 -> Color(0xFFFF9800)
                                else -> Color(0xFF2196F3)
                            }
                        )
                        ThermalGauge(
                            modifier = Modifier.weight(1f),
                            label = "Battery",
                            value = "$batteryTemp°C",
                            progress = (batteryTemp / 100f).coerceIn(0f, 1f),
                            icon = Icons.Default.BatteryChargingFull,
                            color = when {
                                batteryTemp > 45 -> MaterialTheme.colorScheme.error
                                batteryTemp > 38 -> Color(0xFFFF9800)
                                else -> Color(0xFF4CAF50)
                            }
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ResourceMetric(
                            label = "Host CPU",
                            value = "$cpuUsage%",
                            progress = cpuUsage / 100f,
                            color = if (cpuUsage > 80) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        
                        val usedGB = ramUsage.first / (1024 * 1024 * 1024f)
                        val totalGB = ramUsage.second / (1024 * 1024 * 1024f)
                        val ramPercent = ramUsage.first / ramUsage.second.toFloat()
                        
                        ResourceMetric(
                            label = "Host RAM",
                            value = "${String.format("%.1f", usedGB)}G / ${String.format("%.1f", totalGB)}G",
                            progress = ramPercent,
                            color = if (ramPercent > 0.8) Color(0xFFFF9800) else Color(0xFF00BCD4)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Thermal State", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(
                                thermalStatus, 
                                style = MaterialTheme.typography.labelSmall, 
                                fontWeight = FontWeight.ExtraBold,
                                color = when(thermalStatus) {
                                    "Safe", "Cool" -> Color(0xFF4CAF50)
                                    "Moderate", "Warm" -> Color(0xFFFFC107)
                                    "Throttling", "Critical" -> Color.Red
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    StatusBadge(label = if (isKvmSupported) "KVM" else "TCG", color = if (isKvmSupported) Color(0xFF4CAF50) else Color(0xFFFF9800))
                    StatusBadge(label = if (isGpuSupported) "GPU:OK" else "GPU:NO", color = if (isGpuSupported) Color(0xFF2196F3) else MaterialTheme.colorScheme.error)
                    StatusBadge(
                        label = if (isIgnoringBattery) "BOOST:ON" else "POW:LIMIT", 
                        color = if (isIgnoringBattery) Color(0xFF4CAF50) else Color(0xFFFF5722),
                        onClick = if (!isIgnoringBattery) onRequestBatteryExemption else null
                    )
                }
            }
        }
    }
}

@Composable
fun ThermalGauge(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    progress: Float,
    icon: ImageVector,
    color: Color
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.05f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, modifier = Modifier.size(14.dp), tint = color)
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = color,
                trackColor = color.copy(alpha = 0.1f),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}

@Composable
fun ResourceMetric(label: String, value: String, progress: Float, color: Color) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(value, style = MaterialTheme.typography.labelSmall, color = color)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = color,
            trackColor = color.copy(alpha = 0.1f),
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

@Composable
fun StatusBadge(
    label: String,
    color: Color,
    onClick: (() -> Unit)? = null
) {
    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f))
    ) {
        Text(
            label, 
            fontSize = 9.sp, 
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}
