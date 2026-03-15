package com.range.phoneLinuxer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.*
import com.range.phoneLinuxer.ui.screen.MainScreen
import com.range.phoneLinuxer.ui.screen.WelcomeScreen
import com.range.phoneLinuxer.viewModel.LinuxViewModel

@Composable
fun AppNavigation(vm: LinuxViewModel) {

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "welcome"
    ) {

        composable("welcome") {
            WelcomeScreen(navController)
        }

        composable("main") {
            MainScreen(vm)
        }
        composable("linux") {  }
    }
}