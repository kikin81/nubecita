## Context

Nubecita is an adaptive native Bluesky client. `MainShell` uses Jetpack Compose Material 3 Adaptive Navigation (`material3.adaptive.navigation3`) with `ListDetailSceneStrategy`. Root destinations like `Feed`, `Search`, and `Profile` register `ListDetailSceneStrategy.listPane` with a dedicated `detailPlaceholder`.

Until now, `NotificationsTab` was the only root destination lacking `ListDetailSceneStrategy` integration. On foldable/tablet screens, this caused notifications to stretch across the full width or leave unutilized space, with row taps navigating full-screen.

A SPIKE was implemented and validated across `emulator-5554` (phone, Compact width) and `emulator-5556` (Pixel Fold, Expanded width 2076x2152), verifying single-pane fallback, two-pane layout, detail swapping, back navigation, and row selection highlight.

## Goals / Non-Goals

**Goals:**
- Make `NotificationsTab` an adaptive list pane in `MainShell`'s `ListDetailSceneStrategy`.
- Provide an empty detail placeholder (`NotificationsDetailPlaceholder`) shown on Medium/Expanded widths before any notification is selected.
- Use `mainShellNavState.replaceTop(...)` for detail navigation to support swapping detail panes without creating deep backstacks.
- Visually indicate the currently selected notification row in the list pane with `secondaryContainer` tint.
- Maintain strict tab backstack isolation so details opened in other tabs do not trigger highlights in Notifications.
- Maintain existing phone (Compact) single-pane behavior and backstack navigation.

**Non-Goals:**
- Promoting the empty placeholder Composable to `:designsystem` (feature-specific placeholders stay in their respective feature modules).
- Supporting multi-selection or batch actions in the notifications list.
- Modifying how notifications are fetched, mapped, or filtered at the ViewModel / repository layer.

## Decisions

### 1. `ListDetailSceneStrategy.listPane` on `NotificationsTab` Entry
- **Choice**: Pass `metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { NotificationsDetailPlaceholder() })` in `NotificationsNavigationModule`.
- **Alternatives Considered**:
  - *No placeholder (detailPane fallback)*: Leaves the right pane blank or causes layout instability until an item is tapped.
  - *Generic shared placeholder in `:designsystem`*: Rejected per project convention (`feature-feed` spec L508); feature modules keep their own internal placeholders until a consolidated design system component is standardized.

### 2. Detail Navigation Semantics (`replaceTop` vs `add`)
- **Choice**: Use `mainShellNavState.replaceTop(effect.target)` for `NavigateTo` effects and `ActorListSheet` profile clicks.
- **Rationale**: On Medium/Expanded screens, clicking Notification 1 shows Post 1 in the detail pane. Clicking Notification 2 should replace Post 1 with Post 2 in place. Pressing Back once clears the detail and restores the placeholder. On Compact screens, `replaceTop` automatically falls back to standard push because `backStack.size < 3`.
- **Alternatives Considered**:
  - *`mainShellNavState.add(...)`*: Stacks every tapped notification detail onto the backstack, requiring users to press Back multiple times through all previously inspected notifications before returning to the list.

### 3. Scoped Backstack Selection Inspection
- **Choice**: Implement `findSelectedPostUri(backStack)` and `findSelectedProfileHandle(backStack)` in `SelectedNotificationTarget.kt` that strictly inspect `backStack.drop(backStack.lastIndexOf(NotificationsTab))`.
- **Rationale**: `MainShellNavState.backStack` contains `[Feed, NotificationsTab, ...]`. If a user previously navigated into a post on the Feed tab, inspecting the entire backstack without scoping could falsely highlight an unrelated notification row.
- **Alternatives Considered**:
  - *Storing `selectedItem` in `NotificationsViewModel` / `NotificationsState`*: Violates unidirectional state where the backstack in `MainShellNavState` is the single source of truth for navigation state; would also desync on system Back or external deep-links.

### 4. Active Row Highlight Token
- **Choice**: When `isSelected == true`, `NotificationRow` sets `backgroundColor = MaterialTheme.colorScheme.secondaryContainer`.
- **Rationale**: Matches the standard M3 Expressive list-detail selection token used in `Search` and `Chats`. Unselected unread rows remain `surfaceContainerLow`, and unselected read rows remain `surface`.

## Risks / Trade-offs

- **[Risk]**: Actor sheet click navigating to Profile might leave the bottom sheet open over the two-pane view.
  - **Mitigation**: Dismiss the sheet (`showActorList = false`) immediately before calling `mainShellNavState.replaceTop(Profile(it.did))`.
- **[Risk]**: Compact screen regression with `replaceTop`.
  - **Mitigation**: Verified on `emulator-5554` (Compact phone): `replaceTop` cleanly pushes full-screen when `stack.size < 3`, and top app bar / system Back navigates back to the notifications list.
- **[Risk]**: Naming collisions between helper functions and composable parameters.
  - **Mitigation**: Helper functions named `findSelectedPostUri` and `findSelectedProfileHandle` (distinct from `selectedPostUri` parameters).
