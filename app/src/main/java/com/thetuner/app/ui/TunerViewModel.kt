package com.thetuner.app.ui

import androidx.lifecycle.ViewModel
import com.thetuner.app.tuner.TunerEngine
import com.thetuner.app.tuner.TunerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class TunerViewModel @Inject constructor(
    private val tunerEngine: TunerEngine
) : ViewModel() {

    val uiState: StateFlow<TunerState> = tunerEngine.state

    fun startListening() {
        tunerEngine.startListening()
    }

    fun stopListening() {
        tunerEngine.stopListening()
    }
}
