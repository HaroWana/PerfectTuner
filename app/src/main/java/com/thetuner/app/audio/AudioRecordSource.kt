package com.thetuner.app.audio

import android.content.Context
import android.media.AudioRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import javax.inject.Inject

class AudioRecordSource @Inject constructor(
    @ApplicationContext private val context: Context
) : AudioSource {

    private val shortBuffer = ShortArray(AudioConfig.ANALYSIS_BUFFER_SIZE)
    private val floatBuffer = FloatArray(AudioConfig.ANALYSIS_BUFFER_SIZE)

    override fun frames(): Flow<FloatArray> = callbackFlow {
        val minBufferSize = AudioRecord.getMinBufferSize(
            AudioConfig.SAMPLE_RATE,
            AudioConfig.CHANNEL_CONFIG,
            AudioConfig.AUDIO_FORMAT
        )
        if (minBufferSize <= 0) {
            close(IllegalStateException("AudioRecord params unsupported (minBufferSize=$minBufferSize)"))
            return@callbackFlow
        }
        val bufferSize = maxOf(minBufferSize * 2, AudioConfig.ANALYSIS_BUFFER_SIZE * 2)

        val record = AudioRecord(
            AudioConfig.getPreferredAudioSource(context),
            AudioConfig.SAMPLE_RATE,
            AudioConfig.CHANNEL_CONFIG,
            AudioConfig.AUDIO_FORMAT,
            bufferSize * Short.SIZE_BYTES
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            close(IllegalStateException("AudioRecord failed to initialize"))
            return@callbackFlow
        }

        record.startRecording()
        try {
            while (isActive) {
                val readCount = record.read(shortBuffer, 0, shortBuffer.size)
                if (readCount > 0) {
                    // Normalize PCM_16BIT shorts to [-1.0, 1.0] floats for the pitch detector
                    for (i in 0 until readCount) {
                        floatBuffer[i] = shortBuffer[i] / 32768f
                    }
                    send(floatBuffer.copyOf(readCount))
                }
            }
        } finally {
            record.stop()
            record.release()
        }

        awaitClose()
    }.flowOn(Dispatchers.IO)

    override fun start() { /* lifecycle managed by flow collection */ }
    override fun stop() { /* cancel the flow's coroutine scope */ }
}
