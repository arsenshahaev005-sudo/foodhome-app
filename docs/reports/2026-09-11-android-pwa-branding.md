# Android PWA branding — 2026-09-11

## Scope and implementation

Android **0.2.2 / build 4**, on existing branch
`codex/android-notification-banners`, based on main
`3eb7da8f7b29e126148a1d664e56a3741e2c2770`.
Previous notification-banner work was preserved.

- Reused the original PWA v3 regular/maskable launcher icons and full logo.svg
  from food-home commit `6819590a84037c0701994c02250dc1b7c3537055`.
  Selected source files were clean locally; this is not a production-asset audit.
  Source paths and SHA-256 checksums are in `assets/branding/provenance.json`.
- Loading now uses the full wordmark on flat `#FFF7ED`, with three animated
  terracotta dots and accessible indeterminate-progress semantics.
- AndroidX splashscreen 1.2.0 supplies the system launch phase. Its safe-area
  wordmark is smaller than the subsequent loading logo. No extra activity,
  forced delay, network asset request or network-dependent splash hold was added.
- Original vector paths are mechanically converted by a checked-in deterministic
  script. Builds do not need the sibling checkout or a website connection.
- Existing content/offline/retry behavior, package identity and notification
  preferences are preserved. No food-home, iOS, bridge or server changes.
- Notification auto-enrollment and removal of notification prompts remain deferred.

## Verification

- Final combined Gradle run: **BUILD SUCCESSFUL** for debug unit tests,
  debug/release lint, debug/release APKs and Android test APK.
- Unit tests: **90 passed**, zero failures/errors/skips.
- Bridge tests: **25 passed**; branding Node tests: **4 passed**.
- Five existing shell/integration/capability/payment/hardening checks passed.
- Debug and release lint: **0 errors, 30 warnings each**; not warning-free.
- Focused on-device instrumentation: **4 passed** on Xiaomi Mi 9T / Android 10.
  Covered loading/logo/dots/accessibility, content/offline transitions, retry,
  vector inflation and adaptive launcher resource wiring.
- Device-rendered loading and launcher PNGs were pulled and visually inspected:
  original logo/artwork, cream background, unclipped geometry and loading dots.
- Debug APK signature verified; installed with replacement rather than app-data
  deletion. Installed version confirmed as **0.2.2 / build 4**.
- Real activity cold start: **Status: ok**, LaunchState COLD, TotalTime 2376ms.
  This measures activity startup, not completion of all website loading.
- Existing real notification channel remained HIGH / importance 4 with
  userLockedFields 4. No user channel preference was changed by branding tests.
- Auxiliary instrumentation APK removed after verification; main app retained.
- `git diff --check` passed. No hosted CI, commit, push or PR in this task.

During development, converter output and API-27-only theme attribute issues were
fixed and checked by the successful final build. One earlier instrumentation run
was interrupted by Xiaomi SwipeUpClean. A launcher test was adjusted to inspect
the bundled adaptive resource because Xiaomi wraps PackageManager icons. The
final four-test run completed successfully.

## Artifacts

Installable personal-test debug APK:
`output/apk/branding-8b1235e4/foodhome-app-0.2.2-push-debug.apk`

SHA-256:
`8b1235e403931c35a63b20672b71a3c258313f834316c2c1770a7ed8ddf4d721`

Sanitized device previews:
`output/pwa-loading-preview.png`, `output/pwa-launcher-preview.png`.

The release APK is **unsigned** and is not a distributable owner-signed release.
Client Firebase configuration remains ignored; credentials are not included here.

## Remaining acceptance and limitations

- Android 12+ system-splash appearance has not been visually verified on a real
  device/emulator in this task. Existing API-35 hosted UI coverage has not run yet.
- The focused branding checks do not establish a full website/payment/push
  foreground/background/process-dead regression matrix.
- No push was sent, no server flag changed and no test-delivery timer extended or
  checked in this branding task. Installed push capability is not server rollout.
- PWA and APK remain separate installations; shared artwork cannot merge their
  launcher entries. The system notification icon intentionally stays monochrome.
- Unrelated local files and earlier notification work were preserved.

Implementation runbook: `docs/runbooks/android-branding.md`.
Android system behavior references:
[splashscreen migration](https://developer.android.com/develop/ui/views/launch/splash-screen/migrate),
[AndroidX Core releases](https://developer.android.com/jetpack/androidx/releases/core).
