# Android notification recovery — 2026-09-12

## Scope and result

Local changes on existing `codex/android-notification-banners`: Android 0.2.4,
versionCode 6, additive bridge artifact candidate 1.6.0 / major 1. No commit,
push, PR, deployment or global push-flag change in this task. Existing unrelated
untracked files were preserved. No credentials or account data were changed.

The preceding live diagnosis on HONOR 70 / Android 14 found an ungranted runtime
permission without the native attempted marker, with Food&Home's existing channel
blocked. APK 0.2.3 prioritized the blocked channel and returned `denied`; the web
switch therefore never requested the OS dialog. The origin of the channel's
blocked state is unknown. Server consent was false and must remain respected.

## Changes

- Check unrequested Android 13+ runtime permission before channel blockage.
  Preserve the durable one-attempt policy, callback cancellation and opt-outs.
  A granted runtime permission still does not bypass a blocked channel.
- Add Android-only `openNotificationSettings` with empty payload, RESUMED/recent
  native touch requirement and one launch per five seconds. Reuse the own-app,
  system-component-pinned intent factory. No automatic settings launch or
  permission/channel changes. `presented` does not imply permission or binding.
- Update contract schemas/fixtures/version consistency checks. iOS does not
  advertise the new capability; only its contract version expectation changed.
- Prepare [separate food-home integration task](../integration/food-home-notification-recovery-1.6.md).
  The website has not been changed here; its recovery button requires that work
  and deployment. Existing web adapter filters unknown methods and uses major 1.

## Verified locally

- Android debug unit tests: 107 passed, 0 failures/errors/skips (24 suites).
- Android debug APK and instrumentation APK assembled successfully; instrumentation
  tests were NOT executed on a device in this task.
- Configured release variant assembled successfully; release APK is unsigned.
- Debug and release lint: 0 errors, 30 warnings each; compiler/SDK warnings remain.
- Bridge contract tests: 25 passed, including strict settings request fixtures.
- Secure-shell and Phase 2/3/4/5 invariant checks passed; `git diff --check` passed.
- iOS build/tests and GitHub CI were NOT run locally.

Push-enabled debug APK (signed with local debug key, not a public release):
`output/apk/notification-recovery-f6081365/foodhome-app-0.2.4-push-debug.apk`

SHA-256: `f60813653678d28755d726786dad9d6fc5d2585742e55f33caf5852016677502`.

## Device and publication blockers

ADB repeatedly returned an empty device list after the build. New APK was NOT
installed; the previously confirmed installed version was 0.2.3. Reconnect and
authorize the HONOR, install with `adb install -r` without wiping data, and verify
the actual first OS dialog and blocked-channel recovery. User chooses permission
and channel settings; do not auto-grant/reset them. Actual FCM delivery remains
unverified for this APK. Server flags and queues were untouched.

Publish reviewed native 1.6.0 source with genuine commit/hashes before the sibling
records immutable provenance. Do not reuse the historical 1.5.0 checksum manifest.
After sibling integration/deployment, verify recovery click, return refresh,
consent preservation and delivery on the matching installed APK.
