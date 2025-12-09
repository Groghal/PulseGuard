package com.groghal.pulseguard.presentation

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

class AlarmHelper(private val context: Context) {

    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private var mediaPlayer: MediaPlayer? = null
    private var currentRingtone: android.media.Ringtone? = null

    fun triggerAlarm() {
        // Vibrate
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(500)
        }

        // Sound - Try multiple approaches for better compatibility
        playAlarmSound()
    }

    private fun playAlarmSound() {
        // Stop any currently playing ringtone
        currentRingtone?.stop()
        currentRingtone = null
        
        try {
            // First, try using RingtoneManager with notification sound
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            if (notificationUri != null) {
                val ringtone = RingtoneManager.getRingtone(context, notificationUri)
                if (ringtone != null) {
                    // Set stream type for better compatibility on Wear OS
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ringtone.audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    }
                    ringtone.play()
                    currentRingtone = ringtone
                    Log.d("AlarmHelper", "Playing notification ringtone")
                    return
                }
            }
        } catch (e: Exception) {
            Log.e("AlarmHelper", "Error playing notification ringtone", e)
        }

        // Fallback: Try using MediaPlayer with system notification sound
        try {
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            if (notificationUri != null) {
                // Stop any previous playback
                mediaPlayer?.release()
                
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(context, notificationUri)
                    
                    // Use ALARM stream for better reliability on Wear OS
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                                .build()
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        setAudioStreamType(AudioManager.STREAM_ALARM)
                    }
                    
                    // Set volume to maximum for alarms
                    setVolume(1.0f, 1.0f)
                    
                    prepare()
                    start()
                    Log.d("AlarmHelper", "Playing sound with MediaPlayer")
                    
                    // Release when done
                    setOnCompletionListener {
                        release()
                        mediaPlayer = null
                    }
                    
                    setOnErrorListener { _, what, extra ->
                        Log.e("AlarmHelper", "MediaPlayer error: what=$what, extra=$extra")
                        release()
                        mediaPlayer = null
                        true
                    }
                }
                return
            }
        } catch (e: Exception) {
            Log.e("AlarmHelper", "Error playing sound with MediaPlayer", e)
            mediaPlayer?.release()
            mediaPlayer = null
        }

        // Last resort: Try alarm sound with RingtoneManager
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (alarmUri != null) {
                val ringtone = RingtoneManager.getRingtone(context, alarmUri)
                if (ringtone != null) {
                    // Set stream type for better compatibility
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ringtone.audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                            .build()
                    }
                    ringtone.play()
                    currentRingtone = ringtone
                    Log.d("AlarmHelper", "Playing alarm ringtone as fallback")
                    return
                }
            }
        } catch (e: Exception) {
            Log.e("AlarmHelper", "Error playing alarm ringtone", e)
        }

        Log.w("AlarmHelper", "Could not play any alarm sound")
    }

    fun triggerLongVibration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(5000, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(5000)
        }
    }
}
