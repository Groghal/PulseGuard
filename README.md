# PulseGuard

PulseGuard is a Wear OS application designed to monitor heart rate during workouts and alert users when their heart rate exceeds a set threshold.

## Google Play listing text

**Short description (max ~80 chars)**

Monitor your heart rate on Wear OS workouts and get threshold alerts.

**Full description**

PulseGuard helps you keep an eye on your heart rate during workouts on your Wear OS watch. Set a heart-rate threshold, start an activity, and PulseGuard will show your current heart rate and notify you when you go above your chosen limit.

**Key features**

- Real-time heart rate display during workouts
- Custom threshold with quick +/- adjustment
- Exercise types: Run, Walk, Bike, Hike, Workout
- Alerts when your heart rate exceeds your threshold (visual + vibration)
- Optional screen keep-on during workouts
- Workout summary with duration and average heart rate
- Simple interval helper: 40-second timer in Workout mode

**Notes**

- PulseGuard is designed for fitness and training awareness. It is **not a medical device** and is not intended for diagnosis or treatment.

## Features

- **Heart Rate Monitoring**: Real-time heart rate tracking during workouts using Health Services
- **Customizable Threshold**: Set your own heart rate limit with easy +/- controls
- **Multiple Exercise Types**: Support for Running, Walking, Biking, Hiking, and General Workout
- **Visual & Audio Alerts**: Get notified when your heart rate exceeds the threshold
- **Exercise Timer**: Built-in 40-second exercise timer for interval training (Workout mode)
- **Screen Keep-On**: Option to keep the screen on during workouts
- **Workout Summary**: View total duration and average heart rate after completing a workout

## Requirements

- Wear OS 3.0 or higher
- Android API level 30+
- Heart rate sensor support
- Body sensors permission
- Activity recognition permission

## Installation

### For Developers

1. Clone the repository
2. Open the project in Android Studio
3. Sync Gradle files
4. Build and install on your Wear OS device or emulator

### For Users (Installing APK on Wear OS)

To install the PulseGuard APK on your Wear OS device, you can use [Wear Installer](https://play.google.com/store/apps/details?id=org.freepoc.wearinstaller) from the Play Store. This app makes it easy to install apps from your Android phone to your Wear OS device:

1. Install Wear Installer on your Android phone from the [Play Store](https://play.google.com/store/apps/details?id=org.freepoc.wearinstaller)
2. Transfer the PulseGuard APK to your phone
3. Open Wear Installer and select the PulseGuard APK
4. Follow the prompts to install on your paired Wear OS device

## Usage

1. **Set Heart Rate Threshold**: Use the +/- buttons to adjust your desired heart rate limit
2. **Select Exercise Type**: Tap the exercise type button to cycle through available options (Run, Walk, Bike, Hike, Workout)
3. **Start Workout**: Tap "Start" and grant the required permissions
4. **Monitor**: Watch your heart rate in real-time. The app will alert you if it exceeds your threshold
5. **Stop**: Tap "Stop" when finished to see your workout summary

### Workout Mode Features

When using the "Workout" exercise type, you get additional features:
- **Exercise Timer**: Tap "Exercise 40s" to start a 40-second countdown timer
- **Screen Keep-On Toggle**: Keep the screen active during your workout

## Permissions

The app requires the following permissions:
- `BODY_SENSORS`: To read heart rate data
- `ACTIVITY_RECOGNITION`: To track exercise activities
- `FOREGROUND_SERVICE`: To run workout tracking in the background
- `FOREGROUND_SERVICE_HEALTH`: For health-related foreground services
- `VIBRATE`: For vibration alerts
- `WAKE_LOCK`: To keep the device awake during workouts

## Technical Details

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose for Wear OS
- **Health Services**: Android Health Services API for heart rate monitoring
- **Architecture**: MVVM with ViewModel and StateFlow
- **Data Storage**: DataStore Preferences for saving heart rate threshold

## Development

This app was vibe-coded with [Antigravity](https://github.com/antigravity-dev) and [Cursor](https://cursor.sh).

## License

MIT License - see LICENSE file for details

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

