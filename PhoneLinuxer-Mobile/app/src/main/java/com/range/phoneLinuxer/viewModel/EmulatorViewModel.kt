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
    private val executor = EmulatorExecutor()

    private val _editingVm = MutableStateFlow<VirtualMachineSettings?>(null)
    val editingVm: StateFlow<VirtualMachineSettings?> = _editingVm.asStateFlow()

    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    sealed class UiEvent {
        object SaveSuccess : UiEvent()
        object DeleteSuccess : UiEvent()
        data class Error(val message: String) : UiEvent()
    }

    val vms: StateFlow<List<VirtualMachineSettings>> = repository.findAllVms()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setEditingVm(vm: VirtualMachineSettings?) {
        _editingVm.value = vm
    }

    fun loadVmForEditing(id: String) {
        viewModelScope.launch {
            try {
                val vm = repository.findAllVms().firstOrNull()?.find { it.id == id }
                _editingVm.emit(vm)
                Timber.d("ViewModel: VM loaded for editing: ${vm?.vmName}")
            } catch (e: Exception) {
                _uiEvent.send(UiEvent.Error("Could not load VM data: ${e.message}"))
            }
        }
    }

    fun startVm(settings: VirtualMachineSettings) {
        if (executor.isRunning()) {
            viewModelScope.launch { _uiEvent.send(UiEvent.Error("Another VM is already running!")) }
            return
        }

        viewModelScope.launch {
            try {
                updateVmState(settings.id, VmState.STARTING)

                val command = settings.buildFullCommand()
                executor.launch(command)

                updateVmState(settings.id, VmState.RUNNING)
                Timber.i("Emulator: ${settings.vmName} is now running.")
            } catch (e: Exception) {
                updateVmState(settings.id, VmState.ERROR)
                _uiEvent.send(UiEvent.Error("Launch failed: ${e.message}"))
            }
        }
    }

    fun stopVm(vmId: String) {
        viewModelScope.launch {
            try {
                updateVmState(vmId, VmState.STOPPING)
                executor.stop()
                updateVmState(vmId, VmState.INACTIVE)
                Timber.i("Emulator: VM stopped by user.")
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
        viewModelScope.launch {
            try {
                repository.saveVm(settings)
                _uiEvent.send(UiEvent.SaveSuccess)
                setEditingVm(null)
            } catch (e: Exception) {
                _uiEvent.send(UiEvent.Error(e.message ?: "Save failed"))
            }
        }
    }

    fun deleteVm(vmId: String) {
        viewModelScope.launch {
            try {
                repository.deleteVm(vmId)
                _uiEvent.send(UiEvent.DeleteSuccess)
            } catch (e: Exception) {
                _uiEvent.send(UiEvent.Error(e.message ?: "Delete failed"))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        executor.stop()
    }
}