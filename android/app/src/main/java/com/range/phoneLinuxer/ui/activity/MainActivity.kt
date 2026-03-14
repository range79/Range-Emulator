package com.range.phoneLinuxer.ui.activity

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.net.toUri
import com.range.phoneLinuxer.ui.navigation.AppNavigation
import com.range.phoneLinuxer.ui.theme.PhoneLinuxerTheme
import com.range.phoneLinuxer.viewModel.LinuxViewModel
import timber.log.Timber

class MainActivity : ComponentActivity() {

    private val vm: LinuxViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.plant(Timber.DebugTree())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = ("package:" + packageName).toUri()
                startActivity(intent)
            }
        }

        setContent {
            PhoneLinuxerTheme {
                androidx.compose.material3.Surface(
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(vm)
                }}
        }
    }
}