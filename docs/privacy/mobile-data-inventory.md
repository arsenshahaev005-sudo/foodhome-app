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

Android Firebase Messaging auto-registration and Analytics collection default off.
Only an owner-configured build and explicit authenticated opt-in request a provider
token. No Analytics SDK is added. Provider-managed token storage remains native.
Visible push v2 contains only routing metadata; notification text is generic and
rendered locally. Activation is blocked on the cross-repository contract and real
device verification in `docs/integration/android-visible-push-v2.md`.
