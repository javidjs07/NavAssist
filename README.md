# NavAssist

Floating navigation menu for phones with broken Home / Recents / Volume / Power buttons.

## Build
Push to GitHub. The **Actions** tab builds `app-debug.apk` (artifact: NavAssist-debug-apk).

## Install and set up (ADB route, works even if Settings is broken)
```
adb install -r app-debug.apk
adb shell appops set com.example.navassist SYSTEM_ALERT_WINDOW allow
adb shell pm grant com.example.navassist android.permission.POST_NOTIFICATIONS
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
