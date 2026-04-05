package com.thetuner.app.ui

import android.app.Activity
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    viewModel: TunerViewModel = hiltViewModel()
) {
    val hasPurchased by viewModel.hasPurchased.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingUnlockTuningId by remember { mutableStateOf<String?>(null) }

    // Auto-select pending tuning once hasPurchased flips to true
    LaunchedEffect(hasPurchased) {
        if (hasPurchased) {
            pendingUnlockTuningId?.let { viewModel.selectTuning(it) }
            pendingUnlockTuningId = null
        }
    }

    // Show toast on billing errors
    LaunchedEffect(Unit) {
        viewModel.billingError.collectLatest { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    NavHost(navController = navController, startDestination = "tuner") {
        composable("tuner") {
            TunerScreen(
                viewModel = viewModel,
                hasPurchased = hasPurchased,
                onNavigateToSettings = { navController.navigate("settings") },
                onLockedTuningTap = { tuningId ->
                    pendingUnlockTuningId = tuningId
                    viewModel.launchPurchase(context as Activity)
                }
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
                onRestorePurchases = viewModel::restorePurchases,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
