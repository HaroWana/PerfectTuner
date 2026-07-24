package com.thetuner.app.ui

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thetuner.app.billing.BillingRepository
import com.thetuner.app.data.SettingsRepository
import com.thetuner.app.tuner.TunerEngine
import com.thetuner.app.tuner.TunerState
import com.thetuner.app.tuner.TuningLibrary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TunerViewModel @Inject constructor(
    private val tunerEngine: TunerEngine,
    private val settingsRepository: SettingsRepository,
    private val billingRepository: BillingRepository
) : ViewModel() {

    val uiState: StateFlow<TunerState> = tunerEngine.state

    // Shared by the UI and the engine sync (onEach): one DataStore subscription
    // each, collected eagerly so the engine stays in sync without a visible UI.
    val a4Reference: StateFlow<Float> = settingsRepository.a4Reference
        .distinctUntilChanged()
        .onEach { tunerEngine.setA4Reference(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.DEFAULT_A4)

    val activeTuningId: StateFlow<String> = settingsRepository.activeTuningId
        .distinctUntilChanged()
        .onEach { tunerEngine.setTuning(TuningLibrary.findById(it)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "standard")

    val showToleranceMarkers: StateFlow<Boolean> = settingsRepository.showToleranceMarkers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hasPurchased: StateFlow<Boolean> = billingRepository.hasPurchased
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val billingError: SharedFlow<String> = billingRepository.billingError

    val purchaseCancelled: SharedFlow<Unit> = billingRepository.purchaseCancelled

    fun launchPurchase(activity: Activity) {
        viewModelScope.launch {
            billingRepository.launchPurchase(activity)
        }
    }

    fun restorePurchases() {
        billingRepository.queryPurchases()
    }

    fun startListening() = tunerEngine.startListening()
    fun stopListening() = tunerEngine.stopListening()

    fun selectTuning(tuningId: String) {
        viewModelScope.launch {
            settingsRepository.setActiveTuningId(tuningId)
        }
    }

    // Atomic in DataStore: computing from the stateIn-cached value loses taps
    fun incrementA4() = adjustA4(+1f)
    fun decrementA4() = adjustA4(-1f)

    private fun adjustA4(deltaHz: Float) {
        viewModelScope.launch {
            settingsRepository.adjustA4Reference(deltaHz)
        }
    }

    fun setShowToleranceMarkers(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowToleranceMarkers(show)
        }
    }
}
