# iOS 26 Messages — Liquid Glass (Android APK)

A pixel-faithful recreation of the **iOS 26 Messages** app with Apple's *Liquid Glass*
aesthetic — rebuilt as a real, installable **Android APK**.

The whole UI is one self-contained HTML file running inside a hardened, fully offline
WebView shell. No internet permission, no trackers, no libraries, ~50 KB APK.

<p align="center">
  <img src="screenshots/01-home.png" width="220" alt="Home screen">
  <img src="screenshots/02-messages.png" width="220" alt="Empty inbox">
  <img src="screenshots/08-new-message.png" width="220" alt="Start a new chat">
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
| Version | 1.3 (versionCode 4) |
| Min Android | 7.0 (API 24) |
| Target Android | 14 (API 34) |
| Size | ~50 KB |
| Permissions | **none** |
| Internet | not required — 100% offline |

## What works

- **Liquid glass everywhere** — translucent bars, search field, dock and sheets with
  live `backdrop-filter` blur + saturation over the animated gradient wallpaper.
- **Spring / bounce motion** — a custom multi-keyframe spring curve drives app open,
  close, sheet presentation, message pop-in and press feedback. Strength and speed
  are adjustable at runtime.
- **Starts empty** — no demo chats, no sample messages. The inbox opens on
  *No Conversations* and fills up with whatever you create.
- **New chat** — tap the compose button, type a name, hit **Start chat**. The
  conversation opens immediately, ready to type into.
- **Every message gets a reply** — send anything and the other side answers by
  default (typing indicator first, then a bubble), so a fresh chat never sits dead.
- **Messages list** — searchable, relative timestamps, 1:1 and group chats,
  tap-to-open with a parallax push transition.
- **Long-press any bubble** — a glass context menu opens with **Copy** (routed to the
  native Android clipboard) and **Delete** (bubble collapses, water droplets scatter
  and a synthesized water-splash sound plays — no audio files, all Web Audio).
- **Long-press a conversation row** — the row lifts into a floating card with a
  **Delete** action; the chat collapses away with a softer water trickle. Clearing
  the last chat shows the *No Conversations* empty state.
- **Settings sheet** — transparency %, blur radius, bounce strength/speed, bounce
  on/off, and a light/dark theme switch, all applied live.
- **Hardware back button** — unwinds the UI (context menu → sheet → thread → app)
  instead of dumping you out of the app.
- **Dark mode** — follows the in-app switch, and the system theme on first launch.

## Screenshots

| Home | Empty inbox | New chat |
|---|---|---|
| ![](screenshots/01-home.png) | ![](screenshots/02-messages.png) | ![](screenshots/08-new-message.png) |

| Fresh conversation | Inbox after chatting |
|---|---|
| ![](screenshots/03-thread.png) | ![](screenshots/11-inbox.png) |

| Typing | Reply | Dark |
|---|---|---|
| ![](screenshots/04-typing.png) | ![](screenshots/05-reply.png) | ![](screenshots/06-dark.png) |

| Glass & Bounce settings | Long-press bubble |
|---|---|
| ![](screenshots/07-settings.png) | ![](screenshots/09-context-menu.png) |

| Long-press conversation → delete |
|---|
| ![](screenshots/10-row-menu.png) |

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

## What's new in 1.3

- **Demo content removed** — the six seeded chats and their messages are gone.
  The app starts on an empty *No Conversations* inbox.
- **New-chat flow** — the compose sheet now has a *To: name* field and a
  **Start chat** button; no more picking from a hard-coded contact list.
- **Default auto-reply** — every message you send gets an answer, so a brand-new
  conversation behaves like a real one.
- Screenshots re-rendered for the empty inbox, the new-chat sheet and a real inbox.

## What's new in 1.2

- Long-press a conversation row to delete the whole chat, with a softer water
  trickle, droplet particles and a lift-and-collapse animation.
- *No Conversations* empty state once the list is cleared.
- Guard so a long-press never opens the chat by accident when you release.
- Haptic nudge on the row menu (browser/PWA; the APK runs without the VIBRATE
  permission by design, so there it is a silent no-op).

## What's new in 1.1

- Long-press context menu on every bubble: **Copy** and **Delete**.
- Delete animation with droplet particles and a procedural water-splash sound
  (Web Audio synthesis — the APK ships no audio files).
- Copy goes through a native `ClipboardManager` bridge, because
  `navigator.clipboard` is unreliable on `file://` inside a WebView.
- Thread header avatar no longer overflows the navigation bar.
- Full-bleed layout — the old 900 px preview cap that left a black strip is gone.

## Technical notes

- The WebView is locked down: JavaScript and DOM storage are enabled (the UI needs
  them), while zoom, overscroll, scrollbars, safe browsing and all URL navigation
  away from `file:///android_asset/` are disabled.
- `backdrop-filter` requires Chrome WebView 76+ — present on any device on Android 7
  that has received Play Store updates (effectively all of them). On the rare device
  without it, the glass falls back to a solid tint; the app stays fully usable.
- `window.__androidBack()` is the bridge the Java layer calls from `onBackPressed()`.
- The only native method exposed to the page is `Android.copy(text)` (clipboard).
  `addJavascriptInterface` is limited to that single method, and the app loads only
  local assets — nothing can be reached from the network.
- Bubbles, avatars and all colour are original CSS — no Apple assets are bundled.
  This is a design study, not an Apple product, and is not affiliated with Apple Inc.

## License

MIT — see [LICENSE](LICENSE).
