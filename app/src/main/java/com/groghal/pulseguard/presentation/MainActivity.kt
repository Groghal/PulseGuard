package com.groghal.pulseguard.presentation

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.TimeText
import com.groghal.pulseguard.presentation.theme.PulseGuardTheme
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            WearApp()
        }
    }
}

@Composable
fun WearApp() {
    PulseGuardTheme {
        val viewModel = viewModel<WorkoutViewModel>()
        val uiState by viewModel.uiState.collectAsState()
        
        val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.values.all { it }
            if (allGranted) {
                viewModel.startWorkout()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            TimeText()
            
            when (val state = uiState) {
                is WorkoutState.Idle -> {
                    StartScreen(
                        hrThreshold = state.hrThreshold,
                        onThresholdChange = viewModel::updateThreshold,
                        exerciseType = state.exerciseType,
                        onTypeChange = viewModel::updateExerciseType,
                        onStartClick = {
                            permissionLauncher.launch(arrayOf(
                                android.Manifest.permission.BODY_SENSORS,
                                android.Manifest.permission.ACTIVITY_RECOGNITION,
                                android.Manifest.permission.VIBRATE
                            ))
                        }
                    )
                }
                is WorkoutState.Active -> {
                    // Handle screen-on state
                    ScreenOnHandler(keepScreenOn = state.keepScreenOn)
                    
                    ActiveScreen(
                        elapsedSeconds = state.elapsedSeconds,
                        heartRate = state.heartRate,
                        hrThreshold = state.hrThreshold,
                        exerciseType = state.exerciseType,
                        exerciseTimerSeconds = state.exerciseTimerSeconds,
                        keepScreenOn = state.keepScreenOn,
                        onThresholdChange = viewModel::updateThreshold,
                        onExerciseTimerClick = viewModel::startExerciseTimer,
                        onToggleKeepScreenOn = viewModel::toggleKeepScreenOn,
                        onStopClick = viewModel::stopWorkout
                    )
                }
                is WorkoutState.Summary -> {
                    SummaryScreen(
                        totalSeconds = state.totalDurationSeconds,
                        averageHeartRate = state.averageHeartRate,
                        onRestartClick = viewModel::reset
                    )
                }
            }
        }
    }
}

@Composable
fun ScreenOnHandler(keepScreenOn: Boolean) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    
    DisposableEffect(keepScreenOn) {
        activity?.window?.let { window ->
            if (keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        
        onDispose {
            // Always clear the flag when the composable is disposed
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}