# GoalWall

GoalWall is a native Android app that turns daily goal completion into a visual lock-screen streak.

## Features in this starter build

- Add personal goals and mark them complete for today.
- See each goal's streak and your overall consecutive-day streak.
- Choose one of six generated wallpaper styles:
  - Calendar Heatmap
  - Streak Rings
  - Streak Chain
  - Growing Tree
  - Constellation
  - Mountain Progress
- Apply the generated image to the lock screen.
- Schedule a daily wallpaper refresh using WorkManager.
- Store goals locally on the device; no account or network connection is required.

## Build and install

1. Install the latest stable Android Studio and a JDK 17 runtime.
2. Clone this repository and open the project root in Android Studio.
3. Allow Android Studio to install the Android SDK platform/build tools required by compileSdk 35.
4. Sync Gradle and run the `app` configuration on an Android device or emulator (Android 8.0 / API 26 or newer).
5. On a physical device, approve USB debugging if prompted.

A GitHub Actions workflow is included to build a debug APK. After a successful workflow run, download the `GoalWall-debug-apk` artifact from the run's Artifacts section.

## Important limitations

- This is an early MVP, not a production release. The UI is a simple native Android layout.
- The source has not been compiled or tested on a physical device in this handoff. Treat the first CI build as validation, and fix any build/runtime issues it reveals.
- WorkManager is inexact. Android may delay the daily wallpaper refresh because of Doze mode, battery optimisation, or device-specific background restrictions.
- Goal data is stored locally in SharedPreferences. There is no cloud sync, export/restore, reminders, goal editing/deletion, or onboarding yet.
- The wallpaper is generated at a generic device display size; different phones and lock-screen crops may need layout tuning.
- The app only changes the lock screen when the user taps Apply or when the scheduled worker runs.

## Project structure

- `app/src/main/java/com/goalwall/MainActivity.kt` — app UI, goal/streak logic, worker, and wallpaper renderer.
- `app/src/main/AndroidManifest.xml` — launcher activity and wallpaper permission.
- `app/build.gradle.kts` — Android app and WorkManager dependencies.


Build workflow: `.github/workflows/android.yml` runs on pushes to `main` and pull requests.


Default wallpaper theme: Heatmap.
