package com.thetuner.app.audio

import kotlinx.coroutines.flow.Flow

interface AudioSource {
    fun frames(): Flow<FloatArray>
    fun start()
    fun stop()
}
