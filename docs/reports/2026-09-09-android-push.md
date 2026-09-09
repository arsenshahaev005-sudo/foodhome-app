# Android visible push — 2026-09-09

## Scope and source

Branch: `codex/android-push-notifications`, based on merged `origin/main`
`75ac08bf045dd791d47f3fec57eeada4a590948d`.

The owner requested Android system notifications for chat and order events,
including background delivery. No Firebase project existed at the initial
implementation check; owner provisioning is recorded in the follow-up below.
Changes are local and uncommitted at this report's creation. No PR, deployment,
server configuration, provider project or account has been created in this task.

Only foodhome-app is modified. The existing website remains authoritative for
product UI, authentication and business rules. No second frontend, backend,
database or server was introduced. iOS native behavior is unchanged; its manifest
version test expectation follows the additive shared artifact update.

## Implemented

- Bridge artifact 1.5.0, major 1: conditional Android `managePush` capability with
  status, nonce binding, local clear and scoped server revoke. Disabled builds
  and iOS do not advertise it. Raw provider tokens never cross JavaScript.
- Native FCM service, strict visible application payload v2 validation, generic
  local notifications, immutable notification-tap routing and permission/channel
  checks. Legacy v1 remains silent and is not promoted into visible alerts.
- Persistent opaque binding generation, bounded event dedupe, token-rotation
  invalidation and stale-completion protection. Local clear cancels notifications
  and invalidates pending binds; opt-out survives token rotation.
- Fixed-origin, cookie-free nonce redemption with bounded responses/deadlines,
  no redirects, automatic retries or credential logging. The active server receipt
  must match the new binding generation before native reports enabled.
- Explicit default-off build switch; owner Firebase client configuration is
  required to enable it. Firebase auto-init and analytics collection are disabled.
  Android version is 0.2.0 / code 2, package `market.foodhome.app`.

The required new server bindingId receipt and visible-v2 delivery are documented
as cross-repository work, NOT represented as already available APIs. SDK/provider
updates do not silently migrate the existing backend FCM-token contract to FID.

## Verification performed

| Check | Result |
| --- | --- |
| `testDebugUnitTest` | PASS — 90 tests, 0 failures/errors/skips |
| Bridge contract tests | PASS — 25 tests |
| Secure-shell and phase 2/3/4/5 invariant scripts | PASS — all five |
| `lintDebug`, `lintRelease` | PASS — 0 errors, 24 warnings per variant |
| `assembleDebug` | PASS — local debug-signed APK |
| `assembleRelease` | PASS — unsigned APK, not a distribution release |
| Bridge `npm pack --dry-run --ignore-scripts` | PASS — 1.5.0, 62 packaged files; no publication |
| Enabled Gradle configuration without Firebase client file | EXPECTED REJECTION — explicit missing-owner-config guard |
| `git diff --check` | PASS |
| Tracked Firebase client/signing files | None |

The final combined Gradle run completed successfully in 3m 12s using the installed
Android Studio JBR and Android SDK. XML test and lint reports were inspected.
Lint warnings remain, including a newer-OkHttp-version notice; this is not a
warning-free build. The HTTP-client tests use an in-process interceptor and do not
send registration requests to the production backend.

An intermediate bridge test caught an Ajv strictTypes issue in the new schema;
the schema was corrected and the final 25-test suite passed. The initial npm
pack command ran from the wrong working directory; the correct package-directory
dry run passed. Neither intermediate failure is claimed as a successful check.

## Not verified / release blockers

1. Owner Firebase configuration is now present and its resource processing passed
   (see follow-up below). An enabled Firebase-configured APK and real FCM
   registration/delivery were NOT tested.
2. food-home must implement the generation-aware receipt, visible-v2 provider
   payload and web opt-in/auth-lifecycle adapter before activation. Existing
   silent-v1 delivery alone is insufficient. No sibling files were changed.
3. No APK was installed or tested on a physical device in this task. Foreground,
   background, process-dead, opt-out, logout/account-switch and notification-tap
   acceptance remain open. OS/force-stop/Xiaomi limits are not delivery guarantees.
4. No iOS/Xcode run or new GitHub CI run was performed. Windows checks do not
   establish iOS compilation or App Store readiness.
5. Release signing, store privacy disclosures and review evidence remain release
   gates. The unsigned APK and local unit tests are not production acceptance.

Built artifacts are under `android/app/build/outputs/apk/`: debug/app-debug.apk
and release/app-release-unsigned.apk. Both were built push-disabled. Reinstalling
these files alone will NOT activate notifications. Do not commit APKs or secrets.

## Next steps and rollback

Follow the [owner setup runbook](../runbooks/android-push-setup.md) and implement
the separate [food-home integration tasks](../integration/android-visible-push-v2.md).
Then create a configured test build and record sanitized real-device evidence.
No Firebase Console notification campaign is an acceptable substitute: its
notification envelope can bypass native data-payload validation in the background.

Rollback visible delivery on the server first, revoke bindings where possible,
and preserve existing web functionality and signing material. A disabled binary
alone is not proof that remote bindings have been revoked.

Existing unrelated local `android/gradle/gradle-daemon-jvm.properties`, `debug.log`
and `output/` were preserved. No user data, credentials or signing keys were deleted.

## Owner Firebase configuration follow-up — 2026-09-09

The owner created project `foodhome-6ebce`. Their Google Cloud IAM screenshot
confirmed the project account's Owner role; no IAM changes were made by the agent.
Initially absent Firebase registration controls subsequently appeared. The exact
cause of the earlier console access/display problem was not established.

The owner supplied `Downloads/google-services.json`. Parsed metadata matches
project `foodhome-6ebce`, project number `432596720099`, and exactly one Android
client for `market.foodhome.app`; the Firebase Android app ID matches that project
number. This is a client configuration, not a service-account private key.

Installed it at `android/app/google-services.json`, preserving the original file.
Semantic JSON equality was verified. Its existing git-ignore rule was checked and
the file is not tracked. No key values or full configuration were printed in the
verification report or stored in project memory.

Verification performed in this follow-up:

- `:app:processDebugGoogleServices :app:processReleaseGoogleServices
  -PFOODHOME_NATIVE_PUSH_ENABLED=true --no-daemon`: PASS in 18s; one task executed,
  one reused the Gradle cache. These tasks process resources only; they do not
  install/run an app, register a device or send a push.
- Phase 3 native capability invariant script: PASS with the real ignored config.
- `git diff --check`: PASS.

The command-line switch was temporary for resource validation only. The checked-in
default remains false. No new APK was assembled or installed in this follow-up,
and no server credentials, notification campaigns or production flags were changed.
Prior Android/bridge test results above were not rerun or relabeled as new runs.
Next steps are the separate food-home integration/provider-credential tasks and
then a configured APK plus real-device acceptance.

## Publication preflight — 2026-09-09

The owner authorized commit, push and a pull request for the native work and
contract. Fresh fetch confirmed the branch base still matches origin/main at
75ac08bf045dd791d47f3fec57eeada4a590948d. No native source or bridge payload changes
were added during publication preparation.

- Bridge tests ran again: 25 passed; all five repository invariant scripts passed.
- Gradle tests/lint/debug/release/instrumentation-APK assembly completed in 1m 37s
  with 30 executed tasks, four from cache and 104 up-to-date.
- Unit tests were then explicitly rerun with `:app:testDebugUnitTest --rerun`:
  90 tests, zero failures/errors/skips, successful command in 44s.
- Debug and release lint reports remain at zero errors and 24 warnings each.
  Instrumentation APK assembly is not a device test. SDK XML/native-access and
  existing Compose test deprecation warnings were not hidden.
- Generated debug, release and test BuildConfig values remain push-disabled.
- Staged path checks, exact matching against the supplied Firebase key values and
  common credential-pattern checks found no included config, key or build artifact.
  This targeted preflight is not a full repository security audit.
- All 64 contract-source file checksums were verified against Git index blobs.
  See [source handoff](../releases/foodhome-bridge-contract-1.5.0.md); the checksum
  manifest and its LF policy are included without any owner configuration.

The user reports food-home implementation is prepared using a local candidate
snapshot. That report is not a fresh review or test of the sibling work. Downstream
must pin the published source and verify its snapshot before replacing provenance.
PR creation, GitHub CI and merge state must be checked separately after push; this
preflight is not a claim of completed remote CI, merge or production activation.
