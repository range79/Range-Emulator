package com.range.phoneLinuxer.ui.activity

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.range.phoneLinuxer.data.model.PermissionState
import com.range.phoneLinuxer.data.repository.SettingsRepository
import com.range.phoneLinuxer.data.repository.impl.SettingsRepositoryImpl
import com.range.phoneLinuxer.ui.navigation.AppNavigation
import com.range.phoneLinuxer.ui.screen.PermissionDeniedScreen
import com.range.phoneLinuxer.ui.theme.PhoneLinuxerTheme
import com.range.phoneLinuxer.util.PermissionManager
import com.range.phoneLinuxer.viewModel.EmulatorViewModel
import com.range.phoneLinuxer.viewModel.LinuxViewModel

class MainActivity : ComponentActivity() {
    private val linuxVm: LinuxViewModel by viewModels()
    private val emulatorVm: EmulatorViewModel by viewModels()

    private lateinit var permissionManager: PermissionManager
    private lateinit var settingsRepository: SettingsRepository

    private var uiPermissionState by mutableStateOf(PermissionState())

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { updateState() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionManager = PermissionManager(this)
        settingsRepository = SettingsRepositoryImpl(applicationContext)

        updateState()

        setContent {
            PhoneLinuxerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state = uiPermissionState

                    when {
                        !state.isStorageGranted -> {
                            PermissionDeniedScreen(
                                title = "Storage Access Required",
                                description = "Full storage access is needed to save ISO files and create virtual disks for QEMU.",
                                onGrant = { startActivity(permissionManager.getStoragePermissionIntent()) }
                            )
                        }

                        !state.isNotificationGranted -> {
                            PermissionDeniedScreen(
                                title = "Notification Access",
                                description = "Enable notifications to track download progress and VM status in the background.",
                                onGrant = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            )
                        }

                        !state.isBatteryOptimized -> {
                            PermissionDeniedScreen(
                                title = "High Performance Mode",
                                description = "Disable battery optimization to prevent Android from killing QEMU during background tasks.",
                                onGrant = { startActivity(permissionManager.getBatteryOptimizationIntent()) }
                            )
                        }

                        else -> {
                            AppNavigation(
                                linuxVm = linuxVm,
                                emulatorVm = emulatorVm,
                                settingsRepository = settingsRepository
                            )
                        }
                    }
                }
            }
        }
    }

    private fun updateState() {
        uiPermissionState = permissionManager.getFullPermissionState()
    }

    override fun onResume() {
        super.onResume()
        updateState()
    }
}