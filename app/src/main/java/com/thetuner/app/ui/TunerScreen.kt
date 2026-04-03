package com.thetuner.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun TunerScreen(viewModel: TunerViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.startListening()
        onPauseOrDispose {
            viewModel.stopListening()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Primary display: note name + octave
        Text(
            text = if (state.noteName != null) {
                "${state.noteName}${state.octave ?: ""}"
            } else {
                "--"
            },
            style = MaterialTheme.typography.displayLarge
        )

        // Secondary display: frequency
        Text(
            text = if (state.isSilent) {
                "0.0 Hz"
            } else {
                "%.1f Hz".format(state.frequencyHz)
            },
            style = MaterialTheme.typography.headlineMedium
        )
    }
}
