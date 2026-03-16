package com.range.phoneLinuxer.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.range.phoneLinuxer.util.AppLogCollector
import java.io.OutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val logs = AppLogCollector.logs
    val listState = rememberLazyListState()

    val createFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        uri?.let {
            saveLogsToUri(context, it, logs.joinToString("\n"))
        }
    }

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System Logs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        copyLogsToClipboard(context, logs.joinToString("\n"))
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Logs")
                    }

                    IconButton(onClick = {
                        if (logs.isNotEmpty()) {
                            createFileLauncher.launch("PhoneLinuxer_Logs_${System.currentTimeMillis()}.txt")
                        } else {
                            Toast.makeText(context, "No logs to download", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "Download Logs")
                    }

                    IconButton(onClick = { logs.clear() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Logs")
                    }
                }
            )
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = Color(0xFF1E1E1E)
        ) {
            if (logs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No logs available.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(12.dp)
                ) {
                    items(items = logs) { log: String ->
                        LogItem(log)
                        HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

private fun saveLogsToUri(context: Context, uri: Uri, content: String) {
    try {
        context.contentResolver.openOutputStream(uri)?.use { outputStream: OutputStream ->
            outputStream.write(content.toByteArray())
        }
        Toast.makeText(context, "Logs successfully saved!", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun copyLogsToClipboard(context: Context, text: String) {
    if (text.isEmpty()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("PhoneLinuxer Logs", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Logs copied to clipboard", Toast.LENGTH_SHORT).show()
}

@Composable
fun LogItem(log: String) {
    Text(
        text = log,
        color = if (log.contains("Error", ignoreCase = true)) Color.Red else Color.LightGray,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
}