<!--
DRAFT. Have it reviewed by a lawyer before publishing, fill in every [BRACKETED] item, and
host it on a public https page (for example GitHub Pages or your own site). Set that address as
PRIVACY_POLICY_URL so the app links to it. If you change what data the app sends, update this
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

- What you log (profile, meals, workouts, weigh-ins, plans, chats) is **stored on your phone**.
- When you use an AI feature, the data that feature needs is sent over an encrypted connection
  to **our server**, which passes it to **Anthropic's Claude AI** to generate the answer. Our
  server doesn't store it.
- **Your name never leaves your phone.**
- You can **export** or **delete** all your data in the app at any time (Profile → Your data).
- FitHall is for adults aged **18 and over**.

## 1. Data FitHall uses

| Data | Examples | Where it's kept |
|---|---|---|
| Profile | Name, age, sex, height, weight, activity level, experience, goal, equipment, training days, injuries or limitations, dietary preferences or allergies | On your phone. Your name is never sent anywhere. |
| Health and fitness logs | Meals and their estimated calories and macros, body weight, workouts, sets and reps, weekly check-ins, plans | On your phone |
| Meal photos | Photos you take or choose for meal analysis | Sent for analysis only. Camera photos are deleted from the app's storage straight after they're processed; FitHall doesn't keep them. |
| Meal descriptions | What you type or say, e.g. "two eggs and toast" | Sent for analysis only |
| Coach messages | Your chat with the AI coach | On your phone; sent to generate replies |
| Device token | A random identifier created when the app is first used | On your phone; sent with each request |
| Barcodes | The number on a product you scan | Sent to Open Food Facts to look up the product |

| Crash reports (only if you opt in) | What the app was doing when it crashed or hit an unexpected error (technical stack trace), phone model, Android version, app version, and a random Crashlytics installation ID | Sent to Google Firebase Crashlytics |

FitHall doesn't use advertising, advertising IDs or analytics trackers, and it doesn't sell your data.

## 2. How each feature uses your data

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
number of requests and the estimated AI cost per device token for the current day, and, for up
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

**Backups.** If Android backup is turned on for your device, Android may include FitHall's data
in your Google account backup, encrypted, so it can be restored on a new phone. You can manage
this in your phone's settings.

## 3. Why we process your data (legal bases)

- **Your explicit consent**, given on the first screen of the app, for processing your health
  and fitness data to provide the AI features (GDPR Article 9(2)(a)). You can withdraw it at any
  time by deleting your data in the app.
- **Providing the service you asked for** (contract) for the device token and request handling.
- **Our legitimate interest** in keeping the service secure and preventing abuse, for rate
  limiting and device checks.
- **Your consent** for crash reports, which are optional and off unless you turn them on.

## 4. How long data is kept

- **On your phone:** until you delete it in the app, clear the app's data, or uninstall it.
- **On our server:** not stored. Usage counters reset daily; sign-up IP addresses are held in
  memory for up to one hour.
- **Anthropic:** [FILL IN FROM ANTHROPIC'S TERMS].
- **Crash reports (if enabled):** kept by Firebase Crashlytics for [VERIFY: 90 days].

## 5. Your choices and rights

- **Export:** Profile → Your data → Export my data gives you everything as a JSON file.
- **Delete:** Profile → Your data → Delete all my data permanently erases everything FitHall holds
  on your phone. Because our server stores none of it, this removes your data from FitHall
  completely. It also withdraws your consent.
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
