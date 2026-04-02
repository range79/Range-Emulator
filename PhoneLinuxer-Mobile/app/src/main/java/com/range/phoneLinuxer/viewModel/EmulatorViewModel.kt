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

    fun toggleVmState(settings: VirtualMachineSettings) {
        viewModelScope.launch {
            if (executor.isAlive(settings.id)) stopVm(settings.id)
            else startVm(settings)
        }
    }

    fun startVm(settings: VirtualMachineSettings) {
        viewModelScope.launch {
            try {
                val correctedPath = settings.diskImgPath?.replace("com.range.PhoneLinuxer", "com.range.phoneLinuxer")
                var safeSettings = settings.copy(diskImgPath = correctedPath)

                // Prepare ISOs: Copy content URIs to local cache so QEMU can access them as files
                appendLog(safeSettings.id, "Preparing ISO files (this may take a while)...")
                val preparedIsos = safeSettings.isoUris.map { uri ->
                    com.range.phoneLinuxer.util.UriHelper.getRealPathFromUri(getApplication(), uri)
                }
                safeSettings = safeSettings.copy(isoUris = preparedIsos)

                val activeVms = vms.value.filter { it.id != safeSettings.id && (it.state == VmState.RUNNING || it.state == VmState.STARTING) }
                var newVncPort = safeSettings.vncPort
                var newRdpPort = safeSettings.rdpPort
                while (activeVms.map { it.vncPort }.contains(newVncPort)) newVncPort++
                while (activeVms.map { it.rdpPort }.contains(newRdpPort)) newRdpPort++

                safeSettings = safeSettings.copy(vncPort = newVncPort, rdpPort = newRdpPort)
                if (safeSettings != settings) repository.saveVm(safeSettings)

                updateVmState(safeSettings.id, VmState.STARTING)
                try { createDiskImageIfMissing(safeSettings) } catch (e: Exception) { Timber.e(e, "Pre-launch disk check failed") }

                clearLogs(safeSettings.id)
                appendLog(safeSettings.id, "--- Starting QEMU Engine ---")

                viewModelScope.launch {
                    executor.getLogStream(safeSettings.id).collect { logLine ->
                        appendLog(safeSettings.id, logLine)
                    }
                }

                val result = executor.executeCommand(
                    vmId = safeSettings.id,
                    fullCommand = safeSettings.buildFullCommand()
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

                result.onSuccess {
                    updateVmState(safeSettings.id, VmState.RUNNING)
                    Timber.i("VM ${safeSettings.vmName} started.")
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

    fun stopVm(vmId: String) {
        viewModelScope.launch {
            try {
                updateVmState(vmId, VmState.STOPPING)
                appendLog(vmId, "--- Terminating VM ---")
                executor.killProcess(vmId)
                updateVmState(vmId, VmState.INACTIVE)
            } catch (e: Exception) {
                _uiEvent.send(UiEvent.Error("Stop error: ${e.message}"))
            }
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
        executor.killAll()
    }

    private fun appendLog(vmId: String, line: String) {
        _vmLogs.update { currentMap ->
            val newList = ((currentMap[vmId] ?: emptyList()) + line).takeLast(500)
            currentMap + (vmId to newList)
        }
    }

    private fun clearLogs(vmId: String) {
        _vmLogs.update { it + (vmId to emptyList()) }
    }

    private suspend fun updateVmState(vmId: String, newState: VmState) {
        vms.value.find { it.id == vmId }?.let { repository.saveVm(it.copy(state = newState)) }
    }

    private suspend fun createDiskImageIfMissing(settings: VirtualMachineSettings) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val path = settings.diskImgPath ?: return@withContext
        val file = java.io.File(path)
        file.parentFile?.mkdirs()
        if (file.exists()) return@withContext

        Timber.i("Creating ${settings.diskFormat.name} disk at $path (${settings.diskSizeGB}GB)")
        try {
            val sizeBytes = settings.diskSizeGB * 1024L * 1024L * 1024L
            java.io.RandomAccessFile(file, "rw").use { raf ->
                if (settings.diskFormat.name == "RAW") {
                    raf.setLength(sizeBytes)
                } else if (settings.diskFormat.name == "QCOW2") {
                    raf.setLength(262144)
                    raf.seek(0)
                    raf.writeInt(0x514649fb); raf.writeInt(3); raf.writeLong(0)
                    raf.writeInt(0); raf.writeInt(16); raf.writeLong(sizeBytes)
                    raf.writeInt(0)
                    val l1Size = kotlin.math.ceil(sizeBytes.toDouble() / 536870912.0).toInt().coerceAtLeast(1)
                    raf.writeInt(l1Size); raf.writeLong(65536); raf.writeLong(131072)
                    raf.writeInt(1); raf.writeInt(0); raf.writeLong(0); raf.writeLong(0)
                    raf.writeLong(0); raf.writeLong(0); raf.writeInt(4); raf.writeInt(104)
                    raf.seek(131072); raf.writeLong(196608)
                    raf.seek(196608); raf.writeShort(1); raf.writeShort(1); raf.writeShort(1); raf.writeShort(1)
                }
            }
            Timber.i("Disk creation successful: $path")
        } catch (e: Exception) {
            Timber.e(e, "Failed to create disk at $path")
            throw e
        }
    }
}