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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thetuner.app.tuner.TuningLibrary
import com.thetuner.app.ui.theme.StringColors
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunerScreen(
    viewModel: TunerViewModel,
    onNavigateToSettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activeTuningId by viewModel.activeTuningId.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.startListening()
        onPauseOrDispose {
            viewModel.stopListening()
        }
    }

    val centsOffset = state.centsOffset
    val detectedStringIndex = state.detectedStringIndex
    val isInTune = state.isInTune
    val isSilent = state.isSilent
    val noteName = state.noteName
    val octave = state.octave

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

    val stringColors = remember(detectedStringIndex, detectedStringColor) {
        StringColors.palette.mapIndexed { index, color ->
            if (index == detectedStringIndex) detectedStringColor else color
        }
    }

    // FAB label: reflect active tuning name, truncated for display
    val activeTuning = TuningLibrary.findById(activeTuningId)
    val fabLabel = when (activeTuning.id) {
        "chromatic" -> "Chr"
        "standard" -> "Std"
        "eb_standard" -> "Eb"
        "d_standard" -> "D Std"
        "drop_d" -> "Drop D"
        "drop_c" -> "Drop C"
        else -> activeTuning.name.take(6)
    }

    var showBottomSheet by remember { mutableStateOf(false) }
    // skipPartiallyExpanded = true: prevents sheet dismissal conflict when scrolling LazyColumn
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        StringsOverlay(
            detectedStringIndex = detectedStringIndex,
            stringColors = stringColors,
            isInTune = isInTune,
            inTuneColor = StringColors.inTuneGreen,
            modifier = Modifier.fillMaxSize()
        )

        StrobeRing(
            centsOffset = if (isSilent) 0f else centsOffset,
            ringColor = ringColor,
            modifier = Modifier
                .align(Alignment.Center)
                .size(320.dp)
        )

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isSilent || noteName == null) "--" else "$noteName${octave ?: ""}",
                style = MaterialTheme.typography.displayLarge,
                color = Color.White
            )
            Text(
                text = if (isSilent || noteName == null) "--" else {
                    val rounded = centsOffset.roundToInt()
                    val prefix = if (rounded > 0) "+" else ""
                    "${prefix}${rounded}c"
                },
                style = MaterialTheme.typography.titleLarge,
                color = Color.White.copy(alpha = 0.7f)
            )
        }

        FloatingActionButton(
            onClick = { showBottomSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = Color(0xFF2C2C2C)
        ) {
            Text(
                text = fabLabel,
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelMedium
            )
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                containerColor = Color(0xFF1E1E1E)
            ) {
                TuningPickerSheet(
                    activeTuningId = activeTuningId,
                    onTuningSelected = { id ->
                        viewModel.selectTuning(id)
                        showBottomSheet = false
                    },
                    onNavigateToSettings = {
                        showBottomSheet = false
                        onNavigateToSettings()
                    }
                )
            }
        }
    }
}
