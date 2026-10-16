# FoodHome bridge1.8.0 seller moderation candidate

Status: local implementation candidate, not a published or merged release.
Canonical source is bridge-contract/ in this foodhome-app branch. Resolve the
reviewed commit/tree and verify all blob checksums before production integration.
The prior1.7.0 handoff and frontend artifact remain unchanged.

Initial candidate source commit:2ac39128d1105ff88d3c418ff3ffc41bc55c7286.
Contract tree:d2f6949d1e28348437a213ae7e26e50026b7fa76.
The70 exact Git blobs are listed in foodhome-bridge-contract-1.8.0.sha256;
manifest SHA256:9a74720828a654a7c27c7d73e7611c65ff55975207898df9b54c573a1a10f72a.
These identify a candidate, not an accepted merge or public APK release.

Android0.2.11/build13 adds the dedicated seller.product.moderated application
payload version3. Existing chat/order application version2 is unchanged. The
bridge major and method set remain1; the additive contract artifact is1.8.0.
This does not enable iOS push or change its capability set.

V3 accepts exactly version,eventType,eventId,bindingId,expiresAt,route. Its only
route is seller.product with a UUID id and an empty query. No private copy or
arbitrary URL is accepted. Local copy is neutral and uses foodhome_updates;
seller.order.new alone continues to use foodhome_seller_new_orders.

The tap destination is the trusted existing seller-products view with productId.
Generation, expiry, persistent dedupe, scope clear and authorization guards remain
in force. Web/API ownership checks still authorize the actual product data.

## Protocol checksums

SHA256 over canonical UTF-8 LF bytes:

- schemas/push-v3.schema.json: ace761301a258394dd48fb2b08c353614f8ac95b72bfbbbbad9a61f9e28e24d5
- fixtures/valid/push-moderation.json: b9bf5c76efa7beed50195fbec47b3896995108554dfff08a2a62c75d2b684ec2

## Local verification

- 27 bridge tests passed.
- 166 Android debug unit tests passed, zero failures/errors/skips.
- Debug unit/lint/assemble passed with push compiled in using the existing ignored
 client Firebase config. Debug lint has37 warnings and2 hints, zero errors.
- Release lint/unsigned assemble passed, with36 warnings and2 hints, zero errors.
- Secure-shell, phase2/3/4/5 and direct-distribution invariant scripts passed.

Source/build checks do not prove real FCM delivery, display, audible sound,
seller-product tap behavior, signed public APK provenance or Xcode/iOS tests.
No phone installation, provider send, backend rollout or service activation was
performed. Backend ANDROID_SELLER_MODERATION_PUSH_ENABLED must stay False until
the signed public APK and coordinated device/deployment gates are complete.
