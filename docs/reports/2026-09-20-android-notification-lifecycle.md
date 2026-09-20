# Android notification lifecycle recovery — 0.2.7 / build 9

## Confirmed gap and change

On 0.2.6 Android 14, the owner granted notification permission after seller login,
but no bind nonce reached the backend. Returning from HOME did not heal it;
instrumentation restart did. The exact first-attempt timing was not captured.
Source inspection confirmed that generic `appResumed` and `permissionChanged`
events had no Android producer: resume was only a payment-return reason.

The shell now records lifecycle ON_RESUME and OS permission-result hints. The
latest hint per kind is retained in memory (at most two), replayed into replacement
documents and deduplicated per document. The WebView dispatches only to the visible
trusted HTTPS main frame after its adapter is attached. Twelve bounded readiness
attempts are followed by retry on a new lifecycle/document/validated bridge request;
there is no continuous polling. Stale page-finish completions cannot ready another
document. Existing durable payment ACK queue is untouched.

Payloads are empty: no tokens, account IDs, nonce, permission/consent assertions or
order data. The website must read current session, preference and native permission.
No opt-out reset, forced permission, channel reset, preference PATCH, or FCM send is
introduced. Existing bridge 1.7.0 event schema already supports both names; no new
contract artifact or method was invented. iOS is unchanged.

## Verification

- 115 Android JVM tests passed (0 failed/skipped), including coalescing, replay,
  envelope fields, bounded payload and dispatcher origin/readiness guards.
- 8 Node runtime tests passed against JavaScript exported by the actual Kotlin
  emitter: foreign origin, HTTP, port, iframe, hidden document, missing adapter,
  delayed readiness, deduplication and replacement-document replay. Included in CI.
- 33 contract/branding/sound tests passed; all five existing invariant scripts pass.
- Release build and lintRelease passed: 0 errors, 30 existing warnings, 2 hints.
- Signed with the existing owner key; v2/v3 signatures and alignment verified.
- APK SHA256: `a68800b99a338c870f8f4cc1320c283bbd36400b54c4a71c4f696c1cd21b93a9`.
- Local artifact: `output/releases/0.2.7-lifecycle/foodhome-0.2.7-android.apk`.
- Installation attempt failed because the USB device was disconnected. No app was
  removed or data cleared. Do NOT claim this binary passed physical-device QA.
- CI status must be checked on the published PR. Earlier sibling CI had billing
  failures; local success does not substitute for hosted CI or provider evidence.

## Remaining release gate

Install the signed APK with `adb install -r` preserving data. Verify login and
logout/relogin for seller and buyer without restart, HOME return, and fresh first
permission with immediate AND delayed Allow, Deny and dismissal. A fresh-install
reset requires owner approval; never reset existing user opt-out for convenience.
Observe bind ownership and visible delivery independently. Test account switching
and permission changes without assuming OS grant means registration or delivery.

Use the deployed food-home login cleanup/retry fix (2245339c or descendant).
Coordinate any exact-version seller-order-sound allowlist and public APK-download
metadata in food-home separately; do not silently widen server rollout policy.
Signing-key portable backup, hosted CI and physical-device acceptance remain gates.
No merge, public release, server settings change or production push was performed.
