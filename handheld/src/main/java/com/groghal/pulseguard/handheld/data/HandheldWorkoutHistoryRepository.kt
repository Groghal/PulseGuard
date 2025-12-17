package com.groghal.pulseguard.handheld.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class WorkoutHistoryItem(
    val endedAtEpochMillis: Long,
    val typeLabel: String,
    val durationSeconds: Long,
    val avgHeartRate: Double
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "handheld_history")

class HandheldWorkoutHistoryRepository(private val context: Context) {
    private val HISTORY_KEY = stringSetPreferencesKey("workout_history_v1")
    private val LAST_RECEIVED_EPOCH_MILLIS_KEY = longPreferencesKey("last_received_epoch_millis")

    val history: Flow<List<WorkoutHistoryItem>> = context.dataStore.data.map { prefs ->
        val raw = prefs[HISTORY_KEY].orEmpty()
        raw.mapNotNull(::decode)
            .sortedByDescending { it.endedAtEpochMillis }
    }

    val lastReceivedEpochMillis: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[LAST_RECEIVED_EPOCH_MILLIS_KEY]
    }

    suspend fun upsert(item: WorkoutHistoryItem) {
        val encoded = encode(item)
        context.dataStore.edit { prefs ->
            val current = prefs[HISTORY_KEY].orEmpty()
            val withoutSame = current.filterNot { it.startsWith("${item.endedAtEpochMillis}|") }.toSet()
            prefs[HISTORY_KEY] = withoutSame + encoded
            prefs[LAST_RECEIVED_EPOCH_MILLIS_KEY] = System.currentTimeMillis()
        }
    }

    suspend fun clear() {
        context.dataStore.edit { prefs ->
            prefs.remove(HISTORY_KEY)
            prefs.remove(LAST_RECEIVED_EPOCH_MILLIS_KEY)
        }
    }

    private fun encode(item: WorkoutHistoryItem): String {
        return buildString {
            append(item.endedAtEpochMillis)
            append("|")
            append(item.typeLabel.replace("|", " "))
            append("|")
            append(item.durationSeconds)
            append("|")
            append(item.avgHeartRate)
        }
    }

    private fun decode(raw: String): WorkoutHistoryItem? {
        val parts = raw.split("|")
        if (parts.size != 4) return null
        val endedAt = parts[0].toLongOrNull() ?: return null
        val typeLabel = parts[1]
        val durationSeconds = parts[2].toLongOrNull() ?: return null
        val avgHeartRate = parts[3].toDoubleOrNull() ?: return null
        return WorkoutHistoryItem(
            endedAtEpochMillis = endedAt,
            typeLabel = typeLabel,
            durationSeconds = durationSeconds,
            avgHeartRate = avgHeartRate
        )
    }
}


