package com.range.phoneLinuxer.ui.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.range.phoneLinuxer.data.model.AppSettings
import com.range.phoneLinuxer.data.enums.DarkModeEnum
import com.range.phoneLinuxer.data.repository.SettingsRepository
import com.range.phoneLinuxer.data.repository.impl.SettingsRepositoryImpl
import com.range.phoneLinuxer.ui.navigation.AppNavigation
import com.range.phoneLinuxer.ui.screen.PermissionDeniedScreen
import com.range.phoneLinuxer.ui.theme.PhoneLinuxerTheme
import com.range.phoneLinuxer.util.AppLogCollector
import com.range.phoneLinuxer.viewModel.LinuxViewModel
import com.range.phoneLinuxer.viewModel.EmulatorViewModel
import timber.log.Timber

class MainActivity : ComponentActivity() {

    private val linuxViewModel: LinuxViewModel by viewModels()
    private val emulatorViewModel: EmulatorViewModel by viewModels()

    private lateinit var settingsRepository: SettingsRepository

    private val isStoragePermissionGranted = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsRepository = SettingsRepositoryImpl(applicationContext)

        setupLogging()
        checkPermissionState()

        setContent {
            val settings by settingsRepository.settingsFlow.collectAsState(initial = AppSettings())

            val useDarkTheme = when (settings.darkMode) {
                DarkModeEnum.LIGHT -> false
                DarkModeEnum.DARK -> true
                DarkModeEnum.SYSTEM -> isSystemInDarkTheme()
            }

            PhoneLinuxerTheme(
                darkTheme = useDarkTheme,
                dynamicColor = settings.useDynamicColors,
                seedColor = Color(settings.themeColorArgb)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val hasPermission by remember { isStoragePermissionGranted }

                    if (hasPermission) {
                        AppNavigation(
                            linuxVm = linuxViewModel,
                            emulatorVm = emulatorViewModel,
                            settingsRepository = settingsRepository
                        )
                    } else {
                        PermissionDeniedScreen(onRequestPermission = {
                            checkAndRequestStoragePermissions()
                        })
                    }
                }
            }
        }
    }

    private fun setupLogging() {
        if (Timber.treeCount == 0) {
            AppLogCollector.init(applicationContext)
            Timber.plant(AppLogCollector)
            Timber.i("Logging system initialized")
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissionState()
        AppLogCollector.init(applicationContext)
    }

    private fun checkPermissionState() {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

        isStoragePermissionGranted.value = granted
        Timber.d("Storage Permission Status: $granted")
    }

    private fun checkAndRequestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = "package:$packageName".toUri()
                }
                startActivity(intent)
            } catch (_: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        checkPermissionState()
    }
}