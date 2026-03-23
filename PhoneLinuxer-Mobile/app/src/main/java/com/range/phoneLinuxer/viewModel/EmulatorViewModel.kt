package com.range.phoneLinuxer.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.range.phoneLinuxer.data.enums.VmState
import com.range.phoneLinuxer.data.executor.EmulatorExecutor
import com.range.phoneLinuxer.data.model.VirtualMachineSettings
import com.range.phoneLinuxer.data.model.buildFullCommand
import com.range.phoneLinuxer.data.repository.impl.VmSettingsRepositoryImpl
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class EmulatorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VmSettingsRepositoryImpl(application.applicationContext)
    private val executor = EmulatorExecutor(application)

    private val _vmLogs = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val vmLogs: StateFlow<Map<String, List<String>>> = _vmLogs.asStateFlow()

    private val _editingVm = MutableStateFlow<VirtualMachineSettings?>(null)
    val editingVm: StateFlow<VirtualMachineSettings?> = _editingVm.asStateFlow()

    private val _isSavingVm = MutableStateFlow(false)
    val isSavingVm: StateFlow<Boolean> = _isSavingVm.asStateFlow()

    private val _isEngineDownloaded = MutableStateFlow(java.io.File(application.applicationContext.filesDir, "pc-bios").exists())
    val isEngineDownloaded: StateFlow<Boolean> = _isEngineDownloaded.asStateFlow()

    private val _isEngineDownloading = MutableStateFlow(false)
    val isEngineDownloading: StateFlow<Boolean> = _isEngineDownloading.asStateFlow()

    private val _engineDownloadProgress = MutableStateFlow(0)
    val engineDownloadProgress: StateFlow<Int> = _engineDownloadProgress.asStateFlow()

    private var engineDownloadJob: kotlinx.coroutines.Job? = null

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

    private val _showEngineMobileDataWarning = MutableStateFlow(false)
    val showEngineMobileDataWarning: StateFlow<Boolean> = _showEngineMobileDataWarning.asStateFlow()

    fun dismissEngineMobileDataWarning() {
        _showEngineMobileDataWarning.value = false
    }

    private val settingsRepository: com.range.phoneLinuxer.data.repository.SettingsRepository = com.range.phoneLinuxer.data.repository.impl.SettingsRepositoryImpl(application.applicationContext)
    private val appSettingsFlow = settingsRepository.settingsFlow

    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    sealed class UiEvent {
        object SaveSuccess : UiEvent()
        object DeleteSuccess : UiEvent()
        data class NavigateToEmulator(val vmId: String) : UiEvent()
        data class Error(val message: String) : UiEvent()
    }

    val vms: StateFlow<List<VirtualMachineSettings>> = repository.findAllVms()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun toggleVmState(settings: VirtualMachineSettings) {
        viewModelScope.launch {
            if (executor.isAlive(settings.id)) {
                stopVm(settings.id)
            } else {
                startVm(settings)
            }
        }
    }

    fun startVm(settings: VirtualMachineSettings) {
        viewModelScope.launch {
            try {
                val correctedPath = settings.diskImgPath?.replace("com.range.PhoneLinuxer", "com.range.phoneLinuxer")
                var safeSettings = settings.copy(diskImgPath = correctedPath)

             
                val activeVms = vms.value.filter { it.id != safeSettings.id && (it.state == VmState.RUNNING || it.state == VmState.STARTING) }
                val activeVncPorts = activeVms.map { it.vncPort }
                val activeRdpPorts = activeVms.map { it.rdpPort }

                var newVncPort = safeSettings.vncPort
                while (activeVncPorts.contains(newVncPort)) newVncPort++

                var newRdpPort = safeSettings.rdpPort
                while (activeRdpPorts.contains(newRdpPort)) newRdpPort++

                safeSettings = safeSettings.copy(vncPort = newVncPort, rdpPort = newRdpPort)
                if (safeSettings != settings) repository.saveVm(safeSettings)

                updateVmState(safeSettings.id, VmState.STARTING)
                
                try {
                    createDiskImageIfMissing(safeSettings)
                } catch(e: Exception) {
                    Timber.e(e, "Pre-launch disk check failed")
                }

                clearLogs(safeSettings.id)
                appendLog(safeSettings.id, "--- Starting QEMU Engine ---")

                val fullCommand = safeSettings.buildFullCommand()

                viewModelScope.launch {
                    executor.getLogStream(safeSettings.id).collect { logLine ->
                        appendLog(safeSettings.id, logLine)
                    }
                }

                val result = executor.executeCommand(
                    vmId = safeSettings.id,
                    fullCommand = fullCommand
                ) { exitCode ->
                    viewModelScope.launch {
                        val currentState = vms.value.find { it.id == safeSettings.id }?.state
                        if (currentState != VmState.STOPPING && currentState != VmState.INACTIVE) {
                            if (exitCode == 0 || exitCode == 143 || exitCode == 137) {
                                updateVmState(safeSettings.id, VmState.INACTIVE)
                                appendLog(safeSettings.id, "--- VM Exited Normally ---")
                            } else {
                                updateVmState(safeSettings.id, VmState.ERROR)
                                appendLog(safeSettings.id, "--- ERROR: VM Crashed (Code: $exitCode) ---")
                                _uiEvent.send(UiEvent.Error("Emulator crashed (Code: $exitCode)"))
                            }
                        }
                    }
                }

                result.onSuccess { pid ->
                    updateVmState(safeSettings.id, VmState.RUNNING)
                    Timber.i("VM ${safeSettings.vmName} started. PID: $pid")

                    _uiEvent.send(UiEvent.NavigateToEmulator(safeSettings.id))
                }.onFailure { e ->
                    updateVmState(safeSettings.id, VmState.ERROR)
                    appendLog(safeSettings.id, "LAUNCH ERROR: ${e.message}")
                    _uiEvent.send(UiEvent.Error("Launch failed: ${e.message}"))
                }

            } catch (e: Exception) {
                updateVmState(settings.id, VmState.ERROR)
                appendLog(settings.id, "SYSTEM ERROR: ${e.message}")
                _uiEvent.send(UiEvent.Error("System failure: ${e.message}"))
            }
        }
    }

    private fun appendLog(vmId: String, line: String) {
        _vmLogs.update { currentMap ->
            val currentList = currentMap[vmId] ?: emptyList()
            val newList = (currentList + line).takeLast(500)
            currentMap + (vmId to newList)
        }
    }

    private fun clearLogs(vmId: String) {
        _vmLogs.update { it + (vmId to emptyList()) }
    }

    fun toggleEnginePauseResume() {
        if (_isEnginePaused.value) {
            _isEnginePaused.value = false
            startEngineDownload()
        } else {
            _isEnginePaused.value = true
            engineDownloadJob?.cancel()
            _engineDownloadStatus.value = "Paused"
            _engineDownloadSpeed.value = "0 KB/s"
            _engineRemainingTime.value = ""
        }
    }

    fun cancelEngineDownload() {
        _isEnginePaused.value = false
        engineDownloadJob?.cancel()
        _isEngineDownloading.value = false
        _engineDownloadStatus.value = "Canceled"
        _engineDownloadProgress.value = 0
        _engineDownloadSpeed.value = "0 KB/s"
        _engineRemainingTime.value = ""
        
        val zipFile = java.io.File(getApplication<Application>().applicationContext.filesDir, "engine.zip")
        if (zipFile.exists()) zipFile.delete()
    }

    private fun startEngineDownload(forceMobileData: Boolean = false) {
        val targetUrl = _engineTargetUrl.value
        if (targetUrl.isEmpty()) {
            _engineDownloadStatus.value = "Error: Invalid OTA URL"
            return
        }
        engineDownloadJob?.cancel()
        engineDownloadJob = viewModelScope.launch {
            val appSettings = appSettingsFlow.first()
            val allowMobile = appSettings.allowDownloadOnMobileData || forceMobileData

            _isEngineDownloading.value = true
            _engineDownloadStatus.value = "Downloading QEMU Engine..."
            val startTime = System.currentTimeMillis()

            executor.downloadEngineZip(targetUrl, allowMobile) { downloaded, total, isError, mobileRestricted ->
                if (mobileRestricted) {
                    _showEngineMobileDataWarning.value = true
                    _isEnginePaused.value = true
                    _isEngineDownloading.value = false
                    return@downloadEngineZip
                }

                if (isError) {
                    _engineDownloadStatus.value = "Error: Connection Lost"
                    _isEnginePaused.value = true
                } else if (!_isEnginePaused.value) {
                    val progress = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                    _engineDownloadProgress.value = progress
                    _engineDownloadStatus.value = "Downloading... $progress%"

                    val timeSec = (System.currentTimeMillis() - startTime) / 1000.0
                    if (timeSec > 0.5) {
                        val speedInBytes = downloaded / timeSec
                        _engineDownloadSpeed.value = if (speedInBytes >= 1024 * 1024) "%.1f MB/s".format(speedInBytes / (1024 * 1024)) else "%.1f KB/s".format(speedInBytes / 1024)
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
                        _engineDownloadStatus.value = "Extracting components..."
                        _engineDownloadSpeed.value = ""
                        _engineRemainingTime.value = ""
                        
                        viewModelScope.launch {
                            val success = executor.extractEngineZip { extractProgress ->
                                _engineDownloadProgress.value = extractProgress
                                _engineDownloadStatus.value = "Extracting... $extractProgress%"
                            }

                            if (success) {
                                _engineDownloadStatus.value = "Extraction Complete!"
                                _isEngineDownloaded.value = true
                                _isEngineDownloading.value = false
                                _uiEvent.send(UiEvent.SaveSuccess)
                            } else {
                                _engineDownloadStatus.value = "Extraction Failed!"
                                _isEnginePaused.value = true
                            }
                        }
                    }
                }
            }
        }
    }

    fun downloadQemuEngine(forceMobileData: Boolean = false) {
        if (_isEngineDownloading.value && !_isEnginePaused.value) return
        startEngineDownload(forceMobileData)
    }

    fun prepareLatestEngineOTA() {
        if (_engineTargetUrl.value.isNotEmpty()) return
        viewModelScope.launch {
            try {
                _engineDownloadStatus.value = "Fetching OTA specs..."
                val url = java.net.URL("https://api.github.com/repos/range79x/phone-linuxer-dependencies/releases/latest")
                val connection = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    url.openConnection() as java.net.HttpURLConnection
                }
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                val response = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                }
                
                val urlRegex = """"browser_download_url"\s*:\s*"([^"]+PhoneLinuxer-Dependencies\.zip)"""".toRegex()
                val sizeRegex = """"size"\s*:\s*(\d+)""".toRegex()
                
                val urlMatch = urlRegex.find(response)
                val sizeMatch = sizeRegex.find(response)
                
                if (urlMatch != null) {
                    _engineTargetUrl.value = urlMatch.groupValues[1]
                    if (sizeMatch != null) {
                        val mb = sizeMatch.groupValues[1].toLong() / (1024 * 1024)
                        _engineTargetSizeMB.value = "$mb"
                    } else {
                        _engineTargetSizeMB.value = "107"
                    }
                } else {
                    _engineTargetUrl.value = "https://github.com/range79x/phone-linuxer-dependencies/releases/download/v1.0.0/PhoneLinuxer-Dependencies.zip"
                    _engineTargetSizeMB.value = "107"
                }
                _engineDownloadStatus.value = "Idle"
            } catch (e: Exception) {
                // Fallback hardcoded defaults if offline during modal prep
                _engineTargetUrl.value = "https://github.com/range79x/phone-linuxer-dependencies/releases/download/v1.0.0/PhoneLinuxer-Dependencies.zip"
                _engineTargetSizeMB.value = "107"
                _engineDownloadStatus.value = "Idle"
            }
        }
    }

    fun stopVm(vmId: String) {
        viewModelScope.launch {
            try {
                updateVmState(vmId, VmState.STOPPING)
                appendLog(vmId, "--- Terminating VM ---")

                executor.killProcess(vmId)

                updateVmState(vmId, VmState.INACTIVE)
                Timber.i("VM $vmId stopped.")
            } catch (e: Exception) {
                _uiEvent.send(UiEvent.Error("Stop error: ${e.message}"))
            }
        }
    }

    private suspend fun updateVmState(vmId: String, newState: VmState) {
        vms.value.find { it.id == vmId }?.let { vm ->
            repository.saveVm(vm.copy(state = newState))
        }
    }

    fun saveVm(settings: VirtualMachineSettings) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isSavingVm.value = true
            try {
                val correctedPath = settings.diskImgPath?.replace("com.range.PhoneLinuxer", "com.range.phoneLinuxer")
                val safeSettings = settings.copy(diskImgPath = correctedPath)
                createDiskImageIfMissing(safeSettings)
                repository.saveVm(safeSettings)
                _uiEvent.send(UiEvent.SaveSuccess)
                _editingVm.value = null
            } catch (e: Exception) {
                _uiEvent.send(UiEvent.Error(e.message ?: "Save failed"))
            } finally {
                _isSavingVm.value = false
            }
        }
    }

    fun deleteVm(vmId: String) {
        viewModelScope.launch {
            try {
                if (executor.isAlive(vmId)) {
                    executor.killProcess(vmId)
                }
                repository.deleteVm(vmId)
                _uiEvent.send(UiEvent.DeleteSuccess)
            } catch (e: Exception) {
                _uiEvent.send(UiEvent.Error(e.message ?: "Delete failed"))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        executor.killAll()
    }

    fun setEditingVm(vm: VirtualMachineSettings?) {
        _editingVm.value = vm
    }

    fun loadVmForEditing(id: String) {
        viewModelScope.launch {
            val vm = vms.value.find { it.id == id }
            _editingVm.emit(vm)
        }
    }

    private suspend fun createDiskImageIfMissing(settings: VirtualMachineSettings) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val path = settings.diskImgPath ?: return@withContext
        val file = java.io.File(path)
        file.parentFile?.mkdirs()
        if (file.exists()) {
            Timber.i("Disk already exists: $path")
            return@withContext
        }
        
        Timber.i("Creating ${settings.diskFormat.name} disk at $path (Size: ${settings.diskSizeGB}GB)")
        
        try {
            val sizeBytes = settings.diskSizeGB * 1024L * 1024L * 1024L
            java.io.RandomAccessFile(file, "rw").use { raf ->
                if (settings.diskFormat.name == "RAW") {
                    raf.setLength(sizeBytes)
                } else if (settings.diskFormat.name == "QCOW2") {
                    raf.setLength(262144)
                    raf.seek(0)
                    raf.writeInt(0x514649fb)
                    raf.writeInt(3)
                    raf.writeLong(0)
                    raf.writeInt(0)
                    raf.writeInt(16)
                    raf.writeLong(sizeBytes)
                    raf.writeInt(0)
                    val l1Size = kotlin.math.ceil(sizeBytes.toDouble() / 536870912.0).toInt()
                        .coerceAtLeast(1)
                    raf.writeInt(l1Size)
                    raf.writeLong(65536)
                    raf.writeLong(131072)
                    raf.writeInt(1)
                    raf.writeInt(0)
                    raf.writeLong(0)
                    raf.writeLong(0)
                    raf.writeLong(0)
                    raf.writeLong(0)
                    raf.writeInt(4)
                    raf.writeInt(104)
                    raf.seek(131072)
                    raf.writeLong(196608)
                    raf.seek(196608)
                    raf.writeShort(1)
                    raf.writeShort(1)
                    raf.writeShort(1)
                    raf.writeShort(1)
                }
            }
            Timber.i("Disk creation successful: $path")
        } catch (e: Exception) {
            Timber.e(e, "Failed to create disk at $path")
            throw e
        }
    }
}