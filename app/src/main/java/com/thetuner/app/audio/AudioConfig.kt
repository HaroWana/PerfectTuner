package com.thetuner.app.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.MediaRecorder

object AudioConfig {
    const val SAMPLE_RATE = 44100
    const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    const val ANALYSIS_BUFFER_SIZE = 4096 // ~93ms at 44100 Hz, ~3.8 cycles of E2 (82.4 Hz)

    fun getPreferredAudioSource(context: Context): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val supportsUnprocessed = audioManager.getProperty(
            AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED
        )
        return when {
            supportsUnprocessed?.toBoolean() == true ->
                MediaRecorder.AudioSource.UNPROCESSED
            else ->
                MediaRecorder.AudioSource.VOICE_RECOGNITION
        }
    }
}
