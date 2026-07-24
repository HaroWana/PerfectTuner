package com.thetuner.app.audio

import android.content.Context
import android.media.AudioRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import javax.inject.Inject

class AudioRecordSource @Inject constructor(
    @ApplicationContext private val context: Context
) : AudioSource {

    override fun frames(): Flow<FloatArray> = callbackFlow {
        // Buffers are per-collection: a stop/start restart can briefly overlap
        // two read loops (the blocking read() does not respond to cancellation),
        // so they must not be shared instance state.
        val shortBuffer = ShortArray(AudioConfig.ANALYSIS_BUFFER_SIZE)
        val floatBuffer = FloatArray(AudioConfig.ANALYSIS_BUFFER_SIZE)

        val minBufferSizeBytes = AudioRecord.getMinBufferSize(
            AudioConfig.SAMPLE_RATE,
            AudioConfig.CHANNEL_CONFIG,
            AudioConfig.AUDIO_FORMAT
        )
        if (minBufferSizeBytes <= 0) {
            close(IllegalStateException("AudioRecord params unsupported (minBufferSize=$minBufferSizeBytes)"))
            return@callbackFlow
        }
        val minBufferSizeShorts = minBufferSizeBytes / Short.SIZE_BYTES
        val bufferSizeShorts = maxOf(minBufferSizeShorts, AudioConfig.ANALYSIS_BUFFER_SIZE) * 2

        val record = AudioRecord(
            AudioConfig.getPreferredAudioSource(context),
            AudioConfig.SAMPLE_RATE,
            AudioConfig.CHANNEL_CONFIG,
            AudioConfig.AUDIO_FORMAT,
            bufferSizeShorts * Short.SIZE_BYTES
        )

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            close(IllegalStateException("AudioRecord failed to initialize"))
            return@callbackFlow
        }

        try {
            record.startRecording()
            while (isActive) {
                val readCount = record.read(shortBuffer, 0, shortBuffer.size)
                if (readCount > 0) {
                    // Normalize PCM_16BIT shorts to [-1.0, 1.0] floats for the pitch detector
                    for (i in 0 until readCount) {
                        floatBuffer[i] = shortBuffer[i] / 32768f
                    }
                    send(floatBuffer.copyOf(readCount))
                } else if (readCount < 0) {
                    // ERROR_DEAD_OBJECT, ERROR_INVALID_OPERATION, ... — read() returns
                    // immediately on these, so looping would busy-spin a core forever.
                    close(IllegalStateException("AudioRecord.read failed (code=$readCount)"))
                    break
                }
            }
        } finally {
            // stop() throws if startRecording() itself failed; release() must still run
            runCatching { record.stop() }
            record.release()
        }

        awaitClose()
    }.conflate().flowOn(Dispatchers.IO)
}
