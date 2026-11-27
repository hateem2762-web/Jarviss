# JARVIS

A full-stack React, TypeScript, Vite, and Express personal AI assistant interface rewritten from the original Android Jetpack Compose application.

## Features

- **Neural HUD & Voice Assistant**: Interactive particle nebula canvas responding to voice input levels, visual states (`IDLE`, `LISTENING`, `THINKING`, `SPEAKING`, `ERROR`), and response moods (`neutral`, `serious`, `analytical`, `energetic`, `alert`), paired with Web Speech API voice recognition (`"Jarvis"` wake-word mode) and Turkish TTS replies.
- **Server-Side Gemini AI**: Express backend (`server.ts`) routes `/api/jarvis/ask`, `/api/jarvis/evaluate-whatsapp`, and `/api/jarvis/screen-note` through `@google/genai` (`gemini-4-hydrogen`) with structured JSON outputs and deterministic Turkish duration parsing.
- **Quick Commands & Action Dispatch**: Local instant command parser (`LocalQuickCommandMatcher`) and AI action dispatcher supporting app/web shortcuts, YouTube search, reminders, screen-note extraction, tap coordinate triggering, and simulated device telemetry/controls.
- **Notes, Coordinates & Reminders**: Persistent `localStorage`-backed note management (with AI-powered text/image note extraction), crosshair coordinate profile picker, and countdown reminder scheduler with Web Notifications.
- **Proactive Alert Evaluator & Settings**: WhatsApp message filter and AI urgency scoring simulator (`1–10` threshold), Obsidian Dark / Tactical Light theme toggle, user context profile, and floating mini HUD toggle.

## Getting Started

1. Install dependencies:
   ```bash
   npm install
   ```
2. Configure environment variables (see `.env.example`):
   ```bash
   GEMINI_API_KEY=your_api_key_here
   ```
3. Run the development server (port `3000`):
   ```bash
   npm run dev
   ```
4. Build and run in production:
   ```bash
   npm run build
   npm start
   ```
