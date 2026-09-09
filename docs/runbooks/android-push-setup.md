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
