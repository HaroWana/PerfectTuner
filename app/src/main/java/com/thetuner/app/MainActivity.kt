package com.thetuner.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.thetuner.app.ui.MicrophonePermissionGate
import com.thetuner.app.ui.TunerScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MicrophonePermissionGate {
                    TunerScreen()
                }
            }
        }
    }
}
