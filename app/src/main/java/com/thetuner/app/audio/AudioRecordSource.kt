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

    private val buffer = FloatArray(AudioConfig.ANALYSIS_BUFFER_SIZE)

    override fun frames(): Flow<FloatArray> = callbackFlow {
        val minBufferSize = AudioRecord.getMinBufferSize(
            AudioConfig.SAMPLE_RATE,
            AudioConfig.CHANNEL_CONFIG,
            AudioConfig.AUDIO_FORMAT
        )
        val bufferSize = maxOf(minBufferSize * 2, AudioConfig.ANALYSIS_BUFFER_SIZE * 4)

        val record = AudioRecord(
            AudioConfig.getPreferredAudioSource(context),
            AudioConfig.SAMPLE_RATE,
            AudioConfig.CHANNEL_CONFIG,
            AudioConfig.AUDIO_FORMAT,
            bufferSize * Float.SIZE_BYTES
        )

        record.startRecording()
        try {
            while (isActive) {
                val readCount = record.read(
                    buffer, 0, buffer.size, AudioRecord.READ_BLOCKING
                )
                if (readCount > 0) {
                    send(buffer.copyOf(readCount))
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
