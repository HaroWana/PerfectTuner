package com.thetuner.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MicrophonePermissionGate(content: @Composable () -> Unit) {
    val permissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    // Auto-request once, not on every activity recreation: each auto-shown and
    // dismissed dialog counts toward Android's two-denial limit
    var autoRequested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!autoRequested) {
            autoRequested = true
            permissionState.launchPermissionRequest()
        }
    }

    // "Permanently denied" is only knowable as: rationale was shown at some
    // point (a denial happened) and is no longer shown. A fresh install has
    // never seen the rationale and must keep the re-request path.
    var deniedOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(permissionState.status) {
        if (permissionState.status.shouldShowRationale) deniedOnce = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Always render the tuner content
        content()

        // Overlay banner when permission is not granted
        if (!permissionState.status.isGranted) {
            val context = LocalContext.current
            val permanentlyDenied = deniedOnce && !permissionState.status.shouldShowRationale

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .clickable {
                        if (permanentlyDenied) {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                            context.startActivity(intent)
                        } else {
                            permissionState.launchPermissionRequest()
                        }
                    },
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = if (permanentlyDenied) {
                        "Microphone access required. Tap to open Settings."
                    } else {
                        "Microphone access required for tuning. Tap to allow."
                    },
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
