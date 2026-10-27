<!--
DRAFT. Have it reviewed by a lawyer before publishing, fill in every [BRACKETED] item, and
publish it. The FitHall server serves this file at /privacy, and the app links there unless
PRIVACY_POLICY_URL is set. If you change what data the app sends, update this
policy and bump PrivacyPolicy.VERSION in AppRepository.kt so users are asked to agree again.
-->

# FitHall Privacy Policy

**Effective date:** [DATE]
**App:** FitHall (Android, package `com.fithall.app`)
**Provider ("we", "us"):** [YOUR NAME OR COMPANY], [ADDRESS]
**Contact:** [PRIVACY CONTACT EMAIL]

FitHall is a fitness and nutrition app. It estimates the nutrition of your meals, creates workout
and meal plans, runs a weekly check-in and offers an AI coach. This policy explains what data
FitHall uses, where it goes, and the choices you have.

## The short version

- You sign in with an **email address and password**, or with **Google** or **Facebook**, managed by
  Google Firebase Authentication.
- What you log (profile, meals, workouts, weigh-ins, plans, chats) is **stored on your phone**,
  kept separately for each account that signs in on it.
- When you use an AI feature, the data that feature needs is sent over an encrypted connection
  to **our server**, which passes it to **Anthropic's Claude AI** to generate the answer. Our
  server doesn't store it.
- **Your name never leaves your phone.**
- You can **export** or **delete** all your data in the app at any time (Profile → Your data),
  and **delete your account** in the app or at [/delete-account](/delete-account).
- Usage analytics and crash reports are **optional and off** unless you turn them on.
- FitHall is for adults aged **18 and over**.

## 1. Data FitHall uses

| Data | Examples | Where it's kept |
|---|---|---|
| Profile | Name, age, sex, height, weight, activity level, experience, goal, equipment, training days, injuries or limitations, dietary preferences or allergies | On your phone. Your name is never sent anywhere. |
| Health and fitness logs | Meals and their estimated calories and macros, body weight, workouts, sets and reps, weekly check-ins, plans | On your phone |
| Meal photos | Photos you take or choose for meal analysis | Sent for analysis only. Camera photos are deleted from the app's storage straight after they're processed; FitHall doesn't keep them. |
| Meal descriptions | What you type or say, e.g. "two eggs and toast" | Sent for analysis only |
| Coach messages | Your chat with the AI coach | On your phone; sent to generate replies |
| Account | Your email address, a password (stored only as a secure hash by Firebase) or, if you use Google or Facebook, your ID with that service and the name and photo it shares, whether your email is verified, and a random account ID | Google Firebase Authentication |
| Device token | A random identifier created when the app is first used | On your phone; sent with each request |
| Barcodes | The number on a product you scan | Sent to Open Food Facts to look up the product |

| Usage analytics (only if you opt in) | Which screens you open and which features you use (for example "meal logged by photo", "plan generated"), app version, phone model, approximate country, and a random app-instance ID. Never your health values, meal contents, name or email. | Sent to Google Analytics for Firebase |
| Crash reports (only if you opt in) | What the app was doing when it crashed or hit an unexpected error (technical stack trace), phone model, Android version, app version, and a random Crashlytics installation ID | Sent to Google Firebase Crashlytics |

FitHall doesn't show ads, doesn't collect the advertising ID, and doesn't sell your data.

## 2. How each feature uses your data

**Your account.** When you sign up, your email address and password go to Google Firebase
Authentication, which stores the account for us as our service provider and sends the
verification and password-reset emails. If you choose **Continue with Google** or **Continue with
Facebook**, that service confirms who you are and shares your email address, name and profile
photo with Firebase; we don't use the name or photo, and we never see your Google or Facebook
password. Facebook Login runs on your phone only while you sign in, with its automatic event
logging and advertising-ID collection turned off. Google's and Facebook's own privacy policies
cover what they do when you sign in with them. Each request to our server includes a short-lived
sign-in token so we can check that it comes from a verified account and apply usage limits per
account. Your account isn't linked to your logs on our side: those stay on your phone.

**AI features** (meal estimates from photos or descriptions, workout and meal plans, weekly
check-ins, coach chat). The app sends the information the feature needs over HTTPS to our server
at [YOUR SERVER DOMAIN]. Our server checks the request, applies usage limits, and sends it to
Anthropic, PBC, which provides the Claude AI models, to generate the result. The result is sent
back to your phone and saved there. Depending on the feature, the request includes: your age,
sex, height, weight, activity level, experience, goal, equipment, training schedule, injuries and
dietary notes; a meal photo or description; recent workouts and weigh-ins; your calorie targets;
and your coach messages.

**Our server does not store this data.** It keeps no database of users or their content and
doesn't log request contents. For rate limiting and cost monitoring it keeps, in memory only, the
number of requests and the estimated AI cost per account (or device token) for the current day, and, for up
to one hour, the IP addresses that created new device tokens. Its logs record each AI request's
type, token counts and estimated cost, without any content or device token. Our hosting provider, [HOSTING PROVIDER], may keep standard connection logs (such
as IP addresses) under its own policy for [RETENTION PERIOD].

**Anthropic** processes the data as our service provider under its commercial terms.
[VERIFY AGAINST ANTHROPIC'S CURRENT COMMERCIAL TERMS AND PRIVACY POLICY BEFORE PUBLISHING:
whether and for how long Anthropic retains API inputs and outputs, and that it does not use them
to train its models.] See https://www.anthropic.com/legal/privacy.

**Barcode scanning.** The scanner runs in Google Play services on your phone. Only the barcode
number is sent to Open Food Facts (https://world.openfoodfacts.org), a public, open food database,
to look up the product's nutrition label. Open Food Facts' privacy policy applies to that request.

**Voice input.** When you tap the microphone, your phone's built-in speech recognition service
(usually Google's) converts your speech to text. FitHall receives only the text, never the audio.
That service's privacy policy applies to the audio.

**Device check (Play Integrity).** When the app first connects to our server, it may ask Google
Play to confirm that it is the genuine FitHall app on a genuine Android device. Google provides
the verdict under Google's privacy policy; we use it only to protect the service from abuse and
don't store it.

**Crash reports (optional).** If you tick "send crash reports" on the first screen, or turn it on
later in Profile → Your data, the app sends crash and error reports to Google Firebase
Crashlytics, which processes them for us as a service provider. Reports contain technical
information only: the error and where in the app's code it happened, your phone model, Android
version, app version and a random installation ID. We don't attach a user id, your name, or any
of your health and fitness data. Turning the option off stops reports and deletes any that
haven't been sent yet. Google keeps crash reports for [VERIFY: currently 90 days per the
Crashlytics documentation]; see https://firebase.google.com/support/privacy.

**Usage analytics (optional).** If you tick "share usage analytics" on the consent screen, or
turn it on later in Profile → Your data, the app sends usage events to Google Analytics for
Firebase so we can see which features are used and where people get stuck. Events never include
your health values, meal contents, name or email, and we don't set a user ID or collect the
advertising ID. Turning it off stops collection. Google keeps analytics data for [VERIFY: the
retention period set in your Google Analytics property, e.g. 2 months].

**Backups.** If Android backup is turned on for your device, Android may include FitHall's data
in your Google account backup so it can be restored on a new phone. On Android 12 and later,
FitHall only allows this backup when it is end-to-end encrypted with your screen lock. Your
sign-in and device token are never backed up: after a restore, sign in again to see your data.
You can manage backup in your phone's settings.

## 3. Why we process your data (legal bases)

- **Your explicit consent**, given on the first screen of the app, for processing your health
  and fitness data to provide the AI features (GDPR Article 9(2)(a)). You can withdraw it at any
  time by deleting your data in the app.
- **Providing the service you asked for** (contract) for the device token and request handling.
- **Our legitimate interest** in keeping the service secure and preventing abuse, for rate
  limiting and device checks.
- **Providing the service you asked for** (contract) for your account.
- **Your consent** for crash reports and usage analytics, which are optional and off unless you
  turn them on.

## 4. How long data is kept

- **On your phone:** until you delete it in the app, clear the app's data, or uninstall it.
- **Your account:** until you delete it. Deleting it removes it from Firebase Authentication.
- **On our server:** not stored. Usage counters reset daily; sign-up IP addresses are held in
  memory for up to one hour.
- **Anthropic:** [FILL IN FROM ANTHROPIC'S TERMS].
- **Crash reports (if enabled):** kept by Firebase Crashlytics for [VERIFY: 90 days].
- **Analytics (if enabled):** kept by Google Analytics for [VERIFY: your property's retention setting].

## 5. Your choices and rights

- **Export:** Profile → Your data → Export my data gives you everything as a JSON file.
- **Delete your data:** Profile → Your data → Delete all my data permanently erases everything
  FitHall holds on your phone for your account. It also withdraws your consent.
- **Delete your account:** Profile → Account → Delete account deletes your Firebase account and
  everything FitHall holds on your phone for it. You can also delete your account on the web at
  [/delete-account](/delete-account). Because our server stores none of your logs, this removes
  your data from FitHall completely.
- Depending on where you live (for example the EU, UK or US states such as California or
  Washington), you may have further rights, such as access, correction, restriction, objection,
  or complaining to your data protection authority. Contact us at [PRIVACY CONTACT EMAIL].

## 6. Children

FitHall is for adults aged 18 and over. The app asks you to confirm your age, and doesn't create
plans for anyone under 18. If you believe someone under 18 has used FitHall, contact us.

## 7. Health information

FitHall gives general fitness and nutrition guidance. It isn't medical advice and isn't a medical
device. It applies safety limits: it won't set calorie deficits for people who are underweight or
losing weight too quickly. Talk to a doctor before changing your diet or exercise if you have a
medical condition, an injury, are pregnant, or have a history of disordered eating.

## 8. Security

Data sent between the app and our server is encrypted with HTTPS. Data on your phone is protected
by Android's app sandbox. No method of storage or transmission is completely secure, but we limit
what we collect and don't store your data on our servers.

## 9. International transfers

Our server is hosted in [REGION], and Anthropic may process data in the United States and other
countries. Where required, transfers rely on [STANDARD CONTRACTUAL CLAUSES / ADEQUACY DECISIONS /
OTHER SAFEGUARDS].

## 10. Changes

If we change this policy in a way that affects how your data is used, the app will ask you to
review and agree again before you continue.

## 11. Contact

[YOUR NAME OR COMPANY], [ADDRESS], [PRIVACY CONTACT EMAIL]
