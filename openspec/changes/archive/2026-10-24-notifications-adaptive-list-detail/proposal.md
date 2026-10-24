## Why

Currently, `NotificationsTab` is the only primary root destination in Nubecita that does not participate in `MainShell`'s `ListDetailSceneStrategy`. On foldable devices (unfolded) and tablets (Medium/Expanded window width classes), navigating to the Notifications tab shows a single stretched list across the entire display or leaves wide empty margins without utilizing the screen estate. When users tap a notification, the app navigates full-screen rather than displaying the list and detail side by side.

Adopting `ListDetailSceneStrategy` for `NotificationsTab` aligns the notifications experience with `Feed`, `Search`, and `Profile`, providing a consistent two-pane layout on large screens while preserving standard full-screen navigation on compact phones.

## What Changes

- Register `entry<NotificationsTab>` with `metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = ...)` in `:feature:notifications:impl`.
- Add `NotificationsDetailPlaceholder` displaying an empty state prompt (`nubecita_detail_pane_select_notification`) when no notification target is selected in two-pane mode.
- Use `mainShellNavState.replaceTop(...)` for detail navigation from notification row taps (posts and profiles) and aggregated actor sheet taps, preventing deep detail stacking and matching the behavior of `Chats` and `Search`.
- Implement active row selection highlighting (`secondaryContainer` tint) for the notification item corresponding to the active detail pane.
- Scope backstack selection inspection to the `NotificationsTab` backstack segment to isolate selection state from feed or other top-level tab routes.
- Preserve compact single-pane phone behavior where detail entries open full-screen and Back returns cleanly to the notifications list.

## Non-goals

- Inline actions or dismissing notifications directly from the detail pane.
- Custom tablet-specific notification filtering or multi-column notification lists.
- Persisting selected notification state across app reboots or tab switches beyond standard backstack preservation.

## Capabilities

### New Capabilities
<!-- None -->

### Modified Capabilities
- `feature-notifications`: Specify `ListDetailSceneStrategy.listPane` registration, empty detail placeholder, `replaceTop` detail navigation, and selected-row visual treatment.
- `app-navigation-shell`: Note `NotificationsTab` as a participating list pane alongside `Feed`, `Search`, and `Profile`.

## Impact

- **UI / Features**: `:feature:notifications:impl` now depends on `androidx.compose.material3.adaptive.navigation3` and injects `ListDetailSceneStrategy.listPane`.
- **Resources**: New string resource `nubecita_detail_pane_select_notification` across `values`, `values-b+es+419`, and `values-pt-rBR`.
- **Navigation**: Detail navigation in notifications adopts `replaceTop` semantics instead of unrestricted push stacking.
- **Dependencies**: No breaking API or module changes; `:feature:notifications:api` remains untouched.
