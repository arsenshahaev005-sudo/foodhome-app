# Android notification banners — 2026-09-11

## Scope and implementation

Branch: `codex/android-notification-banners`, based on published main
`3eb7da8f7b29e126148a1d664e56a3741e2c2770`.
Android version: **0.2.1 / build 3**. Bridge remains **1.5.0**, unchanged.

- New `foodhome_updates` channels use `IMPORTANCE_HIGH` for heads-up eligibility.
- Existing channels are left untouched, including default, quiet and blocked channels.
  No channel-ID migration, delete/recreate, app-data reset or permission bypass.
- Each new private notification includes a **Настройки** action. The immutable
  PendingIntent is pinned to a resolved, exported system settings component and
  contains only the fixed app/channel identifiers. No web payload or credentials.
- If channel settings cannot resolve, try app notification settings and then app
  details. If none resolve, omit the action. Manifest queries are scoped to those
  three intents; no broad package-discovery permission was added.
- Notification-body routing, generic private copy, public lock-screen version,
  consent, binding generation and expiration checks remain unchanged.
- No food-home source changes, new bridge method, iOS change or server rollout.

## Verified

- `testDebugUnitTest`: **90 passed**, zero failures/errors.
- Bridge `node --test`: **25 passed**, zero failures.
- All five existing shell/integration/capability/payment/hardening scripts passed.
- Final Firebase-enabled debug/release build: **BUILD SUCCESSFUL**.
- Debug and release lint: **0 errors, 26 warnings each**.
- Release artifact is **unsigned**, not a distributable owner-signed release.
- Focused `NotificationChannelSettingsTest`: **5 passed on Xiaomi Mi 9T**.
  Tests cover new HIGH importance; preservation of existing DEFAULT, LOW and NONE
  channels; system-resolved settings intent targeting this app/channel.
  Only UUID-scoped test channels were created and deleted. The real user channel
  was not modified by tests. Initial test APK install was denied by Xiaomi; after
  explicit owner confirmation the retry succeeded. The helper test APK was removed
  after testing; the main app and its data were retained.
- Main debug APK signature verified, installed with `adb install -r`, app start
  returned `Status: ok`; installed version is 0.2.1/build 3.
- Existing user channel still has importance **3 / DEFAULT**, userLockedFields 0,
  confirming this update did not silently override its settings.
- Opened the real channel settings via Android's standard action; resolved to
  `com.android.settings/.SubSettings`, `Status: ok`. No setting was toggled by agent.

Debug APK SHA-256:
`ccef03f3defa5ef6c7899fda9698f4b5c92ddafbf2d2063b1ba35a90ca22aa18`

Local artifact: `output/apk/banners-ccef03f3/foodhome-app-0.2.1-push-debug.apk`.
Client Firebase configuration stays ignored; no secrets are included in this report.

## Remaining acceptance and operational state

- Real heads-up display after the user's channel-setting change is **not yet
  verified**. HIGH is not a guarantee: Android/OEM settings and Do Not Disturb can
  still suppress a banner.
- The owner previously reported real notification receipt/tap behavior on 0.2.0;
  that is not a full 0.2.1 foreground/background/process-dead/lifecycle matrix.
- Actual notification-action tapping on a received 0.2.1 push remains to be checked;
  intent resolution and direct system settings launch were verified separately.
- Previously authorized server test timer completed: read-only inspection returned
  `restored-binding-only`, timer/service inactive. No extension or new send was
  performed during this change. A later live retest needs a separately approved
  delivery window; do not treat installed APK support as active server delivery.
- No commit, push, PR or hosted CI performed in this task. Existing unrelated
  `android/gradle/gradle-daemon-jvm.properties`, `debug.log`, and `output/` preserved.

References: [channel importance and user control](https://developer.android.com/develop/ui/compose/notifications/channels),
[scoped package visibility](https://developer.android.com/training/package-visibility/declaring).
