package com.thetuner.app.di

import com.thetuner.app.audio.AudioRecordSource
import com.thetuner.app.audio.AudioSource
import com.thetuner.app.detection.PitchDetector
import com.thetuner.app.detection.YinPitchDetector
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {

    @Binds
    @Singleton
    abstract fun bindAudioSource(impl: AudioRecordSource): AudioSource

    companion object {
        @Provides
        @Singleton
        fun providePitchDetector(): PitchDetector {
            return YinPitchDetector(
                sampleRate = 44100,
                threshold = 0.15f,
                bufferSize = 4096
            )
        }
    }
}
