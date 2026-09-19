# Android notification branding — 2026-09-19

## Result

Local candidate on `codex/android-notification-branding`, based on `ce3f9cd`.
Android version **0.2.6 / build 8**, bridge remains **1.7.0**.

- Replaced the generic bell with a transparent white vector adapted from the existing house/heart/dish brand mark.
- Both normal and privacy-redacted notifications use the pinned local PWA maskable logo, decoded at 128px, plus the existing brand accent.
- Notification payload, privacy, channels, sounds, consent and delivery behavior are unchanged. Android/OEM templates still control placement and visibility of artwork.
- Existing branding PNGs and provenance hashes were not changed.

## Verification

- Android `testDebugUnitTest`: **110 passed**, zero failures/errors/skips.
- `lintDebug` and `lintRelease`: completed, **0 errors / 30 warnings each**; no findings reference the new branding helper/test/vector.
- `assembleDebug`, `assembleRelease`, `assembleDebugAndroidTest`: passed. Release output is unsigned, not a distributable production release.
- Node branding and sound tests: **7 passed**.
- `git diff --check`: passed.
- Vector preview rendered and visually inspected; 3,051 nontransparent pixels at 96px, transparent background and white geometry. This does not replace Android rendering verification.
- New instrumentation tests compile but were **not run**: no local AVD exists, and the owner asked to preserve the old installed APK.
- APK metadata verified with `aapt`: package `market.foodhome.app`, versionName `0.2.6`, versionCode `8`, minSdk 26, targetSdk 36. Generated BuildConfig has `NATIVE_PUSH_ENABLED=true`.
- `apksigner verify --print-certs` passed with the existing Android Debug certificate (SHA-256 `4b4c8fdcc0148b7a440643db13acd37483d3ddcac6c53a828e68bdd26542f8d5`). This is a test APK, not production signing.

## Artifact

`output/apk/notification-branding-87eeb87b/foodhome-app-0.2.6-push-debug.apk`

SHA-256: `87eeb87b1a1ecf833bfaab331bfefa9cf084e26a549caaacbc504fc69c2cfbf4`.

## Device and publication boundaries

Read-only ADB package inspection confirms the connected phone still has **0.2.5 / build 7**. No new installation, notification, order, production change or database mutation was performed for this branding change. No commit, push, PR or hosted CI was performed.

The previously user-confirmed local seller-order notification test used 0.2.5; it does **not** verify this new icon or new APK delivery.

## Cross-repository dependency

The current food-home allowlist accepts seller-order sound for exact tuple `(1.7.0, 0.2.5, 7)`. Before upgrading the phone, publish/review native changes, then extend the server allowlist to `(1.7.0, 0.2.6, 8)` while retaining the old tuple. Verify safe installation metadata refresh and test delivery after the coordinated rollout. Unknown releases must continue using the existing fallback.

Ready-to-send task: [food-home compatibility task](../integration/food-home-notification-branding-0.2.6.md).

The sibling repository was only inspected, not edited. Keep the phone on 0.2.5 until that dependency is resolved, as explicitly requested by the owner.
