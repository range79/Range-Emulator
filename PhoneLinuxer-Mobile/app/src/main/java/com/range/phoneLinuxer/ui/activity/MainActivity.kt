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
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.range.phoneLinuxer.ui.navigation.AppNavigation
import com.range.phoneLinuxer.ui.screen.PermissionDeniedScreen
import com.range.phoneLinuxer.ui.theme.PhoneLinuxerTheme
import com.range.phoneLinuxer.util.AppLogCollector
import com.range.phoneLinuxer.viewModel.LinuxViewModel
import timber.log.Timber

class MainActivity : ComponentActivity() {

    private val viewModel: LinuxViewModel by viewModels()

    // İzin durumunu takip eden State
    private val isStoragePermissionGranted = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Timber log toplayıcısını başlat
        if (Timber.treeCount == 0) {
            Timber.plant(AppLogCollector)
        }

        // İlk açılışta izin durumunu kontrol et
        checkPermissionState()

        setContent {
            PhoneLinuxerTheme {
                // 'by remember' yerine doğrudan state'i gözlemliyoruz
                val hasPermission by remember { isStoragePermissionGranted }

                Surface {
                    if (hasPermission) {
                        AppNavigation(viewModel)
                    } else {
                        PermissionDeniedScreen(onRequestPermission = {
                            checkAndRequestStoragePermissions()
                        })
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Kullanıcı ayarlardan geri döndüğünde izni tekrar kontrol et
        checkPermissionState()
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
            } catch (e: Exception) {
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