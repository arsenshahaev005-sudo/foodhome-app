# Android geolocation: GPS-only request fallback

## Scope and cause

The owner reports a long-running location lookup that fails in the APK, including
on a newer Android phone. Connected device: Mi 9T / Android 10 (API 29), initially
FoodHome 0.2.9 / build 11. Both coarse/fine permissions are already granted and
system location mode is enabled. No permissions or location settings were changed.

The previous `AndroidLocationProvider` chose the first enabled provider in
GPS-before-network order. An enabled GPS is not proof that it has a location fix.
With fine permission, network was never queried if GPS was enabled. Android 8-10
also waited for a new single update without using a recent cached fix. Both native
lookup and the website bridge had a 10-second timeout, leaving no response margin.
The GPS-only source selection is confirmed from source; its relation to the
owner's symptom is consistent with the report, not a measured old-versus-new
device benchmark of the entire address flow.

## Changes

- Query available network and permitted GPS sources in one bounded request;
  return the first valid result. One failing source cannot cancel the other.
- Use the existing AndroidX `LocationManagerCompat.getCurrentLocation` on all
  supported Android versions. Its Android 8-10 implementation can use a very
  recent fix, while preserving cancellation of new requests.
- An eight-second native deadline leaves a response margin within the existing
  bridge timeout. Cancel every source and timer after success, failure or cancel;
  ignore duplicate/late callbacks. Cancel a previous lookup before replacing its
  native confirmation dialog.
- Reject non-finite/out-of-range coordinates or invalid accuracy instead of
  coercing them into a fabricated valid coordinate. The wire contract is unchanged.
- Prepare candidate 0.2.10 / versionCode 12. Bridge remains 1.7.0.
- Update the existing Phase 3 source gate for the extracted one-shot coordinator.

Files: `AndroidLocationProvider.kt`, `CurrentLocationRequest.kt`,
`LocationRequestResult.kt`, `FoodHomeAppShell.kt`, `CurrentLocationRequestTest.kt`,
`android/app/build.gradle.kts`, `scripts/verify-phase-3-capabilities.mjs`.

## Validation

- 165 Android JVM tests passed, including 12 new request-coordinator tests.
  Network while GPS stalls, null/throwing source, coarse-only source, timeout,
  cancel, synchronous cache delivery, duplicate/late callback and invalid fix
  paths are covered. JVM tests do not exercise Android framework permissions.
- Debug and native-push-enabled release build/lint passed. Lint: zero errors;
  debug 37 warnings/two hints, release 36 warnings/two hints. No location-related
  lint findings were observed.
- 26 bridge contract tests and 15 branding/sound/lifecycle tests passed.
  Secure shell and Phase 2/3/4/5 invariant scripts passed. `git diff --check` passed.
- Existing owner signing certificate retained. v2/v3 signature and alignment
  verified; additional apksigner SDK 33-36 signature verification passed.
- Exact candidate installed onto Mi 9T with `adb install -r`: Success;
  firstInstallTime remains 2026-09-20 22:57:57, version becomes 0.2.10/build 12.
  Cold launch Status ok. No uninstall or data wipe of the main app.
- A same-signed temporary instrumentation package invoked the actual installed
  release provider through this build's local R8 mapping (not copied production
  code and not a mock). Result: `locationGranted=true`, typed result valid,
  elapsed 54ms. It printed no coordinates, accuracy values or account data.
  The temporary package was removed and the main application reopened.

Candidate: `output/releases/0.2.10-location/foodhome-0.2.10-android.apk`.
SHA-256: `c2e76675bb434687e1cb617818d151b62e82da88317ca88a68383b3929809b5a`.
Certificate SHA-256:
`b3e8823f71ad073653b0314f40f13fd902f9b5850458674c1b7db59d9f5e2645`.
At the device-QA checkpoint, source was local changes on
`codex/android-location-fallback`, based on main
`68140bf63c13a83ac9ea43864a890176f8641a43`. Accepted source/CI and public artifact
provenance must be recorded separately when publishing the release.

## Physical-device address-flow acceptance

The owner authorized retry and the temporary same-signed web-flow package was
installed after the earlier Xiaomi INSTALL_FAILED_USER_RESTRICTED rejections.
The helper checks interactive/unlocked prerequisites and sets FLAG_KEEP_SCREEN_ON
on its target activity only for the test lifetime. OS screen-timeout settings
remain unchanged. Initial locked/dozing runs are not accepted as foreground QA.

Guarded foreground runs confirmed the deployed website posts a valid native
requestLocation message with a purpose-only payload, not browser geolocation.
A getNotificationStatus roundtrip over the same bridge was the positive control.
Two foreground timeout samples had no observed completed consent. UI automation
could not find/click the native confirmation on this Xiaomi; this absence in its
accessibility tree is not evidence that the dialog was absent. The owner confirmed
pressing Continue in the successful run and asked to repeat a later run whose
dialog they had not seen. No transport or malformed-message defect was established.

With owner confirmation, the exact installed signed candidate completed two native
roundtrips: 8,584ms and 2,733ms from the address-button click, including consent time.
The first also triggered reverse geocoding. The final complete run additionally
confirmed geocoding completion, a nonempty address input and a map marker; the
location button stopped being busy, no timeout text appeared, and browser
geolocation was not called. Coordinates, address text, accuracy values and account
data were not printed. No address-save action was taken. Temporary JS observers
delegated the actual production handlers and were restored after each run.
This confirms the happy path on Mi 9T / Android 10, not universal device coverage
or a measured old-versus-new performance comparison. No physical Android 13+
test was performed in this task; the same compat provider path builds for it and
the existing runtime permission checks remain in place.

The temporary diagnostic package was removed after acceptance and the main app
was reopened. Main application data was not cleared.

## Remaining publication and cross-repository dependency

The owner subsequently authorized publishing 0.2.10 and testing actual in-app
update detection from the previous public 0.2.9. The verified public 0.2.9 APK
was restored with `adb install -r -d`: Success, installed versionCode 11, original
firstInstallTime retained. No uninstall or main-app data clear was performed.
This preparation is not yet proof of update detection, download or OS installation.
The updater checks at most once per six hours while content is foreground/ready;
any QA-only reset of its check timer must be disclosed and must not clear account,
notification or website state. Do not describe user-confirmed installation as a
silent automatic update.

The website bridge's 10-second request timeout includes time spent in native
confirmation and the OS permission dialog. A slow confirmation may still expire
the web request even with a fast provider. This is a separate cross-repository
timeout/consent-budget issue to review with `food-home`; this patch does not
change the published bridge limits or silently bypass native confirmation.

Before public distribution: publish source through the normal PR/CI/merge flow,
then publish the signed artifact. Separately coordinate
the website APK link and exact special seller-order sound compatibility profile
`(1.7.0, 0.2.10, 12)` with food-home. No food-home source, server configuration,
production flags, manual database mutations or real push sends were performed
by this task. Ordinary app startup/session behavior was not modified.
