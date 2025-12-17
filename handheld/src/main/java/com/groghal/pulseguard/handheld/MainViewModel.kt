package com.groghal.pulseguard.handheld

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.groghal.pulseguard.handheld.data.HandheldWorkoutHistoryRepository
import com.groghal.pulseguard.handheld.data.WorkoutHistoryItem
import com.groghal.pulseguard.handheld.datalayer.DataLayerPaths
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

data class WearConnectionStatus(
    val connectedNodeCount: Int = 0,
    val lastError: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = HandheldWorkoutHistoryRepository(application)
    private val appContext = application.applicationContext
    private val dataClient = Wearable.getDataClient(appContext)
    private val nodeClient = Wearable.getNodeClient(appContext)

    private val _wearConnectionStatus = MutableStateFlow(WearConnectionStatus())
    val wearConnectionStatus: StateFlow<WearConnectionStatus> = _wearConnectionStatus.asStateFlow()

    val history: StateFlow<List<com.groghal.pulseguard.handheld.data.WorkoutHistoryItem>> =
        repo.history.stateIn(viewModelScope, WhileSubscribed(5_000), emptyList())

    val lastReceivedEpochMillis: StateFlow<Long?> =
        repo.lastReceivedEpochMillis.stateIn(viewModelScope, WhileSubscribed(5_000), null)

    init {
        refreshConnectionStatus()
    }

    fun clearHistory() {
        viewModelScope.launch { repo.clear() }
    }

    fun clearHistoryEverywhere() {
        // Delete Data Layer items first so they don't get re-imported after local clear.
        dataClient.dataItems
            .addOnSuccessListener { buffer ->
                val toDelete = mutableListOf<android.net.Uri>()
                try {
                    buffer.forEach { item ->
                        val path = item.uri.path ?: return@forEach
                        if (path.startsWith(DataLayerPaths.WORKOUT_SUMMARY_PREFIX)) {
                            toDelete.add(item.uri)
                        }
                    }
                } catch (t: Throwable) {
                    Log.e("PulseGuard.Handheld", "Failed enumerating DataItems for clear", t)
                    _wearConnectionStatus.value = _wearConnectionStatus.value.copy(lastError = friendlyWearableError(t))
                } finally {
                    buffer.release()
                }

                if (toDelete.isEmpty()) {
                    viewModelScope.launch { repo.clear() }
                    return@addOnSuccessListener
                }

                val remaining = AtomicInteger(toDelete.size)
                toDelete.forEach { uri ->
                    dataClient.deleteDataItems(uri)
                        .addOnCompleteListener {
                            if (remaining.decrementAndGet() == 0) {
                                viewModelScope.launch { repo.clear() }
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("PulseGuard.Handheld", "Failed querying DataItems for clear", e)
                _wearConnectionStatus.value = _wearConnectionStatus.value.copy(lastError = friendlyWearableError(e))
                viewModelScope.launch { repo.clear() }
            }
    }

    fun refreshConnectionStatus() {
        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                _wearConnectionStatus.value = _wearConnectionStatus.value.copy(
                    connectedNodeCount = nodes.size,
                    lastError = null
                )
            }
            .addOnFailureListener { e ->
                _wearConnectionStatus.value = _wearConnectionStatus.value.copy(
                    connectedNodeCount = 0,
                    lastError = friendlyWearableError(e)
                )
            }
    }

    fun refreshFromDataLayer() {
        dataClient.dataItems
            .addOnSuccessListener { buffer ->
                try {
                    buffer.forEach { item ->
                        val path = item.uri.path ?: return@forEach
                        if (!path.startsWith(DataLayerPaths.WORKOUT_SUMMARY_PREFIX)) return@forEach

                        val dataMap = DataMapItem.fromDataItem(item).dataMap
                        val endedAt = dataMap.getLong("endedAtEpochMillis", -1L)
                        if (endedAt <= 0L) return@forEach

                        val typeLabel = dataMap.getString("typeLabel") ?: "Workout"
                        val durationSeconds = dataMap.getLong("durationSeconds", 0L)
                        val avgHeartRate = dataMap.getDouble("avgHeartRate", 0.0)

                        viewModelScope.launch {
                            repo.upsert(
                                WorkoutHistoryItem(
                                    endedAtEpochMillis = endedAt,
                                    typeLabel = typeLabel,
                                    durationSeconds = durationSeconds,
                                    avgHeartRate = avgHeartRate
                                )
                            )
                        }
                    }
                } catch (t: Throwable) {
                    Log.e("PulseGuard.Handheld", "refreshFromDataLayer failed", t)
                    _wearConnectionStatus.value = _wearConnectionStatus.value.copy(lastError = friendlyWearableError(t))
                } finally {
                    buffer.release()
                }
            }
            .addOnFailureListener { e ->
                Log.e("PulseGuard.Handheld", "refreshFromDataLayer: failed to query data items", e)
                _wearConnectionStatus.value = _wearConnectionStatus.value.copy(lastError = friendlyWearableError(e))
            }
    }

    private fun friendlyWearableError(t: Throwable): String {
        val api = t as? ApiException
        // 17 = API_NOT_CONNECTED
        return if (api?.statusCode == 17) {
            "Wear Data Layer not connected (API_NOT_CONNECTED). Pair phone+watch emulators using Android Studio’s Wear OS pairing assistant and use Google Play system images."
        } else {
            t.message ?: t.javaClass.simpleName
        }
    }
}


