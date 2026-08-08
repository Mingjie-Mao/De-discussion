# De

[![English](https://img.shields.io/badge/English-4285F4?style=for-the-badge)](readme.md)
[![中文](https://img.shields.io/badge/%E4%B8%AD%E6%96%87-555555?style=for-the-badge)](readme.zh-CN.md)

De is a demo Android app for campus discussion communities, built by the 404 Sleep Not Found team.

It combines a campus forum, a moderation workflow, and a school heat-token game. Users can browse posts across different school channels, publish content with images, and read threaded comments; admins can handle reported content; and everyone can take part in the Heat Market and leaderboards driven by community activity.

The moderation logic in this app is also available as a standalone backend service: [De-Moderation](https://github.com/Mingjie-Mao/De-Moderation), a Spring Boot content moderation backend with LLM-assisted review.

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
