package com.thetuner.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thetuner.app.tuner.STANDARD_TUNING
import com.thetuner.app.tuner.TuningLibrary
import com.thetuner.app.ui.theme.StringColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunerScreen(
    viewModel: TunerViewModel,
    hasPurchased: Boolean,
    onNavigateToSettings: () -> Unit,
    onLockedTuningTap: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activeTuningId by viewModel.activeTuningId.collectAsStateWithLifecycle()
    val showToleranceMarkers by viewModel.showToleranceMarkers.collectAsStateWithLifecycle()

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

    val activeTuning = TuningLibrary.findById(activeTuningId)
    // Chromatic mode has no strings; the overlay lights the nearest Standard
    // string (existing engine behavior), so label with Standard's note names.
    val labelTuning = if (activeTuning.strings.isEmpty()) STANDARD_TUNING else activeTuning
    val stringLabels = remember(labelTuning.id) { labelTuning.strings.map { it.noteName } }

    val fabLabel = activeTuning.shortName ?: activeTuning.name.take(6)

    var showBottomSheet by remember { mutableStateOf(false) }
    // skipPartiallyExpanded = true: prevents sheet dismissal conflict when scrolling LazyColumn
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Play the slide-out animation before removing the sheet from composition
    val dismissSheet: (afterHide: () -> Unit) -> Unit = { afterHide ->
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            showBottomSheet = false
            afterHide()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PitchTrace(
                centsOffset = centsOffset,
                isInTune = isInTune,
                isSilent = isSilent,
                detectedStringIndex = detectedStringIndex,
                stringColors = StringColors.palette,
                inTuneColor = StringColors.inTuneGreen,
                neutralColor = StringColors.neutralColor,
                tuningId = activeTuningId,
                showToleranceMarkers = showToleranceMarkers,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.55f)
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            )
            StringsOverlay(
                detectedStringIndex = detectedStringIndex,
                stringColors = stringColors,
                isInTune = isInTune,
                inTuneColor = StringColors.inTuneGreen,
                stringLabels = stringLabels,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.45f)
            )
        }

        AssistChip(
            onClick = { showBottomSheet = true },
            label = {
                Text(
                    text = fabLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.9f)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                    tint = Color.White.copy(alpha = 0.7f)
                )
            },
            shape = RoundedCornerShape(50),
            colors = AssistChipDefaults.assistChipColors(
                containerColor = Color(0xFF2C2C2C)
            ),
            border = null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 16.dp, top = 8.dp)
                .windowInsetsPadding(WindowInsets.statusBars)
        )

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                containerColor = Color(0xFF1E1E1E)
            ) {
                TuningPickerSheet(
                    activeTuningId = activeTuningId,
                    hasPurchased = hasPurchased,
                    onTuningSelected = { id ->
                        viewModel.selectTuning(id)
                        dismissSheet {}
                    },
                    onLockedTuningTap = { tuningId ->
                        dismissSheet { onLockedTuningTap(tuningId) }
                    },
                    onNavigateToSettings = {
                        dismissSheet { onNavigateToSettings() }
                    }
                )
            }
        }
    }
}
