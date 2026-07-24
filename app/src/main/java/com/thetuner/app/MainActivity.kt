package com.thetuner.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.thetuner.app.ui.AppNavHost
import com.thetuner.app.ui.MicrophonePermissionGate
import com.thetuner.app.ui.theme.StringColors
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // The app is dark-only; without this, Material components that read
            // colorScheme (switches, dialog buttons) rendered light-theme
            // accents on the hardcoded dark backgrounds
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = StringColors.inTuneGreen,
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E)
                )
            ) {
                MicrophonePermissionGate {
                    AppNavHost()
                }
            }
        }
    }
}
