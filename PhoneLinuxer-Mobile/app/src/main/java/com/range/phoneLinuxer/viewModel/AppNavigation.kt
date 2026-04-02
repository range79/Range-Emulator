package com.range.phoneLinuxer.ui.navigation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.range.phoneLinuxer.data.repository.SettingsRepository
import com.range.phoneLinuxer.ui.screen.WelcomeScreen
import com.range.phoneLinuxer.ui.screen.download.DownloadScreen
import com.range.phoneLinuxer.ui.screen.emulator.AddNewEmulatorScreen
import com.range.phoneLinuxer.ui.screen.emulator.VmControlScreen
import com.range.phoneLinuxer.ui.screen.emulatorList.EditEmulatorScreen
import com.range.phoneLinuxer.ui.screen.emulatorList.EmulatorListScreen
import com.range.phoneLinuxer.ui.screen.log.LogScreen
import com.range.phoneLinuxer.ui.screen.log.QemuLogsScreen
import com.range.phoneLinuxer.ui.screen.settings.SettingsScreen
import com.range.phoneLinuxer.util.NavDebouncer
import com.range.phoneLinuxer.viewModel.LinuxViewModel
import com.range.phoneLinuxer.viewModel.EmulatorViewModel
import com.range.phoneLinuxer.viewModel.EngineViewModel

object Screen {
    const val Welcome = "welcome"
    const val Main = "main"
    const val Settings = "settings"
    const val Logs = "logs"
    const val VmLogs = "vmLogs/{vmId}"
    const val EmulatorScreen = "emulatorScreen"
    const val AddEmulator = "addEmulator"
    const val EditEmulator = "editEmulator"
    const val VncViewer = "vncViewer/{vmId}"
}

@Composable
fun AppNavigation(
    linuxVm: LinuxViewModel,
    emulatorVm: EmulatorViewModel,
    engineVm: EngineViewModel,
    settingsRepository: SettingsRepository
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val vmList by emulatorVm.vms.collectAsState(initial = emptyList())

    fun safePop() {
        if (NavDebouncer.canNavigate()) navController.popBackStack()
    }

    fun safeNavigate(route: String) {
        if (NavDebouncer.canNavigate()) navController.navigate(route)
    }

    LaunchedEffect(Unit) {
        emulatorVm.uiEvent.collect { event ->
            when (event) {
                is EmulatorViewModel.UiEvent.Error ->
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()

                is EmulatorViewModel.UiEvent.DeleteSuccess ->
                    Toast.makeText(context, "Virtual machine deleted successfully", Toast.LENGTH_SHORT).show()

                is EmulatorViewModel.UiEvent.SaveSuccess -> {
                    Toast.makeText(context, "Virtual machine configuration saved", Toast.LENGTH_SHORT).show()
                    safePop()
                }

                is EmulatorViewModel.UiEvent.NavigateToEmulator -> {
                    if (NavDebouncer.canNavigate()) {
                        navController.navigate("vncViewer/${event.vmId}")
                    }
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = Screen.Welcome) {

        composable(Screen.Welcome) {
            WelcomeScreen(
                emulatorVm = emulatorVm,
                engineVm = engineVm,
                onDownloadDistro = { safeNavigate(Screen.Main) },
                onStartDistro = { safeNavigate(Screen.EmulatorScreen) },
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

        composable(
            route = Screen.VmLogs,
            arguments = listOf(navArgument("vmId") { type = NavType.StringType })
        ) { backStackEntry ->
            val vmId = backStackEntry.arguments?.getString("vmId") ?: ""
            BackHandler { safePop() }

            QemuLogsScreen(
                vmId = vmId,
                viewModel = emulatorVm,
                onBack = { safePop() }
            )
        }

        composable(Screen.EmulatorScreen) {
            BackHandler { safePop() }
            EmulatorListScreen(
                onBack = { safePop() },
                vms = vmList,
                onAddEmulator = {
                    emulatorVm.setEditingVm(null)
                    safeNavigate(Screen.AddEmulator)
                },
                onStartVM = { selectedVm ->
                    emulatorVm.toggleVmState(selectedVm)
                },
                onDeleteVM = { vmId -> emulatorVm.deleteVm(vmId) },
                onEditVM = { vmToEdit ->
                    emulatorVm.loadVmForEditing(vmToEdit.id)
                    safeNavigate(Screen.EditEmulator)
                },
                onNavigateToEmulator = { vmId ->
                    safeNavigate("vncViewer/$vmId")
                }
            )
        }

        composable(
            route = Screen.VncViewer,
            arguments = listOf(navArgument("vmId") { type = NavType.StringType })
        ) { backStackEntry ->
            val vmId = backStackEntry.arguments?.getString("vmId") ?: ""

            BackHandler { safePop() }

            VmControlScreen(
                vmId = vmId,
                viewModel = emulatorVm,
                onNavigateToLogs = { safeNavigate("vmLogs/$vmId") },
                onBack = { safePop() }
            )
        }

        composable(Screen.AddEmulator) {
            BackHandler {
                emulatorVm.setEditingVm(null)
                safePop()
            }
            AddNewEmulatorScreen(
                viewModel = emulatorVm,
                onBack = {
                    emulatorVm.setEditingVm(null)
                    safePop()
                },
                onSave = { newVm ->
                    emulatorVm.saveVm(newVm)
                }
            )
        }

        composable(Screen.EditEmulator) {
            BackHandler {
                emulatorVm.setEditingVm(null)
                safePop()
            }
            EditEmulatorScreen(
                viewModel = emulatorVm,
                onBack = {
                    emulatorVm.setEditingVm(null)
                    safePop()
                },
                onSave = { updatedVm ->
                    emulatorVm.saveVm(updatedVm)
                }
            )
        }
    }
}