# FitHall

An AI fitness app: snap a photo of your plate, say what you ate, or scan a barcode to count your
macros, get a workout plan and a meal
plan generated for your level, activity and goal, log your sessions set by set, track body
weight and strength progress, and chat with an AI coach that knows your profile, today's meals,
your plans and your recent training. Every week an AI check-in reviews your progress and proposes
calorie and training changes you can accept with one tap. AI is powered by Claude (`claude-opus-5-5`).

## Setup

The app never talks to Claude directly. It talks to the FitHall server in [`server/`](server),
which holds the Anthropic API key, checks every request, and enforces per-device and global
usage limits. The app contains no key.

### 1. Run the server

Get an API key from the [Claude Console](https://platform.claude.com/), then from the project root:

```shell
export ANTHROPIC_API_KEY=sk-ant-...
export TOKEN_SECRET=$(openssl rand -base64 48)   # any random string of 32+ characters
./gradlew :server:run
```

On Windows PowerShell use `$env:ANTHROPIC_API_KEY="sk-ant-..."` and `$env:TOKEN_SECRET="..."`, then
`.\gradlew.bat :server:run`. The server listens on port 8080; `GET /health` returns `ok`.

| Variable | Default | |
|---|---|---|
| `ANTHROPIC_API_KEY` | required | |
| `TOKEN_SECRET` | required | Signs device tokens. Changing it makes every install re-register automatically. |
| `PORT` | `8080` | |
| `TRUST_PROXY` | `false` | Set `true` behind a load balancer that sets `X-Forwarded-For`, so per-IP limits see real client IPs. |
| `DEVICE_UNITS_PER_DAY` | `40` | Per install. Costs: text meal 1, coach 1, photo meal 2, meal plan 3, workout plan 4, check-in 4. |
| `DEVICE_REQUESTS_PER_MINUTE` | `10` | |
| `GLOBAL_UNITS_PER_DAY` | `20000` | Hard daily cap on what the server will spend. |
| `REGISTRATIONS_PER_IP_PER_HOUR` | `20` | Slows down token farming. |

### 2. Point the app at it

By default the debug app uses `http://10.0.2.2:8080`, which is your computer as seen from the
Android emulator, so with the server running you can just start the app. For a real phone, add
your computer's LAN address, or your deployed server, to `local.properties`:

```properties
BACKEND_URL=http://192.168.1.20:8080
```

Plain HTTP is only allowed to `10.0.2.2` and `localhost`; any other address must be HTTPS, or
you need to add it to `composeApp/src/androidMain/res/xml/network_security_config.xml` for
local testing.

### 3. Deploy

Build the jar and the container image, then run it on any container host (Cloud Run, Fly.io,
Render, a VPS...) with the environment variables above:

```shell
./gradlew :server:buildFatJar
docker build -t fithall-server -f server/Dockerfile .
```

Put it behind HTTPS (most hosts do this for you), set `TRUST_PROXY=true` if the host uses a
load balancer, and set `BACKEND_URL=https://your-server` before building the release app. Plan
and check-in requests can take up to a couple of minutes, so allow long request timeouts.

**Before scaling out:** usage limits are kept in memory, so they're per server instance. Run a
single instance, or move the counters in `UsageLimiter` to a shared store such as Redis. Device
tokens are anonymous and can't be revoked individually; adding Google Play Integrity checks at
registration is the next step to stop scripted clients.

## How it's organised

| Area | Where |
|---|---|
| Data models, AI prompts and schemas, API contract (shared by app and server) | `shared/` |
| Server: routes, validation, limits, device tokens, Claude calls | `server/` |
| App client for the server | `composeApp/.../ai/BackendAiService.kt` |
| Calorie & macro targets (Mifflin-St Jeor, deterministic) | `commonMain/.../domain/MacroCalculator.kt` |
| Workout stats, streaks, estimated 1RM, set prefill | `commonMain/.../domain/ProgressCalculator.kt` |
| Weekly check-in: weight trend, adherence, stalled lifts, bounded changes | `commonMain/.../domain/CheckInCalculator.kt` |
| Barcode lookup (Open Food Facts) and portion math | `commonMain/.../data/FoodDatabase.kt`, `androidMain/.../data/OpenFoodFactsDatabase.kt` |
| Voice input and barcode scanner (Android) | `androidMain/.../platform/FoodInput.android.kt` |
| On-device storage (SharedPreferences, JSON) | `commonMain/.../data/AppRepository.kt` |
| Camera / gallery capture | `androidMain/.../platform/Platform.android.kt` |
| Screens (Nutrition, Workouts + live session, Progress, Coach, Profile) | `commonMain/.../ui/` |

Android is the only fully supported target for now. Desktop and iOS use the server for AI too,
but they keep data in memory only and have no camera, voice or barcode input.

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