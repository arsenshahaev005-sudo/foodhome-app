# Direct APK in-app updates

Android 0.2.9 introduces an optional native updater for direct APK distribution.
Existing 0.2.8 users must install 0.2.9 manually once. There is no silent-install
promise: downloading requires a user tap, Android may require unknown-source
permission, and its installer requires user confirmation. No uninstall/data wipe.

## User flow

After web content loads in the foreground, check GitHub's public `releases/latest`
at most once per six hours. Show stable newer versions only. “Позже”/dismissal
defers for 24 hours. No forced updates or interruption of offline use. Failed
checks (including rate limits) are silent; failed downloads offer retry/later.
Downloads have percentage progress and cancel; app backgrounding does not invoke
the installer. After download select “Установить”. If needed, explicitly open
Android's install-source settings, return and select “Установить” again.
The system installer owns final verification, confirmation and installation.
Cancellation leaves the current app usable. Small pending release/stage/cache-name
state is kept in private preferences for at most 24 hours. Rotation, process death
or return from source settings restores the prompt independently of the six-hour
check interval. A cached APK is fully revalidated before showing it as ready and
again before installation. Missing/tampered cache offers retry; an interrupted
download returns to an explicit download button, never automatically restarts.
Installed/superseded or expired pending releases are ignored. Restoration never
opens settings or launches installation itself. There is no foreground/background
service and no promise to finish downloading after process death.

## Trust boundaries

No server, website cookies, bridge method, credentials, user IDs or push flags.
The client accesses only the existing public GitHub repository. Public metadata
is size-limited, stable-only, requires exactly the expected named uploaded APK
and GitHub's SHA-256 digest. Downloads allow HTTPS and a bounded redirect chain
only to GitHub release download paths and the exact GitHub asset CDN hosts.
No arbitrary URLs or redirects from web UI/config. APK size capped at 64 MiB.

Downloads live only in private updater cache, separately from captured media.
Validate full size/hash, archive package/version/build, minimum Android version,
non-debuggable status and the pinned current owner certificate. Current installed
certificate must also match. Revalidate immediately before the system installer,
including after permissions settings. Reject same/older versionCode. Do not
support signing-key rotation implicitly: separately review pin updates and
migration. Android's installer performs final cryptographic signature checks.
Corrupt/incomplete/cancelled files are removed; previous updater files are cleaned
before downloading. Never log signed CDN query strings or response bodies.

## Publishing

1. Increase both stable versionName and versionCode. Use the permanent existing
   owner signing key. Run local checks and CI and merge the reviewed PR.
2. Build/sign/test the exact release APK, including the upgrade path, account
   preservation, push rebinding and seller-order sound compatibility.
3. Publish a normal GitHub release `vX.Y.Z` in
   `arsenshahaev005-sudo/foodhome-app`, with one
   `foodhome-X.Y.Z-android.apk`. Confirm the uploaded asset's API digest starts
   with `sha256:` and matches the tested binary, and mark it latest.
4. Verify unauthenticated download/hash. Never publish debug/QA APKs, keys or
   Firebase/service-account configuration. Draft/prerelease is not offered.
5. Separately update food-home's website APK link and any exact-version push
   compatibility allowlists. No food-home source/backend change is required for
   update detection itself. APK publication and website deploy remain separate.

Debug builds never check for updates. For a future Google Play build pass
`-PFOODHOME_DIRECT_APK_UPDATES_ENABLED=false`: this disables the native updater
and removes REQUEST_INSTALL_PACKAGES from the merged manifest. Verify the
merged manifest and use the store update workflow, not a self-update bypass.
Do not submit the direct APK build as a Play build.

## Required device acceptance before publication

Test owner-signed direct builds on Android 10 and Android 13+: no-update/offline,
offer/later, download cancellation/retry, rejected unknown-source permission,
permission settings return, installer cancellation, actual signed higher-build
upgrade without uninstall, package/hash/certificate tampering rejected, account
and notification binding preserved. JVM tests do not prove system installer or
OEM behavior. Do not publish a dummy production release to test detection.

References: [Android installer](https://developer.android.com/reference/android/content/pm/PackageInstaller),
[GitHub releases API](https://docs.github.com/en/rest/releases/releases#get-the-latest-release).
