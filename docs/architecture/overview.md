# Architecture overview

Food&Home Mobile is a pair of small native containers for the existing first-party web product at `https://foodhome.market`.

## Responsibilities

The native applications own operating-system integration: secure WebView containment, lifecycle recovery, platform navigation, deep-link routing, notifications, media selection, location, sharing, and controlled external handoffs.

The web product remains authoritative for product presentation, authentication, user roles, catalog data, cart and checkout behavior, orders, messages, and business rules. This repository does not contain a second frontend, backend, database, or mobile server.

## Platform shells

- The iOS application uses Swift, a SwiftUI lifecycle, and `WKWebView`.
- The Android application uses Kotlin, Jetpack Compose, AndroidX WebKit, and an Android `WebView` hosted in Compose.
- The versioned `FoodHomeBridge` contract is defined once in `bridge-contract/` and verified by fixtures and automated checks.

## Trust boundary

Only the exact HTTPS production origin `https://foodhome.market` may load as trusted first-party content. Bridge messages are accepted only from that origin and the main frame. External pages do not receive bridge access.

TLS errors, unknown schemes, invalid messages, unsupported methods, unavailable native features, and unsafe navigation requests fail closed. Remote configuration may disable or select capabilities already compiled into the application, but it cannot add trusted origins, permissions, bridge methods, or weaker security rules.

Native routing and client markers are convenience mechanisms, never authorization boundaries. Server-side authorization remains authoritative.
