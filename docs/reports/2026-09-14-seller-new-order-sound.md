# Seller new-order sound — local implementation, 2026-09-14

Publication follow-up: the owner subsequently authorized commit/push/PR/CI/merge.
Initial source commit is `d4d022c2575ea9157781d2e92df453e0dbe8f175` on the new
`codex/seller-new-order-sound` branch based on accepted main `ae87051`.
See [immutable source handoff](../releases/foodhome-bridge-contract-1.7.0.md).
The implementation and device-verification record below describes the local
pre-publication result; current GitHub CI/merge status must be verified separately.

## Scope

Owner selected `03_rising_excite.wav` and confirmed seller-only new orders.
Implemented on the existing `codex/android-notification-banners` branch, starting
from `e7ef82a`. No commit, push, PR, merge, deployment, provider send or server flag
change. Existing untracked daemon configuration, debug.log and output were preserved.
No food-home source changes; its required work is a separate integration task.

## Native result

- Android 0.2.5/build 7, bridge artifact candidate 1.7.0, major 1 unchanged.
- Only explicit `seller.order.new` selects `foodhome_seller_new_orders` and generic
  local new-order copy; `order.updated` and `chat.message` retain the old category.
- Existing push envelope v2 keys, exact routes, expiry, binding generation,
  logout/opt-out and persistent dedupe guards remain. No role flag, arbitrary
  sound URL or private message copy is accepted. Server still owns recipient roles
  and the actual new-order business event; this is not implemented in the shell.
- Supplied 4.01998-second, mono 44.1kHz/16-bit PCM WAV copied byte-for-byte, with
  a stable named resource URI and resource-shrinker keep rule. Source SHA-256:
  `7fb0a3e173d9cb882b3a95aa55072f34b536bcb9842c3729c10c6a3829559710`.
- The new category is created lazily for an eligible seller event. It inherits
  existing lower importance/silence/badge/vibration and never rewrites an existing
  channel. No forced volume, DND bypass, alarm usage or looping player.
- Existing aggregate updates-channel gate remains conservative. Disabling that
  old category still blocks all native push; the seller category adds its own
  restriction. This is not a new independent per-category consent/status API.
- Notification Settings actions use their corresponding native allowlisted
  channel and distinct PendingIntent request codes. Existing bridge method still
  opens the old settings target with its unchanged empty payload/gesture policy.
- iOS runtime and PWA are unchanged. Only iOS contract-version test expectation
  moves with the canonical artifact; no iOS custom-sound support is claimed.

## Verification

- Final configured Android build: unit tests, debug/release lint, debug APK,
  unsigned release APK and instrumentation APK assembly all PASS.
- 110 Android unit tests, 0 failures/errors/skips.
- 26 bridge tests + 2 sound asset tests + 4 branding tests PASS.
- All 5 secure-shell/Phase 2/3/4/5 invariant scripts and diff whitespace check PASS.
- Debug/release lint: 0 errors, 30 existing warnings each. No new lint warning.
- Both built APKs contain the original WAV bytes and SHA-256. Release resource
  table retains `raw/seller_new_order`, mapped to optimized `res/dQ.wav`.
- Final debug APK signature verifies; manifest reports versionCode 7 / 0.2.5.
- Added 7 isolated instrumentation tests for named resource decode/hash, channel
  sound/defaults, legacy mute/importance preservation, existing-category preservation
  and settings allowlist. They compiled but were NOT executed locally.
- Android CI additionally runs the sound integrity tests; GitHub CI was NOT run.
- iOS/Xcode tests, provider delivery and audible on-device playback NOT verified.
  ADB reported no connected devices. No APK was installed and no test notifications
  were posted. Build success does not establish real delivery or audible playback.

Final push-enabled debug artifact (not a production-signed distribution):
`output/apk/seller-sound-3e3916b3/foodhome-app-0.2.5-push-debug.apk`

APK SHA-256: `3e3916b3e83a880acc80df1551a56b0f45b39d04ed2f524019cac72f9206b973`.

## Next steps / blockers

[Food-home implementation prompt and payload/rollout rules](../integration/food-home-seller-order-sound-1.7.md).
Publish actual reviewed native 1.7.0 provenance before claiming immutable release
compatibility. Sender must classify seller new orders and gate the new enum by
verified installation compatibility; old/unknown APKs retain `order.updated`.
Use existing authenticated rebind if installed-version metadata needs refreshing.
Never send both formats or replay historical orders as new alerts.

Install the matching APK without clearing user data, then verify one approved
new-order scenario for seller, unchanged buyer/chat/status-update sounds, duplicate
suppression, muted settings and foreground/background/process-dead delivery.
Server event selection must fall back before a downgrade to older APKs.
