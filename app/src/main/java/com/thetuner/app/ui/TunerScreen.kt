package com.thetuner.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thetuner.app.ui.theme.StringColors
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunerScreen(viewModel: TunerViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.startListening()
        onPauseOrDispose {
            viewModel.stopListening()
        }
    }

    // Extract state for readability
    val centsOffset = state.centsOffset
    val detectedStringIndex = state.detectedStringIndex
    val isInTune = state.isInTune
    val isSilent = state.isSilent
    val noteName = state.noteName
    val octave = state.octave

    // Color logic with animated transitions (200ms tween)
    val targetRingColor = when {
        detectedStringIndex != null && isInTune -> StringColors.inTuneGreen
        detectedStringIndex != null -> StringColors.palette[detectedStringIndex]
        else -> StringColors.neutralColor
    }
    val ringColor by animateColorAsState(
        targetValue = targetRingColor,
        animationSpec = tween(durationMillis = 200),
        label = "ringColor"
    )

    // Animated string color for the detected string
    val targetDetectedStringColor = when {
        detectedStringIndex != null && isInTune -> StringColors.inTuneGreen
        detectedStringIndex != null -> StringColors.palette[detectedStringIndex]
        else -> StringColors.neutralColor
    }
    val detectedStringColor by animateColorAsState(
        targetValue = targetDetectedStringColor,
        animationSpec = tween(durationMillis = 200),
        label = "detectedStringColor"
    )

    // Build string colors list with animated detected string color
    val stringColors = remember(detectedStringIndex, detectedStringColor) {
        StringColors.palette.mapIndexed { index, color ->
            if (index == detectedStringIndex) detectedStringColor else color
        }
    }

    // Bottom sheet state
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // Layer 1: Perspective guitar strings (behind everything)
        StringsOverlay(
            detectedStringIndex = detectedStringIndex,
            stringColors = stringColors,
            isInTune = isInTune,
            inTuneColor = StringColors.inTuneGreen,
            modifier = Modifier.fillMaxSize()
        )

        // Layer 2: Strobe ring (centered)
        StrobeRing(
            centsOffset = if (isSilent) 0f else centsOffset,
            ringColor = ringColor,
            modifier = Modifier
                .align(Alignment.Center)
                .size(320.dp)
        )

        // Layer 3: Note and cent readout (centered inside ring)
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Note name + octave
            Text(
                text = if (isSilent || noteName == null) {
                    "--"
                } else {
                    "$noteName${octave ?: ""}"
                },
                style = MaterialTheme.typography.displayLarge,
                color = Color.White
            )

            // Cent offset
            Text(
                text = if (isSilent || noteName == null) {
                    "--"
                } else {
                    val rounded = centsOffset.roundToInt()
                    val prefix = if (rounded > 0) "+" else ""
                    "${prefix}${rounded}c"
                },
                style = MaterialTheme.typography.titleLarge,
                color = Color.White.copy(alpha = 0.7f)
            )
        }

        // Layer 4: FAB (bottom-end)
        FloatingActionButton(
            onClick = { showBottomSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = Color(0xFF2C2C2C)
        ) {
            Text(
                text = "Std",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelMedium
            )
        }

        // Bottom sheet for tuning selection (placeholder)
        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                containerColor = Color(0xFF1E1E1E)
            ) {
                Text(
                    text = "Standard",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(24.dp)
                )
            }
        }
    }
}
