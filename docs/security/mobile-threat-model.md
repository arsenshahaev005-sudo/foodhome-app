# Mobile threat model

This summary documents durable trust boundaries without operational infrastructure details.

- **First-party XSS:** trusted-origin script execution could reach bridge capabilities, so methods remain allowlisted, scoped, rate-limited, and validated per message.
- **Forged redirect:** URL normalization, exact scheme and host checks, and deny-by-default routing prevent nested or encoded destinations from inheriting trust.
- **Supply-chain:** continuous-integration actions are pinned to immutable revisions, builds are unsigned, and signing material is excluded from the repository.
- **Telemetry:** native events use a fixed name and attribute allowlist and must exclude raw URLs, credentials, personal data, payment data, and message contents.

TLS errors cancel navigation. External content receives no bridge. Native routing never replaces server-side authorization.
