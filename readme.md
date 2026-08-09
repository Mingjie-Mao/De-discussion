# De

[![English](https://img.shields.io/badge/English-4285F4?style=for-the-badge)](readme.md)
[![中文](https://img.shields.io/badge/%E4%B8%AD%E6%96%87-555555?style=for-the-badge)](readme.zh-CN.md)

De is a demo Android app for campus discussion communities, built by the 404 Sleep Not Found team.

It combines a campus forum, a moderation workflow, and a school heat-token game. Users can browse posts across different school channels, publish content with images, and read threaded comments; admins can handle reported content; and everyone can take part in the Heat Market and leaderboards driven by community activity.

Online reports are connected to the standalone [De-Moderation](https://github.com/Mingjie-Mao/De-Moderation) Spring Boot service. When its model engine is configured, a report filed in this app is analysed by the LLM and appears in the in-app administrator queue with the engine recommendation, confidence and rationale.

## Highlights

- Switch between multiple school channels, each with its own visual style
- Post creation, image upload, and comment interaction
- Expandable and collapsible comment threads for cleaner mobile reading
- Both member mode and admin mode
- Admins can review and act on reported content from a report queue
- School token system, with heat driven by posts, replies, and likes
- Leaderboards showing how different users perform in the market
- Daily balance reset, so the game stays playable over time

## Screenshots

| Feed | Comments |
| --- | --- |
| ![Feed](picture/feed.png) | ![Comments](picture/comments.png) |

| Heat Market | Leaderboard |
| --- | --- |
| ![Heat Market](picture/market.png) | ![Leaderboard](picture/leaderboard.png) |

## Demo Walkthrough

1. Sign in with the demo account
2. Choose Member or Admin
3. Browse posts across the different school channels
4. Report a comment in member mode
5. Switch to admin mode and handle the report from the review queue
6. Trade school tokens in the Heat Market and check the leaderboard

## Demo Account

Username: 1234

Password: 1234

## Tech Stack

- Java
- Android SDK
- Gradle
- Custom moderation and data structure modules

## Project Structure

- `android/` — Android app code and resource files
- `android/app/src/main/java/com/example/myapplication/` — screens, components, and core business logic
- `android/app/src/main/java/moderation/` — reporting, hiding, and review queue logic
- `android/app/src/main/java/backend/` — De-Moderation HTTP integration, account mapping and lazy content mirroring
- `app/src/` — the original coursework-side Java module kept in the repo
- `picture/` — project screenshots used in this README

## Getting Started

Open `android/` in Android Studio.

Wait for the Gradle sync to finish.

Run the app on an emulator or a physical device.

You can also build from the command line:

```bash
cd android
./gradlew assembleDebug
```

## Online LLM moderation

The two repositories remain separate applications and communicate through the De-Moderation REST API. A reported local post/comment chain is mirrored lazily to the backend under stable per-install accounts, then the report is submitted to its durable moderation queue. Content is not claimed to be reported if that request fails.

1. Start De-Moderation and its PostgreSQL database, including its `ADMIN_USERNAME` / `ADMIN_PASSWORD` settings.
2. For LLM review, configure the backend's `AI_CHAT_MODEL`, `GEMINI_API_KEY`, `GEMINI_MODELS` and `MODERATION_ENGINE`. Without those, the same connection works with the deterministic rule engine.
3. In the Android app, open **Settings → Moderation backend**. An emulator uses `http://10.0.2.2:8080`; a physical device needs a reachable HTTPS deployment (or the development machine's LAN address in a debug build).
4. Enter the backend administrator credentials to use the online review queue. The password is memory-only and must be re-entered after the app process restarts.

Admin mode has two screens for this. **Moderation Queue** holds cases an engine has analysed and nobody has ruled on yet, each with the recommendation, confidence and rationale. **Reviewed Records** holds the cases already decided, and the decision can still be changed there — the backend restores hidden content or reinstates a banned author as needed, and appends the correction to the audit trail instead of overwriting the original.

Online mode is enabled by default. The Settings status says explicitly whether an LLM is active, rules are active, or the service is unreachable. Disable online mode there to use the original device-only moderation demo. Release builds do not allow cleartext HTTP; the HTTP exception exists only in the debug manifest.
