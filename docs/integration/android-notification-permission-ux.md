# Android notification permission UX — 0.2.3 / build 5

## Ownership and scope

The owner approved replacing redundant notification enable cards/pre-prompts with
automatic transactional chat/order enrollment after authenticated login, subject
to OS permission and existing user opt-outs. Android implementation is here;
authenticated session detection, user preferences, UI and binding orchestration
belong to food-home. No marketing enrollment is implied.

Bridge contract **1.5.0 / major 1 is unchanged**. No new method, schema, server
endpoint, token exposure or capability is added. This document supersedes the
extra contextual native confirmation step in the original visible-push UX.
Never copy unpublished native changes into a claimed published source commit.

## Native behavior

- `getNotificationStatus` remains read-only and never prompts.
- `requestNotificationPermission` no longer shows a custom native confirmation.
  Optional `purpose` is still accepted by the existing validator; Android owns the
  system dialog wording and the APK does not render purpose as another prompt.
- Android 13+: first not-determined request while RESUMED launches the standard
  `POST_NOTIFICATIONS` dialog. A durable attempted marker is written first.
- Already authorized: return authorized without UI or another marker write.
- Android 12 and below: return actual app/channel permission without a dialog.
  Globally blocked notifications are denied, not notDetermined.
- Denied, disabled channel or prior interrupted/dismissed attempt: no automatic
  repeat. An ungranted attempted request maps to effective `denied`; that status
  does not claim the user pressed the Deny button (swipe-away can cause it too).
- Background request is CANCELLED without consuming the first attempt; concurrent
  requests cannot replace the original callback. Renderer/disposal and push
  clear/revoke cancel pending callbacks. A late OS result cannot bind an account
  or complete a newer request. A storage failure returns unavailable without UI.
- Returning from manually changed system settings uses fresh authorization state.
  The APK does not open settings automatically, raise an existing channel's
  importance, modify Xiaomi switches or bypass any denial.
- Permission is not registration: only a separate valid generation-aware bind can
  enable push. Firebase auto-init remains disabled; no token request or binding
  occurs inside the permission flow.

The marker is installation-wide, survives logout and is not reset by an account
switch. A declined or dismissed prompt is recovered through deliberate user action
in Android settings, not repeated login prompts. Existing in-notification Settings
action remains available; this change adds no bridge settings-navigation method.

## Web rollout contract

For the new automatic UX, require a validated Android handshake, effective
`managePush`, `getNotificationStatus` and `requestNotificationPermission`
capabilities, and APK **0.2.3 or newer / build 5 or newer**. Read version metadata
from the existing validated handshake, not UA/query parameters or a made-up
capability. Version only gates this behavior, never authorizes a user or bypasses
capability/server checks. Unknown/older shells retain an unobtrusive manual fallback
in settings; never automatically invoke their old custom pre-prompt.

food-home must establish the authenticated session and server preference first,
clear the old native owner before switching, and preserve `push_enabled=false`.
For an eligible session, query OS permission, request only notDetermined once,
then obtain a fresh bind nonce only if permission is authorized. Do not use the
existing `enable()` unchanged: it writes consent=true and could override an opt-out.
Validate session revision after every async step and bound retries. Settings and
status updates must never themselves re-open the permission dialog.

Keep a small accessible opt-out/re-enable setting and recovery explanation, not a
promotional card. Browser/PWA Web Push and iOS behavior remain unchanged. Do not
hide their settings using a broad mobile/Android UA test.

Native source publication, the matching installed APK, food-home implementation
and an agreed delivery test remain distinct acceptance steps. This change does
not activate server flags or establish an end-to-end delivery result.

Implementation prompt: `food-home-automatic-native-push-prompt.md`.
Evidence: `../reports/2026-09-11-android-notification-permission-ux.md`.

Reference: [Android notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission).
