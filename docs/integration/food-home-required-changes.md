# Web integration contract markers

This public document records only the ownership boundaries referenced by repository verification. It does not define production endpoints, credentials, provider configuration, or delivery status.

The current local candidate targets bridge artifact `1.8.0` (major1). The last
published artifact remains `1.7.0`; its source and checksums are in the
[published source handoff](../releases/foodhome-bridge-contract-1.7.0.md).
Publish and pin the accepted new source before production activation. Historical
provenance must not be reused for the1.8.0 candidate.
Server authorization and product behavior remain web-authoritative.

Android notification settings recovery requires a separate food-home integration:
[Notification recovery 1.6](food-home-notification-recovery-1.6.md).

Seller-only new-order sounds require a gated server event, not a renamed generic
order update: [Seller new-order sound 1.7](food-home-seller-order-sound-1.7.md).

Android visible push has a separate, currently disabled activation contract:
[Android visible push v2](android-visible-push-v2.md). The native client does not
claim compatibility with the old silent delivery payload for visible alerts.

| Marker | Public responsibility summary |
| --- | --- |
| FH-WEB-001 | Expose a web-side adapter with safe fallback when native capabilities are absent. |
| FH-WEB-002 | Preserve browser behavior when the native marker is not present. |
| FH-LINK-001 | Host and validate platform association files before enabling verified links. |
| FH-CONFIG-001 | Serve a strictly validated configuration that may only reduce compiled capabilities. |
| FH-PUSH-001 | Bind an installation to an authenticated user without exposing session credentials to native code. |
| FH-PUSH-002 | Keep notification payloads non-authoritative and free of sensitive message content. |
| FH-AUTH-001 | Keep first-party authentication inside the trusted web session. |
| FH-PAY-001 | Treat server-side reconciliation as authoritative for payment status. |
| FH-SEC-001 | Maintain web content-security and sanitization controls for the trusted origin. |
| FH-COMPAT-001 | Keep deployed web versions compatible with published bridge major versions. |
| FH-MEDIA-001 | Validate uploaded media on the server regardless of client-side selection. |
| FH-LOCATION-001 | Preserve manual address entry when location is unavailable or declined. |
| FH-PRIV-001 | Maintain public privacy disclosures for data accessed through mobile capabilities. |
| FH-OBS-001 | Accept only the documented, redacted telemetry allowlist. |
| FH-STORE-001 | Maintain store metadata, review access, and account-deletion evidence outside source control. |
| FH-SIGNAL-001 | Treat native signals and deep links as routing hints, never authorization. |
# Seller moderation candidate

The coordinated candidate artifact `1.8.0` adds a separate version-3 push schema
for `seller.product.moderated`, supported by Android0.2.11/build13. Existing v2
chat/order payloads and the seller new-order sound keep their prior contracts.
The backend service event stays default-off until this native source and APK are
published and the deployment/device gates are completed. No iOS push activation.
