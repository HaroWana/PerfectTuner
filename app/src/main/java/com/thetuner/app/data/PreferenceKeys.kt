package com.thetuner.app.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object PreferenceKeys {
    val A4_REFERENCE = floatPreferencesKey("a4_reference")
    val ACTIVE_TUNING_ID = stringPreferencesKey("active_tuning_id")
    val SHOW_TOLERANCE_MARKERS = booleanPreferencesKey("show_tolerance_markers")
}
