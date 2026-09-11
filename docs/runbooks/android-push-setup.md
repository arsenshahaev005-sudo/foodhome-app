# Android push setup and activation

Do not enable production based on a successful APK build. On 2026-09-09 the owner
created Firebase project `foodhome-6ebce`, registered `market.foodhome.app`, and
provided the Android client configuration. It is installed locally as the ignored
`android/app/google-services.json`; both Google Services resource-processing tasks
passed. This does not verify an enabled APK or delivery. Actual delivery remains
blocked on food-home integration, server credentials and real-device acceptance.

## Owner setup

1. Use the Food&Home-owned Google account in [Firebase Console](https://console.firebase.google.com/).
   Create the project, or explicitly choose the appropriate existing owned Google
   Cloud project if one exists. Do not create a second application backend/database.
2. Analytics is not needed for this implementation; leave it disabled. Do not add
   Firebase Authentication, Firestore, Realtime Database, Hosting or Cloud Functions.
3. Register the Android application using its exact package `market.foodhome.app`.
   Verify ownership/long-term identifier choice before distribution.
4. Download the Android **client** `google-services.json` to
   `android/app/google-services.json`. It is git-ignored. Do not confuse this with
   a privileged server service-account JSON/private key; those never belong in the APK.
5. Have the food-home deployment owner configure `FCM_PROJECT_ID`,
   `FCM_ANDROID_PACKAGE`, and least-privilege Google Application Default Credentials
   through the existing secret store. Never paste private keys into chat or git.
   Follow existing backend encryption/key-rotation instructions for stored push tokens.

## Prerequisites before an enabled build

Complete the separate tasks in
[Android visible push v2](../integration/android-visible-push-v2.md):
generation-aware bind response, versioned visible data-only delivery, web adapter,
buyer/seller settings and auth lifecycle cleanup. Keep old iOS/silent-v1 delivery
separate. Do not enable both platforms simply because Android is being tested.

No server changes, credentials or delivery flags are applied by this repository.
The existing `food-home` binding and native-delivery flags stay fail-closed until
the matching contract and provider configuration have been verified.

## Build

Ordinary builds are push-disabled and need no Firebase configuration:

```powershell
cd android
.\gradlew.bat testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease --no-daemon
```

After prerequisites and owner-provided client configuration, an explicitly enabled
test build uses:

```powershell
.\gradlew.bat assembleDebug -PFOODHOME_NATIVE_PUSH_ENABLED=true --no-daemon
```

The build must fail if the configuration file is missing. It rejects custom debug
base URLs when push is enabled; this client is bound to the verified production
origin and installation environment. Never substitute guessed staging domains,
production credentials or an invented API to get a test to pass.

## Device acceptance

### Heads-up banners and existing installations

Android 0.2.1 (build 3) creates new `foodhome_updates` channels with
`IMPORTANCE_HIGH`. Heads-up banners still depend on Android/OEM settings,
Do Not Disturb and the user's permission; this is not a delivery guarantee.
Already existing channels are preserved exactly, including the old default
importance, silent settings and disabled channels. Never delete/recreate or rename
a channel to bypass user preferences, and do not clear app data or uninstall it.

Expand a Food&Home notification and choose **Настройки** to open the system
settings for this app's channel. Enable floating/pop-up notifications there if
desired (labels differ by Android/OEM version). If channel settings are unavailable,
the action falls back to this app's notification settings or app details. The
action uses an immutable PendingIntent pinned to a resolved system component;
it accepts no web-controlled package/channel/URL and contains no push payload.
If none of these destinations can be resolved, the action is omitted.

Updating the APK does **not** raise an already created channel's importance.
Test both a fresh channel and an upgrade with existing default/quiet/disabled
channels. On a real device, independently verify the action destination, optional
user-enabled banner and unchanged notification-body chat/order navigation.

Focused instrumented regression (isolated `foodhome.test.updates.*` channels only):
`market.foodhome.app.notifications.NotificationChannelSettingsTest`.
The tests do not delete or modify the user's `foodhome_updates` channel.

References: [Android notification channels](https://developer.android.com/develop/ui/compose/notifications/channels),
[scoped package visibility](https://developer.android.com/training/package-visibility/declaring).

- Use working Google services. Direct APK installation does not require publication
  on Google Play. A device without Google services needs a separately designed
  provider integration; that is not silently emulated by polling.
- Test owner-controlled buyer and seller accounts after user consent.
- Trigger an actual test chat/order event through the authorized product workflow.
  Do not use a Firebase Console notification campaign: FCM `notification` payloads
  can be auto-displayed and bypass native data-payload validation.
- Confirm receipt in foreground/background/process-dead states, generic lock-screen
  text, and the correct authorized destination on tap. Force-stop and OS/Xiaomi
  battery restrictions are separate cases, not a delivery guarantee.
- Test disabled OS permission/channel, app opt-out, token rotation, reinstall,
  logout offline, account switch, old queued messages and old notification taps.
- Record sanitized pass/fail evidence, build version, OS, and configuration status;
  never record raw token, nonce, private message, full URL or credential.

## Rollback

Disable visible-v2 delivery in food-home first and keep normal web/browser behavior.
Use the app opt-out/scoped revoke flow to remove device bindings where possible.
Ship a push-disabled binary only as a controlled fallback, not as a substitute for
server revocation. Do not delete signing keys or user WebView cookies during rollback.
