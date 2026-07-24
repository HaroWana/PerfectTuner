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
            // Defaults come from AudioConfig — the detector must analyze at the
            // same rate and frame size AudioRecordSource captures with.
            return YinPitchDetector()
        }
    }
}
