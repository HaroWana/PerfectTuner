package com.thetuner.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    // DataStore throws IOException on disk read failures; fall back to defaults
    // instead of crashing the collecting scope
    private val safeData: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val a4Reference: Flow<Float> = safeData.map { prefs ->
        prefs[PreferenceKeys.A4_REFERENCE] ?: DEFAULT_A4
    }

    val activeTuningId: Flow<String> = safeData.map { prefs ->
        prefs[PreferenceKeys.ACTIVE_TUNING_ID] ?: "standard"
    }

    val showToleranceMarkers: Flow<Boolean> = safeData.map { prefs ->
        prefs[PreferenceKeys.SHOW_TOLERANCE_MARKERS] ?: false
    }

    /**
     * Atomic read-modify-write: callers must not compute the new value from a
     * UI-cached read, or rapid taps collapse into a single step.
     */
    suspend fun adjustA4Reference(deltaHz: Float) {
        dataStore.edit { prefs ->
            val current = prefs[PreferenceKeys.A4_REFERENCE] ?: DEFAULT_A4
            prefs[PreferenceKeys.A4_REFERENCE] = (current + deltaHz).coerceIn(A4_MIN, A4_MAX)
        }
    }

    suspend fun setActiveTuningId(id: String) {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.ACTIVE_TUNING_ID] = id
        }
    }

    suspend fun setShowToleranceMarkers(show: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.SHOW_TOLERANCE_MARKERS] = show
        }
    }

    companion object {
        const val DEFAULT_A4 = 440f
        const val A4_MIN = 430f
        const val A4_MAX = 450f
    }
}
