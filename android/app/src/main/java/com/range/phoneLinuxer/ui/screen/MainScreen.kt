package com.range.phoneLinuxer.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.range.phoneLinuxer.viewModel.LinuxViewModel

@Composable
fun MainScreen(vm: LinuxViewModel) {

    val downloadPath by vm.downloadPath.collectAsState()
    val progress by vm.downloadProgress.collectAsState()
    val status by vm.downloadStatus.collectAsState()
    val isDownloading by vm.isDownloading.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            vm.chooseDownloadPath(it)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Button(onClick = { launcher.launch(null) }) {
            Text("Select Download Folder")
        }

        downloadPath?.let {
            Text("Selected folder: $it")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { vm.downloadArch() },
            enabled = !isDownloading && downloadPath != null
        ) {
            Text("Download Arch Linux")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { vm.downloadUbuntu() },
            enabled = !isDownloading && downloadPath != null
        ) {
            Text("Download Ubuntu")
        }

        Spacer(modifier = Modifier.height(32.dp))

        LinearProgressIndicator(
            progress = { progress / 100f },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text("Progress: $progress%")

        Spacer(modifier = Modifier.height(16.dp))

        Text("Status: $status")
    }
}