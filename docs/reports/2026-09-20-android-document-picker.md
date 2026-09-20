# Android document picker: HTML accept normalization

## Scope and cause

The owner reported that the NPD certificate appears in the Android system picker
but cannot be selected, while the same website form works in a browser.
`SellerVerificationDocumentsPanel` supplies `.jpg,.jpeg,.png,.webp,.pdf,.doc,.docx`.
The native shell forwarded these extensions into `OpenDocument`'s MIME-type array.
HTML extensions are not Android MIME types. Chromium's standard chooser intent
normalizes them, but this shell uses a custom Activity Result picker.

The passport form mixes image MIME types with `.heif,.heic`; its accept list is
also covered. A real passport upload has not been attempted.

## Changes

- `MediaRequestPolicy` splits comma-separated accept values, normalizes case with
  `Locale.ROOT`, translates document/image extensions to stable MIME types, and
  deduplicates the result. Other extensions use Android `MimeTypeMap` supplied by
  the shell, keeping the policy independently unit-testable.
- Removed the eight-type truncation so later allowed formats are not silently lost.
- Empty, unrestricted, or unresolved accept filters use the document picker with
  `*/*`, not a photo-only picker or an invalid extension filter. HTML accept is a
  picker hint, not authorization or validation; existing website/backend file
  validation is unchanged.
- Known image/video paths and the existing camera/multiple-selection behavior
  remain. Mixed image/document lists can offer the existing camera/source dialog.
- `allowFileAccess=false`, `allowContentAccess=false`, scoped FileProvider paths,
  bridge, location, notification settings, permissions and server code are unchanged.

## Verification

- Full Android JVM suite: **123 passed**, including **11 media-policy tests**.
- Android debug/release lint: **0 errors, 30 warnings, 2 hints** each.
- Release assembly with native push enabled: **passed** (unsigned verification
  artifact only, still version 0.2.7/code 9; not a new distributable release).
- Bridge contract: **26 passed**; branding/sound/lifecycle Node tests: **15 passed**.
- Five secure-shell/Phase 2-5 invariant scripts and `git diff --check`: passed.
- New `FoodHomeDocumentUploadTest` compiles and passes on the physical device. It loads a local synthetic HTML form
  in the production WebView composable, checks the real chooser callback's
  normalized Android intent filters and supplies a synthetic PDF through the
  FileProvider to test JavaScript file reading with secure settings unchanged.
  It does not send an HTTP upload or automate the actual DocumentsUI selection.
- Device: Mi 9T, Android 10, existing main APK 0.2.7. A local-only Gradle init
  script builds a separate `market.foodhome.app.uploadqa` application and its test
  APK to avoid replacing the owner's release or touching their account data.
  Initial installations were rejected with `INSTALL_FAILED_USER_RESTRICTED`.
  On the owner's next explicit retry both packages installed successfully.
  The first test exposed a test-harness issue: JavaScript-only input.click did
  not activate the chooser. The test now dispatches a touch to the input inside
  its own WebView (no external UI or permission-setting manipulation).
  One subsequent run timed out awaiting the initial JavaScript callback (5s);
  the unchanged test then passed twice consecutively: **OK (1 test)** followed by
  **OK (2 tests)** with the existing WebView viewport/layout regression test.
  The cold-start callback timeout remains a test-stability observation, not a
  claimed upload failure. These runs prove normalized chooser-intent MIME values
  and synthetic selected-PDF byte reading with both access flags still false.
  They do not prove manual DocumentsUI interaction or a server upload.
  No device security settings were changed or bypassed.
  Both isolated QA packages were uninstalled successfully after the tests; the
  owner's main app remains installed at 0.2.7/code 9 with its data untouched.

## Remaining acceptance / release boundary

1. Synthetic WebView instrumentation: completed on Android 10; monitor the initial
   WebView callback timeout noted above in future cold-start/CI device runs.
2. Verify actual system picker selection on Android 10 and Android 13+, including
   NPD PDF, passport image, cancellation and multiple images where supported.
3. Verify upload to the server only with an explicitly approved test document/account;
   never upload a fake certificate to production verification records.
4. For distribution, bump the Android version/code, build and sign with the existing
   release key, test the exact artifact, publish via the normal reviewed release flow.
   Do not replace the already published 0.2.7 artifact with different bytes.

No commit, push, PR, public release, main-app installation, production changes or
real-document upload was performed for this fix. Geolocation integration in
`food-home` remains a separate task: the website must invoke the existing native
`requestLocation` capability rather than the intentionally disabled browser API.

## Follow-up: owner-requested 0.2.8 build

The owner requested a new release build and reported that the sibling geolocation
fix is done (not independently verified). Android is now 0.2.8/code 10. Re-ran the
123 JVM tests, both lint variants, release assembly with native push enabled,
26 bridge tests, 15 branding/sound/lifecycle tests and five invariant scripts: all
passed, lint still 0 errors/30 warnings/2 hints.

Signed with the existing owner certificate; v2/v3 signature and alignment verified.
Local candidate: `output/releases/0.2.8-documents/foodhome-0.2.8-android.apk`.
SHA-256: `d63e29033bda6376e112c26a69e9cd2049572e929c670c745d8a576356143996`.
The candidate directory retains checksum, unsigned APK and honest local source
provenance (base commit plus source patch; not a clean accepted release commit).

After the owner reconnected the phone, installed the exact signed APK over 0.2.7
on Mi 9T/Android 10 using `adb install -r`, without uninstalling or clearing data.
Device version/code and APK SHA-256 matched; the main activity started. Requested
owner manual verification of PDF selectability without selecting/uploading the
document. No public release, commit, push, PR, server flag/allowlist/download URL
change or production document upload was performed. Exact 0.2.8 manual document,
geolocation and notification/seller-sound acceptance remained pending at installation.

The owner subsequently replied "works" to the manual PDF-selectability and
geolocation checks and explicitly requested publication. Record those two checks
as owner-confirmed on the installed 0.2.8 APK, not as an independently observed
server upload. Notification/seller-sound acceptance for 0.2.8 remains unverified.
Publication is being prepared through a PR and CI; no release gate is bypassed.
