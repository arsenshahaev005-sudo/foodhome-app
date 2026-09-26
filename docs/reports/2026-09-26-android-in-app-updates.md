# Android direct APK updater — local implementation

## Scope

Implemented locally in foodhome-app, version 0.2.9/build 11. No food-home edits,
backend, database, provider messages, production flags, deploy, commit/push/PR,
published release. A separate local QA helper was installed with owner approval;
the owner subsequently confirmed a main-app in-place upgrade as recorded below.
Existing untracked owner files retained.

## Behavior

- Optional stable-update prompt after foreground web content loads; GitHub public
  latest-release metadata only. At most one check per six hours; defer 24 hours.
- User-approved download with progress/cancel, bounded size and HTTPS redirects,
  digest verification, owner certificate/package/build validation, OS permission
  settings and explicit installer confirmation. No silent installation claim.
- Private temporary cache/FileProvider scope, no account cookies/tokens/device
  IDs forwarded to GitHub. Ordinary network metadata/IP still reach the provider.
- Debug builds do not activate updater. Store option disables runtime feature
  and excludes REQUEST_INSTALL_PACKAGES through a release-only manifest overlay.
- Existing users need one manual install of a published signed 0.2.9. Website
  download link and server exact-version push allowlists remain separate tasks.

## Verification

- Android JVM results: 140 tests, zero failures, including 17 new updater-policy
  tests. Checks cover version ordering, stable release/asset constraints, digest,
  size, redirect allowlist, APK identity policy, corrupt/truncated/oversized and
  cancelled download cleanup, and bounded metadata reading.
- Local `testDebugUnitTest lintDebug lintRelease assembleRelease`: successful.
  Debug lint: zero errors, 28 warnings, two hints. Release lint: zero errors,
  27 warnings, two hints. Generated release APK is unsigned and push-disabled;
  it is a local verification artifact, not a publishable production APK.
- Bridge contract: 26 passed. Branding/seller-sound source/asset checks: 7 passed.
  Five secure-shell/phase invariant checks and `git diff --check` passed.
- Direct-release generated manifest and BuildConfig checked. Store-disabled
  manifest/BuildConfig generation passed and distribution script verified no
  REQUEST_INSTALL_PACKAGES and false runtime flag. CI workflow now repeats both
  distribution checks; hosted CI has not been started.
- Live read-only GitHub API: latest public stable release is v0.2.8; named APK
  contains SHA-256 digest matching the previously published binary. Local
  apksigner verification of that binary confirms the owner certificate equals
  the updater pin. No dummy release or test asset was uploaded.

Initial Gradle attempt hit insufficient disk space. Owner freed space; later
local build succeeded. User's Gradle daemon JVM criteria select JDK 25 locally;
CI remains configured for JDK 17. A manifest-placeholder attempt was rejected
by manifest-merger; final implementation uses a conditional release source-set
overlay, tested in both modes.

## Remaining release gates

### USB preparation on 2026-09-26

Connected Mi 9T Android 10/API 29, currently owner-signed 0.2.8/build 10.
Rebuilt candidate with native push enabled and the real Firebase client profile;
signed with the existing owner key (v2/v3 and alignment verified). Candidate:
`output/releases/0.2.9-updater/foodhome-0.2.9-android.apk`, SHA-256
`b3c736d38f9e38019c2a112313ca382346ba45216369424e85154307c4d4fadd`.
140 JVM tests and debug/release lint repeated successfully after adding internal
test client injection. No public release or source publication.

Built an ignored, separately packaged `market.foodhome.app.updaterqa` harness
with exact hash-matched copies of the three production updater source files and
the actual signed candidate asset. Its metadata/download transport is a local
fixture, not a published GitHub release. The installed helper would be signed
with the owner certificate and run the same UI/hash/archive policy; Android's
real installer would update the existing main app only after explicit approval.
No website session/backend/database or actual push sending is part of this test.

First helper install was rejected by Xiaomi (`INSTALL_FAILED_USER_RESTRICTED`)
while the phone was dozing. After owner unlock/approval the helper installed.

### USB findings and corrected candidate

- Observed stable offer, download progress, cancellation, no offer for an equal
  version, and no immediate offer after Later/relaunch. Local fixture transport
  only: this is not evidence of a published 0.2.9 GitHub download.
- Bad digest and mismatched-version scenarios reached Error without installer.
  The initial version-negative test was confounded by the certificate-parser
  failure below; repeat the version-negative control against the corrected code.
- Positive control revealed a real Android 10/Mi9T archive-parser compatibility
  defect: GET_SIGNING_CERTIFICATES alone returned correct archive identity but
  empty current signers. Requesting GET_SIGNATURES as well caused SigningInfo to
  contain the expected pinned current signer. Production now requests both for
  archive parsing; API 28+ still reads current SigningInfo only, with no fallback
  to the legacy oldest/rotated signer and no relaxation of the certificate pin.
- Corrected helper reached Ready with a valid signed candidate. Install without
  source permission displayed the explanation and opened Android settings.
  Returning without granting permission did not launch an installer; the prompt
  was lost on this return. Settings-return readiness and actual installation
  remain unverified (process/configuration loss does not auto-install).
- Repeated main `testDebugUnitTest lintDebug lintRelease assembleRelease` with
  native push enabled: successful, 140 tests / zero failures. Signed corrected
  candidate with the existing owner key, verified v2/v3 and alignment:
  `output/releases/0.2.9-updater/foodhome-0.2.9-android-certfix.apk`, SHA-256
  `99d231113fe40d91deae777181eeef56317c452586c05299c0bb163ffd9fc2bd`.
  This supersedes the first candidate; neither has been published.
- Temporary diagnostics were added only to ignored helper copies, then removed
  by recopying exact production source before the final helper build. No account,
  token, password or private signing key was logged.
- Final corrected candidate reached Ready and then the real Android installer
  displayed Food&Home's update confirmation. Source permission was already
  allowed at this point. Awaiting owner confirmation; not yet an installed update.

### Installed upgrade and Android 13+ code review

Owner confirmed installation. Fresh ADB verifies main app 0.2.9/build 11,
firstInstallTime still 2026-09-20 22:57:57, lastUpdateTime 2026-09-26 16:44:33.
Pulled installed base APK SHA-256 equals the corrected candidate
`99d231113fe40d91deae777181eeef56317c452586c05299c0bb163ffd9fc2bd`.
Cold launch returned Status ok, 665 ms; process exists. No agent uninstall/data
clear. Unchanged first-install time confirms an in-place upgrade, not actual
logged-in session or push delivery preservation; those remain owner/device checks.

Only Mi9T/API29 is connected. Android 13+/API33+ review confirms:

- Direct manifest has REQUEST_INSTALL_PACKAGES, minSdk26/targetSdk36. Runtime
  canRequestPackageInstalls and ACTION_MANAGE_UNKNOWN_APP_SOURCES use public APIs
  available since API26. Permission is per installation source and user-owned.
- Private cache and FileProvider content URI/read grant do not require Android
  13 media permissions. No external-storage APK path, PendingIntent, service or
  background installer is introduced. Notification permission is unrelated to
  download/install; existing notification permission flow is unchanged.
- Current SigningInfo API28+ and longVersionCode remain valid. Strict pinned
  identity checks fail closed, with Android installer final verification.
- Direct manifest/runtime distribution check repeated successfully. Fresh JVM
  run successful: 140 tests, zero failures. No emulator/API33+ installation test;
  JVM policy tests must not be represented as Android framework/device tests.

Release review finding: ApkUpdatePrompt.kt lines45-47 keep release/stage/file in
`remember` only. Activity recreation on rotation, memory pressure or return from
source-permission settings loses the pending flow; persistent checked_at can
suppress a new offer for six hours. The earlier lost-return prompt is consistent
with this risk, but the exact device lifecycle trigger was not instrumented.
Preserve/recover a small validated pending state, without auto-installing, and
test settings return/configuration restoration before treating the updater as
production-ready. The subsequent owner-requested fix is documented below.

References: [Compose state restoration](https://developer.android.com/develop/ui/compose/state-saving),
[install-source settings](https://developer.android.com/reference/android/provider/Settings#ACTION_MANAGE_UNKNOWN_APP_SOURCES),
[FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider).

Before public release: reviewed PR and hosted CI, permanent-key signing with
native push configured, owner-signed Android 10 and 13+ update tests without
uninstall/data wipe, account/push-binding preservation and server seller sound
compatibility. Also test permission denial/settings return, cancellation/retry,
offline and tampered files. Process death/rotation never auto-resume downloads or
auto-install; bounded private pending state now restores the explicit prompt.
These limitations are documented, not reported as tested delivery or installation.

### Pending state fix and repeated Android 13+ review

Implemented after the owner requested fixing the lost pending prompt:

- New `ApkUpdateSessionPolicy` persists a bounded schema-versioned descriptor in
  private preferences, maximum accepted age 24 hours. It reuses strict public
  release URL/version/hash/size validation, limits JSON to 4096 characters and
  rejects absolute/traversing cache paths. Same/older installed versions discard
  pending state. No account data or signing credentials are stored.
- A downloaded file is recovered only as an explicit button, fully revalidated
  on foreground recovery and before installer handoff. Interrupted downloads
  return to Offer; missing/untrusted files return to Error/retry. No automatic
  download, settings launch or installation on restoration.
- State is synchronously persisted before external settings/installer UI.
  Activity-result callbacks return to an explicit button and are not treated as
  installation proof. Temporarily hiding the dialog for external UI is not Later.
- Local release assembly and 153 JVM tests passed (zero failures/errors/skips).
  Debug/release lint: zero errors, respectively 29/28 warnings and two hints;
  synchronous preference commits are intentional before external UI handoff.
  Direct/store manifest/runtime checks passed and direct configuration restored.
  SDK policy
  matrix covers 26/29/33/34/35/36, but these are JVM policy tests, not framework
  or physical Android 13+ installation tests. Public API usage, private cache/
  FileProvider grants and store-disabled distribution were reviewed again.

Only Android 10/Mi9T is attached. The installed main APK is still the earlier
`99d231...` candidate without this pending-state fix. New production source has
not been installed into the main app or published; helper tests use exact copies
of the four production updater files and fixture metadata/transport only.

USB helper evidence: valid signed archive reached Ready; force-stop/cold launch
with pending preferences restored Ready within two seconds, not the installer.
Portrait -> landscape -> portrait retained Ready. Original rotation settings were
restored (user_rotation=0, accelerometer_rotation=0). Later followed by cold launch
showed no update dialog. Screenshots are local ignored QA artifacts under
`output/qa/updater-harness/restoration-*.png`. These are tests of exact production
source in a separate package, not published-release discovery or main-app upgrade.
Repeated mismatched-version negative control against the corrected parser:
metadata 0.2.10 with valid owner-signed 0.2.9 bytes reached Error, not installer.
The preceding valid control reached Ready with that same archive, so the earlier
certificate-parser failure no longer confounds this version-negative test.

Prepared owner-signed corrected candidate (push enabled, direct updater enabled,
non-debug, v2/v3 signature and alignment verified):
`output/releases/0.2.9-updater/foodhome-0.2.9-android-restoration.apk`, SHA-256
`ad858b2fef4f4ee9805f81190d1632daeab4dd9521c449c1ddc2fa67170cb52b`.
This supersedes previous local candidates. No public release, hosted CI, PR,
production changes or main-app installation of this binary in this fix turn.
Physical Android 13+, actual source-permission settings return/denial against the
fixed code and account/push preservation remain acceptance gates.

### Owner decision: proceed without a physical Android 13+ test

On 2026-09-26 the owner explicitly said that an Android 13+ device test is not
available and asked to continue without it. This waives that physical-device
gate for the 0.2.9 publication workflow; it does not change its status to passed.
Repeated code review found no confirmed Android 13+ updater incompatibility.
The exact signed restoration candidate passed `apksigner verify` with SDK bounds
33 through 36, and its manifest declares minSdk26/targetSdk36 and the required
install-source permission. These checks do not prove OEM installer behavior.
Actual account and push preservation on that device remain unverified.
