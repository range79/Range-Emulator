package com.range.rangeEmulator.ui.screen.emulatorControl

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.range.rangeEmulator.data.enums.CpuModel
import com.range.rangeEmulator.data.enums.ScreenType
import com.range.rangeEmulator.data.enums.VmState
import com.range.rangeEmulator.ui.components.PerformanceDashboardCard
import com.range.rangeEmulator.ui.screen.emulatorList.CompactInfoChip
import com.range.rangeEmulator.util.SystemMonitor
import com.range.rangeEmulator.util.NetworkUtils
import com.range.rangeEmulator.viewModel.EmulatorViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.range.rangeEmulator.util.HardwareUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VmControlScreen(
    vmId: String,
    viewModel: EmulatorViewModel,
    onNavigateToLogs: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        window?.let { w ->
            w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                w.setSustainedPerformanceMode(true)
            }
        }
        
        onDispose {
            window?.let { w ->
                w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    w.setSustainedPerformanceMode(false)
                }
            }
        }
    }

    val deviceIp = remember { NetworkUtils.getLocalIpAddress() }
    val vms by viewModel.vms.collectAsState()
    val currentVm = vms.find { it.id == vmId }

    val isSpice = currentVm?.screenType == ScreenType.SPICE
    val protocolName = if (isSpice) "SPICE" else "VNC"

    val portValue = if (isSpice) {
        currentVm!!.spicePort.toString()
    } else {
        currentVm!!.vncPort.toString()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("VM Console", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        val powerManager =  remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
        var isIgnoringBatteryOptimizations by remember { 
            mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName)) 
        }
        val isTitanModeEnabled = currentVm!!.isTitanModeEnabled
        var isMonitoringEnabled by remember { mutableStateOf(false) }
        var cpuUsage by remember { mutableIntStateOf(0) }
        var ramUsage by remember { mutableStateOf(0L to 1L) }
        var cpuTemp by remember { mutableIntStateOf(0) }
        var batteryTemp by remember { mutableIntStateOf(0) }
        var thermalStatus by remember { mutableStateOf("Safe") }

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        LaunchedEffect(isMonitoringEnabled) {
            if (isMonitoringEnabled) {
                while (true) {
                    cpuUsage = SystemMonitor.getCpuUsage()
                    ramUsage = HardwareUtil.getMemoryInfo(context)
                    cpuTemp = SystemMonitor.getCpuTemperature(context)
                    batteryTemp = HardwareUtil.getBatteryTemperature(context)
                    thermalStatus = SystemMonitor.getThermalStatus(context)
                    delay(1500)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PerformanceDashboardCard(
                isTitanEnabled = isTitanModeEnabled,
                isIgnoringBattery = isIgnoringBatteryOptimizations,
                isMonitoring = isMonitoringEnabled,
                cpuUsage = cpuUsage,
                ramUsage = ramUsage,
                cpuTemp = cpuTemp,
                batteryTemp = batteryTemp,
                thermalStatus = thermalStatus,
                onToggleMonitor = { isMonitoringEnabled = it },
                onRequestBatteryExemption = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = "package:${context.packageName}".toUri()
                    }
                    context.startActivity(intent)
                }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            Surface(
                modifier = Modifier.size(100.dp),
                shape = CircleShape,
                color = if (currentVm.state == VmState.RUNNING) Color(0xFF4CAF50) else Color.Gray,
                shadowElevation = 6.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isSpice) Icons.Default.Monitor else Icons.Default.SettingsInputComponent,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.White
                    )
                }
            }

            Text(
                text = currentVm.vmName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 16.dp)
            )

            Text(
                text = "Status: ${currentVm.state.name}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (currentVm.state == VmState.RUNNING) Color(0xFF4CAF50) else MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            currentVm?.let { vm ->
                if (isSpice) {
                    Text(
                        "Note: Manual resolution is disabled for SPICE. System will use optimized dynamic resizing.",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val archLabel = vm.arch.toQemuArch().uppercase()
                    CompactInfoChip(
                        icon = Icons.Default.PhoneAndroid,
                        label = archLabel,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    val cpuLabel = if (vm.cpuModel == CpuModel.HOST) "KVM" else vm.cpuModel.name
                    CompactInfoChip(
                        Icons.Default.DeveloperBoard, 
                        cpuLabel,
                        tint = if (vm.cpuModel == CpuModel.HOST) Color(0xFF4CAF50) else MaterialTheme.colorScheme.secondary
                    )
                    CompactInfoChip(Icons.Default.Memory, "${vm.ramMB}MB")
                    CompactInfoChip(Icons.Default.Storage, "${vm.disks.sumOf { it.sizeGB }}GB", tint = MaterialTheme.colorScheme.tertiary)
                    if (vm.isoUris.isNotEmpty()) {
                        CompactInfoChip(Icons.Default.Album, "${vm.isoUris.size} ISO", tint = MaterialTheme.colorScheme.primary)
                    }
                    CompactInfoChip(Icons.Default.SettingsInputComponent, "${vm.cpuCores} Core")
                    
                    val resLabel = if (vm.screenWidth == 0) "Auto" else "${vm.screenWidth}x${vm.screenHeight}"
                    CompactInfoChip(Icons.Default.Tv, resLabel)

                    if (vm.isGpuEnabled) {
                        val hwSupported = HardwareUtil.isGpuAccelerationSupported(context)
                        CompactInfoChip(
                            if (hwSupported) Icons.Default.FlashOn else Icons.Default.FlashOff, 
                            if (hwSupported) "GPU" else "GPU (Unsupported)", 
                            tint = if (hwSupported) Color(0xFFFFC107) else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "$protocolName Connection Details",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    ControlInfoRow(
                        label = "Local IP",
                        value = deviceIp,
                        onCopy = {
                            scope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("IP Address", deviceIp)))
                                Toast.makeText(context, "IP Copied", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    ControlInfoRow(
                        label = "$protocolName Port",
                        value = portValue,
                        onCopy = {
                            scope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Port", portValue)))
                                Toast.makeText(context, "Port Copied", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    ControlInfoRow(
                        label = "Protocol",
                        value = if (isSpice) "SPICE" else "VNC"
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateToLogs,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Terminal, null)
                    Spacer(Modifier.width(8.dp))
                    Text("View Live Logs")
                }

                Button(
                    onClick = {
                        viewModel.stopVm(vmId)
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Stop, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Terminate VM")
                }
            }
        }
    }
}

@Composable
fun ControlInfoRow(label: String, value: String, onCopy: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (onCopy != null) {
                IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}