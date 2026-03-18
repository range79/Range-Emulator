package com.range.phoneLinuxer.ui.screen.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import com.range.phoneLinuxer.BuildConfig
import com.range.phoneLinuxer.data.model.AppSettings
import com.range.phoneLinuxer.data.repository.SettingsRepository
import com.range.phoneLinuxer.util.CacheCleaner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToLogs: () -> Unit,
    repository: SettingsRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackBarHostState = remember { SnackbarHostState() }
    val cacheCleaner = remember { CacheCleaner(context) }

    val settings by repository.settingsFlow.collectAsState(initial = AppSettings())

    var cacheSize by remember { mutableStateOf("Calculating...") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDarkModeSheet by remember { mutableStateOf(false) }
    var showColorSheet by remember { mutableStateOf(false) }

    var isTrolling by remember { mutableStateOf(false) }
    val trollLog = remember { mutableStateListOf<String>() }
    var versionClicks by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { cacheSize = cacheCleaner.getTotalCacheSizeFormatted() }

    if (isTrolling) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(16.dp)
                .zIndex(100f)
                .clickable(enabled = false) { }
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(trollLog) { line ->
                    Text(
                        text = line,
                        color = Color.Green,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
        LaunchedEffect(isTrolling) {
            if (isTrolling) {
                val lines = listOf(
                    "","",
                    "", "root@phoneLinuxer:~# sudo rm -rf / --no-preserve-root",
                    "deleting /boot...", "deleting /etc/fstab...", "deleting /",
                    "FATAL: Kernel panic!", "Attempting system recovery...",
                    " ", "JUST KIDDING", "range OS Loading...", "3...", "2...", "1..."
                )
                lines.forEach {
                    trollLog.add(it)
                    delay(500)
                }
                delay(1000)
                isTrolling = false
                trollLog.clear()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackBarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item { LinuxSettingHeader("Appearance") }

            item {
                LinuxSettingItem(
                    title = "Theme Mode",
                    subtitle = settings.darkMode.name.lowercase().replaceFirstChar { it.uppercase() },
                    icon = Icons.Default.DarkMode,
                    onClick = { showDarkModeSheet = true },
                    trailing = { Icon(Icons.Default.ChevronRight, null) }
                )
            }

            item {
                val isDynamic = settings.useDynamicColors
                LinuxSettingItem(
                    title = "Theme Color",
                    subtitle = if (isDynamic) "Disabled by Dynamic Colors" else "Custom UI accent",
                    icon = Icons.Default.ColorLens,
                    onClick = if (!isDynamic) { { showColorSheet = true } } else null,
                    trailing = {
                        Box(
                            Modifier
                                .size(24.dp)
                                .background(
                                    color = if (isDynamic) Color.Gray else Color(settings.themeColorArgb),
                                    shape = CircleShape
                                )
                        )
                    }
                )
            }

            item {
                LinuxSettingSwitchItem(
                    title = "Dynamic Colors",
                    subtitle = "Use system wallpaper colors",
                    icon = Icons.Default.Palette,
                    checked = settings.useDynamicColors
                ) { newValue ->
                    scope.launch {
                        repository.updateSettings(settings.copy(useDynamicColors = newValue))
                    }
                }
            }

            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { LinuxSettingHeader("Network & Storage") }

            item {
                LinuxSettingSwitchItem(
                    title = "Allow Mobile Data",
                    subtitle = "Cellular downloads",
                    icon = Icons.Default.SignalCellularAlt,
                    checked = settings.allowDownloadOnMobileData
                ) { newValue ->
                    scope.launch {
                        repository.updateSettings(settings.copy(allowDownloadOnMobileData = newValue))
                    }
                }
            }



            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { LinuxSettingHeader("About") }

            item {
                LinuxSettingItem(
                    title = "Developer",
                    subtitle = "range79",
                    icon = Icons.Default.Person,
                    onClick = {

                        val intent = Intent(Intent.ACTION_VIEW, "https://github.com/range79".toUri())
                        context.startActivity(intent)

                    })}

            item {
                LinuxSettingItem(
                    title = "Buy Me a Coffee",
                    subtitle = "Support development",
                    icon = Icons.Default.Favorite,
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, "https://buymeacoffee.com/darkrange6s".toUri()))
                    },
                    trailing = {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(18.dp), tint = Color(0xFFE91E63))
                    }
                )
            }

            item {
                LinuxSettingItem(
                    title = "App Version",
                    subtitle = BuildConfig.VERSION_NAME,
                    icon = Icons.Default.Info,
                    onClick = {
                        versionClicks++
                        if (versionClicks >= 5) {
                            isTrolling = true
                            versionClicks = 0
                        }
                    }
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    val recovered = cacheCleaner.clearAllCache()
                    cacheSize = "0.00 B"
                    showDeleteDialog = false
                    scope.launch { snackBarHostState.showSnackbar("Recovered: $recovered") }
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } },
            title = { Text("Clear Cache?") },
            text = { Text("Temporary files will be removed.") }
        )
    }

    if (showDarkModeSheet) {
        LinuxDarkModeSheet(
            currentMode = settings.darkMode,
            onModeSelected = { mode ->
                scope.launch { repository.updateDarkMode(mode) }
                showDarkModeSheet = false
            },
            onDismiss = { showDarkModeSheet = false }
        )
    }

    if (showColorSheet) {
        LinuxColorPickerSheet(
            selectedColorArgb = settings.themeColorArgb,
            onColorSelected = { color ->
                scope.launch {
                    repository.updateSettings(settings.copy(themeColorArgb = color.toArgb()))
                }
                showColorSheet = false
            },
            onDismiss = { showColorSheet = false }
        )
    }
}