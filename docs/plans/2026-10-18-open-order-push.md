# Open-order notifications

Candidate: bridge 1.10.0 / Android 0.2.13 / build 15.

Version 5 adds only seller.open_order.available, seller.open_order.updated and
buyer.open_order.updated. Each uses the existing six-field, binding-bound,
expiring data-only payload and a request UUID. V1-v4 stay unchanged. Requests
use the ordinary updates channel. A confirmed paid request becomes a real
Order and uses the existing seller.order.new special sound.

The server checks the exact release tuple, current ownership, delivery eligibility,
account binding and notification preferences at enqueue and delivery. Older APKs
receive no unknown v5 events. Their existing paid-order notification path remains.

Web/staging tests and Android contract/unit tests do not prove physical delivery,
audible sound, signing, store publication or installation. Production push remains
restricted to the production origin; staging debug builds do not enable FCM.

Verification 2026-10-18: 168 Kotlin unit tests, 0 failures/errors; lintDebug, lintRelease and assembleDebug passed. 29 bridge Node contract tests and phase 2–5 verification scripts passed. The v5 schema is byte-independent JSON-identical to food-home backend v5.

Debug APK SHA256: 30567496b9167d44e55f03975d33b3c1eb7086272a59056b3135739edc3d2a8b. This is a local test artifact, not a signed release or device-delivery proof.
