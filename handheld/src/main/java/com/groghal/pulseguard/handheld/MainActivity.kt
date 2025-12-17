package com.groghal.pulseguard.handheld

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.groghal.pulseguard.handheld.data.WorkoutHistoryItem
import com.groghal.pulseguard.handheld.data.HandheldWorkoutHistoryRepository
import com.groghal.pulseguard.handheld.datalayer.DataLayerPaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                HandheldApp()
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HandheldApp(vm: MainViewModel = viewModel()) {
    val history by vm.history.collectAsState()
    val lastReceived by vm.lastReceivedEpochMillis.collectAsState()
    val conn by vm.wearConnectionStatus.collectAsState()
    var showClearConfirm by remember { mutableStateOf(false) }
    var showConnDialog by remember { mutableStateOf(false) }

    // Foreground listener: makes debugging sync much easier while the phone app is open.
    // (The background path is still handled by WearableListenerService.)
    val appContext = LocalContext.current.applicationContext
    val repo = remember { HandheldWorkoutHistoryRepository(appContext) }
    val ioScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    val dataClient = remember { Wearable.getDataClient(appContext) }

    DisposableEffect(dataClient) {
        val listener = DataClient.OnDataChangedListener { dataEvents ->
            try {
                dataEvents.forEach { event ->
                    if (event.type != DataEvent.TYPE_CHANGED) return@forEach
                    val item = event.dataItem
                    val path = item.uri.path ?: return@forEach
                    if (!path.startsWith(DataLayerPaths.WORKOUT_SUMMARY_PREFIX)) return@forEach

                    val dataMap = DataMapItem.fromDataItem(item).dataMap
                    val endedAt = dataMap.getLong("endedAtEpochMillis", -1L)
                    if (endedAt <= 0L) return@forEach

                    val typeLabel = dataMap.getString("typeLabel") ?: "Workout"
                    val durationSeconds = dataMap.getLong("durationSeconds", 0L)
                    val avgHeartRate = dataMap.getDouble("avgHeartRate", 0.0)

                    ioScope.launch {
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
                Log.e("PulseGuard.Handheld", "Foreground Data Layer listener failed", t)
            } finally {
                dataEvents.release()
            }
        }

        dataClient.addListener(listener)
        onDispose {
            dataClient.removeListener(listener)
            ioScope.cancel()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.history_title)) },
                actions = {
                    TextButton(
                        onClick = {
                            vm.refreshConnectionStatus()
                            showConnDialog = true
                        }
                    ) { Text("Status") }
                    TextButton(onClick = vm::refreshFromDataLayer) {
                        Text("Refresh")
                    }
                    TextButton(onClick = { showClearConfirm = true }) {
                        Text(stringResource(id = R.string.clear))
                    }
                }
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(id = R.string.history_empty))
                if (lastReceived != null) {
                    Text(
                        text = "Last received: " + ROW_TIME_FORMATTER.format(Instant.ofEpochMilli(lastReceived!!)),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                ConnectionBanner(conn = conn)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                item(key = "connBanner") {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        ConnectionBanner(conn = conn)
                    }
                }
                if (lastReceived != null) {
                    item(key = "lastReceived") {
                        Text(
                            text = "Last received: " + ROW_TIME_FORMATTER.format(Instant.ofEpochMilli(lastReceived!!)),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
                items(history, key = { it.endedAtEpochMillis }) { item ->
                    WorkoutRow(item)
                }
            }
        }
    }

    if (showConnDialog) {
        val connected = conn.connectedNodeCount > 0 && conn.lastError == null
        AlertDialog(
            onDismissRequest = { showConnDialog = false },
            title = { Text("Watch connection") },
            text = {
                Column {
                    Text(if (connected) "Connected" else "Not connected")
                    Text(
                        text = "Connected nodes: ${conn.connectedNodeCount}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (conn.lastError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = conn.lastError!!,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.refreshConnectionStatus()
                        showConnDialog = false
                    }
                ) { Text("OK") }
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear workout history?") },
            text = { Text("This also deletes the synced items so they won’t come back.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        vm.clearHistoryEverywhere()
                    }
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

private val ROW_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())

@Composable
private fun ConnectionBanner(conn: WearConnectionStatus) {
    val connected = conn.connectedNodeCount > 0 && conn.lastError == null
    val bg = if (connected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val fg = if (connected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(color = bg, shape = MaterialTheme.shapes.medium) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (connected) "Watch: Connected" else "Watch: Not paired",
                color = fg,
                textAlign = TextAlign.Center
            )
            if (!connected) {
                Text(
                    text = "Tap Status and pair your phone+watch emulators.",
                    color = fg,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun WorkoutRow(item: WorkoutHistoryItem) {
    val time = ROW_TIME_FORMATTER.format(Instant.ofEpochMilli(item.endedAtEpochMillis))
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(text = "${item.typeLabel} • ${formatDuration(item.durationSeconds)}")
        Text(
            text = "Avg HR: ${item.avgHeartRate.toInt()} • $time",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", mins, secs)
}


