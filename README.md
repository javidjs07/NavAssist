# NavAssist

Floating navigation menu for phones with broken Home / Recents / Volume / Power buttons.

## Build
Push to GitHub. The **Actions** tab builds `app-debug.apk` (artifact: NavAssist-debug-apk).

## Install and set up (ADB route, works even if Settings is broken)
```
adb install -r app-debug.apk
adb shell appops set com.example.navassist SYSTEM_ALERT_WINDOW allow
adb shell pm grant com.example.navassist android.permission.POST_NOTIFICATIONS
adb shell pm grant com.example.navassist android.permission.RECORD_AUDIO
adb shell cmd notification allow_dnd com.example.navassist
adb shell appops set com.example.navassist WRITE_SETTINGS allow
adb shell pm grant com.example.navassist android.permission.WRITE_SECURE_SETTINGS
adb shell settings put secure enabled_accessibility_services com.example.navassist/com.example.navassist.NavAccessibilityService
adb shell settings put secure accessibility_enabled 1
adb shell am start -n com.example.navassist/.MainActivity
```
If you already use another accessibility service, first run
`adb shell settings get secure enabled_accessibility_services` and append
`:com.example.navassist/com.example.navassist.NavAccessibilityService` to that value.

## Without a computer: "Restricted setting"
Settings > Apps > NavAssist > top-right menu (3 dots) > **Allow restricted settings**,
then Settings > Accessibility > Downloaded apps > NavAssist > On.
(The 3-dot item appears after you have tapped NavAssist once in the Accessibility list.)

## Volume mixer
Android does not allow a third-party app to change the volume of one other app.
The mixer controls the separate system streams instead: Media (YouTube, music),
Call / meeting, Ringtone and Alarm. Meeting apps normally use the Call stream.

## Publishing on Google Play
See PLAY_RELEASE.md and PRIVACY_POLICY.md.

## Design
Dark palette: canvas #0B0F19, surfaces #111827, borders #1F2937, accent #6366F1 (indigo) and #38BDF8 (cyan),
text #F9FAFB / #9CA3AF. Font: Inter (SIL OFL 1.1, see FONT_LICENSE_INTER.txt), bundled in res/font.
NOTE: res/font/inter_var.ttf is a binary file; push with git (not copy/paste in the GitHub web editor).

## Recents not working on your phone?
Open NavAssist > Advanced > **Recents method** and try: Auto, System action only, Swipe gesture, Launcher shortcut.
Tap **Test Recents now**; it tells you which method actually worked. Auto tries the system action first and, if
the phone ignores it, performs the "swipe up and hold" gesture for you.

## AI assistant
NavAssist > AI assistant > AI provider: pick OpenAI, Anthropic, Google Gemini or a custom OpenAI-compatible service
and paste an API key (a ChatGPT/Claude app subscription is NOT an API key; create one on the provider's developer
site). Then the AI button in the floating menu opens a type-or-talk chat sheet over whatever app you are in.
Model names change over time; edit the model field if a provider retires the default.

## What is new in 3.0
- **Do Not Disturb access** now lists NavAssist (the app was missing the ACCESS_NOTIFICATION_POLICY declaration).
  ADB alternative: `adb shell cmd notification allow_dnd com.example.navassist`.
- **Orb gestures:** single tap, double tap, long press can each run any action (open menu, hide orb, screenshot, camera...).
- **Hide orb for screenshots:** long-press the orb (hides for 15 s by default), or use the NavAssist Quick Settings tile
  or the notification "Hide orb" button. The Screenshot button inside the menu hides the orb automatically.
  Android shows every on-screen window in system screenshots; FLAG_SECURE would block ALL screenshots, so it is not used.
- **Peek at edge:** the idle orb slides half off-screen.
- **Themes, accent colors, orb icons/emoji, circle or rounded-square orb, size, opacity, animation speed.**
- **Menu layouts 3x3 / 3x4 / 4x4 with swipeable pages**, Favorite apps page, Brightness slider.
- **New buttons:** Wi-Fi, Internet, Bluetooth, Location, Airplane, Brightness, Timeout, Rotation, Sound mode,
  Recorder, Storage, Hide orb, Favorites.
- **Screen recorder** (resolution, bitrate, fps, mic/mute, saved to Movies/NavAssist).

## What is new in 3.1 (UI redesign)
Five bottom tabs, each with sub-tabs:
- **Home** - 3D hero, start/stop, quick setup, quick actions (hide orb, screenshot, record, AI chat, search, test Recents).
- **Menu** - Layout (live preview, 3x3 / 3x4 / 4x4), Buttons (31 switchable buttons in groups), Gestures (tap / double tap / long press), Favorites.
- **Style** - Theme (8 themes + 12 accents, live preview), Orb (3D pictures, symbols, emoji, size, opacity), Motion.
- **Tools** - Recorder, AI (provider, voice language), Test (Recents, screenshot, device info).
- **Settings** - Setup (permissions + ADB commands with a Copy button), Advanced, About.
3D artwork (hero, banners, floating orbs, app icon) is pre-rendered; see `store/` for the Play Store images.

## What is new in 4.0 (separate pages + real 3D)
- **Side menu + 10 separate pages:** Home, Menu & gestures, Design, Recording, Cleaning, Connections, AI assistant, Features, Settings, About us.
  The bottom bar has Home, Menu, Design, Record and More (opens the side menu).
- **Real 3D (Three.js r149, bundled offline in `assets/`)** on Home, Features and About: a glossy rotating orb, a tile grid and gyroscope rings.
  Drag horizontally to spin them. If a phone cannot run WebGL, a pre-rendered picture is shown instead.
- **3D app-style icons** for every page, new banners and an empty-state illustration.
- **Recording library:** thumbnails, play, share, delete.
- **Cleaning:** storage gauge, NavAssist cache, scan screenshots and videos over 100 MB (asks for photo/video access only when you press Scan; deletes need your confirmation).
- **Connections:** live Wi-Fi / internet / Bluetooth / location / airplane status with one-tap system panels.
