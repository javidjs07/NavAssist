# Publishing NavAssist on Google Play

1. **Target API.** As of Aug 31 2026 Google Play requires new apps and updates to target Android 16 (API 36).
   Change `compileSdk`/`targetSdk` to 36, AGP to 8.9.1 or newer and `gradle-version` in
   `.github/workflows/build.yml` to 8.11.1 or newer, then test (edge-to-edge is enforced; the main screen
   already handles insets).
2. **Signing + bundle.** Create an upload keystore, store it in GitHub Secrets (never commit it),
   build `bundleRelease` to get an `.aab`. Play App Signing is recommended.
3. **Store assets.** 512x512 icon PNG, 1024x500 feature graphic, 2+ phone screenshots, short and full description.
4. **Privacy policy.** Host PRIVACY_POLICY.md publicly (GitHub Pages or raw repo page) and paste the URL.
5. **Accessibility declaration.** In Play Console > App content, complete the AccessibilityService (and
   Foreground service "special use") declarations. Explain: performs Back/Recents/Lock/Power/Screenshot
   for people whose hardware buttons are damaged or who cannot press them; no screen reading, no data collection.
   Add a short demo video. Approval is up to Google review.
6. **Prominent disclosure.** Already built in (dialog before opening Accessibility settings).
7. Optional: remove `WRITE_SECURE_SETTINGS` from the manifest for the Play build.
