# Mira Personal AI Messenger

A personal-use, mobile-friendly AI character chat experiment built with Next.js. Supports Gemini or OpenRouter through a server-side route. The browser never receives the provider API key.

## Requirements

- Node.js 20.9 or later
- A Gemini API key from Google AI Studio, or an OpenRouter API key and model ID

## Run locally

1. Clone this repository.
2. Install dependencies: `npm install`
3. Copy `.env.example` to `.env.local`.
4. Add your key to `.env.local` (never commit that file).
5. Start the app: `npm run dev`
6. Open `http://localhost:3000`.

### Gemini configuration

```env
AI_PROVIDER=gemini
GEMINI_API_KEY=your_key_here
GEMINI_MODEL=gemini-2.5-flash-lite
```

### OpenRouter configuration

```env
AI_PROVIDER=openrouter
OPENROUTER_API_KEY=your_key_here
OPENROUTER_MODEL=provider/model-id
```

Only configure the provider you are using. Verify model availability and current free-tier limits with the provider before use.

## Security notes

- API keys are read only by `app/api/chat/route.ts` on the server.
- Never add real secrets to source files, GitHub commits, screenshots, or client-side environment variables such as `NEXT_PUBLIC_*`.
- Chat history is stored in this browser's localStorage. It is not encrypted, is not synced between devices, and can be cleared from the chat.
- Messages sent to the selected AI provider leave your device and are subject to that provider's terms and data policies.
- This is a personal experiment, not a hardened public multi-user service. Do not expose it publicly without adding authentication, rate limiting, and appropriate deployment protections.

## Current scope

- One fictional character: Mira Bennett
- Responsive messenger-style UI
- Browser-local chat history
- Server-side Gemini and OpenRouter adapters
- API key intentionally absent from the UI

## Android APK testing

A GitHub Actions workflow builds an installable **debug APK** when Android wrapper files change on `main`, or when you manually run **Actions → Build Android APK → Run workflow**.

1. Open the repository's **Actions** tab and select **Build Android APK**.
2. Open the latest successful run.
3. Under **Artifacts**, download `mira-messenger-debug-apk` and extract the ZIP.
4. Transfer `app-debug.apk` to your Android phone and install it. Android may ask you to allow installation from that source.
5. On first launch, enter the HTTPS URL of your deployed Mira Messenger website.

**Important:** this APK is a lightweight Android WebView wrapper, not a self-hosted server. The Next.js website and its `/api/chat` backend must be deployed and reachable over HTTPS for AI chat to work. The app asks for that URL on the phone and saves it locally. Keep Gemini/OpenRouter keys in the server environment only, never in the APK or browser. Chat history remains local to that WebView installation.

The workflow uploads the debug APK as a temporary artifact for 14 days. It is for personal testing, not a signed Play Store release.
