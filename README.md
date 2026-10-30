# iOS 26 Messages — Liquid Glass (Android APK)

A pixel-faithful recreation of the **iOS 26 Messages** app with Apple's *Liquid Glass*
aesthetic — rebuilt as a real, installable **Android APK**.

The whole UI is one self-contained HTML file running inside a hardened, fully offline
WebView shell. No internet permission, no trackers, no libraries, ~46 KB APK.

<p align="center">
  <img src="screenshots/01-home.png" width="220" alt="Home screen">
  <img src="screenshots/02-messages.png" width="220" alt="Message list">
  <img src="screenshots/05-reply.png" width="220" alt="Conversation">
</p>

<p align="center">
  <img src="screenshots/04-typing.png" width="220" alt="Typing indicator">
  <img src="screenshots/06-dark.png" width="220" alt="Dark mode">
  <img src="screenshots/07-settings.png" width="220" alt="Glass and bounce settings">
</p>

## Download & install

**[⬇ Download the APK](https://github.com/technicalnihilist-lgtm/ios26-messages-liquid-glass/releases/latest)**

1. Download `Messages-LiquidGlass.apk` to your phone.
2. Tap it and allow *"Install unknown apps"* for your browser or file manager.
3. Open **Messages** from the app drawer.

| | |
|---|---|
| Package | `com.liquidglass.messages` |
| Version | 1.0 (versionCode 1) |
| Min Android | 7.0 (API 24) |
| Target Android | 14 (API 34) |
| Size | ~46 KB |
| Permissions | **none** |
| Internet | not required — 100% offline |

## What works

- **Liquid glass everywhere** — translucent bars, search field, dock and sheets with
  live `backdrop-filter` blur + saturation over the animated gradient wallpaper.
- **Spring / bounce motion** — a custom multi-keyframe spring curve drives app open,
  close, sheet presentation, message pop-in and press feedback. Strength and speed
  are adjustable at runtime.
- **Messages list** — searchable, unread dots, relative timestamps, 1:1 and group
  chats, tap-to-open with a parallax push transition.
- **Conversation view** — sender/receiver bubbles, day stamp, "Delivered" receipt,
  animated typing indicator and auto-reply.
- **Compose sheet** — start a new chat from any contact.
- **Settings sheet** — transparency %, blur radius, bounce strength/speed, bounce
  on/off, and a light/dark theme switch, all applied live.
- **Hardware back button** — unwinds the UI (sheet → thread → app) instead of
  dumping you out of the app.
- **Dark mode** — follows the in-app switch, and the system theme on first launch.

## Screenshots

| Home | Messages | Conversation |
|---|---|---|
| ![](screenshots/01-home.png) | ![](screenshots/02-messages.png) | ![](screenshots/03-thread.png) |

| Typing | Reply | Dark |
|---|---|---|
| ![](screenshots/04-typing.png) | ![](screenshots/05-reply.png) | ![](screenshots/06-dark.png) |

| Glass & Bounce settings | New message |
|---|---|
| ![](screenshots/07-settings.png) | ![](screenshots/08-new-message.png) |

## Project layout

```
.
├── app/
│   ├── AndroidManifest.xml
│   ├── assets/index.html          # the entire UI (single file, no dependencies)
│   ├── res/
│   │   ├── values/                # strings, colors, theme
│   │   ├── drawable/              # adaptive icon foreground
│   │   └── mipmap-*/              # launcher icons
│   └── src/com/liquidglass/messages/MainActivity.java
├── build.sh                       # aapt2 → javac → d8 → zipalign → apksigner
├── screenshots/
└── apk/Messages-LiquidGlass.apk
```

## Build it yourself

Requirements: JDK 17, Android build-tools 34, platform `android-34`.

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_SDK=/path/to/android-sdk     # needs build-tools/34.0.0 + platforms/android-34
./build.sh
```

`build.sh` compiles resources with `aapt2`, compiles the single Java class against
`android.jar`, dexes with `d8`, aligns with `zipalign` and signs with `apksigner`.
It generates a local keystore on first run — **replace it with your own key before
publishing to the Play Store**.

## Technical notes

- The WebView is locked down: JavaScript and DOM storage are enabled (the UI needs
  them), while zoom, overscroll, scrollbars, safe browsing and all URL navigation
  away from `file:///android_asset/` are disabled.
- `backdrop-filter` requires Chrome WebView 76+ — present on any device on Android 7
  that has received Play Store updates (effectively all of them). On the rare device
  without it, the glass falls back to a solid tint; the app stays fully usable.
- `window.__androidBack()` is the bridge the Java layer calls from `onBackPressed()`.
- Bubbles, avatars and all colour are original CSS — no Apple assets are bundled.
  This is a design study, not an Apple product, and is not affiliated with Apple Inc.

## License

MIT — see [LICENSE](LICENSE).
