package com.range.rangeEmulator.data.repository

import com.range.rangeEmulator.data.model.VirtualMachineSettings
import kotlinx.coroutines.flow.Flow

interface VmSettingsRepository {

    fun findAllVms(): Flow<List<VirtualMachineSettings>>

    suspend fun findVmById(id: String): VirtualMachineSettings?

    suspend fun saveVm(settings: VirtualMachineSettings)

    suspend fun deleteVm(vmId: String)
}