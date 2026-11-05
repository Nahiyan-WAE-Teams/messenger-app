# Lovely | Native Offline Dating App

Lovely is a standalone Android application built with Kotlin and Jetpack Compose. It runs locally on the device. The native app does not load a website, use a WebView, contact a backend, or call an AI API. The Android manifest does not request Internet permission.

## Product model

Lovely has a hybrid local profile library:

- **Fictional characters** are locally defined profiles. Their conversation replies come only from dialogue rules stored in the app.
- **Real-person records** are manually entered private records. They do not represent connected accounts and cannot generate simulated interest, matches, activity, or incoming messages. Their conversation area is a private note space.

The starter library contains fictional profiles only. Real-person records must be added manually.

## Native features

- Jetpack Compose interface with animated navigation and a dark plum / rose visual system.
- Offline profile discovery with age and relationship-intention filters.
- Private preference ranking across interests, values, lifestyle, and personality.
- Greedy diversity reranking to avoid showing near-identical profiles consecutively.
- Likes, passes, and local mutual-match simulation for fictional profiles only.
- Profile library with create, edit, and delete for fictional and real-person records.
- Local personal profile and discovery preferences.
- Local conversations with persisted message history.
- Rule-based fictional dialogue with trigger matching, stage-specific rules, context prefixes, recent-reply avoidance, and a fallback reply.
- Relationship-stage state used internally by fictional dialogue. No relationship percentages or public progress meters.
- Real-person conversations are private notes only. The app never writes messages on another person's behalf.
- Local reset controls.

## Discovery engine

The app filters out profiles already liked or passed, inactive profiles, and profiles outside the selected age and intention filters. It then ranks the remaining candidates using available preference signals:

- Shared interests: 28%
- Shared values: 22%
- Lifestyle fit: 16%
- Personality fit: 12%
- Intention fit: 14%
- Age eligibility signal: 8%

Weights are normalized across the signals the user actually configured. A diversity reranker then balances relevance against similarity to profiles already selected for the feed. Ranking values are internal only and are never shown as a compatibility score.

## Offline data and privacy

Room is the local source of truth for profiles, actions, matches, messages, relationship state, and preferences. All app operations work without a network connection. There is no login, remote discovery, cross-device sync, or cloud backup. Local data is not encrypted, so avoid storing sensitive information about anyone else without permission.

## Android build

The Android project is in `android/`. It uses JDK 17, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Compose, Material 3, and Room.

From the `android/` directory, build and test with Gradle 8.9:

```bash
gradle --no-daemon testDebugUnitTest
gradle --no-daemon assembleDebug
```

The debug APK is written to:

```
android/app/build/outputs/apk/debug/app-debug.apk
```

The GitHub Actions APK workflow is temporarily paused while the native implementation is completed and audited. It will be restored for the final clean test and APK artifact build.

## Repository layout

- `android/app/src/main`: native Kotlin application and Compose UI.
- `android/app/src/test`: ranking and dialogue-engine unit tests.
- `app/` and `lib/`: retained legacy web prototype source for reference only. They are not packaged into the native APK.

This is a personal-use debug build, not a signed Play Store release.
