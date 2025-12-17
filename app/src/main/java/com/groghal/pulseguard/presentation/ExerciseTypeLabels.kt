package com.groghal.pulseguard.presentation

import androidx.health.services.client.data.ExerciseType

fun exerciseTypeLabel(type: ExerciseType): String {
    return when (type) {
        ExerciseType.RUNNING -> "Run"
        ExerciseType.WALKING -> "Walk"
        ExerciseType.BIKING -> "Bike"
        ExerciseType.HIKING -> "Hike"
        else -> "Workout"
    }
}


