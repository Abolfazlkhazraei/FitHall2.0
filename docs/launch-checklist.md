# Launch checklist

Where each launch item lives in FitHall and what's still yours to do. ✅ = built and tested in the
code; 👤 = needs an action from you outside the code.

| # | Item | Status | Where / what's left |
|---|---|---|---|
| 1 | Real login and logout | ✅ | Firebase Authentication (email + password, Google, Facebook) through its REST API: `composeApp/.../auth/`. Log out in Profile → Account. The server checks the Firebase ID token on every AI request (`server/.../FirebaseAuth.kt`). 👤 Enable Email/Password in the Firebase console; for Google and Facebook see README → Firebase, step 3. |
| 2 | Password reset | ✅ | "Forgot password?" on the log-in screen. Firebase sends the email; the app never says whether an account exists. 60-second resend cooldown. |
| 3 | Email verification | ✅ | Sent on sign-up; the app waits on the "Check your email" screen until it's verified, and the server refuses AI requests from unverified accounts. 👤 Customise the email template in Firebase. |
| 4 | Loading states | ✅ | Spinners in every auth button, `LoadingCard` (with a "still working" note after 20 s) for AI requests, progress in the delete dialog. |
| 5 | Empty states | ✅ | Meals, workout plan, workout history, coach chat and check-ins each have one (`EmptyState` in `ui/Components.kt`). |
| 6 | Clear error messages | ✅ | `ErrorMessages` for AI/network errors, Firebase error codes mapped to plain sentences in `FirebaseAuthService.messageFor`, server errors are user-safe. |
| 7 | Form validation | ✅ | `AuthValidation` (email format, password ≥ 8 chars with letters and numbers, confirmation, terms); profile form checks age 18–100, height and weight ranges. Errors appear under the field after the first submit. The server validates every request again. |
| 8 | Rate limiting | ✅ | Server: per-account daily units, per-minute requests, global daily cap, sign-ups per IP (`UsageLimiter`). Firebase rate-limits sign-in attempts. |
| 9 | Database backups | ✅ | Android backs up each account's database and settings (`res/xml/data_extraction_rules.xml`; end-to-end encrypted only on Android 12+), never the sign-in session or device token. The server has no database; Firebase holds the accounts. |
| 10 | Row level security | ✅ | Health data never leaves the phone into a database, so it's enforced on the device: each account has its own database and settings files (`AndroidAccountStorage`), and a second account on the same phone can't see the first one's logs. |
| 11 | API keys off the frontend | ✅ | The Anthropic key exists only on the server. The app contains the Firebase Web API key, which Google designs to be public; 👤 restrict it to `com.fithall.app` + your SHA-1s in Google Cloud (README → Firebase, step 6). |
| 12 | Mobile layout | ✅ | Built mobile-first; auth and consent screens scroll and move with the keyboard; on tablets/landscape content is capped at a readable width. |
| 13 | Custom 404 page | ✅ | The server answers unknown addresses with a branded HTML page in browsers and JSON `not_found` for the app. |
| 14 | Analytics installed | ✅ | Google Analytics for Firebase, **opt-in** (consent screen or Profile → Your data), no ad ID, no user ID, no health values. Events: onboarding, meals logged (by method), plans generated, workouts finished, check-ins, coach messages, screen views. |
| 15 | Error tracking | ✅ | Firebase Crashlytics (opt-in) with non-fatal reports for unexpected errors; server logs every unhandled error. |
| 16 | Privacy policy page | ✅ 👤 | `docs/privacy-policy.md`, published by the server at `/privacy`, updated for accounts and analytics. 👤 Fill in the [BRACKETED] items and have it reviewed. |
| 17 | Terms page | ✅ 👤 | `docs/terms.md`, published at `/terms`; accepted at sign-up. 👤 Fill in and have it reviewed. |
| 18 | Delete account page | ✅ | In the app: Profile → Account → Delete account (password check, deletes the Firebase account and the phone's data). On the web: `/delete-account` (the URL Google Play asks for). |
| 19 | Onboarding flow | ✅ | Welcome pages → sign up → verify email → consent → profile questions → first plans generated. |
