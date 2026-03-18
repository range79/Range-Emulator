package com.range.phoneLinuxer.viewModel

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.range.phoneLinuxer.R
import com.range.phoneLinuxer.data.model.AppSettings
import com.range.phoneLinuxer.data.repository.SettingsRepository
import com.range.phoneLinuxer.data.repository.impl.LinuxRepositoryImpl
import com.range.phoneLinuxer.data.repository.impl.SettingsRepositoryImpl
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LinuxViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = getApplication<Application>().applicationContext

    // Notification Manager Tanımlamaları
    private val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val CHANNEL_ID = "DOWNLOAD_CHANNEL"
    private val NOTIFICATION_ID = 101

    private val settingsRepository: SettingsRepository = SettingsRepositoryImpl(appContext)
    private val settingsFlow = settingsRepository.settingsFlow

    private var repo: LinuxRepositoryImpl? = null
    private var downloadJob: Job? = null

    // UI State'leri
    private val _downloadPath = MutableStateFlow<Uri?>(null)
    val downloadPath = _downloadPath.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress = _downloadProgress.asStateFlow()

    private val _downloadStatus = MutableStateFlow("Idle")
    val downloadStatus = _downloadStatus.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading = _isDownloading.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused = _isPaused.asStateFlow()

    private val _downloadSpeed = MutableStateFlow("0 KB/s")
    val downloadSpeed = _downloadSpeed.asStateFlow()

    private val _remainingTime = MutableStateFlow("")
    val remainingTime = _remainingTime.asStateFlow()

    private var currentUrl = ""
    private var currentLabel = ""

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Linux Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shows progress of ISO downloads" }
            notificationManager.createNotificationChannel(channel)
        }
    }

    // Bildirimi Güncelleyen Fonksiyon
    private fun updateNotification(progress: Int, label: String, isFinished: Boolean = false) {
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download) // Geçici ikon, R.drawable.ic_download ile değiştir
            .setContentTitle(if (isFinished) "Download Complete" else "Downloading $label")
            .setOngoing(!isFinished)
            .setOnlyAlertOnce(true)

        if (isFinished) {
            builder.setContentText("$label is ready to use.")
                .setProgress(0, 0, false)
        } else {
            builder.setContentText("$progress% completed - ${_downloadSpeed.value}")
                .setProgress(100, progress, false)
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    fun chooseDownloadPath(uri: Uri) {
        _downloadPath.value = uri
        repo = LinuxRepositoryImpl(appContext, uri)
    }

    fun downloadArch(force: Boolean = false) {
        startDownload("Arch Linux ARM", "https://os.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz", force)
    }

    fun downloadUbuntu(force: Boolean = false) {
        startDownload("Ubuntu 24.04", "https://cdimage.ubuntu.com/ubuntu-server/noble/daily-live/current/noble-live-server-arm64.iso", force)
    }

    fun togglePauseResume() {
        if (_isPaused.value) {
            _isPaused.value = false
            startDownload(currentLabel, currentUrl)
        } else {
            _isPaused.value = true
            downloadJob?.cancel()
            _downloadStatus.value = "Paused"
            _downloadSpeed.value = "0 KB/s"
            _remainingTime.value = ""
            // Bildirimi duraklatıldı olarak güncelle
            notificationManager.cancel(NOTIFICATION_ID)
        }
    }

    fun cancelDownload() {
        _isPaused.value = false
        downloadJob?.cancel()
        _isDownloading.value = false
        _downloadStatus.value = "Canceled"
        _downloadProgress.value = 0
        _downloadSpeed.value = "0 KB/s"
        _remainingTime.value = ""
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun startDownload(label: String, url: String, isForced: Boolean = false) {
        val repository = repo ?: run {
            _downloadStatus.value = "Error: Select folder first"
            return
        }

        currentUrl = url
        currentLabel = label

        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            val settingsFromRepo = settingsFlow.first()
            val effectiveSettings = if (isForced) settingsFromRepo.copy(allowDownloadOnMobileData = true) else settingsFromRepo

            _isDownloading.value = true
            _downloadStatus.value = "Starting download... Check notifications."

            // İlk bildirimi gönder
            updateNotification(0, label)

            val startTime = System.currentTimeMillis()

            repository.downloadLinux(url, effectiveSettings) { downloaded, total, isError ->
                if (isError) {
                    _downloadStatus.value = "Error: Connection Lost"
                    _isDownloading.value = false
                    notificationManager.cancel(NOTIFICATION_ID)
                } else if (!_isPaused.value) {
                    val progress = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                    _downloadProgress.value = progress
                    _downloadStatus.value = "Downloading $label..."

                    val timeSec = (System.currentTimeMillis() - startTime) / 1000.0
                    if (timeSec > 0.5) {
                        val speedInBytes = downloaded / timeSec
                        _downloadSpeed.value = formatSpeed(speedInBytes)

                        if (speedInBytes > 0 && total > 0) {
                            val remainingBytes = total - downloaded
                            _remainingTime.value = formatEta((remainingBytes / speedInBytes).toLong())
                        }
                    }

                    // BİLDİRİMİ GÜNCELLE
                    updateNotification(progress, label)

                    if (progress == 100) {
                        _downloadStatus.value = "Success: $label ready"
                        _isDownloading.value = false
                        _downloadSpeed.value = "0 KB/s"
                        _remainingTime.value = ""
                        updateNotification(100, label, isFinished = true)
                    }
                }
            }
        }
    }

    private fun formatSpeed(speedBps: Double): String = if (speedBps >= 1024 * 1024) "%.1f MB/s".format(speedBps / (1024 * 1024)) else "%.1f KB/s".format(speedBps / 1024)
    private fun formatEta(seconds: Long): String = when {
        seconds >= 3600 -> "${seconds / 3600}h ${(seconds % 3600) / 60}m left"
        seconds >= 60 -> "${seconds / 60}m ${seconds % 60}s left"
        else -> "${seconds}s left"
    }

    override fun onCleared() {
        super.onCleared()
        repo?.close()
        notificationManager.cancel(NOTIFICATION_ID)
    }
}