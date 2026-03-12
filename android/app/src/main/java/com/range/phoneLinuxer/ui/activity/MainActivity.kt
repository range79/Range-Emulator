package com.range.phoneLinuxer.ui.activity

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.range.phoneLinuxer.ui.screen.MainScreen
import com.range.phoneLinuxer.viewModel.LinuxViewModel
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {

    private val vm: LinuxViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = ("package:" + this.packageName).toUri()
                startActivity(intent)
            }
        }

        setContent {
            MainScreen(vm)
        }
    }
}