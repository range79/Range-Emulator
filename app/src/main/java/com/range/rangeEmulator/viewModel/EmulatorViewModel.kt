package com.range.rangeEmulator.viewModel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.range.rangeEmulator.RangeEmulatorApp
import com.range.rangeEmulator.data.enums.VmState
import com.range.rangeEmulator.data.model.VirtualMachineSettings
import com.range.rangeEmulator.data.model.buildFullCommand
import com.range.rangeEmulator.data.repository.impl.VmSettingsRepositoryImpl
import com.range.rangeEmulator.service.KeepAliveService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

class EmulatorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VmSettingsRepositoryImpl(application.applicationContext)
    private val executor = (application as RangeEmulatorApp).executor
    private val syncedStuckVms = mutableSetOf<String>()

    private val _vmLogs = androidx.compose.runtime.mutableStateMapOf<String, List<String>>()
    val vmLogsMap: Map<String, List<String>> = _vmLogs

    private val _editingVm = MutableStateFlow<VirtualMachineSettings?>(null)
    val editingVm: StateFlow<VirtualMachineSettings?> = _editingVm.asStateFlow()

    private val _isSavingVm = MutableStateFlow(false)
    val isSavingVm: StateFlow<Boolean> = _isSavingVm.asStateFlow()

    private val _uiEvent = Channel<UiEvent>(Channel.BUFFERED)
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

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            repeat(2) {
                val currentVms = repository.findAllVmsSync()
                currentVms.forEach { vm ->
                    if (vm.state == VmState.RUNNING || vm.state == VmState.STARTING || vm.state == VmState.STOPPING) {
                        if (!executor.isAlive(vm.id)) {
                            updateVmState(vm.id, VmState.INACTIVE)
                            if (syncedStuckVms.add(vm.id)) {
                                Timber.tag("EmulatorViewModel").i("Synced stuck VM state for: ${vm.vmName}")
                            }
                        }
                    }
                }
                delay(2000)
            }
        }
    }

    fun toggleVmState(settings: VirtualMachineSettings) {
        viewModelScope.launch {
            if (executor.isAlive(settings.id)) stopVm(settings.id)
            else startVm(settings)
        }
    }

    fun startVm(settings: VirtualMachineSettings) {
        viewModelScope.launch {
            try {
                if (settings.isGpuEnabled && !com.range.rangeEmulator.util.HardwareUtil.isGpuAccelerationSupported(getApplication())) {
                    _uiEvent.send(UiEvent.Error("Warning: Virtio-GPU is enabled but your device might not support OpenGL ES 3.0+. Emulation might be very slow or fail."))
                    delay(2000)
                }

                if (settings.cpuModel == com.range.rangeEmulator.data.enums.CpuModel.HOST && !com.range.rangeEmulator.util.HardwareUtil.isKvmSupported()) {
                    _uiEvent.send(UiEvent.Error("Error: KVM (Host CPU) selected but KVM is not supported or accessible on this device."))
                    return@launch
                }

                val correctedDisks = settings.disks.map { disk ->
                    disk.copy(path = disk.path.replace("com.range.RangeEmulator", "com.range.rangeEmulator"))
                }
                var safeSettings = settings.copy(disks = correctedDisks)

                try {
                    val preparedIsos = safeSettings.isoUris.map { uri ->
                        com.range.rangeEmulator.util.UriHelper.getRealPathFromUri(getApplication(), uri)
                    }
                    safeSettings = safeSettings.copy(isoUris = preparedIsos)

                    val activeVms = vms.value.filter { it.id != safeSettings.id && (it.state == VmState.RUNNING || it.state == VmState.STARTING) }
                    
                    var vncToTry = safeSettings.vncPort
                    var spiceToTry = safeSettings.spicePort
                    var sshToTry = safeSettings.sshPort

                    while (activeVms.any { it.vncPort == vncToTry }) vncToTry++
                    while (activeVms.any { it.spicePort == spiceToTry }) spiceToTry++
                    while (activeVms.any { it.sshPort == sshToTry }) sshToTry++

                    val finalVnc = com.range.rangeEmulator.util.PortUtil.findAvailablePort(vncToTry)
                    val finalSpice = com.range.rangeEmulator.util.PortUtil.findAvailablePort(maxOf(spiceToTry, finalVnc + 1))
                    val finalSsh = com.range.rangeEmulator.util.PortUtil.findAvailablePort(maxOf(sshToTry, finalSpice + 1))

                    safeSettings = safeSettings.copy(
                        vncPort = finalVnc,
                        spicePort = finalSpice,
                        sshPort = finalSsh
                    )

                    if (safeSettings != settings) repository.saveVm(safeSettings)

                    updateVmState(safeSettings.id, VmState.STARTING)
                    try { createDisksIfMissing(safeSettings) } catch (e: Exception) { Timber.e(e, "Pre-launch disk check failed") }

                    clearLogs(safeSettings.id)
                    appendLog(safeSettings.id, "--- Configuration Initialized (Build: 2026-04-14.v5) ---")
                    appendLog(safeSettings.id, "VNC Port: $finalVnc")
                    appendLog(safeSettings.id, "SPICE Port: $finalSpice")
                    appendLog(safeSettings.id, "SSH HostPort: $finalSsh")
                    appendLog(safeSettings.id, "--- Starting QEMU Engine ---")
 
                    // Use resolved ISO paths from step 101
                    val resolvedSafeSettings = safeSettings 

                    val tpmCacheDir = java.io.File(getApplication<Application>().cacheDir, "tpm_${safeSettings.id}")
                    if (!tpmCacheDir.exists()) tpmCacheDir.mkdirs()
                    val tpmSockPath = if (safeSettings.isTpmEnabled) {
                        java.io.File(tpmCacheDir, "swtpm.sock").absolutePath
                    } else null
 
                    viewModelScope.launch {
                        executor.getLogStream(safeSettings.id).collect { logLine ->
                            appendLog(safeSettings.id, logLine)
                        }
                    }
 
                    startKeepAliveService()
 
                    val result = executor.executeCommand(
                        vmId = safeSettings.id,
                        fullCommand = resolvedSafeSettings.buildFullCommand(tpmSockPath = tpmSockPath),
                        isTpmEnabled = safeSettings.isTpmEnabled,
                        tpmSockPath = tpmSockPath,
                        arch = safeSettings.arch
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
                            if (!executor.hasRunningProcesses()) stopKeepAliveService()
                        }
                    }
 
                    result.onSuccess {
                        updateVmState(safeSettings.id, VmState.RUNNING)
                        repository.saveVm(safeSettings.copy(state = VmState.RUNNING, vncPort = finalVnc, spicePort = finalSpice))
                        _uiEvent.send(UiEvent.NavigateToEmulator(safeSettings.id))
                    }
                    result.onFailure { e ->
                        updateVmState(safeSettings.id, VmState.INACTIVE)
                        _uiEvent.send(UiEvent.Error("Launch failed: ${e.message}"))
                    }
                } catch (e: Exception) {
                    _uiEvent.send(UiEvent.Error("Preparation failed: ${e.message}"))
                }

            } catch (e: Exception) {
                updateVmState(settings.id, VmState.ERROR)
                appendLog(settings.id, "SYSTEM ERROR: ${e.message}")
                _uiEvent.send(UiEvent.Error("System failure: ${e.message}"))
            }
        }
    }

    fun stopVm(vmId: String) {
        viewModelScope.launch {
            try {
                if (!executor.isAlive(vmId)) {
                    updateVmState(vmId, VmState.INACTIVE)
                    return@launch
                }
                updateVmState(vmId, VmState.STOPPING)
                appendLog(vmId, "--- Terminating VM ---")
                executor.killProcess(vmId)
                updateVmState(vmId, VmState.INACTIVE)
                if (!executor.hasRunningProcesses()) stopKeepAliveService()
            } catch (e: Exception) {
                _uiEvent.send(UiEvent.Error("Stop error: ${e.message}"))
                updateVmState(vmId, VmState.INACTIVE)
            }
        }
    }

    fun saveVm(settings: VirtualMachineSettings) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isSavingVm.value = true
            try {
                val correctedDisks = settings.disks.map { disk ->
                    disk.copy(path = disk.path.replace("com.range.RangeEmulator", "com.range.rangeEmulator"))
                }
                val safeSettings = settings.copy(disks = correctedDisks)
                createDisksIfMissing(safeSettings)
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
                if (executor.isAlive(vmId)) executor.killProcess(vmId)
                repository.deleteVm(vmId)
                _uiEvent.send(UiEvent.DeleteSuccess)
            } catch (e: Exception) {
                _uiEvent.send(UiEvent.Error(e.message ?: "Delete failed"))
            }
        }
    }

    fun setEditingVm(vm: VirtualMachineSettings?) { _editingVm.value = vm }
    

    fun loadVmForEditing(id: String) {
        viewModelScope.launch { _editingVm.emit(vms.value.find { it.id == id }) }
    }

    override fun onCleared() {
        super.onCleared()
        Timber.i("EmulatorViewModel onCleared: VMs will persist in background.")
    }

    private fun startKeepAliveService() {
        val app = getApplication<Application>()
        val intent = Intent(app.applicationContext, KeepAliveService::class.java).apply {
            putExtra(KeepAliveService.EXTRA_MODE, KeepAliveService.MODE_VM)
        }
        try {
            app.startForegroundService(intent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start KeepAliveService")
        }
    }

    private fun stopKeepAliveService() {
        val app = getApplication<Application>()
        val intent = Intent(app.applicationContext, KeepAliveService::class.java)
        app.stopService(intent)
    }

    private fun appendLog(vmId: String, line: String) {
        val history = _vmLogs[vmId] ?: emptyList()
        if (history.isNotEmpty() && history.last() == line) return
        
        val newList = (history + line).takeLast(500)
        _vmLogs[vmId] = newList
    }

    fun clearLogs(vmId: String) {
        _vmLogs[vmId] = emptyList()
    }

    private suspend fun updateVmState(vmId: String, newState: VmState) {
        vms.value.find { it.id == vmId }?.let { repository.saveVm(it.copy(state = newState)) }
    }

    private suspend fun createDisksIfMissing(settings: VirtualMachineSettings) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        settings.disks.forEach { disk ->
            val path = disk.path
            val file = java.io.File(path)
            file.parentFile?.mkdirs()
            if (file.exists()) return@forEach

            Timber.i("Creating ${disk.format.name} disk at $path (${disk.sizeGB}GB)")
            
            val result = executor.createDiskImage(path, disk.format.name, disk.sizeGB)
            
            if (result.isFailure) {
                val error = result.exceptionOrNull()?.message ?: "Unknown"
                appendLog(settings.id, "CRITICAL ERROR: Disk creation failed ($path): $error")
                appendLog(settings.id, "Please ensure qemu-img is installed and dependencies are downloaded.")
                throw Exception("qemu-img failed ($error). Cannot proceed without a valid disk image.")
            }
        }
    }
}