package com.thetuner.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.collectLatest

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    viewModel: TunerViewModel = hiltViewModel()
) {
    val hasPurchased by viewModel.hasPurchased.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Saveable: the Play purchase sheet triggers a stop/recreate cycle, and the
    // promised auto-select must survive it
    var pendingUnlockTuningId by rememberSaveable { mutableStateOf<String?>(null) }

    // Re-query purchases on every resume (Play Billing recommendation): covers
    // purchases completed while the app was dead and failed initial setups
    LifecycleResumeEffect(Unit) {
        viewModel.restorePurchases()
        onPauseOrDispose { }
    }

    // Auto-select pending tuning once hasPurchased flips to true
    LaunchedEffect(hasPurchased) {
        if (hasPurchased) {
            pendingUnlockTuningId?.let { viewModel.selectTuning(it) }
            pendingUnlockTuningId = null
        }
    }

    // A cancelled or failed purchase must drop the pending auto-select, or a
    // later unrelated unlock (e.g. Restore Purchases) would switch tunings
    LaunchedEffect(Unit) {
        viewModel.purchaseCancelled.collectLatest { pendingUnlockTuningId = null }
    }

    // Show toast on billing errors
    LaunchedEffect(Unit) {
        viewModel.billingError.collectLatest { message ->
            pendingUnlockTuningId = null
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
                    context.findActivity()?.let { activity ->
                        pendingUnlockTuningId = tuningId
                        viewModel.launchPurchase(activity)
                    }
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
