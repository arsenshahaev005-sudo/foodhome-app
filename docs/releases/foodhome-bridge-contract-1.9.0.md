# Buyer photo notification candidate: bridge 1.9.0

Android 0.2.12/build14 displays “Продавец прислал фото заказа — проверьте его”
for dedicated buyer.order.photo_ready version4 data. Tapping opens the existing
trusted /orders/<UUID> page. Only seller.order.new selects the custom sound;
photo, moderation, ordinary order and chat use the existing updates channel.
Lock-screen public copy remains generic. No photo or private body crosses FCM.

Source candidate:6f50f02b7a2694bab24ceb4d4caee7e457b0cba1.
Contract tree:17f6463e4919b364e51fe38f61543f9963294c26.
72 Git blobs are listed in foodhome-bridge-contract-1.9.0.sha256.
Manifest SHA256:61afea8eaebabbfaee6944c33d838e21bd99247377d96bc70f6cf618ba5f820c.
V4 schema SHA256:9ccf1e081b02d0fb178bc5ee1b6b7174080cd0bebd2660422e18a0a057f1f1f3.
V4 fixture SHA256:dbde266221e7cddaf40a791a26699ab0718c1a0c956808853314551af87d64be.

V1/v2/v3 schema bytes are unchanged. The six-field v4 contract is limited to
buyer.order.photo_ready + order.detail with a UUID id and empty query. Unknown
events, cross-version types, extra body/photo fields, invalid routes, expired
messages and stale/cleared bindings fail closed. Persistent dedupe is retained.
The backend must authorize the current buyer/source/order/photo and preferences,
gate exact 1.9.0/0.2.12/14 compatibility and preserve old-APK generic fallback.
ANDROID_BUYER_PHOTO_PUSH_ENABLED is default-off until coordinated activation.
Seller new-order and moderation allowlists must explicitly include the new tuple.

Local checks:167 Android unit tests (zero failures/errors/skips),28 bridge tests,
15 branding/lifecycle/sound scripts; debug and unsigned release assemble/lint
pass with native push enabled. Lint:zero errors,37 debug/36 release warnings.
Secure-shell, phase2/3/4/5 and direct updater invariants pass. The first Gradle
attempt selected the machine's Java8; the successful build used Android Studio's
bundled JBR25 without changing system configuration.

This pins candidate source, not an accepted merge, signed public APK or physical
FCM/display/tap proof. No phone installation, provider send, production change or
release publication was performed. iOS runtime is unchanged; its shared version
assertion is updated, but Xcode tests cannot run on this Windows host.
