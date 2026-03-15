package com.range.phoneLinuxer.viewModel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.range.phoneLinuxer.repository.LinuxRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LinuxViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = getApplication<Application>().applicationContext
    private var repo: LinuxRepositoryImpl? = null

    private val _downloadPath = MutableStateFlow<Uri?>(null)
    val downloadPath: StateFlow<Uri?> = _downloadPath

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress

    private val _downloadStatus = MutableStateFlow("Idle")
    val downloadStatus: StateFlow<String> = _downloadStatus

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading

    fun chooseDownloadPath(uri: Uri) {
        _downloadPath.value = uri
        repo = LinuxRepositoryImpl(appContext, uri)
    }

    fun downloadArch() {
        val repository = repo ?: return
        viewModelScope.launch {
            _isDownloading.value = true
            _downloadStatus.value = "Downloading Arch ARM..."
            repository.downloadLinux(
                "https://os.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz"
            ) { progress ->
                _downloadProgress.value = progress
                if (progress == 100) {
                    _downloadStatus.value = "Download finished"
                    _isDownloading.value = false
                }
            }
        }
    }
    fun downloadUbuntu() {
        val repository = repo ?: return
        viewModelScope.launch {
            _isDownloading.value = true
            _downloadStatus.value = "Downloading Ubuntu ARM..."
            repository.downloadLinux(
                "https://cdimage.ubuntu.com/releases/24.04/release/ubuntu-24.04-live-server-arm64.iso"
            ) { progress ->
                _downloadProgress.value = progress
                if (progress == 100) {
                    _downloadStatus.value = "Download finished"
                    _isDownloading.value = false
                }
            }
        }
    }
}