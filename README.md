# Alertify

**Real-time drowsiness detection for Android.** Alertify uses the front-facing camera and on-device face landmarks to estimate eye and mouth movement, then gives a sound and vibration alert when its detection rules indicate drowsiness.

> Alertify is an experimental aid, not a safety system. It can miss signs of drowsiness or raise a false alert. Never use it in place of rest, safe driving practices, or vehicle safety equipment.

## Install on Android

1. [Download the latest Alertify APK](https://github.com/HeyItzAine/Alertify/releases/latest/download/Alertify-release.apk) from the [Releases page](https://github.com/HeyItzAine/Alertify/releases).
2. Open `Alertify-release.apk` from your downloads. If Android asks, allow your browser or file manager to install unknown apps, then continue with the installation.
3. Open Alertify and allow camera access. Detection begins in real time; the camera preview stays hidden until you turn it on.

This preview APK is signed with an Android debug key for direct device testing. Android only accepts updates signed with the same key. A build signed with a different key requires uninstalling this preview first, which removes its local app data.

## What it does

- Runs MediaPipe Face Landmarker on the device and measures eye aspect ratio (EAR) and mouth aspect ratio (MAR).
- Classifies the live camera feed as **Awake**, **Drowsy**, or **No Face**.
- Plays an alarm and vibrates when the classifier reports drowsiness.
- Shows live EAR, MAR, classification latency, camera FPS, detection FPS, processing time, memory, and CPU statistics.
- Lets you hide or show the camera preview without stopping detection.
- Offers dark mode (black and charcoal) and light mode (white and light gray).
- Lets you show or hide performance statistics and face landmark overlays in Settings.

## Controls

| Control | Action |
| --- | --- |
| Pause / play | Stop or resume real-time detection |
| Appearance | Switch between dark and light themes |
| Camera | Show or hide the live preview; detection continues while hidden |
| Settings | Show or hide performance statistics and face landmarks |

## Privacy

Face detection and classification run locally on the phone. The app requests camera and vibration permissions and does not request Internet access. Camera frames are not uploaded.

## Requirements

- Android 7.0 (API 24) or newer
- A device with a front-facing camera
- Camera permission

## Build from source

Open the project in Android Studio, or use the Gradle wrapper from the project root:

```bash
./gradlew assembleRelease
```

On Windows, run:

```powershell
.\gradlew.bat assembleRelease
```

The build uses JDK 21 and Android SDK 36. Gradle writes the APK to `app/build/outputs/apk/release/`. Configure your own signing key before distributing a release build beyond local testing.

## Technology

- Kotlin and Jetpack Compose
- CameraX for camera frame analysis
- MediaPipe Tasks Vision Face Landmarker
- AndroidX Lifecycle and StateFlow
