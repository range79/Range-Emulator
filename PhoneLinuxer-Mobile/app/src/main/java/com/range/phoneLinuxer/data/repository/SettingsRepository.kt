package com.range.phoneLinuxer.data.repository

import com.range.phoneLinuxer.data.model.AppSettings
import com.range.phoneLinuxer.data.enums.DarkModeEnum
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {

    val settingsFlow: Flow<AppSettings>

    suspend fun updateSettings(settings: AppSettings)

    suspend fun updateDarkMode(mode: DarkModeEnum)

    suspend fun updateThemeColor(colorArgb: Int)
}