package com.range.phoneLinuxer.ui.navigation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.*
import com.range.phoneLinuxer.data.repository.SettingsRepository
import com.range.phoneLinuxer.ui.screen.*
import com.range.phoneLinuxer.ui.screen.download.DownloadScreen
import com.range.phoneLinuxer.ui.screen.emulator.AddNewEmulatorScreen
import com.range.phoneLinuxer.ui.screen.startLinux.StartLinuxScreen
import com.range.phoneLinuxer.ui.screen.log.LogScreen
import com.range.phoneLinuxer.ui.screen.settings.SettingsScreen
import com.range.phoneLinuxer.util.NavDebouncer
import com.range.phoneLinuxer.viewModel.LinuxViewModel
import com.range.phoneLinuxer.viewModel.EmulatorViewModel

object Screen {
    const val Welcome = "welcome"
    const val Main = "main"
    const val Settings = "settings"
    const val Logs = "logs"
    const val StartLinux = "startLinux"
    const val AddEmulator = "addEmulator"
}

@Composable
fun AppNavigation(
    linuxVm: LinuxViewModel,
    emulatorVm: EmulatorViewModel,
    settingsRepository: SettingsRepository
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    val vmList by emulatorVm.vms.collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        emulatorVm.uiEvent.collect { event ->
            when (event) {
                is EmulatorViewModel.UiEvent.Error -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                }
                is EmulatorViewModel.UiEvent.DeleteSuccess -> {
                    Toast.makeText(context, "VM successfully deleted", Toast.LENGTH_SHORT).show()
                }
                is EmulatorViewModel.UiEvent.SaveSuccess -> {
                    Toast.makeText(context, "VM saved successfully", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun safeNavigate(route: String) {
        if (NavDebouncer.canNavigate()) {
            navController.navigate(route)
        }
    }

    fun safePop() {
        if (NavDebouncer.canNavigate() && navController.previousBackStackEntry != null) {
            navController.popBackStack()
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Welcome
    ) {
        composable(Screen.Welcome) {
            WelcomeScreen(
                onDownloadDistro = { safeNavigate(Screen.Main) },
                onStartDistro = { safeNavigate(Screen.StartLinux) },
                onNavigateToSettings = { safeNavigate(Screen.Settings) },
                onNavigateToLogs = { safeNavigate(Screen.Logs) }
            )
        }

        composable(Screen.Main) {
            BackHandler { safePop() }
            DownloadScreen(
                vm = linuxVm,
                settingsRepository = settingsRepository,
                onNavigateToSettings = { safeNavigate(Screen.Settings) },
                onBack = { safePop() }
            )
        }

        composable(Screen.Settings) {
            BackHandler { safePop() }
            SettingsScreen(
                onBack = { safePop() },
                onNavigateToLogs = { safeNavigate(Screen.Logs) },
                repository = settingsRepository
            )
        }

        composable(Screen.Logs) {
            BackHandler { safePop() }
            LogScreen(onBack = { safePop() })
        }

        composable(Screen.StartLinux) {
            BackHandler { safePop() }
            StartLinuxScreen(
                onBack = { safePop() },
                onAddEmulator = { safeNavigate(Screen.AddEmulator) },
                onStartVM = { selectedVm ->
                    safeNavigate(Screen.Logs)
                },
                vms = vmList,
                onDeleteVM = { vmId ->
                    emulatorVm.deleteVm(vmId)
                }
            )
        }

        composable(Screen.AddEmulator) {
            BackHandler { safePop() }
            AddNewEmulatorScreen(
                viewModel = emulatorVm,
                onBack = { safePop() },
                onSave = { newVmSettings ->
                    emulatorVm.saveVm(newVmSettings)
                    safePop()
                }
            )
        }
    }
}