package com.range.rangeEmulator.ui.screen.log

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.range.rangeEmulator.util.AppLogCollector
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    var displayLogs by remember { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(Unit) {
        while (isActive) {
            displayLogs = AppLogCollector.logs.toList()
            delay(500)
        }
    }

    val createFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        uri?.let {
            saveLogsToUri(context, it, displayLogs.joinToString("\n"))
        }
    }

    LaunchedEffect(displayLogs.size) {
        if (displayLogs.isNotEmpty()) {
            listState.animateScrollToItem(displayLogs.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System Logs", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        copyLogsToClipboard(context, displayLogs.joinToString("\n"))
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Logs")
                    }

                    IconButton(onClick = {
                        if (displayLogs.isNotEmpty()) {
                            createFileLauncher.launch("Linuxer_Logs_${System.currentTimeMillis()}.txt")
                        } else {
                            Toast.makeText(context, "Nothing to save", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "Download Logs")
                    }

                    IconButton(onClick = {
                        AppLogCollector.clear()
                        displayLogs = emptyList()
                        Toast.makeText(context, "Logs & Cache wiped", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Clear All",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = Color(0xFF121212)
        ) {
            if (displayLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Terminal is empty.", color = Color.DarkGray)
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(items = displayLogs) { log ->
                        LogItem(log)
                    }
                }
            }
        }
    }
}

@Composable
fun LogItem(log: String) {
    val color = when {
        log.contains("Error", ignoreCase = true) -> Color(0xFFFF5252)
        log.contains("Success", ignoreCase = true) -> Color(0xFF4CAF50)
        log.contains("Downloading", ignoreCase = true) -> Color(0xFF2196F3)
        else -> Color(0xFFE0E0E0)
    }

    Text(
        text = log,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun saveLogsToUri(context: Context, uri: Uri, content: String) {
    try {
        context.contentResolver.openOutputStream(uri)?.use { os ->
            os.write(content.toByteArray())
        }
        Toast.makeText(context, "Saved to documents", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun copyLogsToClipboard(context: Context, text: String) {
    if (text.isEmpty()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Logs", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}