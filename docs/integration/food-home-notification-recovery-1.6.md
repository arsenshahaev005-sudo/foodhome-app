# Food-home follow-up: actionable Android notification recovery

## Confirmed problem

On HONOR 70 / Android 14, APK 0.2.3 returned `denied` because the channel was
blocked even though POST_NOTIFICATIONS had never been requested. The production
seller settings switch consequently performed only successful GET requests and
remained off. Server `push_enabled=false` also intentionally suppresses automatic
enrollment. The title is not a link and there is no settings-navigation action.

Android 0.2.4 checks the unrequested runtime permission before channel state.
It asks once, preserves denial/dismissal and never resets a channel. A grant is
still insufficient for delivery if the channel remains blocked.

## Additive native contract 1.6.0 (bridge major remains 1)

`openNotificationSettings` is Android-only and advertised by Android binaries
whether or not FCM is compiled in. Request payload is strictly `{}`. No URL,
package, token or channel may be supplied by JavaScript. Native requires a
RESUMED activity and a web touch in the last two seconds, and limits opening
to once per five seconds. It opens only the own-app channel settings with
own-app notification/application settings fallbacks pinned to a system handler.
Success: `{ "capability": "openNotificationSettings", "status": "presented" }`.
Success does not mean permission was granted or binding completed.
Errors: CANCELLED (no foreground gesture), RATE_LIMITED, INVALID_PAYLOAD,
LAUNCH_FAILED, plus existing bridge validation/capability errors.

`getNotificationStatus` and `requestNotificationPermission` payloads/results
are unchanged. iOS does not advertise the new method. Existing 1.5 clients
filter unknown capabilities; they retain the corrected first-permission flow.

## Implementation task for food-home (separate repository)

1. Inspect the current branch and its AGENTS.md; preserve unrelated changes.
2. Import the actual reviewed/published 1.6.0 artifact with verified source commit
   and hashes. Until publication, label it a local candidate; never invent a commit.
   Extend the existing adapter method allowlist/types. Keep strict origin/frame checks.
3. In existing NativePushSettings for both buyer and seller, render an explicit
   "Открыть настройки Android" button when permissionDenied and the negotiated
   capability is present. Call the adapter synchronously from its click handler,
   without an API request before it (native gesture expires in two seconds).
4. Refresh current permission and session on return using the existing appResumed
   lifecycle. Never set consent=true merely because settings opened or OS granted.
   If consent=false, retain an explicit account enable action; if true, reconcile
   the binding with existing generation/cancellation guards.
5. For old APKs/iOS/PWA keep appropriate existing behavior and clear manual
   instructions. Show useful errors when settings cannot launch. Avoid a no-op
   switch: explain the required permission and provide the recovery action.
6. Add focused tests for capability presence/absence, no call on render/login,
   click dispatch, failure, return refresh, consent=false and account change.
7. Publish food-home changes through its own PR/CI/deploy workflow; verify on the
   matching installed APK. Do not change global push flags as part of this task.

## Remaining acceptance

Verify first OS dialog on Android 14; denial/dismissal does not loop; blocked
channel remains off until user changes it; recovery opens only Food&Home;
return refresh works. Actual FCM delivery is a separate provider acceptance test.
