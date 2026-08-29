# Web integration contract markers

This public document records only the ownership boundaries referenced by repository verification. It does not define production endpoints, credentials, provider configuration, or delivery status.

The native shells consume immutable bridge artifact `1.4.0`. Server authorization and product behavior remain web-authoritative.

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
