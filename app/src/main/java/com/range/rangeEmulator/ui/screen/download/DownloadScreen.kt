package com.range.rangeEmulator.ui.screen.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.range.rangeEmulator.data.model.AppSettings
import com.range.rangeEmulator.data.repository.SettingsRepository
import com.range.rangeEmulator.util.NetworkObserver
import com.range.rangeEmulator.viewModel.LinuxViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    vm: LinuxViewModel,
    settingsRepository: SettingsRepository,
    onNavigateToSettings: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by settingsRepository.settingsFlow.collectAsState(initial = AppSettings())

    val downloadPath by vm.downloadPath.collectAsState()
    val progress by vm.downloadProgress.collectAsState()
    val status by vm.downloadStatus.collectAsState()
    val isDownloading by vm.isDownloading.collectAsState()
    val isPaused by vm.isPaused.collectAsState()
    val downloadSpeed by vm.downloadSpeed.collectAsState()
    val remainingTime by vm.remainingTime.collectAsState()
    val availableDistros by vm.availableDistros.collectAsState()

    val networkObserver = remember { NetworkObserver(context) }
    val networkStatus by networkObserver.observe.collectAsState(initial = NetworkObserver.Status.Available)
    val isOnline = networkStatus == NetworkObserver.Status.Available

    var showMobileDataWarning by remember { mutableStateOf(false) }
    var selectedDistroToDownload by remember { mutableStateOf<com.range.rangeEmulator.data.model.LinuxDistro?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { vm.chooseDownloadPath(it) } }

    fun isCellularActive(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)
        return capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
    }

    fun triggerDownload(distro: com.range.rangeEmulator.data.model.LinuxDistro, force: Boolean = false) {
        vm.startDownload(distro, force)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Download Center", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { IconButton(onClick = onNavigateToSettings) { Icon(Icons.Default.Settings, "Settings") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(visible = !isOnline) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WifiOff, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(12.dp))
                        Text("No Internet Connection", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("STORAGE LOCATION", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(text = downloadPath?.path ?: "No directory selected", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { launcher.launch(null) }, modifier = Modifier.fillMaxWidth(), enabled = !isDownloading && !isPaused) {
                        Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (downloadPath == null) "Select Folder" else "Change Folder")
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            val btnEnabled = isOnline && !isDownloading && !isPaused && downloadPath != null

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(availableDistros) { distro ->
                    LinuxDownloadButton(distro.name, btnEnabled) {
                        if (isCellularActive() && !settings.allowDownloadOnMobileData) {
                            selectedDistroToDownload = distro
                            showMobileDataWarning = true
                        } else triggerDownload(distro)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            AnimatedVisibility(
                visible = isDownloading || isPaused || progress > 0,
                enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()
            ) {
                Column {
                    SupportSmallCard()
                    Spacer(Modifier.height(16.dp))
                    DownloadProgressPanel(
                        progress = progress, status = status, isDownloading = isDownloading,
                        isPaused = isPaused, speed = downloadSpeed, eta = remainingTime,
                        onTogglePause = { vm.togglePauseResume() }, onCancel = { vm.cancelDownload() }
                    )
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }

    if (showMobileDataWarning) {
        MobileDataWarningDialog(
            onConfirm = {
                selectedDistroToDownload?.let { triggerDownload(it, force = true) }
                showMobileDataWarning = false
                selectedDistroToDownload = null
            },
            onDismiss = { showMobileDataWarning = false; selectedDistroToDownload = null }
        )
    }
}