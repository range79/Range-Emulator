package com.range.phoneLinuxer.data.repository.impl

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.range.phoneLinuxer.data.model.VirtualMachineSettings
import com.range.phoneLinuxer.data.repository.VmSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.File

private val Context.dataStore by preferencesDataStore(name = "vm_prefs")

class VmSettingsRepositoryImpl(private val context: Context) : VmSettingsRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val VM_LIST_KEY = stringPreferencesKey("vm_list")

    override fun findAllVms(): Flow<List<VirtualMachineSettings>> = context.dataStore.data.map { prefs ->
        val jsonString = prefs[VM_LIST_KEY] ?: return@map emptyList()
        try {
            json.decodeFromString<List<VirtualMachineSettings>>(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun saveVm(settings: VirtualMachineSettings) {
        context.dataStore.edit { prefs ->
            val jsonString = prefs[VM_LIST_KEY]
            val list = if (!jsonString.isNullOrEmpty()) {
                try {
                    json.decodeFromString<MutableList<VirtualMachineSettings>>(jsonString)
                } catch (e: Exception) {
                    mutableListOf()
                }
            } else {
                mutableListOf()
            }

            val index = list.indexOfFirst { it.id == settings.id }
            if (index != -1) {
                list[index] = settings
            } else {
                list.add(settings)
            }

            prefs[VM_LIST_KEY] = json.encodeToString(list)
        }
    }

    override suspend fun deleteVm(vmId: String) {
        context.dataStore.edit { prefs ->
            val jsonString = prefs[VM_LIST_KEY] ?: return@edit
            val list = try {
                json.decodeFromString<MutableList<VirtualMachineSettings>>(jsonString)
            } catch (e: Exception) {
                return@edit
            }

            list.find { it.id == vmId }?.diskImgPath?.let { path ->
                val file = File(path)
                if (file.exists()) file.delete()
            }

            if (list.removeAll { it.id == vmId }) {
                prefs[VM_LIST_KEY] = json.encodeToString(list)
            }
        }
    }
}