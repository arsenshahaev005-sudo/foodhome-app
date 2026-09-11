# Android notification permission UX — 2026-09-11

## Result and scope

Implemented the APK side of the approved notification UX, Android **0.2.3 / build 5**.
Branch: `codex/android-notification-banners`; base main
`3eb7da8f7b29e126148a1d664e56a3741e2c2770`. Changes remain local, uncommitted.
Earlier notification-channel/settings-action and PWA branding work was preserved.

- Removed the extra native confirmation dialog and its obsolete string.
- Added a testable single-owner permission flow: one Android 13+ OS request while
  foregrounded, persistent attempted marker before launch, no repeat after denial,
  swipe-away or interrupted request, no callback replacement by concurrent requests.
- Authorized users and Android <=12 receive current state without UI. Pre-13
  globally blocked notifications now correctly return denied, not notDetermined.
- Renderer/disposal and clear/revoke cancel pending callbacks. Late OS completion
  cannot activate push or complete a newer permission request. No launch on storage
  failure or from a background activity; launch exceptions cancel safely.
- Preserved OS/channel user choices, native token privacy, disabled Firebase
  auto-init and the existing generation-aware binding/revocation path.
- No food-home, iOS, bridge schema/manifest, server, credentials or delivery changes.

Authenticated login orchestration and the website's redundant settings cards are
owned by food-home. They are **not removed by this APK alone**. A complete scoped
implementation prompt is in
`docs/integration/food-home-automatic-native-push-prompt.md`, with API/capability and
APK compatibility gates, durable opt-out, auth race tests and truthful provenance.
The existing web enable() must not be invoked unchanged automatically: it writes
consent=true. The automatic path must preserve server push_enabled=false.

## Changed subsystems

- `notifications/NotificationPermissionFlow.kt`: effective status policy and flow.
- `notifications/AndroidNotificationCoordinator.kt`: real status inputs and durable
  attempted marker, retaining existing preferences/key for upgrades.
- `ui/FoodHomeAppShell.kt`: ActivityResult launcher, lifecycle gate, cancellation;
  no notification pre-prompt. Location/media confirmation behavior unchanged.
- `NotificationPermissionFlowTest.kt`: 15 unit cases.
- `NotificationPermissionIntegrationTest.kt`: read-only pre-13 device case; does
  not modify app permission, user preferences or real notification channels.
- Version metadata, phase-3 invariants and integration documentation updated.

## Verified locally

Final configured Gradle run completed **BUILD SUCCESSFUL** (2m 6s):
`testDebugUnitTest`, `lintDebug`, `lintRelease`, `assembleDebug`, `assembleRelease`,
`assembleDebugAndroidTest`, with `-PFOODHOME_NATIVE_PUSH_ENABLED=true`.

- **105 unit tests passed**, 0 failures/errors/skips (90 existing + 15 new).
- **25 bridge tests and 4 branding tests passed**.
- Five existing secure-shell/phase-2/phase-3/phase-4/phase-5 invariant scripts passed.
  Phase-3's old pre-prompt assertion was replaced with the new guarded OS-flow
  assertions; no check was disabled or skipped.
- Debug/release lint: **0 errors, 30 warnings each**. Not warning-free.
- APK signature verification passed (v2). The release artifact is unsigned;
  neither this debug APK nor the unsigned artifact is a public store release.
- No hosted CI, commit, push, PR, merge or deployment was performed.

## Device evidence and limitations

The Xiaomi Mi 9T / Android 10 was initially connected. Replacement installation
of an intermediate 0.2.3 debug build succeeded, preserving app data. Its SHA-256:
`f11fa906f9f609be20ec8c2c49a33561637139dd5345e6e7b5e062111488df7d`.

The first helper test APK installation returned a nonzero result with no diagnostic
reason. A subsequent read-only check reported **device not found**. This was not a
confirmed user denial. The owner then reconnected the phone and authorized the retry.

The final code adds an explicit API-33 guard at the launcher call (in addition to
the existing flow gate), removing the new InlinedApi lint warning. The resulting
final APK below was subsequently installed successfully with replacement, followed
by the helper package. No account data was cleared.

Final focused instrumentation returned **OK (10 tests)**:
1 pre-13 permission integration, 5 channel/settings tests, 3 loading/recovery UI
tests and 1 branding resource test. The permission test reads the real system
state twice and asserts no dialog/attempt-marker write. Channel tests touch only
their own UUID-scoped test channels and remove them; the real channel is preserved.

Removed only `market.foodhome.app.test` after the successful run. Main activity cold
start returned **Status: ok**, LaunchState COLD, TotalTime 2613ms. Installed version
confirmed **0.2.3 / build 5**. The real `foodhome_updates` channel remains importance
4 / HIGH, userLockedFields 4, deleted=false. No user notification setting changed.

Outstanding: Android 13+ real system-dialog grant/deny/swipe-away behavior is not
device-verified; the Xiaomi Android 10 cannot establish it. Full login/bind/FCM
lifecycle acceptance also awaits food-home changes and a separately approved
delivery test. No push was sent or test timer extended.

## Final artifact

`output/apk/permission-b1d69395/foodhome-app-0.2.3-push-debug.apk`

SHA-256:
`b1d6939595ec39169d5a0aeacaff4162c0e72fff5d92ce45d044afab5350dd7b`

## Next steps

1. Complete food-home work using the supplied prompt on its current branch.
2. Publish native and web changes through separately authorized commit/PR/CI.
   Bridge 1.5.0 itself is unchanged; do not invent a new contract release or source
   commit for this uncommitted native behavior.
3. Install the matching APK and deploy the accepted web integration. Keep server
   delivery settings unchanged until an explicitly approved rollout/test.
4. Verify eligible login, existing opt-out, both Android permission generations,
   logout/account switching and real delivery independently.

Reference: [Android notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission).
