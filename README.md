# iOS 26 Messages — Liquid Glass (Android APK)

A pixel-faithful recreation of the **iOS 26 Messages** app with Apple's *Liquid Glass*
aesthetic — rebuilt as a real, installable **Android APK**.

The whole UI is one self-contained HTML file running inside a hardened WebView shell
that talks to Android's real SMS stack — it can be your **default messaging app**.
No internet permission, no trackers, no libraries, ~58 KB APK.

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
| Version | 2.4 (versionCode 9) |
| Min Android | 7.0 (API 24) |
| Target Android | 14 (API 34) |
| Size | ~66 KB |
| Permissions | `SEND_SMS`, `RECEIVE_SMS`, `READ_SMS`, `RECEIVE_MMS`, `RECEIVE_WAP_PUSH`, `READ_CONTACTS`, notifications |
| Internet | **not requested** — the app cannot reach the network at all |

## What works

- **Liquid glass everywhere** — translucent bars, search field, dock and sheets with
  live `backdrop-filter` blur + saturation over the animated gradient wallpaper.
- **Spring / bounce motion** — a custom multi-keyframe spring curve drives app open,
  close, sheet presentation, message pop-in and press feedback. Strength and speed
  are adjustable at runtime.
- **3D + depth** — the whole UI lives in a real perspective space: bubbles tilt toward
  your finger with a specular highlight, cards and sheets carry layered depth shadows,
  the wallpaper and bars parallax against each other, and messages recede as they
  scroll away (smaller, dimmer, pushed back). Toggle and strength live in Settings.
- **4D motion** — depth is driven by time and movement, not just taps: the wallpaper
  blobs drift on a slow 22–28s cycle, and on a real phone the gyroscope tilts the
  whole scene as you move the handset.
- **Tune it** — Settings has *3D & depth* on/off plus **Depth**, **Parallax** and
  **Tilt** sliders, all saved with your other preferences.
- **Starts empty** — no demo chats, no sample messages. The inbox opens on
  *No Conversations* and fills up with whatever you create.
- **New chat, two ways** — tap the compose button and type a phone number, or tap
  the **+** button beside the message field to pick anyone from your contacts
  (searchable list, straight into a chat).
- **Settings stick** — transparency, blur, bounce strength/speed, the bounce toggle
  and the theme are saved the moment you change them and restored on next launch.
- **Real SMS** — your actual conversations are read from the phone's SMS store,
  messages are sent through `SmsManager`, and incoming texts are written back into
  that store, so nothing is lost.
- **Default messaging app** — a first-run sheet asks to become your default SMS app
  (and Settings has the same switch). Tapping it requests the Android permissions,
  then opens the system role dialog. The app ships every component Android requires
  for that role: `SMS_DELIVER` receiver, `WAP_PUSH_DELIVER` receiver, a
  respond-via-message service and `sms:`/`smsto:`/`mms:`/`mmsto:` intent filters.
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

| Long-press conversation → delete | Default messaging app prompt | Pick a contact |
|---|---|---|
| ![](screenshots/10-row-menu.png) | ![](screenshots/12-default-app.png) | ![](screenshots/13-contacts.png) |

| 3D depth in a long thread — near bubbles lift, far ones recede |
|---|
| ![](screenshots/14-depth.png) |

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

## What's new in 2.4 — deletes stay deleted

- **Deleting a message now sticks.** Previously a deleted text came straight back:
  the app keeps its own backup copy of every received SMS, so after the system row was
  removed the backup copy was merged back in on the next refresh.
- Every delete now writes a **tombstone** (address + body + timestamp) before anything
  else happens. The merge step skips anything tombstoned, and the receiver refuses to
  re-store a tombstoned message — so a deleted text can never reappear, while new
  messages from the same person still arrive normally.
- Covers all three paths: deleting a single provider message, deleting a
  backup-only message (no provider id), and deleting a whole conversation.
- Deleting a conversation now also tombstones every message inside it, so the thread
  does not come back after a refresh.

## What's new in 2.3 — incoming messages fixed

- **New texts now actually arrive.** Four things were missing or fragile, all fixed:
  - every received SMS is stored in the app's own backup store *before* anything else,
    so a failed provider insert can no longer swallow it;
  - a `SMS_RECEIVED` receiver was added for the case where the app is not the default
    SMS app yet (Android only sends `SMS_DELIVER` to the default app);
  - a notification is posted for every incoming text, so it is visible even when the
    app is closed;
  - the open app now polls the store every 6 seconds (and on resume), so a thread
    updates on its own instead of waiting for a restart.
- Also fixed: the `SEEN` column was written on Android 7/8 where it does not exist,
  which could make the whole insert throw — the message is now saved either way.
- **More depth, and control over it**: new **Parallax** and **Tilt** sliders next to
  **Depth**; scroll depth is stronger (recession, tilt and dimming), tilt rotates
  further toward your finger, and all three values persist.
- Settings sheet compacted so the *Done* button no longer falls off the screen.

## What's new in 2.2 — 3D, depth and motion

- **3D tilt**: press or drag across a bubble and it rotates toward the pointer
  (`rotateX`/`rotateY` in a 1100px perspective) with a soft specular highlight that
  follows the touch point.
- **Depth effect**: every layer now has its own elevation — bubbles, bars, sheets and
  the dock carry multi-layer shadows, and scrolling a conversation pushes distant
  bubbles back in Z, shrinks them slightly and dims them, so the thread reads as a
  corridor instead of a flat list.
- **Parallax**: the wallpaper blobs, the navigation bar and the content move at
  different rates as you move your finger — and via `deviceorientation`, as you tilt
  the phone.
- **4D (time)**: the background drifts on a slow ambient cycle, so the scene breathes
  even when you are not touching it.
- **Controls**: new *3D & depth* switch and *Depth* strength slider in Settings, both
  saved with the rest of your preferences.
- Tap targets stay intact — the depth engine only uses non-negative Z, so bubbles
  never slip behind their parent plane (that used to break long-press).

## What's new in 2.1

- **The `+` button works**: it opens a searchable contact picker (real contacts, via
  `READ_CONTACTS`) and jumps straight into a chat with whoever you tap.
- **Settings and theme persist**: transparency, blur, bounce strength/speed, the
  bounce switch and light/dark are stored in native `SharedPreferences` and restored
  on launch. Picking light keeps light, picking dark keeps dark — the system theme no
  longer overrides your choice after a restart.
- **Thread header fixed**: the back chevron and the contact title were overlapping;
  the bar is now a proper three-column layout with an ellipsised title, and it stays
  readable on devices where the blur is weak.
- Fixed contact avatars sometimes rendering with no colour.

## What's new in 2.0 — real messaging

- **Real SMS**: threads and messages come from the Android SMS provider; sending uses
  `SmsManager` and also records the outgoing text.
- **Default SMS app**: first-run prompt + a Settings switch. Requests permissions,
  then the system role dialog. Includes every component the role requires.
- **Incoming texts are safe**: the `SMS_DELIVER` receiver writes each message into the
  system SMS store itself, so it survives even if the UI is closed.
- **`sms:`/`smsto:` links** from contacts or the dialer open straight into a chat.
- The old fake auto-reply is gone — the other side is a real person now.
- Fixed a container-scroll bug that could shift the whole UI sideways after using
  the keyboard.

### Known limitation
**MMS is not downloaded.** The app declares the required MMS receiver to qualify for
the role, but full MMS (WAP push download) is out of scope here — when an MMS arrives
you get a notification saying so, instead of the message vanishing silently.

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
- The JS bridge (`Android.*`) is deliberately narrow: clipboard copy, SMS read/send/
  delete, contact lookup, preference get/set, permission and role requests. There is no network access and no file access
  beyond the bundled assets.
- Everything SMS-related goes through the standard provider (`content://sms`), so
  other SMS apps can still read the same store if you switch back.
- Received messages are additionally mirrored into a small local backup store
  (`SharedPreferences`) and merged, de-duplicated, back into the list — that is the
  safety net that makes "a message arrived but never showed up" impossible.
- Deletions are recorded as tombstones in the same store and filtered out of every
  merge, which is what keeps a deleted message deleted.
- The depth engine is transform-only and rAF-throttled: scroll depth recomputes once
  per frame, parallax writes CSS custom properties, and nothing touches layout.
- Bubbles, avatars and all colour are original CSS — no Apple assets are bundled.
  This is a design study, not an Apple product, and is not affiliated with Apple Inc.

## License

MIT — see [LICENSE](LICENSE).
