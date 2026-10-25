# Android broadcast/buyer push v6/v7 — source candidate

Branch: `codex/android-broadcast-buyer-push`, created from refreshed
`origin/main` at `7b84a2da19e5e7571d5b9ee3ac6104f84e7d6db0`.
Tuple: `["1.11.0", "0.2.14", "16"]`.

## Implemented

- Existing FCM service/runtime now accept v6 broadcast.notification and v7
  buyer.notification, exact six-field strings, strict version/event/identifier/
  binding/expiry/2048 UTF-8 byte rules and fixed-origin role-inbox/owned-order routes.
  V1 silent and visible v2-v5 remain separate and unchanged in wire shape.
- Neutral local Russian copy, ordinary updates channel, existing private/public
  lock-screen variants and branding. Seller-only paid-new-order sound remains
  exclusive to seller.order.new. No WebView-derived or polling-derived alerts.
- Provider notification envelopes are rejected before SDK dispatch (including
  background auto-display), with the existing onMessageReceived guard retained.
  Missing collapse_key is accepted; no client-side collapse by event type.
- Binding-specific immutable PendingIntents; persisted TTL-bound receipt/tap
  ledger; duplicate/unknown/repeated/stale taps rejected across process restart,
  logout, account switch, rebind and token rotation. Legacy ledger migration does
  not reset consent or owner binding. At128 live receipts, new alerts fail closed
  until expiry rather than evicting dedupe history; server feed remains intact.
- onDeletedMessages persists a coalesced hint and reuses the existing trusted,
  visible, main-frame appResumed lifecycle transport. Successful JavaScript
  dispatch clears only the matching hint revision, not business/payment events
  and not proof that the server synchronization completed. No new API, permission
  loop, consent toggle, auth backend, notification feed or product UI was added.

## Source/provenance

Server remote main and merged PR#369/#371 were checked at
`e245b0e859de93ecd193538fb18aef7b5c9bbdbd`. All eight selected Git blobs were read
without changing the sibling checkout or its refs. Canonical schema/fixture
SHA256 manifests were verified against raw Git bytes before mechanical import.
`bridge-contract/provenance/push-v6-v7.json` records paths, blob IDs and all hashes;
`node scripts/sync-server-push-contracts.mjs` checks them offline in CI.
LF rules include checksum manifests, avoiding Windows CRLF hash drift.

No SDK upgrades: Firebase BOM34.18.0 resolves messaging25.1.2, AGP9.1.1,
Kotlin2.3.21, google-services4.5.0. Android uses compileSdk37/target36/min26.
Local runtime was Studio JBR25.0.3 / Gradle9.3.1; the owner's existing untracked
daemon-JVM file was preserved and is not part of the PR. CI retains its JDK17 job.
Node22.22.2 and the existing npm lockfile were used. No owner release signing
material was read or created; Gradle used its ordinary debug signer. Only the
shared manifest expectation changes in the iOS test; no iOS
push implementation is claimed.

## Verification completed locally

- Firebase-enabled command: `gradlew.bat testDebugUnitTest lintDebug lintRelease
  assembleDebug assembleRelease assembleDebugAndroidTest
  -PFOODHOME_NATIVE_PUSH_ENABLED=true --no-daemon --console=plain`: passed.
- 178 JVM tests across30 suites,0 failures/errors;10 new expanded-push cases cover
  canonical fixtures, roles/order routes, strict types and invalid inputs, exact
  UTF-8 boundary, envelopes, expiry, duplicates/distinct events, process restart,
  stale/repeated taps, logout/rebind, rotation, legacy migration and recovery state.
- lintDebug0 errors/37 warnings/2 hints; lintRelease0 errors/36 warnings/2 hints.
  Warnings remain visible, not suppressed or converted into a baseline.
- 33 bridge Node tests passed, including imported schemas, canonical sorted compact
  FCM route serialization and source hashes. Secure-shell and Phase2-5 invariant
  scripts passed. Branding/seller-sound7 tests and lifecycle/expanded-push12 tests
  passed, including evaluation of the Kotlin-emitted lifecycle JavaScript.
- Debug, unsigned release and instrumentation-test APKs compiled. Instrumentation
  execution, emulator/device delivery, foreground/background/process-dead/lock
  screen, real notification tap/sound and Android13+ permission acceptance were
  NOT performed. No APK was installed, and no push/provider request was sent.
- Default-off command `testDebugUnitTest assembleDebug
  -PFOODHOME_NATIVE_PUSH_ENABLED=false` passed with the same178 tests. Existing
  direct-distribution verification passed. `processReleaseMainManifest
  generateReleaseBuildConfig -PFOODHOME_NATIVE_PUSH_ENABLED=false
  -PFOODHOME_DIRECT_APK_UPDATES_ENABLED=false` and store-distribution verifier
  passed; no APK-updater installation permission leaks into that manifest.
- Hosted GitHub CI is separate from these local results. Unpacked archive checks
  use the original source package-lock for exactly the tested dev dependencies:
  all33 tests also passed in the unpacked1.11.0 archive, without repository docs.
  Initial artifact-only dependency setup failed due to a wrong npm working directory
  and then an uncached unlocked transitive version; final offline `npm ci` with
  the source lockfile passed. No implementation gate was skipped.

## Local artifacts (ignored, not published)

These bytes were built from the matching candidate worktree, not an owner-signed
release. Saved under `output/qa/push-v6-v7/`:

| Artifact | SHA256 |
|---|---|
| Native-enabled debug APK | `140189696d38b246911a548aa432034b69d69e8ea9f2cbe5ba5c5cd5d4acb8de` |
| Native-enabled unsigned release APK | `4de0a41fc336457ccfb1f644a0c8adb5b23f99fe5b501ea182acd39a8585843c` |
| Private bridge1.11.0 archive | `722860f5ec7df7aef9de97b9fa5c5c982948e04dce18f7985a3c5979d17facdb` |

## Remaining gates and handoff

[Integration/runbook](../integration/android-broadcast-buyer-push-v6-v7.md)
lists the complete v1-v7 compatibility matrix, existing seller sound/v3/v4/v5
reviewed-profile changes and separate default-off v6/v7 settings. The new tuple
is a candidate, not approved for production allowlists. Do not add old/synthetic
tuples or infer old-event support from v7 activation.

Separate owner approval is required for merge, owner-signed public release,
device installation/testing, server configuration and real sends. Preserve the
same package and owner signing identity; update in place through the existing
verified updater and normal nonce-authorized metadata rebind. Never uninstall,
clear account data, reset opt-out, patch installation versions or replay old events.

No food-home source, server setting, production service, release/download link,
credential, signing key or user permission was changed. Existing untracked
`debug.log` and `android/gradle/gradle-daemon-jvm.properties` remain excluded.
