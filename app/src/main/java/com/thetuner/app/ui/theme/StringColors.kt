package com.thetuner.app.ui.theme

import androidx.compose.ui.graphics.Color

object StringColors {
    val palette: List<Color> = listOf(
        Color(0xFFFF1744), // Vivid red     — E2 (string 6)
        Color(0xFFFF6D00), // Deep orange   — A2 (string 5)
        Color(0xFFFFD600), // Rich yellow   — D3 (string 4)
        Color(0xFF69F0AE), // Spring green  — G3 (string 3) — lighter than inTuneGreen to avoid collision
        Color(0xFF2979FF), // Electric blue — B3 (string 2)
        Color(0xFFD500F9)  // Vivid violet  — E4 (string 1)
    )

    val inTuneGreen = Color(0xFF00E676)   // unchanged
    val neutralColor = Color(0xFF616161)  // unchanged
}
