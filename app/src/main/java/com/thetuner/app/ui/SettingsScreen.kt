package com.thetuner.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    a4Reference: Float,
    showToleranceMarkers: Boolean,
    onA4Increment: () -> Unit,
    onA4Decrement: () -> Unit,
    onToleranceMarkersChanged: (Boolean) -> Unit,
    onRestorePurchases: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

        // A4 Reference section
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
            Text(
                text = "Reference Pitch (A4)",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally)
            ) {
                IconButton(
                    onClick = onA4Decrement,
                    enabled = a4Reference > 430f
                ) {
                    Text(
                        text = "\u2212",
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (a4Reference > 430f) Color.White else Color.White.copy(alpha = 0.3f)
                    )
                }
                Text(
                    text = "${a4Reference.roundToInt()} Hz",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                IconButton(
                    onClick = onA4Increment,
                    enabled = a4Reference < 450f
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Increase A4",
                        tint = if (a4Reference < 450f) Color.White else Color.White.copy(alpha = 0.3f)
                    )
                }
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

        // Tolerance markers toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Show tolerance markers",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                Text(
                    text = "Display \u00b15\u00a2 zone indicators on the pitch trace",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
            Switch(
                checked = showToleranceMarkers,
                onCheckedChange = onToleranceMarkersChanged,
                modifier = Modifier.padding(start = 16.dp)
            )
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

        // Restore Purchases row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onRestorePurchases() }
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Restore Purchases",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                Text(
                    text = "Re-sync your purchase if it's not showing",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}
