package com.thetuner.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.thetuner.app.tuner.GuitarTuning
import com.thetuner.app.tuner.TuningLibrary

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TuningPickerSheet(
    activeTuningId: String,
    hasPurchased: Boolean,
    onTuningSelected: (String) -> Unit,
    onLockedTuningTap: (tuningId: String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    var showPurchaseDialog by remember { mutableStateOf(false) }
    var pendingTuningId by remember { mutableStateOf<String?>(null) }

    LazyColumn {
        // Sheet header row: title + gear icon
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tunings",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }

        TuningLibrary.sections.forEach { section ->
            // Sticky section header
            stickyHeader {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF252525))
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }

            items(section.tunings) { tuning ->
                TuningRow(
                    tuning = tuning,
                    isActive = tuning.id == activeTuningId,
                    isFree = TuningLibrary.isFree(tuning.id),
                    hasPurchased = hasPurchased,
                    onSelect = { onTuningSelected(tuning.id) },
                    onLockedTap = {
                        pendingTuningId = tuning.id
                        showPurchaseDialog = true
                    }
                )
            }
        }

        // Bottom padding so last item clears nav bar
        item {
            Box(modifier = Modifier.padding(bottom = 24.dp))
        }
    }

    if (showPurchaseDialog) {
        PurchaseConfirmationDialog(
            onConfirm = {
                showPurchaseDialog = false
                pendingTuningId?.let { onLockedTuningTap(it) }
                pendingTuningId = null
            },
            onDismiss = {
                showPurchaseDialog = false
                pendingTuningId = null
            }
        )
    }
}

@Composable
fun TuningRow(
    tuning: GuitarTuning,
    isActive: Boolean,
    isFree: Boolean,
    hasPurchased: Boolean,
    onSelect: () -> Unit,
    onLockedTap: () -> Unit
) {
    val isChromatic = tuning.id == "chromatic"
    val isUnlocked = isFree || isChromatic || hasPurchased
    val rowAlpha = if (isUnlocked) 1f else 0.45f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isActive) Color(0xFF2A2A2A) else Color.Transparent)
            .clickable(enabled = true) { if (isUnlocked) onSelect() else onLockedTap() }
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .alpha(rowAlpha),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Tuning name
        Text(
            text = tuning.name,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )

        // Note bubbles (6 strings, low to high)
        if (!isChromatic && tuning.strings.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                tuning.strings.forEach { string ->
                    NoteBubble(noteName = string.noteName)
                }
            }
        } else if (isChromatic) {
            // Chromatic: show "12" as a text indicator instead of note bubbles
            Text(
                text = "12",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        // Right indicator: checkmark for active, lock for locked (when not purchased), nothing otherwise
        Box(modifier = Modifier.padding(start = 8.dp).size(20.dp), contentAlignment = Alignment.Center) {
            when {
                isActive -> Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Active",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                !isFree && !isChromatic && !hasPurchased -> Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun PurchaseConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Unlock Full Library",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        },
        text = {
            Text(
                text = "35+ alternate and open tunings. One-time purchase, never expires.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Buy", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not now", color = Color.White.copy(alpha = 0.6f))
            }
        },
        containerColor = Color(0xFF2A2A2A)
    )
}

@Composable
fun NoteBubble(noteName: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(Color(0xFF3A3A3A)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = noteName,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.75f)
        )
    }
}
