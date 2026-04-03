package com.range.rangeEmulator.ui.activity

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.range.rangeEmulator.data.enums.DarkModeEnum
import com.range.rangeEmulator.data.model.AppSettings
import com.range.rangeEmulator.data.model.PermissionState
import com.range.rangeEmulator.data.repository.SettingsRepository
import com.range.rangeEmulator.data.repository.impl.SettingsRepositoryImpl
import com.range.rangeEmulator.ui.navigation.AppNavigation
import com.range.rangeEmulator.ui.screen.PermissionDeniedScreen
import com.range.rangeEmulator.ui.theme.RangeEmulatorTheme
import com.range.rangeEmulator.util.AppLogCollector
import com.range.rangeEmulator.util.PermissionManager
import com.range.rangeEmulator.viewModel.EmulatorViewModel
import com.range.rangeEmulator.viewModel.EngineViewModel
import com.range.rangeEmulator.viewModel.LinuxViewModel
import timber.log.Timber

class MainActivity : ComponentActivity() {
    private val linuxVm: LinuxViewModel by viewModels()
    private val emulatorVm: EmulatorViewModel by viewModels()
    private val engineVm: EngineViewModel by viewModels()

    private lateinit var permissionManager: PermissionManager
    private lateinit var settingsRepository: SettingsRepository

    private var uiPermissionState by mutableStateOf(PermissionState())

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        Timber.i("Notification permission result received.")
        updateState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupLogging()

        permissionManager = PermissionManager(this)
        settingsRepository = SettingsRepositoryImpl(applicationContext)

        Timber.i("MainActivity: Lifecycle onCreate started.")
        updateState()

        setContent {
            val settings by settingsRepository.settingsFlow.collectAsState(initial = AppSettings())

            val useDarkTheme = when (settings.darkMode) {
                DarkModeEnum.LIGHT -> false
                DarkModeEnum.DARK -> true
                DarkModeEnum.SYSTEM -> isSystemInDarkTheme()
            }

            RangeEmulatorTheme(
                darkTheme = useDarkTheme,
                dynamicColor = settings.useDynamicColors,
                seedColor = Color(settings.themeColorArgb)
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state = uiPermissionState

                    when {
                        !state.isStorageGranted -> {
                            PermissionDeniedScreen(
                                title = "Storage Access Required",
                                description = "Full storage access is needed to save ISO files and create virtual disks for QEMU.",
                                onGrant = {
                                    Timber.w("Storage permission requested by user.")
                                    startActivity(permissionManager.getStoragePermissionIntent())
                                }
                            )
                        }

                        !state.isNotificationGranted -> {
                            PermissionDeniedScreen(
                                title = "Notification Access",
                                description = "Enable notifications to track download progress and VM status in the background.",
                                onGrant = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        Timber.w("Notification permission requested (Android 13+).")
                                        requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            )
                        }

                        !state.isBatteryOptimized -> {
                            PermissionDeniedScreen(
                                title = "High Performance Mode",
                                description = "Disable battery optimization to prevent Android from killing QEMU during background tasks.",
                                onGrant = {
                                    Timber.w("Battery optimization exemption requested.")
                                    startActivity(permissionManager.getBatteryOptimizationIntent())
                                }
                            )
                        }

                        else -> {
                            LaunchedEffect(Unit) {
                                Timber.i("All permissions granted. Launching AppNavigation.")
                            }
                            AppNavigation(
                                linuxVm = linuxVm,
                                emulatorVm = emulatorVm,
                                engineVm = engineVm,
                                settingsRepository = settingsRepository
                            )
                        }
                    }
                }
            }
        }
    }

    private fun setupLogging() {
        if (Timber.treeCount == 0) {
            AppLogCollector.init(applicationContext)
            Timber.plant(AppLogCollector)
            Timber.tag("RangeEmulator").i("Logging engine is online.")
        }
    }

    private fun updateState() {
        uiPermissionState = permissionManager.getFullPermissionState()
        Timber.d("Permission state updated: $uiPermissionState")
    }

    override fun onResume() {
        super.onResume()
        updateState()
    }
}