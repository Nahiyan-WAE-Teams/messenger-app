# Lovely | Offline Dating

Lovely is a premium, data-driven dating-app prototype packaged as a standalone Android APK. It runs entirely on the device: no backend, hosted website, Termux, AI API, or network connection is required.

## Product model

Lovely combines two profile types in one local profile library:

- **Fictional characters:** locally defined personas with interests, dating intentions, predefined dialogue triggers and written reply variations.
- **Real-person records:** manually entered profiles with optional locally stored photos and profile details. These are private records only, not connected accounts.

The starter collection contains fictional characters only. Real-person profiles must be added manually. The app does not fabricate real-person activity, incoming messages, verification, or mutual interest.

## Features

- Premium responsive discovery interface with swipe gestures and Like / Pass actions
- Profile cards, locally stored profile photos, bios, age, location, interests and relationship intentions
- Hidden, preference-aware discovery ranking with diversity balancing and configurable filters
- Likes, mutual-match simulation based on explicit local profile data, and match collection
- Messages and conversation history
- Rule-based dialogue for fictional profiles only; real-person conversations are private notes
- Create, edit and delete profiles, including choosing fictional or real-person record type
- Personal profile and interests
- Local privacy settings, block action and data reset
- Animated transitions, responsive mobile navigation and reduced-motion support

## How the data engine works

All profile data is stored in the app's local WebView storage. Discovery filters profiles by age, relationship intention, activity, and local interaction history. Discovery uses private local ranking signals based on shared interests, values, lifestyle, personality and selected preferences. No compatibility percentage or score is displayed.

A Like creates a match only when the local fictional profile data explicitly marks interest as mutual. This is a simulation for the offline experience, not a real-world match notification.

Fictional character conversations use written trigger/reply rules, relationship-stage rules, optional previous-message context conditions, and recent-reply avoidance. For example, `[after:work] fine | Was work busy today?` only applies when the user's previous message mentioned work. The reply engine never calls an AI model and does not invent replies when a rule is missing. Real-person records cannot send or receive messages; their chat area is a private note-taking interface.

## Standalone Android app

The APK bundles the complete static web interface into Android app assets and loads it using AndroidX WebKit's local asset loader. The Android manifest does not request Internet permission. The installed app does not start or connect to a server.

## Build

GitHub Actions builds the standalone debug APK:

1. Install Node dependencies.
2. Run local dating-engine and reply-engine tests.
3. Export the Next.js app as static files.
4. Bundle the static output into Android assets.
5. Build and upload `app-debug.apk`.

For local source development (Node.js 20.9+):

```bash
npm install
npm test
npm run dev
```

The development server is only for developing the source. The APK does not use it.

## Local storage and privacy

- Profiles, photos, likes, matches, conversations, filters and personal profile settings remain in the app's local storage.
- No profile or message data is sent to a server or AI provider.
- There is no cross-device sync, live messaging, real account system, or remote discovery.
- Local profile photos are stored as data URLs. Large images are limited to 1.5 MB when added.
- Data is not encrypted by this prototype. Clearing app data or uninstalling may erase it.
- Do not store private information about another person without their permission.

## Repository layout

- `app/`: dating interface and responsive design
- `lib/dating-engine.js`: local discovery, compatibility, match and dialogue logic
- `lib/reply-engine.js`: original local message matching utility
- `android/`: standalone Android WebView shell
- `.github/workflows/android-apk.yml`: offline APK build pipeline

This is a personal-use debug APK, not a signed Play Store release.
