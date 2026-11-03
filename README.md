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
