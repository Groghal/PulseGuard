package com.groghal.pulseguard.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class HrThresholdRepository(private val context: Context) {
    private val HR_THRESHOLD_KEY = intPreferencesKey("hr_threshold")

    val hrThreshold: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[HR_THRESHOLD_KEY] ?: 100 // Default to 100
        }

    suspend fun setHrThreshold(threshold: Int) {
        context.dataStore.edit { preferences ->
            preferences[HR_THRESHOLD_KEY] = threshold
        }
    }
}
