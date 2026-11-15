<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/1e6f5955-a7ec-47f2-8c3e-7a25c555cacd

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device
7. If you have already published your app in AI Studio, please [request upload key reset](https://support.google.com/googleplay/android-developer/answer/9842756#zippy=%2Crequest-an-upload-key-reset) in Google Play Console.

## How FocusChat works

- **People**: everyone who signs in with Google appears in the people list, so friends must install the app and sign in once.
- **Channels and DMs**: only their members can see them (see `firestore.rules`). Pick people when you create a channel or start a DM.
- **Topic Guard**: Gemini checks drafts and messages against the channel topic. Strict channels warn before an off-topic message is sent.
- **Files**: the attach button opens the phone's file picker. Files up to 5 MB are scanned by Gemini, then stored in Firestore
  (`channels/{id}/files/{fileId}` in base64 chunks) and opened with **Open file**.
- **Calls**: the call buttons start a real call in a new [Jitsi Meet](https://meet.jit.si) room and post a **Join** button to the chat.
  The call opens in the Jitsi Meet app if installed, otherwise in the browser. On meet.jit.si, the first person in the room may be asked to sign in to start the meeting.

### Deploy the rules

```bash
npx firebase-tools deploy --only firestore:rules --project gen-lang-client-0973407933
```
(the database id is `ai-studio-android-focuscha-1e6f5955-a7ec-47f2-8c3e-7a25c555cacd`, set in `res/values/firebase_applet_config.xml`).
