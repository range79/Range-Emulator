package com.range.rangeEmulator.data.repository

import com.range.rangeEmulator.data.model.AppSettings
import com.range.rangeEmulator.data.enums.DarkModeEnum
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {

    val settingsFlow: Flow<AppSettings>

    suspend fun updateSettings(settings: AppSettings)

    suspend fun updateDarkMode(mode: DarkModeEnum)

    suspend fun updateThemeColor(colorArgb: Int)
}