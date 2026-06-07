# Unofficial [IdleWorlds](https://www.idleworlds.com) for Android

*This is not developed by or affiliated with IdleWorlds.*

**This is an unofficial Android client for IdleWorlds with native notification support.**

Based on [Unofficial Milky Way Idle for Android](https://github.com/McPeyen/Unofficial-Milky-Way-Idle-for-Android) by McPeyen (MIT License).

## Features

- **WebView wrapper** for [idleworlds.com](https://www.idleworlds.com)
- **Push notification support** — world boss alerts and in-game notifications while the app is in the background
- **Notification permission handling** for in-app notification prompts

## Notifications

Android WebView does not support browser Web Push directly. This app bridges that gap by:

1. Polyfilling `pushManager` so IdleWorlds' notification settings work in the app
2. Polling the game API in a background service when notifications are enabled
3. Showing native Android alerts for unread notifications and world boss changes

To enable notifications:

1. Allow notifications when the app asks on first launch
2. In IdleWorlds, enable world boss notifications (or use the in-game test button in Settings)

## Installation

### Download from GitHub Actions

1. Push this repo to GitHub.
2. Open **Actions** → **Build APK** → pick a successful run.
3. Download **idleworlds-release-apk** (or **idleworlds-debug-apk**) from the Artifacts section.

The workflow also runs automatically on pushes to `main`/`master` and on pull requests.

To attach APKs to a GitHub Release, create and push a version tag:

```bash
git tag v1.1.0
git push origin v1.1.0
```

Optional release signing secrets (otherwise the release APK is signed with the debug key):

| Secret | Description |
|--------|-------------|
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded `.jks` / `.keystore` file |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias |
| `ANDROID_KEY_PASSWORD` | Key password |

### Build locally

```bash
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Usage

- Open the app to load IdleWorlds in a full-screen WebView
- Tap the **⋮** menu for **Reload Game**

## Privacy Policy

This app is a wrapper for https://www.idleworlds.com and collects no user data.

## License

MIT License — see [LICENSE](LICENSE).
