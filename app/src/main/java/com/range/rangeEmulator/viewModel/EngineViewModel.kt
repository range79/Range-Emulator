package com.range.rangeEmulator.viewModel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.range.rangeEmulator.RangeEmulatorApp
import com.range.rangeEmulator.data.repository.SettingsRepository
import com.range.rangeEmulator.data.repository.impl.SettingsRepositoryImpl
import com.range.rangeEmulator.service.KeepAliveService
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EngineViewModel(application: Application) : AndroidViewModel(application) {

    private val executor = (application as RangeEmulatorApp).executor
    private val settingsRepository: SettingsRepository =
        SettingsRepositoryImpl(application.applicationContext)
    private val appSettingsFlow = settingsRepository.settingsFlow
    private val prefs = application.applicationContext.getSharedPreferences("engine_prefs", Context.MODE_PRIVATE)

    private val _isEngineDownloaded = MutableStateFlow(File(application.applicationContext.filesDir, "pc-bios").exists())
    val isEngineDownloaded: StateFlow<Boolean> = _isEngineDownloaded.asStateFlow()

    private val _isEngineDownloading = MutableStateFlow(false)
    val isEngineDownloading: StateFlow<Boolean> = _isEngineDownloading.asStateFlow()

    private val _engineDownloadProgress = MutableStateFlow(0)
    val engineDownloadProgress: StateFlow<Int> = _engineDownloadProgress.asStateFlow()

    private val _engineDownloadStatus = MutableStateFlow("Idle")
    val engineDownloadStatus: StateFlow<String> = _engineDownloadStatus.asStateFlow()

    private val _engineDownloadSpeed = MutableStateFlow("0 KB/s")
    val engineDownloadSpeed: StateFlow<String> = _engineDownloadSpeed.asStateFlow()

    private val _engineRemainingTime = MutableStateFlow("")
    val engineRemainingTime: StateFlow<String> = _engineRemainingTime.asStateFlow()

    private val _engineTargetUrl = MutableStateFlow("")
    val engineTargetUrl: StateFlow<String> = _engineTargetUrl.asStateFlow()

    private val _engineTargetSizeMB = MutableStateFlow("...")
    val engineTargetSizeMB: StateFlow<String> = _engineTargetSizeMB.asStateFlow()

    private val _isEnginePaused = MutableStateFlow(false)
    val isEnginePaused: StateFlow<Boolean> = _isEnginePaused.asStateFlow()

    private val _showMobileDataWarning = MutableStateFlow(false)
    val showMobileDataWarning: StateFlow<Boolean> = _showMobileDataWarning.asStateFlow()

    private val _event = Channel<EngineEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    private var downloadJob: Job? = null

    sealed class EngineEvent {
        object DownloadComplete : EngineEvent()
        data class UpdateAvailable(val newVersion: String, val downloadUrl: String, val sizeMB: String) : EngineEvent()
        data class Error(val message: String) : EngineEvent()
    }

    fun dismissMobileDataWarning() { _showMobileDataWarning.value = false }

    fun setEngineTargetUrl(url: String, sizeMB: String) {
        _engineTargetUrl.value = url
        _engineTargetSizeMB.value = sizeMB
    }

    fun prepareLatestEngineOTA() {
        if (_engineTargetUrl.value.isNotEmpty()) return
        viewModelScope.launch {
            try {
                _engineDownloadStatus.value = "Fetching OTA specs..."
                val url = java.net.URL("https://api.github.com/repos/range79x/Range-Emulator-Dependencies/releases/latest")
                val connection = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    url.openConnection() as java.net.HttpURLConnection
                }
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                val response = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                }

                val tagRegex = """"tag_name"\s*:\s*"([^"]+)"""".toRegex()
                val urlRegex = """"browser_download_url"\s*:\s*"([^"]+Range-Emulator-Dependencies\.zip)"""".toRegex()
                val sizeRegex = """"size"\s*:\s*(\d+)""".toRegex()

                val urlMatch = urlRegex.find(response)
                val sizeMatch = sizeRegex.find(response)
                val tagMatch = tagRegex.find(response)

                if (urlMatch != null) {
                    _engineTargetUrl.value = urlMatch.groupValues[1]
                    tagMatch?.groupValues?.get(1)?.let { tag ->
                        prefs.edit().putString("engine_target_tag", tag).apply()
                    }
                    _engineTargetSizeMB.value = sizeMatch?.groupValues?.get(1)
                        ?.toLongOrNull()?.let { "${it / (1024 * 1024)}" } ?: "107"
                } else {
                    _engineTargetUrl.value = "https://github.com/range79x/Range-Emulator-Dependencies/releases/latest/download/Range-Emulator-Dependencies.zip"
                    _engineTargetSizeMB.value = "107"
                }
                _engineDownloadStatus.value = "Idle"
            } catch (e: Exception) {
                _engineTargetUrl.value = "https://github.com/range79x/Range-Emulator-Dependencies/releases/latest/download/Range-Emulator-Dependencies.zip"
                _engineTargetSizeMB.value = "107"
                _engineDownloadStatus.value = "Idle"
            }
        }
    }

    fun checkForEngineUpdate() {
        if (!_isEngineDownloaded.value) return
        viewModelScope.launch {
            try {
                val url = java.net.URL("https://api.github.com/repos/range79x/Range-Emulator-Dependencies/releases/latest")
                val connection = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    (url.openConnection() as java.net.HttpURLConnection).apply {
                        setRequestProperty("Accept", "application/vnd.github.v3+json")
                        connectTimeout = 10000
                        readTimeout = 10000
                    }
                }
                val response = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                }

                val tagRegex = """"tag_name"\s*:\s*"([^"]+)"""".toRegex()
                val urlRegex = """"browser_download_url"\s*:\s*"([^"]+Range-Emulator-Dependencies\.zip)"""".toRegex()
                val sizeRegex = """"size"\s*:\s*(\d+)""".toRegex()

                val latestTag = tagRegex.find(response)?.groupValues?.get(1) ?: return@launch
                val installedTag = prefs.getString("installed_engine_version", "") ?: ""

                if (installedTag.isNotEmpty() && latestTag != installedTag) {
                    val downloadUrl = urlRegex.find(response)?.groupValues?.get(1) ?: return@launch
                    val sizeMB = sizeRegex.find(response)?.groupValues?.get(1)?.toLongOrNull()
                        ?.let { "${it / (1024 * 1024)}" } ?: "?"
                    _event.send(EngineEvent.UpdateAvailable(latestTag, downloadUrl, sizeMB))
                }
            } catch (_: Exception) {}
        }
    }

    fun saveInstalledVersion(tag: String) {
        prefs.edit().putString("installed_engine_version", tag).apply()
    }

    fun downloadEngine(forceMobileData: Boolean = false) {
        if (_isEngineDownloading.value && !_isEnginePaused.value) return
        startDownload(forceMobileData)
    }

    fun togglePauseResume() {
        if (_isEnginePaused.value) {
            _isEnginePaused.value = false
            startDownload()
        } else {
            _isEnginePaused.value = true
            stopKeepAlive()
            downloadJob?.cancel()
            _engineDownloadStatus.value = "Paused"
            _engineDownloadSpeed.value = "0 KB/s"
            _engineRemainingTime.value = ""
        }
    }

    fun cancelDownload() {
        stopKeepAlive()
        _isEnginePaused.value = false
        downloadJob?.cancel()
        _isEngineDownloading.value = false
        _engineDownloadStatus.value = "Canceled"
        _engineDownloadProgress.value = 0
        _engineDownloadSpeed.value = "0 KB/s"
        _engineRemainingTime.value = ""
        File(getApplication<Application>().applicationContext.filesDir, "engine.zip").let {
            if (it.exists()) it.delete()
        }
    }

    private fun startDownload(forceMobileData: Boolean = false) {
        val targetUrl = _engineTargetUrl.value
        if (targetUrl.isEmpty()) { _engineDownloadStatus.value = "Error: Invalid OTA URL"; return }

        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            val appSettings = appSettingsFlow.first()
            val allowMobile = appSettings.allowDownloadOnMobileData || forceMobileData

            _isEngineDownloading.value = true
            _engineDownloadStatus.value = "Downloading QEMU Engine..."
            startKeepAlive()
            val startTime = System.currentTimeMillis()

            executor.downloadEngineZip(targetUrl, allowMobile) { downloaded, total, isError, mobileRestricted ->
                if (mobileRestricted) {
                    _showMobileDataWarning.value = true
                    _isEnginePaused.value = true
                    _isEngineDownloading.value = false
                    stopKeepAlive()
                    return@downloadEngineZip
                }

                if (isError) {
                    _engineDownloadStatus.value = "Error: Connection Lost"
                    _isEnginePaused.value = true
                    stopKeepAlive()
                } else if (!_isEnginePaused.value) {
                    val progress = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                    _engineDownloadProgress.value = progress
                    _engineDownloadStatus.value = "Downloading... $progress%"

                    val timeSec = (System.currentTimeMillis() - startTime) / 1000.0
                    if (timeSec > 0.5) {
                        val speedInBytes = downloaded / timeSec
                        _engineDownloadSpeed.value = if (speedInBytes >= 1024 * 1024)
                            "%.1f MB/s".format(speedInBytes / (1024 * 1024))
                        else "%.1f KB/s".format(speedInBytes / 1024)

                        if (speedInBytes > 0 && total > 0) {
                            val remainingBytes = total - downloaded
                            val seconds = (remainingBytes / speedInBytes).toLong()
                            _engineRemainingTime.value = when {
                                seconds >= 3600 -> "${seconds / 3600}h ${(seconds % 3600) / 60}m left"
                                seconds >= 60 -> "${seconds / 60}m ${seconds % 60}s left"
                                else -> "${seconds}s left"
                            }
                        }
                    }

                    if (progress == 100) {
                        _engineDownloadStatus.value = "Download Finished. Extracting..."
                        _engineDownloadSpeed.value = ""
                        _engineRemainingTime.value = ""

                        viewModelScope.launch {
                            val success = executor.extractEngineZip { extractProgress ->
                                _engineDownloadProgress.value = extractProgress
                                _engineDownloadStatus.value = "Extracting... $extractProgress%"
                            }
                            if (success) {
                                _engineDownloadStatus.value = "Download Finished"
                                _isEngineDownloaded.value = true
                                _isEngineDownloading.value = false
                                stopKeepAlive()
                                val installedTag = prefs.getString("engine_target_tag", "") ?: ""
                                if (installedTag.isNotEmpty()) saveInstalledVersion(installedTag)
                                _event.send(EngineEvent.DownloadComplete)
                            } else {
                                _engineDownloadStatus.value = "Extraction Failed!"
                                _isEnginePaused.value = true
                                stopKeepAlive()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startKeepAlive() {
        val app = getApplication<Application>()
        val intent = Intent(app.applicationContext, KeepAliveService::class.java).apply {
            putExtra(KeepAliveService.EXTRA_MODE, KeepAliveService.MODE_DOWNLOAD)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) app.startForegroundService(intent)
            else app.startService(intent)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun stopKeepAlive() {
        val app = getApplication<Application>()
        app.stopService(Intent(app.applicationContext, KeepAliveService::class.java))
    }
}
