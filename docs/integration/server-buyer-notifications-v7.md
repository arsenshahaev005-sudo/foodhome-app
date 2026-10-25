# Buyer notifications and Android visible push v7

Approved on 2026-10-25: operational events1-6 plus personal promo codes. Depends on administrative broadcast PR#369. No production replay, provider sends, deployment or APK changes are included.

## Event inventory

| Event | Source and behavior | Destination |
|---|---|---|
| Seller message / support reply | Existing immutable message UUID and chat.message v2; no duplicate feed item. Buyer browser preview becomes generic. | Existing chat inbox/order/producer context |
| Support closed/reopened | Public SupportInboxThread transition, buyer role only; assignment and staff notes excluded. | Chat, or owned order when linked |
| Chat/product complaint decision | ChatComplaint resolved transition to reporter; no snapshots, sanction details or private notes copied. | Owned order or notifications inbox |
| Order complaint/dispute | Public api.Dispute and marketplace Claim/Dispute transitions, including processing/failure and final decision. | Owned legacy order; inbox for standalone marketplace events |
| Review dispute | Final RatingDispute retain/remove/reject decision; legacy order ownership remains available after review deletion. | Owned order |
| Refund request | Immutable RefundRequestEvent with changed public status; final REFUNDED journal entry alone uses provider-confirmed wording. Approval/processing is not card-credit confirmation. Deprecated direct refund-create endpoints stay disabled. | Owned order |
| Seller review correction offer | Durable outbox replaces process-local callback. Full current offer validated against correction_requested_at, acceptance and ownership. | Owned order |
| Order/delivery/photo/address surcharge | Existing ORDER paths and order.updated/photo v4 contracts retained. Buyer browser copy is generic. Existing address/detail-change and open-order flows retained. | Owned order / existing open-order route |
| Seller surcharge required/paid | Durable capture after creation and confirmed payment; stable surcharge+phase source keys for buyer and seller preserve both role feeds and avoid retries duplicating push. | Owned order |
| Administrative service announcement | Separate broadcast.notification v6, including audience/generation checks and delivery diagnostics from PR#369. | Role-specific notifications inbox |
| Personal platform/support promo | Explicit notify creates a notification event with personalPromoId; current assignment, activity, expiry and usage limits checked at send. Intentional notify-again remains a new event. | Notifications inbox with code available in feed |
| Personal seller promo | marketplace.PromoCode creation with buyerId captures durable event; normalized existing Profile identity, expiry and consent rechecked. | Notifications inbox |

Operational feed items are retained when push is disabled. Marketing push requires push_notifications, push_promotions and currently active marketing consent. Consent is never inferred or auto-created. Existing personal-code access/feed behavior is retained. Changing a setting does not replay historic notifications. Expired, superseded, deleted, reassigned or now unauthorized sources are suppressed before transport; an already in-flight provider request cannot be recalled.

## Delivery and idempotency

buyer.activity.requested contains only source kind/ID, public expected state and immutable captureId. Domain save hooks lock the persisted old status and capture its outbox entry in the same transaction. CAS-based refund failure paths use their immutable journal. Explicit review/surcharge captures live inside existing domain transactions. No provider I/O runs in those hooks.

Projection uses unique Notification(user,recipient_role,source_key) and the immutable capture UUID. Notification persistence failure raises to the existing worker retry/dead-letter path. A partial aggregate/created_at index supports latest-source checks, including close/open/close ABA transitions; migration0328 builds it CONCURRENTLY on PostgreSQL. Future bulk status updates must explicitly capture activity or retain the journal path; bulk ORM writes bypass model.save hooks.

Before browser/Android delivery, recheck current source, owner, role, order access, preferences, binding generation, TTL and marketing consent. Feed text can contain an authorized promo code or surcharge detail; browser and Android lock screens get generic local/service text. Browser buyerNotificationId stays inside the internal outbox and is not a new public push field.

New Android v7 adds only buyer.notification. Exact public keys:

```json
{"version":7,"eventType":"buyer.notification","eventId":"notification-uuid","bindingId":"64-lowercase-hex","expiresAt":"RFC3339","route":{"protocol":"foodhome.logical-route","version":1,"name":"notifications.inbox","params":{},"query":{"role":"CLIENT"}}}
```

The displayed identifier placeholders above are explanatory. Use canonical fixtures for valid UUID/binding/expiry values. The only other v7 route is order.detail with params.id=owned legacy order UUID and query={}. notifications.inbox requires exactly role=CLIENT. Reject added fields, title/body, URLs, mismatched event/version, invalid identifiers, expired/>24h lifetime and payloads above2048 UTF-8 key+value bytes. eventId is Notification.id, not a domain object, campaign or outbox capture UUID.

v6/v7 FCM data messages omit collapse_key so unrelated pending inbox events do not replace one another. See [official FCM collapse semantics](https://firebase.google.com/docs/cloud-messaging/customize-messages/collapsible-message-types). This is not a device-delivery guarantee: provider acceptance, OS permission, network, TTL, client deduplication and actual device display are separate evidence.

## Release gates

ANDROID_BUYER_ACTIVITY_PUSH_ENABLED=False and ANDROID_BUYER_ACTIVITY_PUSH_PROFILES=[] by default. Profiles are exact JSON tuples [bridgeVersion,appVersion,buildVersion], validated like the broadcast list. No old APK or synthetic test tuple is enabled. Older clients preserve v1-v6 eligibility and never receive unsupported v7 or a silent-v1 fallback for it.

Implement and review the matching APK first, record immutable source/artifact provenance, run schema/unit/build checks and physical-device foreground/background/process-dead/lock-screen/tap tests. Review the candidate tuple for both new flags and existing per-event capability allowlists: v3 moderation/v4 photo/v5 open-orders have independent reviewed profiles. Do not infer old-event support from the new v7 flag. Activation is a separate coordinated change with rollback by disabling flags. No automatic replay/backfill of old campaigns or feed items.

For an approved candidate, verify browser/feed delivery first, then a controlled test-account push with generic text, ownership/consent withdrawal, account switch/logout, stale tap, provider retry and client deduplication. Read-only diagnostics cannot prove physical-device delivery.
