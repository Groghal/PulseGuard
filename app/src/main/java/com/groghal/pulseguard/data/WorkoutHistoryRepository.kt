package com.groghal.pulseguard.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class WorkoutHistoryItem(
    val endedAtEpochMillis: Long,
    val typeLabel: String,
    val durationSeconds: Long,
    val avgHeartRate: Double
)

class WorkoutHistoryRepository(private val context: Context) {
    private val HISTORY_KEY = stringSetPreferencesKey("workout_history_v1")
    private val SENT_IDS_KEY = stringSetPreferencesKey("workout_history_sent_ids_v1")
    private val FAILED_IDS_KEY = stringSetPreferencesKey("workout_history_failed_ids_v1")
    private val LAST_SYNC_ERROR_KEY = stringPreferencesKey("workout_history_last_sync_error_v1")

    val history: Flow<List<WorkoutHistoryItem>> = context.dataStore.data.map { prefs ->
        val raw = prefs[HISTORY_KEY].orEmpty()
        raw.mapNotNull(::decode)
            .sortedByDescending { it.endedAtEpochMillis }
    }

    val sentIds: Flow<Set<Long>> = context.dataStore.data.map { prefs ->
        prefs[SENT_IDS_KEY].orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
    }

    val failedIds: Flow<Set<Long>> = context.dataStore.data.map { prefs ->
        prefs[FAILED_IDS_KEY].orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
    }

    val lastSyncError: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[LAST_SYNC_ERROR_KEY]
    }

    suspend fun add(item: WorkoutHistoryItem) {
        val encoded = encode(item)
        context.dataStore.edit { prefs ->
            val current = prefs[HISTORY_KEY].orEmpty()
            prefs[HISTORY_KEY] = current + encoded
        }
    }

    suspend fun markSent(endedAtEpochMillis: Long) {
        context.dataStore.edit { prefs ->
            val sent = prefs[SENT_IDS_KEY].orEmpty()
            prefs[SENT_IDS_KEY] = sent + endedAtEpochMillis.toString()

            val failed = prefs[FAILED_IDS_KEY].orEmpty()
            if (failed.isNotEmpty()) {
                prefs[FAILED_IDS_KEY] = failed - endedAtEpochMillis.toString()
            }
            prefs.remove(LAST_SYNC_ERROR_KEY)
        }
    }

    suspend fun markFailed(endedAtEpochMillis: Long, error: String) {
        context.dataStore.edit { prefs ->
            val failed = prefs[FAILED_IDS_KEY].orEmpty()
            prefs[FAILED_IDS_KEY] = failed + endedAtEpochMillis.toString()
            prefs[LAST_SYNC_ERROR_KEY] = error
        }
    }

    suspend fun clear() {
        context.dataStore.edit { prefs ->
            prefs.remove(HISTORY_KEY)
            prefs.remove(SENT_IDS_KEY)
            prefs.remove(FAILED_IDS_KEY)
            prefs.remove(LAST_SYNC_ERROR_KEY)
        }
    }

    private fun encode(item: WorkoutHistoryItem): String {
        // Stable, dependency-free format: endedAt|typeLabel|durationSeconds|avgHeartRate
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


