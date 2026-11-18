# Connecting TopicPulse to a Firebase project

TopicPulse signs people in with Google and keeps channels, messages and attachments in Firestore.
This is everything the app needs from a Firebase project, and what to do when moving it to a new one.

TopicPulse has its own Firebase project (free Spark plan), separate from
[Gatekeeper](https://github.com/Anandhavaradhan/Gatekeeper-Universal-Chat)'s. A named, non-default Firestore
database needs the paid Blaze plan, so two free projects is simpler than one project with two databases.

## 1. In the Firebase console

**Create the project**, then:

### Firestore
Create the database (the console calls it `(default)`) in a region close to you, in **production mode**.
That is the one this app uses.

### Authentication
**Sign-in method → Google → Enable.** Pick a support email. Nothing else is needed for this app.

### The Android app
**Project settings → Your apps → Add app → Android.**

- Package name: `com.aistudio.topicpulse.xk9zp` (it must match exactly; it is set in `app/build.gradle.kts`)
- SHA-1 certificate fingerprint: add the key that signs the APK you install.

The APKs built by `.github/workflows/build.yml` are signed with the committed `ci/debug.keystore`, whose
fingerprint is fixed:

```
SHA-1  26:4A:CB:60:50:7D:92:C8:7F:2B:9A:99:0E:3B:E9:4E:AE:A5:0C:F7
```

To read it yourself, or to read the fingerprint of any other key:

```bash
keytool -list -v -keystore ci/debug.keystore -storepass android -alias androiddebugkey | grep SHA1
apksigner verify --print-certs FocusChat.apk        # the fingerprint of an APK you already have
```

Add a fingerprint for every key you sign with: the CI key above, your own Android Studio debug key
(`~/.android/debug.keystore`, password `android`), and later any release or Play upload key. They can all
be registered at once. **Google sign-in fails on an APK whose fingerprint is not registered**, usually
looking like "no credentials available" rather than a configuration error.

Then **download `google-services.json`**.

## 2. In this repository

Replace two things:

| File | What changes |
|---|---|
| `app/google-services.json` | The whole file, as downloaded |
| `app/src/main/res/values/firebase_applet_config.xml` | `firestore_database_id` → `(default)` |
| `firebase.json` | the `database` name, so rules deploy to the right place |

The app reads the database id from that string resource (`ChatViewModel.Factory`), so nothing else is hard-coded.

## 3. Deploy the rules

`firestore.rules` restricts channels, messages and attachments to their members. It must be deployed, or
every read fails:

```bash
npx firebase-tools login
npx firebase-tools deploy --only firestore:rules --project focus-apps-topicpulse
```

## 4. Check it works

Install a fresh APK (the CI one, or `gradle :app:assembleDebug`), sign in with Google, and create a channel.
If sign-in fails, work through the list in the main README: the provider must be enabled, the device needs a
Google account, and the installed APK must be signed with a registered key.

Nothing carries over from another Firebase project: accounts, channels and messages all start empty.
