package com.groghal.pulseguard.presentation

import android.app.Application
import android.util.Log
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.BatchingMode
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseLapSummary
import androidx.health.services.client.data.ExerciseType
import androidx.health.services.client.data.ExerciseUpdate
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.concurrent.futures.await
import com.groghal.pulseguard.data.HrThresholdRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

sealed class WorkoutState {
    data class Idle(
        val hrThreshold: Int = 100,
        val exerciseType: ExerciseType = ExerciseType.WORKOUT
    ) : WorkoutState()

    data class Active(
        val elapsedSeconds: Long, 
        val heartRate: Double = 0.0, 
        val avgHeartRate: Double = 0.0,
        val hrThreshold: Int,
        val exerciseType: ExerciseType,
        val exerciseTimerSeconds: Int? = null,
        val keepScreenOn: Boolean = false
    ) : WorkoutState()

    data class Summary(val totalDurationSeconds: Long, val averageHeartRate: Double) : WorkoutState()
}

class WorkoutViewModel(application: Application) : AndroidViewModel(application) {

    private val healthServicesClient = HealthServices.getClient(application)
    private val exerciseClient = healthServicesClient.exerciseClient
    private val repository = HrThresholdRepository(application)
    private val alarmHelper = AlarmHelper(application)

    private val _uiState = MutableStateFlow<WorkoutState>(WorkoutState.Idle(100, ExerciseType.WORKOUT))
    val uiState: StateFlow<WorkoutState> = _uiState.asStateFlow()

    private var exerciseStartTime: Instant? = null
    private var timerJob: Job? = null
    
    // Manual average calculation
    private var heartRateSum: Double = 0.0
    private var heartRateCount: Int = 0

    init {
        viewModelScope.launch {
            try {
                // Load saved threshold
                val savedThreshold = repository.hrThreshold.first()
                val current = _uiState.value
                if (current is WorkoutState.Idle) {
                    _uiState.value = current.copy(hrThreshold = savedThreshold)
                }

                // Restore state if exercise is in progress
                val info = exerciseClient.getCurrentExerciseInfoAsync().await()
                if (info.exerciseTrackedStatus == androidx.health.services.client.data.ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS) {
                    exerciseStartTime = Instant.now()
                    
                    exerciseClient.setUpdateCallback(exerciseUpdateListener)
                    
                    val elapsed = if (exerciseStartTime != null) Duration.between(exerciseStartTime, Instant.now()).seconds else 0L
                    
                    // We might not know the original exercise type or threshold easily without persisting it elsewhere,
                    // but for now we restore with saved threshold and default type.
                    _uiState.value = WorkoutState.Active(
                        elapsedSeconds = elapsed, 
                        heartRate = 0.0, 
                        avgHeartRate = 0.0, 
                        hrThreshold = savedThreshold,
                        exerciseType = ExerciseType.WORKOUT, // Fallback
                        keepScreenOn = false
                    )
                    startTimer()
                }
            } catch (e: Exception) {
                Log.e("WorkoutApp", "Error restoring state", e)
            }
        }
    }

    private val exerciseUpdateListener = object : androidx.health.services.client.ExerciseUpdateCallback {
        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            val metrics = update.latestMetrics
            val hr = metrics.getData(DataType.HEART_RATE_BPM).lastOrNull()?.value ?: 0.0
            
            // Manual average update
            if (hr > 0) {
                heartRateSum += hr
                heartRateCount++
            }
            val avgHr = if (heartRateCount > 0) heartRateSum / heartRateCount else 0.0

            val currentState = _uiState.value
            if (currentState is WorkoutState.Active) {
                _uiState.value = currentState.copy(heartRate = hr, avgHeartRate = avgHr)
            }
        }

        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) {}
        override fun onRegistered() {}
        override fun onRegistrationFailed(throwable: Throwable) {}
        override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: androidx.health.services.client.data.Availability) {}
    }

    fun updateThreshold(newThreshold: Int) {
        viewModelScope.launch {
            repository.setHrThreshold(newThreshold)
            val current = _uiState.value
            when (current) {
                is WorkoutState.Idle -> _uiState.value = current.copy(hrThreshold = newThreshold)
                is WorkoutState.Active -> _uiState.value = current.copy(hrThreshold = newThreshold)
                else -> {}
            }
        }
    }

    fun updateExerciseType(type: ExerciseType) {
        val current = _uiState.value
        if (current is WorkoutState.Idle) {
            _uiState.value = current.copy(exerciseType = type)
        }
    }

    fun startWorkout() {
        val currentIdle = _uiState.value as? WorkoutState.Idle ?: return

        viewModelScope.launch {
            try {
                // Start Foreground Service
                val serviceIntent = android.content.Intent(getApplication(), WorkoutService::class.java)
                getApplication<Application>().startForegroundService(serviceIntent)

                val config = ExerciseConfig.builder(currentIdle.exerciseType)
                    .setBatchingModeOverrides(setOf(BatchingMode.HEART_RATE_5_SECONDS))
                    .setDataTypes(setOf(DataType.HEART_RATE_BPM))
                    .build()

                exerciseClient.startExerciseAsync(config).await()
                exerciseClient.setUpdateCallback(exerciseUpdateListener)

                exerciseStartTime = Instant.now()
                heartRateSum = 0.0
                heartRateCount = 0
                _uiState.value = WorkoutState.Active(
                    elapsedSeconds = 0, 
                    exerciseType = currentIdle.exerciseType,
                    hrThreshold = currentIdle.hrThreshold,
                    keepScreenOn = false
                )
                startTimer()

            } catch (e: Exception) {
                Log.e("WorkoutApp", "Error starting workout", e)
            }
        }
    }

    fun stopWorkout() {
        viewModelScope.launch {
            try {
                exerciseClient.endExerciseAsync().await()
                
                // Stop Service
                val serviceIntent = android.content.Intent(getApplication(), WorkoutService::class.java)
                getApplication<Application>().stopService(serviceIntent)
                
                timerJob?.cancel()
                timerJob = null
                
                val current = _uiState.value
                if (current is WorkoutState.Active) {
                    _uiState.value = WorkoutState.Summary(current.elapsedSeconds, current.avgHeartRate)
                }
            } catch (e: Exception) {
                Log.e("WorkoutApp", "Error stopping workout", e)
            }
        }
    }

    fun reset() {
        viewModelScope.launch { 
             try { exerciseClient.endExerciseAsync().await() } catch (e: Exception) {}
             val savedThreshold = repository.hrThreshold.first()
             _uiState.value = WorkoutState.Idle(savedThreshold)
        }
        timerJob?.cancel()
        timerJob = null
        exerciseStartTime = null
        heartRateSum = 0.0
        heartRateCount = 0
    }

    fun startExerciseTimer() {
        val current = _uiState.value
        if (current is WorkoutState.Active) {
            _uiState.value = current.copy(exerciseTimerSeconds = 40)
        }
    }

    fun toggleKeepScreenOn() {
        val current = _uiState.value
        if (current is WorkoutState.Active) {
            _uiState.value = current.copy(keepScreenOn = !current.keepScreenOn)
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                // Log status every second
                Log.d("WorkoutApp", "Status: ${_uiState.value}")
                
                exerciseStartTime?.let { start ->
                    val elapsed = Duration.between(start, Instant.now()).seconds
                    val activeState = _uiState.value as? WorkoutState.Active
                    
                    if (activeState != null) {
                        val currentHr = activeState.heartRate
                        val threshold = activeState.hrThreshold
                        
                        // Alarm Logic
                        if (currentHr > threshold) {
                            alarmHelper.triggerAlarm()
                        }
                        
                        // Exercise Timer Logic
                        var timer = activeState.exerciseTimerSeconds
                        if (timer != null) {
                            timer -= 1
                            if (timer <= 0) {
                                alarmHelper.triggerLongVibration()
                                timer = null // Timer finished
                            }
                        }

                        _uiState.value = activeState.copy(
                            elapsedSeconds = elapsed,
                            exerciseTimerSeconds = timer
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
             try { exerciseClient.clearUpdateCallbackAsync(exerciseUpdateListener).await() } catch(e: Exception){}
        }
    }
}
