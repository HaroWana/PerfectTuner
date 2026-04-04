package com.thetuner.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    val a4Reference: Flow<Float> = dataStore.data.map { prefs ->
        prefs[PreferenceKeys.A4_REFERENCE] ?: 440f
    }

    val activeTuningId: Flow<String> = dataStore.data.map { prefs ->
        prefs[PreferenceKeys.ACTIVE_TUNING_ID] ?: "standard"
    }

    val showToleranceMarkers: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PreferenceKeys.SHOW_TOLERANCE_MARKERS] ?: false
    }

    suspend fun setA4Reference(hz: Float) {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.A4_REFERENCE] = hz.coerceIn(430f, 450f)
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
}
