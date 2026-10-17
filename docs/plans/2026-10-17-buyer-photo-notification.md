# Buyer notification for a seller's finished-order photo

The owner confirmed a distinct buyer alert asking them to review the seller's
photo before delivery. The web notification already has that text; Android maps
it to generic order.updated copy. Continue using the trusted order detail page.

Add a dedicated six-field v4 buyer.order.photo_ready payload, accepted only by
bridge 1.9.0 / Android 0.2.12 / build14. V1/v2/v3 schemas remain unchanged. Use the
ordinary updates channel and local text, without a photo URL, buyer identity,
private body, arbitrary destination or seller order sound in the provider data.
Keep generation/permission/expiry/dedupe/tap guards. Old APKs receive the existing
generic order.updated fallback; a server flag controls the new typed delivery.

Implement native validation/copy and source/contract tests, then coordinate the
backend's typed source markers, buyer ownership/preferences checks, exact release
allowlist and default-off gate. Preserve seller order/moderation compatibility on
the new APK. Build and lint without installing anything on the owner's phone.
Publish source PRs and record release/device boundaries; do not claim physical
delivery from local tests.
