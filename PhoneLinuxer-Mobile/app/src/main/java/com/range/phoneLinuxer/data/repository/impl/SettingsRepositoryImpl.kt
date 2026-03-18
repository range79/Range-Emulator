package com.range.phoneLinuxer.data.repository.impl

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.range.phoneLinuxer.data.model.AppSettings
import com.range.phoneLinuxer.data.enums.DarkModeEnum
import com.range.phoneLinuxer.data.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

class SettingsRepositoryImpl(private val context: Context) : SettingsRepository {

    private object PreferencesKeys {
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
        val ALLOW_MOBILE_DATA = booleanPreferencesKey("allow_mobile_data")
        val PHONE_ARCH = stringPreferencesKey("phone_arch")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val LOGGING_ENABLED = booleanPreferencesKey("logging_enabled")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val THEME_COLOR = intPreferencesKey("theme_color")
    }

    override val settingsFlow: Flow<AppSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            AppSettings(
                darkMode = DarkModeEnum.valueOf(
                    preferences[PreferencesKeys.DARK_MODE] ?: DarkModeEnum.SYSTEM.name
                ),
                useDynamicColors = preferences[PreferencesKeys.DYNAMIC_COLORS] ?: true,
                allowDownloadOnMobileData = preferences[PreferencesKeys.ALLOW_MOBILE_DATA] ?: false,
                phoneArchitecture = preferences[PreferencesKeys.PHONE_ARCH] ?: "arm64",
                keepScreenOn = preferences[PreferencesKeys.KEEP_SCREEN_ON] ?: true,
                loggingEnabled = preferences[PreferencesKeys.LOGGING_ENABLED] ?: true,
                biometricLock = preferences[PreferencesKeys.BIOMETRIC_LOCK] ?: false,
                themeColorArgb = preferences[PreferencesKeys.THEME_COLOR] ?: 0xFF6750A4.toInt()
            )
        }

    override suspend fun updateSettings(settings: AppSettings) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DARK_MODE] = settings.darkMode.name
            preferences[PreferencesKeys.DYNAMIC_COLORS] = settings.useDynamicColors
            preferences[PreferencesKeys.ALLOW_MOBILE_DATA] = settings.allowDownloadOnMobileData
            preferences[PreferencesKeys.PHONE_ARCH] = settings.phoneArchitecture
            preferences[PreferencesKeys.KEEP_SCREEN_ON] = settings.keepScreenOn
            preferences[PreferencesKeys.LOGGING_ENABLED] = settings.loggingEnabled
            preferences[PreferencesKeys.BIOMETRIC_LOCK] = settings.biometricLock
            preferences[PreferencesKeys.THEME_COLOR] = settings.themeColorArgb
        }
    }

    override suspend fun updateDarkMode(mode: DarkModeEnum) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DARK_MODE] = mode.name
        }
    }

    override suspend fun updateThemeColor(colorArgb: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_COLOR] = colorArgb
        }
    }
}