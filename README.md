# Mira Messenger

Mira Messenger is a personal, fully offline character-chat app. The Android APK contains the complete interface and local reply engine. It does not need a backend, hosted website, Termux, an API key, or an internet connection after installation.

## What is included

- One fictional character: Mira Bennett
- Messenger-style chat interface and multiple conversations
- Local reply rules and editable Reply Library
- Character tone, fallback replies, and continuity notes
- Memory library and emotional continuity state
- Local browser storage for conversations and settings
- Responsive Aurora Glass interface

## How replies work

Mira uses the reply rules you configure in the app. Each rule has trigger phrases and one or more written replies. When a message matches a rule, the local reply engine selects a configured response. If no rule matches, Mira uses the fallback reply you set.

There are no AI model calls and no remote services. Replies are only as varied and context-aware as the local rules and dialogue you write.

## Install and use

1. Download the latest `mira-messenger-standalone-apk` artifact from the repository's **Actions → Build Android APK** workflow.
2. Extract the ZIP and install `app-debug.apk` on your Android phone.
3. Open Mira Messenger. That's it. No server startup or setup screen is required.

The APK bundles the static web interface under Android assets and serves it through AndroidX WebKit's local asset loader. The app does not request Android's Internet permission.

## Build the APK

The GitHub Actions workflow:

1. Installs the web build dependencies.
2. Runs the local reply-engine tests.
3. exports the Next.js interface as static files.
4. Copies those files into the Android app assets.
5. Builds and uploads a standalone debug APK.

For local development, use Node.js 20.9 or later:

```bash
npm install
npm test
npm run dev
```

The development server is only for working on the source code. The installed APK does not use it.

## Local data and privacy

- Chat history, character settings, reply rules, memories, and continuity notes are stored locally in the app's WebView storage.
- No conversation data is sent to an AI provider or backend.
- Data is not synced between devices and is not encrypted by this prototype.
- Uninstalling the app or clearing its app data can erase locally stored conversations and settings.

## Project architecture

- `app/`: user interface
- `lib/reply-engine.js`: deterministic local reply matching
- `android/`: Android WebView shell and app configuration
- `.github/workflows/android-apk.yml`: static export and standalone APK build

This is a personal-use debug APK, not a signed Play Store release.
