# Publishing NavAssist on Google Play

Ready-made store images are in the `store/` folder: `play-icon-512.png` and `play-feature-graphic-1024x500.png`.
You still need 2+ real phone screenshots (take them on your phone).

1. **Target API.** As of Aug 31 2026 Google Play requires new apps and updates to target Android 16 (API 36).
   Change `compileSdk`/`targetSdk` to 36, AGP to 8.9.1 or newer and `gradle-version` in
   `.github/workflows/build.yml` to 8.11.1 or newer, then test.
2. **Signing + bundle.** Create an upload keystore, keep it in GitHub Secrets (never commit it), build
   `bundleRelease` to get an `.aab`. Use Play App Signing.
3. **Privacy policy.** Host PRIVACY_POLICY.md publicly (GitHub Pages) and paste the URL in Play Console.
4. **Data safety form.** Declare: audio (voice, processed by Android speech service), user-generated content sent to the
   AI provider you choose, no data shared with the developer, API key stored encrypted on device.
5. **Accessibility declaration.** Complete the AccessibilityService and Foreground service ("special use") declarations.
   Explain: performs Back/Recents/Lock/Power/Screenshot (and a swipe gesture for Recents on phones that ignore the
   standard action) for people with damaged or hard-to-press buttons; no screen reading, no data collection.
   Add a short demo video. Approval is up to Google review.
6. **Foreground service declarations.** Besides "special use", declare "media projection" (screen recorder) and "microphone" in Play Console, with the demo video.
7. **Sensitive permissions.** WRITE_SETTINGS and ACCESS_NOTIFICATION_POLICY are special permissions the user grants in Settings; describe them in your listing (brightness / timeout / rotation shortcuts, silent mode).
8. **AI-generated content policy.** Apps with generative AI must let users report offensive output. Add an in-chat
   "Report" action (e.g. a mailto link) before submitting, and describe the AI feature in the listing.
9. **Prominent disclosure.** Already built in (dialog before opening Accessibility settings).
10. Optional: remove `WRITE_SECURE_SETTINGS` from the manifest for the Play build.

11. **Photo and video permissions policy.** READ_MEDIA_IMAGES / READ_MEDIA_VIDEO are used only by the optional Cleaning scan. Google restricts these
    permissions to apps whose core feature needs broad access. If review objects, remove the "Find big files" section (Cleaner.largeVideos / screenshots)
    and the two permissions; the rest of the app keeps working (NavAssist's own recordings need no permission).
12. **WebView.** The 3D scenes load only bundled local files (no network). Mention Three.js (MIT) in your open-source notices.
