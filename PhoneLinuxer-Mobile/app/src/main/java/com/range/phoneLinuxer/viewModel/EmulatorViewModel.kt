package com.range.phoneLinuxer.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.range.phoneLinuxer.data.model.VirtualMachineSettings
import com.range.phoneLinuxer.data.repository.VmSettingsRepository
import com.range.phoneLinuxer.data.repository.impl.VmSettingsRepositoryImpl
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EmulatorViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = getApplication<Application>().applicationContext
    private val repository: VmSettingsRepository = VmSettingsRepositoryImpl(appContext)

    val vms: StateFlow<List<VirtualMachineSettings>> = repository.findAllVms()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun saveVm(settings: VirtualMachineSettings) {
        viewModelScope.launch {
            repository.saveVm(settings)
        }
    }

    fun deleteVm(vmId: String) {
        viewModelScope.launch {
            repository.deleteVm(vmId)
        }
    }
}