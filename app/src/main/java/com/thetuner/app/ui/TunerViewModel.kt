package com.thetuner.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thetuner.app.data.SettingsRepository
import com.thetuner.app.tuner.TunerEngine
import com.thetuner.app.tuner.TunerState
import com.thetuner.app.tuner.TuningLibrary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TunerViewModel @Inject constructor(
    private val tunerEngine: TunerEngine,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<TunerState> = tunerEngine.state

    val a4Reference: StateFlow<Float> = settingsRepository.a4Reference
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 440f)

    val activeTuningId: StateFlow<String> = settingsRepository.activeTuningId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "standard")

    val showToleranceMarkers: StateFlow<Boolean> = settingsRepository.showToleranceMarkers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        // Collect persisted tuning ID and keep TunerEngine in sync
        viewModelScope.launch {
            settingsRepository.activeTuningId.collect { id ->
                tunerEngine.setTuning(TuningLibrary.findById(id))
            }
        }
        // Collect persisted A4 reference and keep TunerEngine in sync
        viewModelScope.launch {
            settingsRepository.a4Reference.collect { hz ->
                tunerEngine.setA4Reference(hz)
            }
        }
    }

    fun startListening() = tunerEngine.startListening()
    fun stopListening() = tunerEngine.stopListening()

    fun selectTuning(tuningId: String) {
        viewModelScope.launch {
            settingsRepository.setActiveTuningId(tuningId)
        }
    }

    fun setA4Reference(hz: Float) {
        viewModelScope.launch {
            settingsRepository.setA4Reference(hz)
        }
    }

    fun incrementA4() = setA4Reference(a4Reference.value + 1f)
    fun decrementA4() = setA4Reference(a4Reference.value - 1f)

    fun setShowToleranceMarkers(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowToleranceMarkers(show)
        }
    }
}
