# Unofficial [IdleWorlds](https://www.idleworlds.com) for Android

*This is not developed by or affiliated with IdleWorlds.*

**This is an unofficial Android client for IdleWorlds with a built-in userscript manager.**

Based on [Unofficial Milky Way Idle for Android](https://github.com/McPeyen/Unofficial-Milky-Way-Idle-for-Android) by McPeyen (MIT License).

## Features

- **WebView wrapper** for [idleworlds.com](https://www.idleworlds.com)
- **Custom JavaScript support** via Greasemonkey-compatible userscripts
- **Script Manager** — add, enable, disable, and remove scripts
- **Built on webview-gm** — [webview-gm](https://github.com/wbayer/webview-gm)

## Installation

### Download from GitHub Actions

1. Push this repo to GitHub.
2. Open **Actions** → **Build APK** → pick a successful run.
3. Download **idleworlds-release-apk** (or **idleworlds-debug-apk**) from the Artifacts section.

The workflow also runs automatically on pushes to `main`/`master` and on pull requests.

To attach APKs to a GitHub Release, create and push a version tag:

```bash
git tag v1.0.0
git push origin v1.0.0
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
- Tap the **⋮** menu for **Script Manager** or **Reload Game**
- Script changes require a reload or app restart to take effect

## Privacy Policy

This app is a wrapper for https://www.idleworlds.com and collects no user data.

## License

MIT License — see [LICENSE](LICENSE).
