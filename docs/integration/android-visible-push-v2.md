# Android visible push integration

Status: Android implementation completed and locally verified; owner Android
Firebase configuration imported and resource processing verified. Production
delivery is BLOCKED on food-home integration, server credentials and real-device
acceptance.
See [verification report](../reports/2026-09-09-android-push.md).

This is an Android-only follow-up to the Phase 3 seams. It keeps the existing
WebView/frontend/backend boundary. iOS and browser push are unchanged.

## Contract and privacy

Bridge artifact 1.5.0 adds `managePush` to bridge major 1. Only a configured Android
binary advertises it. Never infer availability from the version or OS permission.
Payloads are `{action: "status"|"clear"}` or
`{action: "bind"|"revoke", nonce: "...", expiresAt: "RFC3339"}`.
`clear` immediately disables local delivery, cancels notifications and invalidates
in-flight binding, including offline. It is NOT proof of server revocation.
Results contain only `capability: "push"` and a status:
`enabled`, `disabled`, `needsBinding`, `permissionDenied`, `unavailable`.
Tokens, installation IDs, cookies, JWTs and nonces are never returned to JavaScript.
The native rate limit is five bind attempts per minute. Local status/clear/revoke
are not blocked by exhausted bind limits; server revocation remains throttled by
the existing backend policy.

Binding uses the existing food-home POST endpoints under
`https://foodhome.market/api/v1/mobile/installations/`: `bind/` and `revoke/`.
Only a short-lived `Authorization: FoodHomeBinding <nonce>` authorizes those calls.
There are no cookies, redirects, arbitrary URLs, automatic retries or token logs.
The authenticated website obtains each purpose-specific nonce using the existing
`binding-nonces/` endpoint. A consumed nonce must never be retried.

### Required food-home extension (not implemented in that repository here)

Existing binding v1 response has no binding generation. Existing silent payload
v1 has no audience generation. Do NOT enable visible delivery against that contract.
The server must atomically save `bindingId = lowercase SHA-256(raw bind nonce)` on
every successful bind, return it as additive safe metadata, and copy it into each
visible delivery. This opaque generation is NOT a credential or authorization.
Native requires the matching receipt before enabling delivery. This also rejects
already queued/in-flight messages and old notification taps after account changes.

The new **application payload v2** is a data-only FCM envelope with these exact keys:
`version: "2"`, `eventType: "order.updated"|"chat.message"`, `eventId` (UUID),
`bindingId` (64 lowercase hex), `expiresAt` (RFC3339, at most 24 hours ahead),
and `route` (JSON string containing the existing logical-route v1 object).
Only `order.detail` and `chat.inbox` are accepted, matched to event type. They map
to the existing `/orders/<uuid>` and `/chat[?orderId=...|producerId=...]` paths.
Server authentication/authorization remains authoritative at the destination.
No title, body, message text, address, image, arbitrary URL or payment details.
Native renders generic local Russian copy with private lock-screen visibility.
**Never send an FCM `notification` block**: the SDK may display it itself in the
background, bypassing application validation. Firebase Console notification
campaigns are not a supported test or delivery path.

v1 remains silent; no automatic conversion to visible alerts. Outbox/provider
changes belong to food-home and must retain recipient/environment rechecks,
revocation, dedupe and bounded retries. High priority is only for actual visible,
user-enabled chat/order alerts, never silent synchronization. TTL must match expiry.

## Required web integration task (food-home)

1. Vendor the pinned 1.5.0 artifact with provenance and negotiate `managePush`.
2. Keep browser Web Push unchanged. In native mode use the bridge, not PushManager.
3. Show the existing buyer/seller notification setting with truthful state.
4. After a contextual user opt-in, call `requestNotificationPermission`, then issue
   a bind nonce and call `managePush`. Only `enabled` means registration succeeded.
5. On authenticated startup/resume and token-refresh recovery, query status and
   obtain a fresh nonce if `needsBinding`; avoid unbounded loops/prompting.
6. On logout/account switch/session expiry, call `clear` before changing auth state.
   When online, obtain a revoke nonce before logout, call `revoke`, then clear the
   web session. Handle offline/server failures explicitly; local clear is immediate.
   Do not automatically re-enable after a user has opted out.
7. Account deletion/revoke-all clears backend bindings as before. Test old-shell/
   new-web and new-shell/old-web fallbacks; do not expose a nonfunctional toggle.

## Activation gate

Owner-controlled Firebase Android app must match `market.foodhome.app`.
No Firebase configuration, service-account credential, or signing key is committed.
An ordinary build remains push-disabled and must compile without Firebase config.
Production activation requires the above server/web changes, provider credentials
in the existing server secret store, and real-device evidence. No Apple account or
Google Play publication is required for this Android APK transport; a device with
working Google services is required. Force-stop, offline operation, OS power limits
and Xiaomi restrictions mean delivery timing cannot be guaranteed.

## Verification required before activation

Test opt-in/denial/channel disable, foreground/background/process-dead receipt,
tap to authorized/unauthorized/deleted resources, duplicates/expiry/bad payloads,
token rotation/reinstall, account switch, logout offline, stale async completion,
old binding receipt, old notification tap, and no sensitive logging. Test real
Android 10 (owner device) and Android 13+ permissions. Unit tests and an unsigned
build alone are NOT end-to-end delivery evidence.

Sources: [FCM Android setup](https://firebase.google.com/docs/cloud-messaging/android/get-started),
[FCM receive behavior](https://firebase.google.com/docs/cloud-messaging/android/receive),
[Android notification permission](https://developer.android.com/develop/ui/views/notifications/notification-permission).
