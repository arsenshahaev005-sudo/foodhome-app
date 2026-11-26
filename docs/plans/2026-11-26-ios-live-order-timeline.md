# iOS Live Order Timeline Implementation Plan

**Goal.** Let the buyer read the current order status and the remaining stages at a glance.

**Architecture.** Build the timeline from the authorised ActivityKit snapshot. The server adds the existing pickup flag to the initial response and subsequent updates. The iOS decoder accepts earlier snapshots with only a status. The lock screen and expanded Dynamic Island show the timeline in the Food&Home widget.

**Tech stack.** SwiftUI, WidgetKit, ActivityKit, Django and APNs.

## Implementation

1. Verify that registration and ActivityKit pushes expose the same pickup flag for an order before the seller accepts it. Verify the existing pickup transition after cooking.
2. Map the displayed stages and test delivery, pickup, old payloads, completion and interruptions. Keep the detailed status visible and the order deep link on the widget.
3. Run the iOS and backend checks. Apply the additive response change on staging using the existing APNs configuration. Upload the reviewed iOS commit to the internal Food&Home Staging group.
