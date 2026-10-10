# Google Play Data safety and Health apps answers

Suggested answers for Play Console, based on what the code does as of this commit. Re-check them
whenever the app starts sending new data. Play's definitions: **collected** means it leaves the
device (to you or a service provider); **shared** means it goes to a third party *other than* a
service provider acting on your behalf. Anthropic is your service provider (it processes requests
for you), so sending data to it is collection, not sharing. Open Food Facts receives only a
barcode, which isn't user data.

## Data collection and security

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | Yes |
| Is all of the user data collected by your app encrypted in transit? | Yes (HTTPS; release builds refuse a non-https server) |
| Do you provide a way for users to request that their data is deleted? | Yes: in the app, Profile → Your data → Delete all my data |

## Data types

| Category → type | Collected | Shared | Processed ephemerally? | Required or optional | Purposes |
|---|---|---|---|---|---|
| Health and fitness → Health info (weight, height, injuries, dietary notes) | Yes | No | Yes | Optional (only when using AI features) | App functionality |
| Health and fitness → Fitness info (workouts, activity level, goals) | Yes | No | Yes | Optional | App functionality |
| Personal info → Other info (age, sex) | Yes | No | Yes | Optional | App functionality |
| Photos and videos → Photos (meal photos) | Yes | No | Yes | Optional | App functionality |
| Messages → Other in-app messages (coach chat) | Yes | No | Yes | Optional | App functionality |
| Device or other IDs (random device token) | Yes | No | No | Required to use AI features | App functionality, Fraud prevention, security and compliance |

Not collected: name (it stays on the device), email, location, contacts, financial info, audio
(speech is converted to text by the phone's own speech service), app activity analytics,
advertising ID.

"Processed ephemerally" is yes for the health data, photos and messages because the server only
uses them to answer the request and doesn't store them. **Confirm Anthropic's retention terms
first:** if Anthropic keeps API data beyond the request (for example for abuse monitoring), check
whether Play still considers that ephemeral, and answer "No" if in doubt.

## Health apps declaration

- Categories: **Fitness** (activity and workout tracking, training plans) and **Nutrition and
  weight management** (meal and calorie tracking).
- The app does not use Health Connect.
- Not a medical device; it offers no diagnosis or treatment. Say this in the store description
  too, along with the 18+ age requirement.

## Other Play Console items

- **Target audience:** 18 and over.
- **Privacy policy URL:** the public page where you host `docs/privacy-policy.md` (the same
  address as `PRIVACY_POLICY_URL`).
- **Ads:** No.
- **App access:** reviewers can use every feature without an account. The AI features need your
  server to be running.
