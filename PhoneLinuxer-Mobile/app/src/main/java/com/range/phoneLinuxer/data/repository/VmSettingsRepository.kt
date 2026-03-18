package com.range.phoneLinuxer.data.repository

import com.range.phoneLinuxer.data.model.VirtualMachineSettings
import kotlinx.coroutines.flow.Flow

interface VmSettingsRepository {

    fun findAllVms(): Flow<List<VirtualMachineSettings>>

    suspend fun findVmById(id: String): VirtualMachineSettings?

    suspend fun saveVm(settings: VirtualMachineSettings)

    suspend fun deleteVm(vmId: String)
}