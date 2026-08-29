# Food&Home Mobile

Native iOS and Android containers for [foodhome.market](https://foodhome.market).

The repository intentionally contains thin platform shells rather than a second product frontend or backend:

- iOS uses Swift, SwiftUI, and `WKWebView`;
- Android uses Kotlin, Jetpack Compose, and Android `WebView`;
- `bridge-contract/` defines the versioned interface between the trusted website and native capabilities;
- business rules, authentication, catalog, checkout, orders, and other product UI remain web-authoritative.

## Repository layout

```text
android/          Android application and Gradle wrapper
ios/              iOS application and Xcode project
bridge-contract/  Versioned schemas, fixtures, and contract tests
scripts/          Cross-platform security and integration checks
docs/             Public architecture notes
```

## Local verification

The bridge contract and repository invariant checks require Node.js 22 or newer:

```text
npm --prefix bridge-contract ci --ignore-scripts --no-audit --no-fund
npm --prefix bridge-contract test
node scripts/verify-secure-shells.mjs
node scripts/verify-phase-2-integration.mjs
node scripts/verify-phase-3-capabilities.mjs
node scripts/verify-phase-4-payments.mjs
node scripts/verify-phase-5-hardening.mjs
```

Android builds require JDK 17 and an Android SDK. Run the repository Gradle wrapper from `android/`:

```text
./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease --no-daemon
```

iOS builds require a supported macOS and Xcode installation. Continuous integration builds and tests an unsigned simulator target; production signing material is not stored in this repository.

## Security and source use

The production container trusts only the exact HTTPS origin `https://foodhome.market`. Native bridge capabilities are restricted to that origin and fail closed when validation or platform support is unavailable.

Do not commit credentials, signing certificates, provisioning profiles, keystores, private keys, service-account files, or signed build artifacts. Report suspected vulnerabilities through the process in [SECURITY.md](SECURITY.md).

No open-source license is granted. Public visibility makes the source available for inspection but does not grant permission to copy, modify, or redistribute it.
