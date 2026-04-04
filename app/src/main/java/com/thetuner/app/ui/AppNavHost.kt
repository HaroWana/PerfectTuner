package com.thetuner.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    viewModel: TunerViewModel = hiltViewModel()
) {
    NavHost(navController = navController, startDestination = "tuner") {
        composable("tuner") {
            TunerScreen(
                viewModel = viewModel,
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }
        composable("settings") {
            val a4Reference by viewModel.a4Reference.collectAsStateWithLifecycle()
            val showToleranceMarkers by viewModel.showToleranceMarkers.collectAsStateWithLifecycle()
            SettingsScreen(
                a4Reference = a4Reference,
                showToleranceMarkers = showToleranceMarkers,
                onA4Increment = viewModel::incrementA4,
                onA4Decrement = viewModel::decrementA4,
                onToleranceMarkersChanged = viewModel::setShowToleranceMarkers,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
