# Android branding shared with the PWA

The APK does not read the PWA manifest to configure its launcher or OS splash.
Reuse reviewed source assets from `food-home` as a pinned local snapshot instead.
See `assets/branding/provenance.json` for paths, commit and checksums. The sibling
repository is not needed to build or verify this APK.

## Visual contract

- Name: Food&Home; application ID remains `market.foodhome.app`.
- Launcher: original PWA v3 artwork. The regular PNG is retained as a fallback;
  the adaptive icon uses the PWA maskable image with an Android-safe inset. The
  launcher may apply its own shape. Round and regular launchers use the same resource.
- Background: `#FFF7ED`, matching the PWA manifest and launch overlay.
- Loading: original `/logo.svg` wordmark (up to 280dp, 76% of available width),
  16dp gap, three 6dp terracotta dots. No text placeholder or new image generation.
- The SVG's 16 path geometries and fill colors are preserved in VectorDrawables.
  The transform accounts for the original nonzero viewBox and inverted SVG group.
- AndroidX core-splashscreen 1.2.0 handles the system launch phase. Android limits
  the system icon to a safe area; the wordmark is smaller during that brief phase.
  Once Compose draws, the existing network-loading surface uses the full PWA layout.
  There is no second activity, forced minimum duration, delay or wait for network
  on the system splash. Error/retry states remain available through existing logic.
- Animation uses the Compose duration scale; the loading state has an accessible
  label and indeterminate progress semantics. Branding assets load locally/offline.

## Reproducibility

```text
node scripts/generate-android-branding.mjs
node --test scripts/android-branding.test.mjs
```

The converter only supports the reviewed SVG structure and rejects unsupported
path counts/features. Update provenance after an explicitly approved asset update.
Do not silently fetch new icons during a build. PNG files are unchanged copies;
their SHA-256 checksums and dimensions are verified. SVG hashes normalize CRLF to LF.
Android CI runs the branding verifier and existing UI smoke tests.

## Device verification

Run `FoodHomeShellSmokeTest` and `BrandingResourcesTest` with the existing Android
instrumentation runner. They exercise loading/content/offline transitions,
accessibility, drawable inflation and launcher resource wiring. Test-only PNGs
`foodhome-loading.png` and `foodhome-launcher.png` are written under the target
app's external files directory for visual inspection; they contain no account data.
Xiaomi may wrap icons returned by PackageManager, so the adaptive-icon type is
asserted against the bundled resource, not the OEM wrapper.

Check real cold start and return from background without clearing app data.
Android 12+ system-launch behavior also needs device/emulator coverage; an Android
10 screenshot alone does not establish it. Do not change notification preferences,
clear sessions, bypass TLS, or extend a push-test window for branding verification.

## Scope

Android APK only. No `food-home`, iOS, bridge, backend or server changes are required.
PWA and APK remain distinct installations; identical artwork does not merge their
launcher entries. The monochrome system-notification icon is intentionally separate
from the colored launcher icon and is not changed by this work.
