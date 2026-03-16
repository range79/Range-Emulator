package com.range.phoneLinuxer.ui.screen

import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.range.phoneLinuxer.BuildConfig
import com.range.phoneLinuxer.util.CacheCleaner
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onNavigateToLogs: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackBarHostState = remember { SnackbarHostState() }
    val cacheCleaner = remember { CacheCleaner(context) }

    var cacheSize by remember { mutableStateOf("Calculating...") }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        cacheSize = cacheCleaner.getTotalCacheSizeFormatted()
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Clear Cache?") },
            text = {
                Text("This will remove temporary installation files and logs. " +
                        "Your downloaded Linux images (ISOs) will NOT be deleted.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val recovered = cacheCleaner.clearAllCache()
                        cacheSize = "0.00 B"
                        showDeleteDialog = false
                        scope.launch {
                            snackBarHostState.showSnackbar("Recovered $recovered")
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val hasFullStorageAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else true

    Scaffold(
        snackbarHost = { SnackbarHost(snackBarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("App Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            item { SettingHeader("Storage & Permissions") }
            item {
                SettingItem(
                    title = "All Files Access",
                    subtitle = if (hasFullStorageAccess) "Granted" else "Required for large ISOs",
                    icon = Icons.Default.Folder,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = "package:${context.packageName}".toUri()
                            }
                            context.startActivity(intent)
                        }
                    },
                    trailing = {
                        Icon(
                            imageVector = if (hasFullStorageAccess) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (hasFullStorageAccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            item { SettingHeader("Diagnostics") }
            item {
                SettingItem(
                    title = "View Session Logs",
                    subtitle = "Debug download and VM errors",
                    icon = Icons.AutoMirrored.Filled.ListAlt,
                    onClick = onNavigateToLogs,
                    trailing = { Icon(Icons.Default.ChevronRight, null) }
                )
            }

            item {
                SettingItem(
                    title = "Clear Cache",
                    subtitle = "Current usage: $cacheSize",
                    icon = Icons.Default.DeleteSweep,
                    onClick = {
                        showDeleteDialog = true
                    }
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            item { SettingHeader("About") }

            item {
                SettingItem(
                    title = "Developer",
                    subtitle = "range79",
                    icon = Icons.Default.Person
                )
            }

            item {
                SettingItem(
                    title = "GitHub",
                    subtitle = "github.com/range79",
                    icon = Icons.Default.Code,
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, "https://github.com/range79".toUri())
                        context.startActivity(intent)
                    },
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }

            item {
                SettingItem(
                    title = "App Version",
                    subtitle = BuildConfig.VERSION_NAME,
                    icon = Icons.Default.Info
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun SettingHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun SettingItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null
    ) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(subtitle) },
            leadingContent = { Icon(icon, contentDescription = null) },
            trailingContent = trailing
        )
    }
}