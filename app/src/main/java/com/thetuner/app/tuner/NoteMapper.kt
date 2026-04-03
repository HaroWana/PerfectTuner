package com.thetuner.app.tuner

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

object NoteMapper {

    private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private const val A4_FREQUENCY = 440.0f
    private const val A4_MIDI = 69

    data class Note(
        val name: String,
        val octave: Int,
        val midiNumber: Int,
        val frequency: Float // exact frequency for this MIDI note at the given A4 reference
    )

    fun frequencyToNote(frequencyHz: Float, a4Reference: Float = A4_FREQUENCY): Note {
        // MIDI number from frequency: 69 + 12 * log2(freq / A4)
        val midiFloat = A4_MIDI + 12.0 * ln(frequencyHz / a4Reference.toDouble()) / ln(2.0)
        val midiNumber = midiFloat.roundToInt()

        val noteName = NOTE_NAMES[((midiNumber % 12) + 12) % 12]
        val octave = (midiNumber / 12) - 1 // MIDI octave convention

        // Exact frequency for this MIDI note
        val exactFrequency = a4Reference * 2f.pow((midiNumber - A4_MIDI) / 12f)

        return Note(
            name = noteName,
            octave = octave,
            midiNumber = midiNumber,
            frequency = exactFrequency
        )
    }

    fun centsBetween(detected: Float, target: Float): Float {
        // Positive = sharp, negative = flat
        return (1200.0 * ln(detected / target.toDouble()) / ln(2.0)).toFloat()
    }
}
