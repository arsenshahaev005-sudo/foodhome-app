# Mobile data inventory

| Data class | Purpose | Storage rule |
| --- | --- | --- |
| Web session cookies | Maintain first-party login inside the persistent WebView store. | Remain in the platform WebView store; never copied through the bridge. |
| Opaque payment recovery context | Resume a pending external handoff after process restart. | Non-secret, minimal, local, short-lived, and excluded from device backup. |
| Telemetry allowlist | Diagnose shell reliability using predefined event names and coarse attributes. | No raw URL, credentials, message content, address, payment data, or unrestricted custom fields. |
| Selected media | Let the user choose or capture content for a web upload. | Temporary platform files are scoped to the request and cleaned up. |
| One-shot location | Fill an address after explicit confirmation. | Returned only for the active request; continuous tracking is not used. |
| Notification token | Register an application installation for delivery. | Never logged or passed to web JavaScript; server binding requires authenticated context. |
