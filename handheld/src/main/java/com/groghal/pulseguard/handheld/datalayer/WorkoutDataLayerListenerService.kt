package com.groghal.pulseguard.handheld.datalayer

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.groghal.pulseguard.handheld.data.HandheldWorkoutHistoryRepository
import com.groghal.pulseguard.handheld.data.WorkoutHistoryItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WorkoutDataLayerListenerService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val repo = HandheldWorkoutHistoryRepository(applicationContext)

        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach

            val item = event.dataItem
            val path = item.uri.path ?: return@forEach
            if (!path.startsWith(DataLayerPaths.WORKOUT_SUMMARY_PREFIX)) return@forEach

            try {
                val dataMap = DataMapItem.fromDataItem(item).dataMap
                val endedAt = dataMap.getLong("endedAtEpochMillis", -1L)
                if (endedAt <= 0L) return@forEach

                val typeLabel = dataMap.getString("typeLabel") ?: "Workout"
                val durationSeconds = dataMap.getLong("durationSeconds", 0L)
                val avgHeartRate = dataMap.getDouble("avgHeartRate", 0.0)

                Log.d("PulseGuard.Handheld", "Received workout: path=$path endedAt=$endedAt type=$typeLabel")
                scope.launch {
                    repo.upsert(
                        WorkoutHistoryItem(
                            endedAtEpochMillis = endedAt,
                            typeLabel = typeLabel,
                            durationSeconds = durationSeconds,
                            avgHeartRate = avgHeartRate
                        )
                    )
                }
            } catch (t: Throwable) {
                Log.e("PulseGuard.Handheld", "Failed to parse incoming workout DataItem", t)
            }
        }
    }
}


