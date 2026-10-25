# Android broadcast and buyer notifications (candidate)

Candidate tuple: `["1.11.0", "0.2.14", "16"]`. Base Android main:
`7b84a2da19e5e7571d5b9ee3ac6104f84e7d6db0`. This is a source/unsigned-build
candidate, NOT a public APK, physical-device acceptance or rollout approval.

## Canonical server source and reproducibility

Server main was verified against its remote at
`e245b0e859de93ecd193538fb18aef7b5c9bbdbd` (merged PRs #369/#371).
`bridge-contract/provenance/push-v6-v7.json` records all eight source Git blob IDs,
SHA256 digests and local paths. The imports are exact Git bytes, not Windows
working-tree bytes; no source file/checkout/ref in food-home was changed.

| Input | SHA256 |
|---|---|
| v6 schema | `fc61e511a3711d8426540c6918a7fa03e7c63b221f726c896ac72b11bc3fa7d4` |
| v6 fixture | `b4362e35b7429b747042b7c025460f5eeac3a834c6f13a8dc274c14b31fb5d72` |
| v7 schema | `1800e5d9c47c773f3ad57664c1e5e38f1d422fe96939517e33d1f659c4d3679e` |
| v7 fixture | `3116c982294ebe1ac33b21bcd972f1cb878196bc623f3a1c4aa28c15d83bf3ac` |

Verify offline: `node scripts/sync-server-push-contracts.mjs`. Explicit mechanical
reimport from the already pinned source object:
`node scripts/sync-server-push-contracts.mjs --import-from <food-home-path>`.
The importer validates the canonical server checksum manifests before copying.
Copies of the two server documents/checksum files live alongside this document;
they are immutable source snapshots, not claims of production activation.

No SDK upgrades: AGP9.1.1, Kotlin2.3.21, Firebase BOM34.18.0, resolved
firebase-messaging25.1.2, google-services4.5.0, org.json20240303 for JVM tests.
Firebase's pre-dispatch `handleIntent` seam is checked against that resolved SDK;
upgrades must retain envelope rejection before SDK background auto-display, not
only the additional `onMessageReceived` guard. No token/FID migration is included.

## Wire, navigation and presentation

Exactly six string values in FCM data: version, eventType, eventId, bindingId,
expiresAt, route (compact JSON). Version6 only broadcast.notification; version7
only buyer.notification. Canonical lower-case UUIDs/64-hex bindings, future expiry
within24h and a2048-byte UTF-8 keys+values budget are enforced at receipt AND tap.
Unknown fields, type coercion, mismatched pairs, URLs, title/body and provider
notification envelopes fail closed. Missing collapse_key is accepted.

| Contract route | Fixed-origin destination |
|---|---|
| v6 notifications.inbox / SELLER | `https://foodhome.market/seller?view=CHAT&section=notifications` |
| v6 notifications.inbox / CLIENT or ACCOUNT | `https://foodhome.market/chat?section=notifications` |
| v7 notifications.inbox / CLIENT only | `https://foodhome.market/chat?section=notifications` |
| v7 order.detail / params.id UUID, empty query | `https://foodhome.market/orders/{id}` |

Role and route are hints, not authorization. Current authenticated web session
and backend still check order/feed access. EventId is BroadcastDelivery.push_attempt_id
for v6 and Notification.id for v7, never an inferred campaign/domain/capture ID.
PUSH_ONLY broadcasts open the inbox without inventing a feed row.

Both use foodhome_updates, with local title «Новое уведомление от Food&Home» and
body «Откройте приложение, чтобы посмотреть подробности». Existing private/public
lock-screen variants and branded icons remain; no server copy, order details,
amounts, addresses, promo codes or staff notes are displayed. Only seller.order.new
uses the seller-specific sound. Different eventIds have distinct system tags.

## Lifecycle, storage and recovery

Receipt is synchronously persisted before display. A bounded ledger keeps up to128
LIVE event receipts, their expiries and consumed taps per binding. It never evicts
an unexpired receipt to admit another: overflow suppresses new alerts until slots
expire; the authenticated server feed remains authoritative. This preserves
at-most-once presentation rather than re-showing an evicted duplicate. Old APK
receipts migrate conservatively for24h without changing consent or owner binding.

First tap is persisted before routing; repeated/unknown taps fail closed. PendingIntent
identity includes binding generation and eventId. Logout/account-switch clear,
token rotation and rebind cancel notifications and invalidate stale taps, including
after process death. No raw token or nonce is stored in this ledger. Existing
permission denial/request-once policy is unchanged; no user settings are enabled.

onDeletedMessages persists a coalesced sync hint, not a synthetic notification.
While active, and on next resume/replacement document/cold start, it reuses the
existing empty-payload appResumed transport guarded by trusted origin/main frame/
visible document/ready bridge. The flag is cleared only after successful JS
dispatch for the matching sync revision. This acknowledges a sync hint, NOT a
server fetch, FCM receipt, payment event or completed business synchronization.
No WebView text scraping, notification polling or new bridge method is added.

## Separate server handoff — do NOT activate as part of this PR

Keep these defaults unchanged now:

```text
ANDROID_BROADCAST_PUSH_ENABLED=False
ANDROID_BROADCAST_PUSH_PROFILES=[]
ANDROID_BUYER_ACTIVITY_PUSH_ENABLED=False
ANDROID_BUYER_ACTIVITY_PUSH_PROFILES=[]
```

After accepted source, verified owner-signed APK and separately authorized physical
acceptance, the food-home owner should make a separate reviewed change:

1. Pin the accepted Android commit, bridge tree/archive SHA256, signed APK SHA256,
   certificate identity and exact tuple. Do not cite an old source commit as the
   new artifact's provenance. Existing published frontend bridge archives stay
   untouched; pin a new archive separately if updating the web dependency.
2. Preserve ordinary v1/v2 eligibility. Review the new tuple independently in ALL
   existing capability gates listed below, with regression tests; do not replace
   existing tuples or allow all future versions.
3. Add only `["1.11.0","0.2.14","16"]` to each newly approved v6/v7 profile
   array. Coordinate backend+worker settings only after explicit rollout approval,
   retaining native/visible/Firebase/consent/source/owner/binding/TTL checks.
4. Never enable marketing/category preferences for users, replay old feed/campaign
   events, refresh old TTL/binding, or manually patch installation metadata.
   Normal nonce-authorized rebind records the real upgraded APK tuple.
5. Roll back new types by disabling their flags/removing that exact profile on
   both services; preserve existing deliveries, binding generations and expiries.

Compatibility at the pinned server source (NOT a current production audit):

| Type | Native candidate | Separate server review required |
|---|---|---|
| v1 silent/legacy tap | unchanged, never promoted into visible alerts | existing behavior only |
| v2 chat.message/order.updated | retained | existing visible/binding/consent gates |
| v2 seller.order.new | retained, special seller sound only | add tuple to `_SELLER_NEW_ORDER_RELEASES`; otherwise order.updated fallback |
| v3 seller.product.moderated | retained | add tuple to `_supports_seller_moderation`; preserve ANDROID_SELLER_MODERATION_PUSH_ENABLED |
| v4 buyer.order.photo_ready | retained | add tuple to `_supports_buyer_photo`; preserve ANDROID_BUYER_PHOTO_PUSH_ENABLED; otherwise existing order.updated fallback |
| v5 open-order buyer/seller | retained | extend `_supports_open_orders` exact reviewed profiles without dropping0.2.13 |
| v6 broadcast.notification | added | ANDROID_BROADCAST_PUSH_ENABLED + ANDROID_BROADCAST_PUSH_PROFILES |
| v7 buyer.notification | added | ANDROID_BUYER_ACTIVITY_PUSH_ENABLED + ANDROID_BUYER_ACTIVITY_PUSH_PROFILES |

Messages/support remain chat.message v2; orders, delivery, photo, surcharges and
open orders retain their existing contracts. Server-selected SYSTEM/PROMO sources
use v7. Campaign audience, personal promo assignment/activity/expiry/usage limits,
marketing consent, ownership and preference checks remain server-owned.

## Safe update and acceptance gates

Do not uninstall or clear app data. Future public0.2.14 must use the same owner
signing key/certificate, package market.foodhome.app and increasing versionCode16.
Verify signed artifact hash/signature before publication. The existing in-app
updater can download a later authorized stable release and hand installation to
Android for user confirmation; it cannot silently install. Do not publish a
release or point the website/update metadata at this unsigned candidate.

Physical acceptance requires separate owner approval and a test account: foreground,
background, process-dead (not Android force-stop), lock screen/private-public copy,
both inbox roles and owned order tap, duplicate and two different eventIds,
stale/repeated tap, logout/login/account-switch/rebind, denied permission, token
rotation, onDeletedMessages sync recovery and ordinary/seller sound regression.
Test Android13+ runtime permission separately from Android10. Unit/bridge tests,
unsigned APK compilation and emulator checks are not proof of FCM/device delivery.
