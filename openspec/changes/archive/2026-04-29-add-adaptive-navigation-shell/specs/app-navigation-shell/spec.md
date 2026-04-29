## ADDED Requirements

### Requirement: `Main` NavEntry hosts a `MainShell` composable instead of `MainScreen`

`:app`'s outer `NavDisplay` (declared in `app/Navigation.kt`) SHALL render `MainShell()` for the `Main : NavKey` entry. The previous placeholder `MainScreen` Composable SHALL be removed. `MainShell` SHALL be defined under `:app/.../shell/`.

#### Scenario: Outer NavDisplay renders MainShell on Main

- **WHEN** the outer `Navigator.backStack` has `Main` at the top
- **THEN** the rendered Composable SHALL be `MainShell()` and SHALL NOT reference any symbol from the previous `MainScreen` content (greetings list, refresh button)

### Requirement: `MainShell` hosts `NavigationSuiteScaffold` over an inner `NavDisplay`

`MainShell` SHALL host `androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold`. The scaffold's `navigationSuiteItems` lambda SHALL declare exactly four items in this order: Feed, Search, Chats, You. The scaffold's content lambda SHALL host an inner `NavDisplay` whose `backStack` SHALL be sourced from a `MainShellNavState` and whose `entryProvider` SHALL be populated from the `@MainShell`-qualified `Set<EntryProviderInstaller>` collected via `NavigationEntryPoint`.

#### Scenario: NavigationSuiteScaffold present in MainShell

- **WHEN** `MainShell()` is composed
- **THEN** the rendered tree SHALL contain a single `NavigationSuiteScaffold` whose `navigationSuiteItems` produces four items in the order [Feed, Search, Chats, You], and whose content hosts a `NavDisplay` reading `mainShellNavState.backStack`

### Requirement: Top-level destination icons follow Material Symbols

The four top-level destinations SHALL use Material Symbols icons:

- Feed → `Home`
- Search → `Search`
- Chats → `ChatBubbleOutline` (unselected) / `ChatBubble` (selected)
- You → `Person`

The selected state of each item SHALL be derived from `mainShellNavState.topLevelKey`.

#### Scenario: Selected tab icon reflects active top-level key

- **WHEN** `mainShellNavState.topLevelKey == Search`
- **THEN** the Search nav item SHALL be in selected state and the other three items SHALL be unselected

### Requirement: Adaptive chrome swaps `NavigationBar` ↔ `NavigationRail` based on `WindowSizeClass`

`NavigationSuiteScaffold` SHALL be configured to render `NavigationBar` at compact width and `NavigationRail` at medium and expanded widths. Drawer mode SHALL NOT be used for the four-destination shell.

#### Scenario: Compact width renders NavigationBar

- **WHEN** the device window's `windowWidthSizeClass` is `Compact`
- **THEN** `NavigationSuiteScaffold` SHALL render a `NavigationBar` at the bottom of the screen

#### Scenario: Medium and expanded widths render NavigationRail

- **WHEN** the device window's `windowWidthSizeClass` is `Medium` or `Expanded`
- **THEN** `NavigationSuiteScaffold` SHALL render a `NavigationRail` at the start of the screen

### Requirement: Initial start tab is Feed

On first composition of `MainShell` with no persisted state, `mainShellNavState.topLevelKey` SHALL equal `Feed`.

#### Scenario: Cold launch lands on Feed

- **WHEN** the user reaches `MainShell` for the first time after a cold launch with no persisted state
- **THEN** `mainShellNavState.topLevelKey` SHALL be `Feed` and the inner `NavDisplay` SHALL render the Feed top-level destination

### Requirement: Per-tab back stacks are preserved across tab switches

Switching from one top-level destination to another via `mainShellNavState.addTopLevel(key)` SHALL preserve the back stack of the outgoing tab. Returning to the previously-active tab SHALL restore its back stack at the same depth it was left.

#### Scenario: Sub-route survives tab switch

- **WHEN** the user is on Feed, navigates to a sub-route (e.g. `Profile(handle = "alice")` pushed onto Feed's stack), switches to Search, then switches back to Feed
- **THEN** the Feed tab SHALL render the `Profile(handle = "alice")` sub-route, not the Feed top-level destination

### Requirement: Back navigation follows "exit through home"

`MainShell`'s inner `NavDisplay.onBack` SHALL call `mainShellNavState.removeLast()`. Pressing back from a sub-route SHALL pop that sub-route off the active tab's stack. Pressing back from a non-Feed top-level destination SHALL switch the active tab to Feed (the start route). Pressing back from the Feed top-level destination with an empty Feed sub-stack SHALL allow the system to exit the app.

#### Scenario: Back from sub-route pops within the active tab

- **WHEN** the user is on Search with a sub-route on top of the Search top-level destination, and presses the system back button
- **THEN** the sub-route SHALL be popped and the Search top-level destination SHALL be visible

#### Scenario: Back from non-Feed top-level switches to Feed

- **WHEN** the user is on the Chats top-level destination (no sub-routes pushed) and presses the system back button
- **THEN** `mainShellNavState.topLevelKey` SHALL become `Feed` and the Chats stack SHALL remain available if the user returns

#### Scenario: Back from Feed top-level exits the app

- **WHEN** the user is on the Feed top-level destination with an empty Feed sub-stack and presses the system back button
- **THEN** the system back press SHALL not be consumed by `MainShell` and the activity SHALL exit the app per Android default behavior

### Requirement: State persists across configuration change and process death

`MainShellNavState`'s active-tab pointer and all per-tab back stacks SHALL survive configuration changes (rotation) and process death (low-memory kill, system restart of the activity). Restoration SHALL place the user on the same tab and at the same back-stack depth they left.

#### Scenario: Rotation preserves tab and sub-route

- **WHEN** the user is on Search with a sub-route pushed and the device rotates
- **THEN** after rotation the active tab SHALL still be Search and the sub-route SHALL still be visible

#### Scenario: Process death preserves tab and sub-route

- **WHEN** the user is on the You tab with `Settings` pushed onto its stack, the activity is destroyed via `saveInstanceState`, and is later recreated
- **THEN** `mainShellNavState.topLevelKey` SHALL be `You` and the top of the You back stack SHALL be `Settings`

### Requirement: Tab-internal navigation flows through the MVI Effect channel

ViewModels rendered inside `MainShell`'s inner `NavDisplay` SHALL NOT inject or otherwise reference `MainShellNavState`. Tab-internal navigation SHALL be initiated by a ViewModel emitting a `UiEffect` whose payload identifies the target `NavKey` (e.g. `NavigateTo(target: NavKey) : UiEffect`); the screen Composable SHALL collect that effect via `LaunchedEffect` and call `mainShellNavState.add(target)` using the value retrieved from `LocalMainShellNavState.current`.

#### Scenario: ViewModel does not reference MainShellNavState

- **WHEN** any ViewModel under `:feature:*:impl` or under `:app/.../shell/` is inspected
- **THEN** no constructor parameter, field, or method-local reference to `MainShellNavState` SHALL be present

### Requirement: Cross-tab navigation links push onto the active tab's stack

Tapping an interactive element inside one tab whose target is a `NavKey` registered as a different tab's content (e.g. tapping an author handle inside a Feed post when the target screen is `Profile`) SHALL push that target onto the **currently active** tab's back stack. The active tab SHALL NOT change as a side effect of the link.

#### Scenario: Profile link in Feed pushes onto Feed stack

- **WHEN** the active tab is Feed and a `FeedScreen` element with target `Profile(handle = "alice.bsky.social")` is tapped
- **THEN** `mainShellNavState.topLevelKey` SHALL remain `Feed` and the top of the Feed back stack SHALL become `Profile(handle = "alice.bsky.social")`

### Requirement: Empty tabs render `:app`-side placeholder Composables

Until each of `:feature:search:impl`, `:feature:chats:impl`, and `:feature:profile:impl` exists, `:app` SHALL provide internal `@MainShell`-qualified `EntryProviderInstaller` bindings that register placeholder Composables for the corresponding top-level destinations. Each placeholder SHALL identify the destination and indicate that the feature is not yet implemented (e.g. "Search — coming soon").

#### Scenario: Search tab without :impl renders placeholder

- **WHEN** `:feature:search:impl` does not exist in the build and the user activates the Search tab
- **THEN** `MainShell` SHALL render a placeholder Composable provided by `:app` that visually identifies the Search destination

### Requirement: Logout clears MainShell state with no residue

When the outer `Navigator` transitions away from `Main` (e.g. `replaceTo(Login)` on session expiry or explicit logout), the `Main` NavEntry SHALL leave composition and the associated `MainShellNavState` instance SHALL be eligible for garbage collection. Subsequent re-entry to `Main` SHALL produce a fresh `MainShellNavState` with default initial state (active tab = Feed, all per-tab stacks empty except for their start routes).

#### Scenario: Logout discards per-tab state

- **WHEN** the user has navigated into sub-routes on multiple tabs, then logs out (outer `Navigator.replaceTo(Login)`), and later re-authenticates and reaches `MainShell` again
- **THEN** the new `MainShellNavState` SHALL have `topLevelKey == Feed` and SHALL NOT carry any sub-route entries from the previous session
