# FitHall

An AI fitness app: snap a photo of your plate to count its macros, get a workout plan and a meal
plan generated for your level, activity and goal, log your sessions set by set, track body
weight and strength progress, and chat with an AI coach that knows your profile, today's meals,
your plans and your recent training. AI is powered by Claude (`claude-opus-5-5`).

## Setup

1. Get an API key from the [Claude Console](https://platform.claude.com/).
2. Add it to `local.properties` in the project root (this file is git-ignored):
   ```properties
   ANTHROPIC_API_KEY=sk-ant-...
   ```
3. Build and run the Android app (see below). Without a key the app still runs, but AI features show an error.

> **Development only.** The key is compiled into the debug APK, and anyone with the APK can
> extract it. Before releasing, put a small backend in front of the Claude API and implement
> [`AiService`](composeApp/src/commonMain/kotlin/com/example/fithall/ai/AiService.kt) against it.
> The prompts and schemas in `AiPrompts.kt` can move to that backend unchanged.

## How it's organised

| Area | Where |
|---|---|
| Data models | `commonMain/.../model/Models.kt` |
| Calorie & macro targets (Mifflin-St Jeor, deterministic) | `commonMain/.../domain/MacroCalculator.kt` |
| Workout stats, streaks, estimated 1RM, set prefill | `commonMain/.../domain/ProgressCalculator.kt` |
| AI interface, prompts, JSON schemas | `commonMain/.../ai/` |
| Claude implementation (Android) | `androidMain/.../ai/ClaudeAiService.kt` |
| On-device storage (SharedPreferences, JSON) | `commonMain/.../data/AppRepository.kt` |
| Camera / gallery capture | `androidMain/.../platform/Platform.android.kt` |
| Screens (Nutrition, Workouts + live session, Progress, Coach, Profile) | `commonMain/.../ui/` |

Android is the only fully supported target for now. iOS and Desktop compile, but they use
in-memory storage and have no camera or AI.

---

This is a Kotlin Multiplatform project targeting Android, iOS, Desktop (JVM).

* [/composeApp](./composeApp/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./composeApp/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./composeApp/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./composeApp/src/jvmMain/kotlin)
    folder is the appropriate location.

* [/iosApp](./iosApp/iosApp) contains iOS applications. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

### Build and Run Android Application

To build and run the development version of the Android app, use the run configuration from the run widget
in your IDE’s toolbar or build it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :composeApp:assembleDebug
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:assembleDebug
  ```

### Build and Run Desktop (JVM) Application

To build and run the development version of the desktop app, use the run configuration from the run widget
in your IDE’s toolbar or run it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :composeApp:run
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:run
  ```

### Build and Run iOS Application

To build and run the development version of the iOS app, use the run configuration from the run widget
in your IDE’s toolbar or open the [/iosApp](./iosApp) directory in Xcode and run it from there.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…