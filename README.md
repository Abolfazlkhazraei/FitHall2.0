# FitHall

An AI fitness app: snap a photo of your plate, say what you ate, or scan a barcode to count your
macros, get a workout plan and a meal
plan generated for your level, activity and goal, log your sessions set by set, track body
weight and strength progress, and chat with an AI coach that knows your profile, today's meals,
your plans and your recent training. Every week an AI check-in reviews your progress and proposes
calorie and training changes you can accept with one tap, with an optional reminder notification
when it's ready. Portions can be corrected on any food, and the app works in metric or imperial
units (everything is stored in kg and cm). Without a connection, logging keeps working, an
offline banner appears, and AI requests fail straight away with a Retry instead of timing out. AI is powered by Claude (`claude-opus-5-5`).

## Setup

The app never talks to Claude directly. It talks to the FitHall server in [`server/`](server),
which holds the Anthropic API key, checks every request, and enforces per-device and global
usage limits. The app contains no key. People sign in with an email and password (Firebase
Authentication) before using the app; see "Firebase" below.

### 1. Run the server

Get an API key from the [Claude Console](https://platform.claude.com/), then from the project root:

```shell
export ANTHROPIC_API_KEY=sk-ant-...
export TOKEN_SECRET=$(openssl rand -base64 48)   # any random string of 32+ characters
./gradlew :server:run
```

On Windows PowerShell use `$env:ANTHROPIC_API_KEY="sk-ant-..."` and `$env:TOKEN_SECRET="..."`, then
`.\gradlew.bat :server:run`. The server listens on port 8080; `GET /health` returns `ok`.
For development, `server/run-dev.ps1` (Windows) or `server/run-dev.sh` does all of this, asks for
the key without echoing it, and turns accounts on automatically when
`composeApp/google-services.json` exists.

The server also publishes the web pages Google Play asks for: `/privacy` and `/terms` (rendered
from `docs/privacy-policy.md` and `docs/terms.md`), `/delete-account`, and a friendly 404 page.

| Variable | Default | |
|---|---|---|
| `ANTHROPIC_API_KEY` | required | |
| `TOKEN_SECRET` | required | Signs device tokens. Changing it makes every install re-register automatically. |
| `PORT` | `8080` | |
| `TRUST_PROXY` | `false` | Set `true` behind a load balancer that sets `X-Forwarded-For`, so per-IP limits see real client IPs. |
| `DEVICE_UNITS_PER_DAY` | `40` | Per account (per install when accounts are off). Costs: text meal 1, coach 1, photo meal 2, meal plan 3, workout plan 4, check-in 4. |
| `DEVICE_REQUESTS_PER_MINUTE` | `10` | |
| `GLOBAL_UNITS_PER_DAY` | `20000` | Hard daily cap on what the server will spend. |
| `REGISTRATIONS_PER_IP_PER_HOUR` | `20` | Slows down token farming. |
| `INTEGRITY_MODE` | `off` | Play Integrity at registration: `off`, `monitor` (check and log, everyone gets full access) or `enforce`. See below. |
| `LIMITED_UNITS_PER_DAY` | `8` | In `enforce` mode, the allowance for devices that fail the check (no Google Play, rooted, modified app). `0` refuses them. |
| `APP_PACKAGE_NAME` | `com.fithall.app` | Verdicts for any other package are rejected. |
| `INTEGRITY_REQUIRE_DEVICE` | `true` | Require `MEETS_DEVICE_INTEGRITY`; `false` accepts `MEETS_BASIC_INTEGRITY`. |
| `INTEGRITY_REQUIRE_LICENSED` | `false` | Require the app to have been installed from Google Play. |
| `APP_CERT_SHA256` | none | Optional comma-separated signing certificate digests, as Play reports them. |
| `PLAN_EFFORT` | `medium` | Claude effort for workout and meal plans: `low`, `medium`, `high`, `xhigh` or `max`. Lower is faster and cheaper; check-ins always use `high`. |
| `FIREBASE_PROJECT_ID` | none | Turns accounts on: AI requests then need a signed-in user with a verified email. Must match the app's `google-services.json`. |
| `FIREBASE_WEB_API_KEY` | none | Lets the `/delete-account` web page delete accounts. The `current_key` from `google-services.json`. |
| `ADMIN_TOKEN` | none | Enables the spend report at `GET /admin/usage` (32+ characters). See "AI costs" below. |
| `GOOGLE_APPLICATION_CREDENTIALS` | none | Service account key used to decode integrity tokens (needed unless `INTEGRITY_MODE=off`). |

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
tokens are anonymous and can't be revoked individually; accounts can be disabled or deleted in
the Firebase console, which locks them out within an hour (when their ID token expires).

### AI costs

Every Claude response is logged on one line, with no content and no device id:

```
ai_usage op=MEAL_PHOTO model=claude-opus-5-5 input=1840 output=612 cache_read=0 cache_write=0 cost_usd=0.019600 ms=6120 stop=end_turn
```

The server also keeps daily totals for the last 31 days in memory (they reset when it restarts;
the log lines are the permanent record). With `ADMIN_TOKEN` set, fetch them with:

```shell
curl -H "Authorization: Bearer $ADMIN_TOKEN" https://your-server/admin/usage
```

For each day and operation you get requests, tokens, total and average cost, and **cost per
allowance unit**. The top of the report turns that into `maxCostPerDevicePerDayUsd`, which is what
one device costs per day if it uses its whole `DEVICE_UNITS_PER_DAY`. Compare it with what you
plan to charge before choosing limits or a price. For the current day there's also the spread of
cost across active devices (median, 90th percentile, max).

Costs are estimates from list prices in `ModelPricing` (in `AiUsage.kt`). Check them against
https://www.anthropic.com/pricing, and use the Anthropic Console's usage page and a monthly spend
limit as the source of truth. When a request is declined and retried on a fallback model, only the
final attempt's tokens are counted.

### Firebase: accounts, crash reporting and analytics

1. Create a project at https://console.firebase.google.com and add an Android app with package
   name `com.fithall.app`.
2. Download `google-services.json` into `composeApp/` (it's git-ignored; on CI, write it from a
   secret before building).
3. **Authentication → Sign-in method:** enable **Email/Password**. Under **Settings**, keep
   **Email enumeration protection** on. Under **Templates**, set the sender name and check the
   verification and password-reset emails.
   - **Google:** enable **Google**, then in Project settings → your Android app add the SHA-1
     fingerprints of your debug and release keys (`./gradlew signingReport`), and download
     `google-services.json` again. The new file contains the web client ID the
     "Continue with Google" button needs; without it the button is hidden.
   - **Facebook:** create an app at https://developers.facebook.com (use case "Authenticate and
     request data from users with Facebook Login"), add the Android platform with package
     `com.fithall.app`, class `com.fithall.app.MainActivity` and your key hashes. Enable
     **Facebook** in Firebase with the app's App ID and App secret, and paste Firebase's OAuth
     redirect URI into Facebook Login → Settings. Then add `FACEBOOK_APP_ID` and
     `FACEBOOK_CLIENT_TOKEN` (App settings → Advanced) to `local.properties`. Without them the
     "Continue with Facebook" button is hidden. Facebook requires the app to be switched to Live
     before people outside your developer team can sign in.
   - Google and Facebook accounts don't go through email verification; the server accepts them
     as verified.
4. **Crashlytics** and **Analytics:** open both for the app. In Google Analytics, set data
   retention (Admin → Data collection → Data retention) and put it in the privacy policy.
5. Start the server with `FIREBASE_PROJECT_ID` (and `FIREBASE_WEB_API_KEY` for the web deletion
   page); the dev scripts read both from `google-services.json`.
6. Recommended: in Google Cloud → APIs & Services → Credentials, restrict the Android API key to
   Android apps with package `com.fithall.app` and your debug and release SHA-1 fingerprints. The
   app sends the matching headers. The web deletion page uses the same key from a browser, so if
   you restrict it, create a separate browser key restricted to your server's domain for
   `FIREBASE_WEB_API_KEY`.

Without the file the app builds with accounts, crash reporting and analytics off (everyone uses
one local profile); release builds require it. Crash reports and analytics are only sent for users
who opt in (consent screen or Profile → Your data), never carry a user id or health data, and
analytics never collect the advertising ID. Release builds upload the R8 mapping file
automatically, so stack traces are readable.

### 4. Turn on Play Integrity

Registration asks Google Play to vouch that the request comes from your genuine app on a real
Android device, so scripted clients can't farm tokens. It only works once the app is on Google
Play (an internal testing track is enough); sideloaded debug builds can't pass.

1. In the Play Console, open **Test and release → App integrity → Play Integrity API** and link a
   Google Cloud project. In that project, enable the **Google Play Integrity API**.
2. Add the project *number* (not the id) to `local.properties` and rebuild the app:
   ```properties
   PLAY_CLOUD_PROJECT_NUMBER=123456789012
   ```
3. In the same Cloud project, create a service account, download a JSON key, and give the
   server `GOOGLE_APPLICATION_CREDENTIALS=/path/to/key.json` (or use the host's built-in
   service account on Google Cloud).
4. Start with `INTEGRITY_MODE=monitor`, install the app from your testing track, and check the
   server log for `Registration integrity check: ... passed=true`. Then switch to `enforce`.

Existing installs re-register automatically the first time they talk to the updated server.
Google's default quota for decoding tokens is 10,000 a day, and each install uses one.

## Release builds

A release build refuses to start until it has an HTTPS server address and a signing key. It's
also shrunk with R8, so the APK is about a tenth of the debug size. Add to `local.properties`
(or set environment variables with the same names on CI):

```properties
RELEASE_BACKEND_URL=https://api.your-domain.com
# Optional: only if the policy is hosted somewhere other than RELEASE_BACKEND_URL/privacy
# PRIVACY_POLICY_URL=https://your-domain.com/privacy
RELEASE_STORE_FILE=/path/to/upload-key.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=upload
RELEASE_KEY_PASSWORD=...
VERSION_CODE=1
VERSION_NAME=1.0
```

Create the upload key once with
`keytool -genkeypair -keystore upload-key.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000`,
keep it and its passwords somewhere safe outside the repo (`*.jks` files are git-ignored), and
enroll in Play App Signing when you first upload. Then build the bundle for Google Play:

```shell
./gradlew :composeApp:bundleRelease
```

The `.aab` is in `composeApp/build/outputs/bundle/release/`. Increase `VERSION_CODE` for every
upload. Shrinking can break code that relies on reflection, so test the release build itself
(an internal testing track is the easiest way) before promoting it.

## Branding (placeholder)

The launcher icon (an adaptive icon with a themed-icon layer), splash screen, colours and the Play
Store artwork are placeholders: a white dumbbell on FitHall green (`#1E8E5A`). Colours live in
`composeApp/.../ui/Theme.kt` (light and dark schemes, all text pairs WCAG AA) and
`res/values*/colors.xml` for the window background.

`docs/store/make_icons.py` generates the icon vectors and `docs/store/play-icon-512.png` /
`feature-graphic-1024x500.png` from one set of shapes. To rebrand, replace the shapes and colours
there (or drop in your own vector drawables) and update `Theme.kt`. You still need phone
screenshots for the store listing.

## Storage

Each account that signs in on a phone gets its own storage: a database `fithall_<uid>.db` and
preferences `fithall_<uid>.xml`, so one person never sees another's logs (the on-device
equivalent of row-level security; the server stores no user data, so there's nothing to secure
there). Logging out keeps the data for when that account logs back in. The device-level
`fithall.xml` holds only the device token and sign-in session. Data from before accounts is handed
to the first account that signs in.

Meals, workouts, weigh-ins, check-ins and coach chat are stored in SQLite through
[SQLDelight](https://sqldelight.github.io/sqldelight/). Each row keeps the columns used for lookups
(id, day, time) plus the full record as JSON, so every change writes one row. The profile, plans,
the workout in progress, targets and consent stay in SharedPreferences. Installs from before the
database copy their old data in once, on first launch.

**Backups:** Android backs up each account's database and preferences to the user's Google account
(`res/xml/data_extraction_rules.xml`; on Android 12+ only when end-to-end encrypted), so they come
back on a new phone after signing in. The device token and sign-in session are excluded. The
server has no database to back up; Firebase backs up the accounts.

The schema is in `composeApp/src/commonMain/sqldelight/com/fithall/app/db/History.sq`. **After the
first release, never edit a table in place:** add a numbered migration file (`1.sqm`, `2.sqm`...)
next to it, or existing users' apps will fail to open their database. iOS and Desktop still keep
everything in memory.

## Privacy

- The app opens with a consent screen that explains what's sent where, and asks for explicit
  agreement to processing health data plus confirmation of 18+. Bumping `PrivacyPolicy.VERSION`
  (in `AppRepository.kt`) asks everyone again.
- Sign-up requires accepting the Terms of Use and Privacy Policy and confirming 18+.
- Profile → Account has **Log out** and **Delete account** (re-checks the password, deletes the
  Firebase account and everything stored on the phone for it). `/delete-account` on the server
  does the same from a browser, which is the link Google Play asks for.
- Profile → Your data has the privacy policy and terms links, the crash-report and analytics
  switches, **Export my data** (a JSON file through the share sheet) and **Delete all my data**
  (wipes the account's storage and cached files, and withdraws consent).
- The user's name never leaves the device, camera photos are deleted after processing, and the
  server stores no user data.
- [`docs/privacy-policy.md`](docs/privacy-policy.md) and [`docs/terms.md`](docs/terms.md) are
  drafts matching this behaviour: fill in the bracketed items and have them reviewed. The server
  publishes them at `/privacy` and `/terms`.
  [`docs/play-data-safety.md`](docs/play-data-safety.md) has the matching Play Console answers.

## Safety rules

These override the user's choices and anything the AI suggests (`shared/.../model/Safety.kt`):

- FitHall is for adults: the profile and the server accept ages 18 to 100 only.
- If BMI is below 18.5, "Lose fat" can't be chosen, and an existing fat-loss goal is treated as
  maintenance. Targets never drop below maintenance, and earlier check-in cuts are ignored.
- Check-ins can't lower calories while the user is underweight or losing more than 1% of body
  weight a week; calories never go below 1,500 kcal (male) / 1,200 kcal (female).
- The meal-plan, coach and check-in prompts forbid extreme restriction and steer users who
  show signs of disordered eating to professional help.

## How it's organised

| Area | Where |
|---|---|
| Data models, AI prompts and schemas, API contract (shared by app and server) | `shared/` |
| Server: routes, validation, limits, device tokens, Claude calls | `server/` |
| App client for the server | `composeApp/.../ai/BackendAiService.kt` |
| Calorie & macro targets (Mifflin-St Jeor, deterministic) | `commonMain/.../domain/MacroCalculator.kt` |
| Workout stats, streaks, estimated 1RM, set prefill | `commonMain/.../domain/ProgressCalculator.kt` |
| Weekly check-in: weight trend, adherence, stalled lifts, bounded changes | `commonMain/.../domain/CheckInCalculator.kt` |
| Portion editing (scales macros from the original estimate) | `commonMain/.../domain/Portions.kt`, `ui/MealEditor.kt` |
| Metric / imperial conversions | `shared/.../model/Units.kt`, `ui/UnitFormat.kt` |
| Check-in reminder (decision logic; WorkManager job and notification on Android) | `commonMain/.../domain/CheckInReminder.kt`, `androidMain/.../platform/Reminders.android.kt` |
| Barcode lookup (Open Food Facts) and portion math | `commonMain/.../data/FoodDatabase.kt`, `androidMain/.../data/OpenFoodFactsDatabase.kt` |
| Voice input and barcode scanner (Android) | `androidMain/.../platform/FoodInput.android.kt` |
| On-device storage: history in SQLite (SQLDelight), single values in SharedPreferences | `commonMain/.../data/HistoryStore.kt`, `AppRepository.kt`, `src/commonMain/sqldelight/` |
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