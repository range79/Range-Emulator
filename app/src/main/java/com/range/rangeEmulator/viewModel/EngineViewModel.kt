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

    private val _toolsTargetUrl = MutableStateFlow("")
    val toolsTargetUrl: StateFlow<String> = _toolsTargetUrl.asStateFlow()

    private val _tpmTargetUrl = MutableStateFlow("")
    val tpmTargetUrl: StateFlow<String> = _tpmTargetUrl.asStateFlow()

    private val _isEnginePaused = MutableStateFlow(false)
    val isEnginePaused: StateFlow<Boolean> = _isEnginePaused.asStateFlow()

    private val _showMobileDataWarning = MutableStateFlow(false)
    val showMobileDataWarning: StateFlow<Boolean> = _showMobileDataWarning.asStateFlow()

    private val _event = Channel<EngineEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    private var downloadJob: Job? = null
    private var isExtractionTriggered = false

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
                val tagMatch = tagRegex.find(response)
                tagMatch?.groupValues?.get(1)?.let { tag ->
                    prefs.edit().putString("engine_target_tag", tag).apply()
                }

                val assetsRegex = """\{[^{}]*"name"\s*:\s*"([^"]+)"[^{}]*"size"\s*:\s*(\d+)[^{}]*"browser_download_url"\s*:\s*"([^"]+)"[^{}]*\}""".toRegex()
                val matches = assetsRegex.findAll(response)
                
                var engineUrl = ""
                var engineSize = "107"
                var toolsUrl = ""

                matches.forEach { match ->
                    val name = match.groupValues[1]
                    val size = match.groupValues[2]
                    val downloadUrl = match.groupValues[3]

                    if (name.contains("Range-Emulator-Dependencies.zip")) {
                        engineUrl = downloadUrl
                        engineSize = (size.toLongOrNull()?.let { it / (1024 * 1024) } ?: 107).toString()
                    } else if (name.contains("Range-Emulator-Qemu-Img.zip")) {
                        toolsUrl = downloadUrl
                    } else if (name.contains("tpm_binaries_android.zip")) {
                        _tpmTargetUrl.value = downloadUrl
                    }
                }

                if (engineUrl.isNotEmpty()) {
                    _engineTargetUrl.value = engineUrl
                    _toolsTargetUrl.value = toolsUrl
                    _engineTargetSizeMB.value = engineSize
                    if (_tpmTargetUrl.value.isEmpty()) {
                        _tpmTargetUrl.value = "https://github.com/range79x/Range-Emulator-Dependencies/releases/latest/download/tpm_binaries_android.zip"
                    }
                } else {
                    _engineTargetUrl.value = "https://github.com/range79x/Range-Emulator-Dependencies/releases/latest/download/Range-Emulator-Dependencies.zip"
                    _toolsTargetUrl.value = "https://github.com/range79x/Range-Emulator-Dependencies/releases/latest/download/Range-Emulator-Qemu-Img.zip"
                    _tpmTargetUrl.value = "https://github.com/range79x/Range-Emulator-Dependencies/releases/latest/download/tpm_binaries_android.zip"
                    _engineTargetSizeMB.value = "107"
                }
                _engineDownloadStatus.value = "Idle"
            } catch (e: Exception) {
                _engineTargetUrl.value = "https://github.com/range79x/Range-Emulator-Dependencies/releases/latest/download/Range-Emulator-Dependencies.zip"
                _toolsTargetUrl.value = "https://github.com/range79x/Range-Emulator-Dependencies/releases/latest/download/Range-Emulator-Qemu-Img.zip"
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
                val assetsRegex = """\{[^{}]*"name"\s*:\s*"([^"]+Dependencies\.zip)"[^{}]*"size"\s*:\s*(\d+)[^{}]*"browser_download_url"\s*:\s*"([^"]+)"[^{}]*\}""".toRegex()

                val latestTag = tagRegex.find(response)?.groupValues?.get(1) ?: return@launch
                val assetMatch = assetsRegex.find(response)
                val installedTag = prefs.getString("installed_engine_version", "") ?: ""

                if (installedTag.isNotEmpty() && latestTag != installedTag && assetMatch != null) {
                    val downloadUrl = assetMatch.groupValues[3]
                    val sizeMB = assetMatch.groupValues[2].toLongOrNull()
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
        
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(network)
        val isMobile = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true
        
        if (isMobile && !forceMobileData) {
            viewModelScope.launch {
                val settings = settingsRepository.settingsFlow.first()
                if (!settings.allowDownloadOnMobileData) {
                    _showMobileDataWarning.value = true
                    _isEnginePaused.value = true
                    return@launch
                } else {
                    startDownload(false)
                }
            }
        } else {
            startDownload(forceMobileData)
        }
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
            _isEnginePaused.value = false
            _engineDownloadStatus.value = "Downloading QEMU Engine..."
            startKeepAlive()
            val startTime = System.currentTimeMillis()

            executor.downloadZip(targetUrl, "engine.zip", allowMobile) { downloaded, total, isError, mobileRestricted ->
                if (mobileRestricted) {
                    _showMobileDataWarning.value = true
                    _isEnginePaused.value = true
                    _isEngineDownloading.value = false
                    _engineDownloadStatus.value = "Paused: Mobile Data Restricted"
                    stopKeepAlive()
                    return@downloadZip
                }

                if (isError) {
                    _engineDownloadStatus.value = "Error: Engine Connection Lost"
                    _isEnginePaused.value = true
                    _isEngineDownloading.value = false
                    stopKeepAlive()
                } else if (!_isEnginePaused.value) {
                    val progress = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                    _engineDownloadProgress.value = progress
                    _engineDownloadStatus.value = "Downloading Engine... $progress%"

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
                        _engineDownloadStatus.value = "Engine Download Finished. Extracting..."
                        _engineDownloadSpeed.value = ""
                        _engineRemainingTime.value = ""

                        viewModelScope.launch {
                            if (isExtractionTriggered) return@launch
                            isExtractionTriggered = true
                            
                            val success = executor.extractZip("engine.zip") { extractProgress ->
                                _engineDownloadProgress.value = extractProgress
                                _engineDownloadStatus.value = "Extracting Engine... $extractProgress%"
                            }
                            if (success) {
                                val toolsUrl = _toolsTargetUrl.value.ifEmpty {
                                    targetUrl.replace("Range-Emulator-Dependencies.zip", "Range-Emulator-Qemu-Img.zip")
                                }
                                _engineDownloadStatus.value = "Downloading QEMU-img Tools..."
                                executor.downloadZip(toolsUrl, "qemu_img_deps.zip", allowMobile) { d, t, err, res ->
                                    if (!err && !res) {
                                        val p = if (t > 0) ((d * 100) / t).toInt() else 0
                                        _engineDownloadProgress.value = p
                                        _engineDownloadStatus.value = "Downloading Tools... $p%"
                                        if (p == 100) {
                                            viewModelScope.launch {
                                                val toolsSuccess = executor.extractZip("qemu_img_deps.zip") { ep ->
                                                    _engineDownloadProgress.value = ep
                                                    _engineDownloadStatus.value = "Extracting Tools... $ep%"
                                                }
                                                    if (toolsSuccess) {
                                                        _engineDownloadStatus.value = "Downloading TPM Binaries..."
                                                        val tpmUrl = _tpmTargetUrl.value.ifEmpty {
                                                            targetUrl.replace("Range-Emulator-Dependencies.zip", "tpm_binaries_android.zip")
                                                        }
                                                        executor.downloadZip(tpmUrl, "tpm_binaries.zip", allowMobile) { td, tt, terr, tres ->
                                                            if (!terr && !tres) {
                                                                val tp = if (tt > 0) ((td * 100) / tt).toInt() else 0
                                                                _engineDownloadProgress.value = tp
                                                                _engineDownloadStatus.value = "Downloading TPM... $tp%"
                                                                if (tp == 100) {
                                                                    viewModelScope.launch {
                                                                        val tpmSuccess = executor.extractZip("tpm_binaries.zip") { tep ->
                                                                            _engineDownloadProgress.value = tep
                                                                            _engineDownloadStatus.value = "Extracting TPM... $tep%"
                                                                        }
                                                                        if (tpmSuccess) {
                                                                            isExtractionTriggered = false
                                                                            _engineDownloadStatus.value = "Download Finished"
                                                                            _isEngineDownloaded.value = true
                                                                            _isEngineDownloading.value = false
                                                                            stopKeepAlive()
                                                                            val installedTag = prefs.getString("engine_target_tag", "") ?: ""
                                                                            if (installedTag.isNotEmpty()) saveInstalledVersion(installedTag)
                                                                            _event.send(EngineEvent.DownloadComplete)
                                                                        } else {
                                                                            _engineDownloadStatus.value = "TPM Extraction Failed!"
                                                                            _isEnginePaused.value = true
                                                                            stopKeepAlive()
                                                                        }
                                                                    }
                                                                }
                                                            } else if (terr) {
                                                                _engineDownloadStatus.value = "TPM Download Failed"
                                                                _isEnginePaused.value = true
                                                                stopKeepAlive()
                                                            }
                                                        }
                                                } else {
                                                    _engineDownloadStatus.value = "Tools Extraction Failed!"
                                                    _isEnginePaused.value = true
                                                    stopKeepAlive()
                                                }
                                            }
                                        }
                                    } else if (err) {
                                        _engineDownloadStatus.value = "Tools Download Failed"
                                        _isEnginePaused.value = true
                                        stopKeepAlive()
                                    }
                                }
                            } else {
                                _engineDownloadStatus.value = "Engine Extraction Failed!"
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
