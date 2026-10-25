# Administrative broadcast native push v6

Status: backend support prepared; Android activation is OFF. This document is not evidence of a published APK, configured production delivery or device display.

## Wire contract

Canonical source: `backend/api/contracts/native_push/v6/payload.schema.json`; fixture: `v6/fixtures/valid/push-broadcast.json`. FCM is data-only with exactly six string fields: version=6, eventType=broadcast.notification, eventId, bindingId, expiresAt and route (compact JSON string). Native measures UTF-8 keys+values, at most2048 bytes. No title/body/notification envelope or arbitrary URL. TTL/binding/deduplication and authenticated account isolation follow existing visible-push rules.

`eventId` is the BroadcastDelivery.push_attempt_id UUID, freshly rotated for every send. It is not a campaign UUID or a feed Notification UUID. Route is exactly protocol=foodhome.logical-route, version=1, name=notifications.inbox, params={}, query={role:ACCOUNT|CLIENT|SELLER}. The role is a navigation hint, never authentication proof. A SELLER route opens /seller?view=CHAT&section=notifications; CLIENT and ACCOUNT open /chat?section=notifications. food-home supports these links and preserves more specific order/thread links when both are supplied.

The APK should display generic localized copy such as "Новое уведомление от Food&Home" and open the authenticated notifications section. Full campaign copy stays on the server. PUSH_ONLY intentionally has no feed row; it can open the same section without inventing a feed entry.

## Eligibility and activation

Default settings: ANDROID_BROADCAST_PUSH_ENABLED=False; ANDROID_BROADCAST_PUSH_PROFILES=[]. Profiles are a JSON array of exact [bridge_version,app_version,build_version] string triples. Existing0.2.12/14/bridge1.9.0 and0.2.13/15/bridge1.10.0 do not implement v6 and MUST NOT be added. Choose the next APK/bridge versions from current foodhome-app main, publish reviewed provenance and test first; no speculative version is approved here.

Enqueue AND delivery recheck enabled runtime, exact allowlist tuple, active authorized Android/FCM installation, current generation, user/profile preference, delivery source, campaign status/deletion/audience and current marketing consent/preference for PROMO. No fallback to chat/order, silent v1 or iOS. Removing the flag/profile or revoking consent blocks already queued delivery. Production activation requires separate authorization, reviewed source/APK/profile and physical-device acceptance.

## Status and migration

Migration0327 adds nullable identity fields, populates a separate UUID per existing delivery, then enforces uniqueness; the other fields are additive. OutboxEvent records an indexed nullable BroadcastDelivery FK, immutable attempt UUID and safe delivery-result counts. Old events retain their generation: stale queued generations are rejected before provider delivery and cannot overwrite the newest status. A request already in flight cannot be recalled. Workers and inline web dispatch project results. QUEUED/RETRYING are not DISPATCHED; DISPATCHED means at least one provider accepted, never proven device display. Partial failures remain visible. Full feed delivery stays SENT even when push fails.

API adds push_reason, push_details and push_has_error to deliveries and push_queued/push_retrying/push_unverified to campaigns. Historical unlinked DISPATCHED rows are exposed as UNVERIFIED and excluded from provider-accepted totals; no history is replayed or certified by inference. Admin GETs do not write or dispatch. Test-send reports queued/skip diagnostics; native test-send does not manufacture a campaign recipient outside its audience.

Web preview uses compact UTF-8 JSON within3993 plaintext bytes (RFC8291). Full feed and outbox text stay intact. Known permanent400/401/403/413 do not enter futile retries; expired404/410 subscriptions are removed as before.503/network failures retry within original TTL; partial success is not retried wholesale.

No automatic resend, historical backfill, production flag changes or deployment in this PR. Keep schema on application rollback; reversing migration drops only the new diagnostics. Review FK validation/index and UUID population on the actual production table sizes before deploy.
