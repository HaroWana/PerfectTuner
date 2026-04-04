package com.thetuner.app.tuner

import kotlin.math.pow

data class GuitarString(
    val index: Int,
    val noteName: String,
    val octave: Int,
    val midiNumber: Int
)

data class GuitarTuning(
    val id: String,
    val name: String,
    val strings: List<GuitarString>,
    val description: String = ""
) {
    fun frequencies(a4Reference: Float = 440f): List<Float> {
        return strings.map { string ->
            a4Reference * 2f.pow((string.midiNumber - 69) / 12f)
        }
    }
}

val STANDARD_TUNING = GuitarTuning(
    id = "standard",
    name = "Standard",
    strings = listOf(
        GuitarString(index = 0, noteName = "E", octave = 2, midiNumber = 40),
        GuitarString(index = 1, noteName = "A", octave = 2, midiNumber = 45),
        GuitarString(index = 2, noteName = "D", octave = 3, midiNumber = 50),
        GuitarString(index = 3, noteName = "G", octave = 3, midiNumber = 55),
        GuitarString(index = 4, noteName = "B", octave = 3, midiNumber = 59),
        GuitarString(index = 5, noteName = "E", octave = 4, midiNumber = 64)
    )
)
