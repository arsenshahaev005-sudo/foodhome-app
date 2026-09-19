# Android direct-download signing

Use an owner-controlled permanent key, never the Android debug certificate. The
Windows helper `scripts/android-release-sign.ps1` creates a new RSA-3072 PKCS12
key only in a new directory outside the repository, restricts its ACL to the
current Windows user and SYSTEM, and encrypts the random password with DPAPI.
Run as the owner, not the sandbox account. Never print the password or commit
key material. Never recreate the key for an update.

`Initialize` requires explicit owner approval. `Sign` requires the existing key,
an unsigned non-debuggable `market.foodhome.app` APK and Android build tools.
It checks alignment, signs using an ephemeral password environment variable,
then verifies the result. Gradle/CI remain unsigned; signing is a separate step.
No password is passed as a command-line argument or to a Gradle daemon.

## Recovery and backup

Before public distribution, the owner must back up the PKCS12 file and store its
password in their password manager, and verify opening the backup. The DPAPI file
alone cannot restore signing on a different Windows account/computer. Do not send
the password or key through chat, GitHub, logs or a public release. Use a trusted
local owner-only UI to transfer the password to the password manager.

Record the public signing-certificate SHA-256 in release evidence. Keep the same
key for direct APK updates. Existing debug-signed installs cannot update to this
identity; uninstalling them loses local app data and requires owner approval.
Do not automatically uninstall, clear data or migrate identities.

## Release gates

Publish source through a reviewed PR and passing CI before marking an artifact
as a public release. Record source commit, package, version/build, bridge,
Firebase project (not credentials), APK SHA-256, certificate SHA-256 and actual
device QA. A draft release is not a public download URL. Never describe local
checks as hosted CI or basic launch as full push/payment acceptance.

Build with `-PFOODHOME_NATIVE_PUSH_ENABLED=true`, no debug origin override, using
the owner-provided ignored Firebase client config. Sign `app-release-unsigned.apk`,
not `app-debug.apk`. Install the exact signed artifact and verify it on the
device before publication. Retain the unsigned build and immutable signed file.

Coordinate food-home's exact-version seller-order allowlist and frontend-server
`ANDROID_RELEASE_APK_URL` separately. Do not silently turn on website downloads
or server features. For future APK upgrades, verify binding version metadata is
refreshed through the ordinary nonce-based flow; the first fresh installation
does not prove the update lifecycle.

References: [Android signing](https://developer.android.com/studio/publish/app-signing),
[apksigner](https://developer.android.com/tools/apksigner).
