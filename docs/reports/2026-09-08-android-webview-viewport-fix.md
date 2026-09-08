# Android WebView viewport fix — 2026-09-08

## Scope

Branch: `codex/fix-android-webview-viewport`, based on `37e908b`.

Fix only Android WebView sizing in `foodhome-app`. No website, iOS, bridge
contract, authentication, payment, provider configuration or credential changes.
The independently diagnosed Tailwind/global CSS conflict remains a separate
`food-home` task. This report is not a store-readiness or full product test claim.

## Evidence and change

On Xiaomi Mi 9T / Android 10 / WebView 149.0.7827.159, the original APK reported
nonzero `innerHeight` but zero CSS viewport-unit heights and zero body height.
Real Gboard input in registration reversed `abcde` to `edcba`, with caret 0.
A plain HTML input in the same layout reproduced the problem without React.
Changing only the live document height temporarily made both the plain input
and the real registration name field work correctly. Those temporary changes
were removed after diagnosis; they are not part of the fix.

`FoodHomeWebView.kt` now sets both child `ViewGroup.LayoutParams` dimensions
to `MATCH_PARENT` before attaching/loading the WebView. Compose's existing
`fillMaxSize`, safe drawing and IME insets remain unchanged. No fixed pixel
height, injected CSS/JavaScript workaround or cursor manipulation was added.

The default child `WRAP_CONTENT` height can cause WebView's forced-zero layout
height behavior even when Compose measures the native view to a visible size.
Explicit match-parent sizing follows the
[Android WebView sizing guidance](https://developer.android.com/develop/ui/views/layout/webapps/best-practices).

## Regression coverage

Added `FoodHomeWebViewLayoutTest` using the actual `FoodHomeWebView` composable
with an in-memory HTML fixture, unavailable capabilities, an empty in-memory
payment store and disabled telemetry. It does not open a production URL or use
an account, backend, local server or provider.

The test checks:

- body and viewport-unit height against the native/Compose viewport;
- height shrink/restore and width changes through deterministic Compose bounds;
- scrolling an initially offscreen form field into view;
- explicit match-parent child layout parameters.

The fixture falls back from `dvh` to `vh` on WebViews without dynamic viewport
unit support. Constraint changes are not claimed as physical IME or device
rotation tests. Existing Android CI already runs all instrumentation tests.

Added `android/.kotlin/` to `.gitignore` for compiler session artifacts generated
by local builds. Existing user files and diagnostic screenshots were preserved.

## Verification performed

| Check | Result |
| --- | --- |
| Android `testDebugUnitTest` | PASS — 72 tests, 0 failures/errors |
| Bridge contract tests | PASS — 23 tests |
| Secure shell + phase 2/3/4/5 invariant scripts | PASS — all five |
| `lintDebug`, `lintRelease` | PASS — 0 errors; 23 warnings per variant remain |
| `assembleDebug` | PASS |
| `assembleRelease` | PASS — unsigned artifact, not a distribution release |
| `assembleDebugAndroidTest` | PASS — new test compiled |
| `git diff --check` | PASS — CRLF conversion notice only |
| Physical-device regression test | PASS — pre-fix fails on body=0; corrected APK passes |
| Full physical-device instrumentation suite | PASS — all 3 tests on Xiaomi Mi 9T |
| Live registration viewport, corrected APK | PASS — body 769, viewport units about 768.7 CSS px; no height overrides |
| Live keyboard opening / closing | PASS — body resizes to 514 and returns to 769 CSS px |
| New APK real Gboard check | PASS — original registration name field produces Abcde, caret positions 1–5; owner confirms |

Initial sandbox/offline attempts failed because of Gradle cache access and
uncached lint dependencies; subsequent runs with the existing local JDK/Gradle
cache and configured dependency repositories succeeded. No lint suppression
or dependency version changes were introduced to make checks pass.

The initial install attempt returned `INSTALL_FAILED_USER_RESTRICTED`.
After the owner enabled USB installation, both APKs were installed with `adb
install -r`, preserving the application's data. The new regression test failed
against the pre-fix APK with `innerWidth=300`, `innerHeight=240`, and
`body=vh=dvh=0` (expected body height 240). Against the corrected APK the full
instrumentation suite returned `OK (3 tests)`, including all resize and scroll
assertions. The helper test package was then removed; the main application was
not uninstalled and its data was not cleared. No security-setting bypass was used.

## Local APK and next validation

Debug APK: `android/app/build/outputs/apk/debug/app-debug.apk`.

SHA-256: `8D216511BD84BA8355A535AFC746F054354F22A623EC03803C3372B8279A59AC`.

This is a debug-signed local test build, not a production-distribution artifact.
Do not commit APKs, local screenshots or signing material.

Physical-device follow-up:

1. Completed pre-fix failure and post-fix pass comparison on the same device.
2. Corrected APK: live registration body height is 769 CSS px; html, 100vh,
   100dvh, 100svh and 100lvh are about 768.7 CSS px. Both html/body inline
   height overrides are empty. A normal automated click on the name field
   succeeds. Local sanitized evidence: `output/playwright/fixed-apk-registration-baseline.json`.
3. Real Gboard produced `A → Ab → Abc → Abcd → Abcde` in the original
   registration name field, with `selectionStart/selectionEnd` advancing
   through 1–5. The owner confirmed that input works. Opening Gboard resized
   body and viewport units together from about 769 to 514 CSS px; closing it
   restored about 769. No height overrides or cursor manipulation were used.
   Sanitized evidence: `output/playwright/fixed-apk-gboard-confirmed.json`.
   No registration was submitted by the agent.
4. Removed the temporary input/resize observer and ADB forward, and cleared
   only synthetic text from the observed test field. By completion the page
   had moved to `/auth/login`; preserved that current screen rather than
   navigating away from the owner's ongoing actions. No account data, cookies
   or current login fields were cleared.

The reported reverse-input defect is verified fixed on this device. Physical
rotation, repeated keyboard reopen cycles, other form fields, other devices
and broader buyer/seller flows remain outside this completed validation; do
not interpret it as full application or store-release acceptance.

The verification above was completed locally; GitHub Actions results are
tracked separately on the pull request. No production deployment, release
signing or website changes are included.

## Required-check trigger follow-up — 2026-09-09

PR #2 initially passed Android and Bridge contract checks, but the required
`iOS` check remained `Expected`: its workflow-level `pull_request.paths`
filter excluded the Android-only change, so no iOS workflow run was created.
This is a trigger configuration issue, not an observed iOS test failure.

All three required workflows now use an unfiltered `pull_request: {}` trigger.
This also prevents the same deadlock on iOS-only or documentation-only PRs.
The tradeoff is running all three suites on every PR update. Main-branch push
path filters, job/check names, permissions, pinned actions, runners and actual
build/test commands are unchanged. Branch protection is not weakened.

`verify-phase-5-hardening.mjs` now requires that explicit unfiltered PR trigger
in all three workflows. The new assertion failed against the previous
configuration and passed after the fix. All five invariant scripts and all
23 bridge tests passed again. YAML parsing and a semantic comparison confirmed
that only the PR triggers changed in the workflows; `git diff --check` passed.

The CI-only follow-up does not change native application code or rebuild the
local APK. Results on the new PR commit must be checked in GitHub Actions;
earlier green runs are not evidence that this new commit passed. Merge remains
the owner's action after all required checks succeed.

## iOS simulator environment follow-up — 2026-09-09

After the trigger fix, iOS run `34272502501` started but exited with code 70
before compilation or tests: no concrete simulator matched `OS=latest` and
`name=iPhone 16 Pro`. Xcode project syntax passed. The runner selected Xcode
16.4 by default and listed only generic destinations; this is not evidence
of a failed application test. The log alone does not explain why the runner's
pre-created devices were unavailable.

The workflow now explicitly selects Xcode 26.3 with the iOS 26.2 runtime and
iPhone 16 Pro device type, a pairing listed in the
[runner image inventory](https://github.com/actions/runner-images/blob/macos-15-arm64/20260829.0321/images/macos/macos-15-arm64-Readme.md).
It initializes Xcode components, prints toolchain/runtime/device diagnostics,
checks that the exact runtime is available, creates its own isolated simulator,
validates the returned UDID and waits for boot completion before testing that
UDID. Preparation is bounded to eight minutes within the existing 30-minute
job limit. It fails explicitly if preparation fails; it does not silently
select another runtime, skip tests or download an unreviewed latest runtime.
The disposable GitHub-hosted VM owns this test device; no physical device,
signing credentials or application data is used.

All unit/UI tests, disabled parallel test execution, unsigned simulator Release
build, required-check name and branch protection remain intact. Native source
and minimum supported iOS version are unchanged. This compiler/runtime update
is CI validation, not App Store signing or release acceptance.

Local validation: the extended source invariant failed before the workflow
change and passed afterwards; all five invariant scripts and 23 bridge tests
passed. YAML parsing and Bash syntax validation passed. Six shell-control-flow
cases with mocked Xcode/simctl/jq commands covered success, missing Xcode,
unavailable runtime, malformed UDID, boot failure and boot-wait failure. These
mocks do not verify Apple's tools or execute native tests. Actual simulator
preparation, compilation and test results must be verified on the new GitHub
Actions commit; Windows cannot run Xcode locally.
