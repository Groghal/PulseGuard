package com.groghal.pulseguard.presentation

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.delay

@Composable
fun RepeatingButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(500) // Initial delay before rapid fire
            while (true) {
                onClick()
                delay(100) // Rapid fire interval
            }
        }
    }

    Button(
        onClick = onClick,
        modifier = modifier,
        interactionSource = interactionSource,
        colors = ButtonDefaults.secondaryButtonColors()
    ) {
        content()
    }
}

@Composable
fun HrThresholdControl(
    threshold: Int,
    onThresholdChange: (Int) -> Unit
) {
    // Use mutable state that's synced with the parameter to always get the latest value
    var currentThreshold by remember { mutableStateOf(threshold) }
    
    // Update the state when the parameter changes
    LaunchedEffect(threshold) {
        currentThreshold = threshold
    }
    
    // Use rememberUpdatedState for the callback to ensure it's always the latest version
    val updatedOnThresholdChange by rememberUpdatedState(onThresholdChange)
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        RepeatingButton(
            onClick = { 
                currentThreshold = currentThreshold - 1
                updatedOnThresholdChange(currentThreshold)
            },
            modifier = Modifier.size(32.dp)
        ) {
            Text("-")
        }
        
        Spacer(modifier = Modifier.width(10.dp))
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Limit", style = MaterialTheme.typography.caption2)
            Text("$threshold", style = MaterialTheme.typography.title3)
        }
        
        Spacer(modifier = Modifier.width(10.dp))
        
        RepeatingButton(
            onClick = { 
                currentThreshold = currentThreshold + 1
                updatedOnThresholdChange(currentThreshold)
            },
            modifier = Modifier.size(32.dp)
        ) {
            Text("+")
        }
    }
}

@Composable
fun WorkoutTypeSelector(
    currentType: androidx.health.services.client.data.ExerciseType,
    onTypeChange: (androidx.health.services.client.data.ExerciseType) -> Unit
) {
    val types = listOf(
        androidx.health.services.client.data.ExerciseType.RUNNING,
        androidx.health.services.client.data.ExerciseType.WALKING,
        androidx.health.services.client.data.ExerciseType.BIKING,
        androidx.health.services.client.data.ExerciseType.HIKING,
        androidx.health.services.client.data.ExerciseType.WORKOUT
    )

    Button(
        onClick = {
            val currentIndex = types.indexOf(currentType)
            val nextIndex = (currentIndex + 1) % types.size
            onTypeChange(types[nextIndex])
        },
        colors = ButtonDefaults.secondaryButtonColors(),
        modifier = Modifier
            .height(40.dp)
            .width(140.dp)
    ) {
        val label = when (currentType) {
            androidx.health.services.client.data.ExerciseType.RUNNING -> "Run"
            androidx.health.services.client.data.ExerciseType.WALKING -> "Walk"
            androidx.health.services.client.data.ExerciseType.BIKING -> "Bike"
            androidx.health.services.client.data.ExerciseType.HIKING -> "Hike"
            else -> "Workout"
        }
        Text(text = label, style = MaterialTheme.typography.button)
    }
}

@Composable
fun StartScreen(
    hrThreshold: Int,
    onThresholdChange: (Int) -> Unit,
    exerciseType: androidx.health.services.client.data.ExerciseType,
    onTypeChange: (androidx.health.services.client.data.ExerciseType) -> Unit,
    onStartClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        WorkoutTypeSelector(
            currentType = exerciseType,
            onTypeChange = onTypeChange
        )

        Spacer(modifier = Modifier.height(8.dp))

        HrThresholdControl(
            threshold = hrThreshold,
            onThresholdChange = onThresholdChange
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onStartClick,
            colors = ButtonDefaults.primaryButtonColors(
                backgroundColor = MaterialTheme.colors.primary
            )
        ) {
            Text("Start")
        }
    }
}

@Composable
fun ActiveScreen(
    elapsedSeconds: Long,
    heartRate: Double,
    hrThreshold: Int,
    exerciseType: androidx.health.services.client.data.ExerciseType,
    exerciseTimerSeconds: Int?,
    keepScreenOn: Boolean,
    onThresholdChange: (Int) -> Unit,
    onExerciseTimerClick: () -> Unit,
    onToggleKeepScreenOn: () -> Unit,
    onStopClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (exerciseTimerSeconds != null) {
            // Big countdown
            Text(
                text = "${exerciseTimerSeconds}s",
                style = MaterialTheme.typography.display1,
                color = MaterialTheme.colors.primaryVariant
            )
        } else {
            Text(
                text = formatDuration(elapsedSeconds),
                style = MaterialTheme.typography.display1
            )
        }
        
        val hrColor = if (heartRate > hrThreshold) Color.Red else MaterialTheme.colors.secondary
        
        Text(
            text = "HR: ${heartRate.toInt()}",
            style = MaterialTheme.typography.title2,
            color = hrColor
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Show Timer Button and Screen Toggle in a Row for WORKOUT type
        if (exerciseType == androidx.health.services.client.data.ExerciseType.WORKOUT && exerciseTimerSeconds == null) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Keep Screen On Toggle Button
                Button(
                    onClick = onToggleKeepScreenOn,
                    colors = if (keepScreenOn) {
                        ButtonDefaults.primaryButtonColors()
                    } else {
                        ButtonDefaults.secondaryButtonColors()
                    },
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(
                        text = if (keepScreenOn) "Screen: On" else "Screen: Off",
                        style = MaterialTheme.typography.caption2
                    )
                }
                
                Spacer(modifier = Modifier.width(4.dp))
                
                Button(
                    onClick = onExerciseTimerClick,
                    colors = ButtonDefaults.secondaryButtonColors(),
                    modifier = Modifier
                        .height(30.dp)
                        .width(120.dp)
                ) {
                    Text("Exercise 40s", style = MaterialTheme.typography.caption2)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        } else {
            // Keep Screen On Toggle Button (for non-WORKOUT types)
            Button(
                onClick = onToggleKeepScreenOn,
                colors = if (keepScreenOn) {
                    ButtonDefaults.primaryButtonColors()
                } else {
                    ButtonDefaults.secondaryButtonColors()
                },
                modifier = Modifier.height(30.dp)
            ) {
                Text(
                    text = if (keepScreenOn) "Screen: On" else "Screen: Off",
                    style = MaterialTheme.typography.caption2
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        HrThresholdControl(
            threshold = hrThreshold,
            onThresholdChange = onThresholdChange
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Button(
            onClick = onStopClick,
            modifier = Modifier.size(40.dp),
            colors = ButtonDefaults.primaryButtonColors(
                backgroundColor = MaterialTheme.colors.error
            )
        ) {
            Text("Stop", style = MaterialTheme.typography.caption2)
        }
    }
}

@Composable
fun SummaryScreen(
    totalSeconds: Long,
    averageHeartRate: Double,
    onRestartClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Total Time",
            style = MaterialTheme.typography.caption1
        )
        Text(
            text = formatDuration(totalSeconds),
            style = MaterialTheme.typography.display2
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Avg HR: ${averageHeartRate.toInt()}",
            style = MaterialTheme.typography.title2,
            color = MaterialTheme.colors.secondary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRestartClick,
            colors = ButtonDefaults.secondaryButtonColors()
        ) {
            Text("Done")
        }
    }
}

fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", mins, secs)
}
