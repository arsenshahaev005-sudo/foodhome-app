# Mobile data inventory

| Data class | Purpose | Storage rule |
| --- | --- | --- |
| Web session cookies | Maintain first-party login inside the persistent WebView store. | Remain in the platform WebView store; never copied through the bridge. |
| Opaque payment recovery context | Resume a pending external handoff after process restart. | Non-secret, minimal, local, short-lived, and excluded from device backup. |
| Telemetry allowlist | Diagnose shell reliability using predefined event names and coarse attributes. | No raw URL, credentials, message content, address, payment data, or unrestricted custom fields. |
| Selected media | Let the user choose or capture content for a web upload. | Temporary platform files are scoped to the request and cleaned up. |
| One-shot location | Fill an address after explicit confirmation. | Returned only for the active request; continuous tracking is not used. |
| Notification token | Register an application installation for delivery. | Never logged or passed to web JavaScript; server binding requires authenticated context. |
| Push binding generation and local opt-in | Reject stale delivery after logout/account switch and respect opt-out. | Opaque nonce digest, random installation UUID, token digest and bounded dedupe IDs in private non-backed-up app preferences; no raw nonce/token. |
| Direct APK release metadata and download | Offer and install a user-approved Android update outside Google Play. | A separate cookie-free client contacts the public GitHub API and release CDN. No session, account ID, device identifier or token is sent; GitHub/CDN still receive ordinary network metadata such as IP address. Private preferences hold last-check/deferral timestamps and a bounded pending release descriptor/stage/cache basename, accepted for up to 24 hours and cleared on dismissal; no CDN credentials or absolute paths. APKs are temporary private cache files. |

Direct APK updates do not add analytics. Signed CDN URLs and release response
bodies must not be logged. Store builds disable this updater and remove its
install permission; see `docs/runbooks/android-in-app-updates.md`.

Android Firebase Messaging auto-registration and Analytics collection default off.
Only an owner-configured build and explicit authenticated opt-in request a provider
token. No Analytics SDK is added. Provider-managed token storage remains native.
Visible push v2 contains only routing metadata; notification text is generic and
rendered locally. Activation is blocked on the cross-repository contract and real
device verification in `docs/integration/android-visible-push-v2.md`.
