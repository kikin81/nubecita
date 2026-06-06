# app-navigation-shell Specification

## Purpose
TBD - created by archiving change add-adaptive-navigation-shell. Update Purpose after archive.
## Requirements
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

Tapping an interactive element inside one tab whose target is a `NavKey` registered as a different tab's content (e.g. tapping an author handle inside a Feed post when the target screen is `Profile`) SHALL push that target onto the **currently active** tab's back stack. The active tab SHALL NOT change as a side effect of the link. On Medium/Expanded widths, when the push occurs while a detail-region entry is present, the pushed target SHALL stack within the detail region per **Sub-routes opened from a detail pane stack within the detail region** — it SHALL NOT re-anchor or replace the list pane, regardless of the target's own pane metadata.

#### Scenario: Profile link in Feed pushes onto Feed stack

- **WHEN** the active tab is Feed and a `FeedScreen` element with target `Profile(handle = "alice.bsky.social")` is tapped
- **THEN** `mainShellNavState.topLevelKey` SHALL remain `Feed` and the top of the Feed back stack SHALL become `Profile(handle = "alice.bsky.social")`

#### Scenario: Profile link tapped from the detail pane does not evict the list

- **WHEN** the active tab is Feed at Expanded width with stack `[Feed, PostDetail(p)]`, and an author element inside `PostDetail(p)` with target `Profile(handle = "alice.bsky.social")` is tapped
- **THEN** `mainShellNavState.topLevelKey` SHALL remain `Feed`, the top of the Feed back stack SHALL become `Profile(handle = "alice.bsky.social")`, and the **Feed list pane SHALL remain rendered** in the left pane while `Profile` renders in the detail pane

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

### Requirement: Inner `NavDisplay` applies a `ListDetailSceneStrategy`

`MainShell`'s inner `NavDisplay` SHALL be invoked with a non-empty `sceneStrategies` list that includes a list-detail scene strategy backed by `androidx.compose.material3.adaptive.navigation3`. Additional scene strategies (e.g. the `AdaptiveDialogSceneStrategy` overlay) MAY be supplied; when multiple strategies are present, overlay strategies SHALL precede the list-detail strategy in the list. The list-detail strategy SHALL assign panes per **Sub-routes opened from a detail pane stack within the detail region** — i.e. the first `listPane()` entry anchors the list pane and all later pane-tagged entries occupy the detail region; a later `listPane()`-tagged entry does NOT re-anchor the list. The outer `NavDisplay` in `app/Navigation.kt` SHALL NOT be given any scene strategy.

#### Scenario: Inner NavDisplay receives a list-detail scene strategy

- **WHEN** `MainShell()` is composed
- **THEN** the inner `NavDisplay` SHALL be called with a `sceneStrategies` parameter containing a list-detail scene strategy element (and, if present, any overlay strategy SHALL appear before it)

#### Scenario: Outer NavDisplay does not receive a scene strategy

- **WHEN** `MainNavigation()` (in `app/Navigation.kt`) is composed
- **THEN** the outer `NavDisplay` SHALL NOT pass `sceneStrategies`, or SHALL pass an empty list

#### Scenario: A later listPane-tagged entry does not re-anchor the list

- **WHEN** the inner `NavDisplay` renders an active stack `[Feed, PostDetail(p), Profile(…)]` on Expanded width, where both `Feed` and `Profile` carry `listPane()` metadata
- **THEN** the list pane SHALL render `Feed` (the first `listPane()` entry) and the detail pane SHALL render `Profile(…)`; `Profile(…)` SHALL NOT be promoted to the list pane

### Requirement: List-pane entries supply a detail placeholder Composable

Any `EntryProviderInstaller @MainShell` that registers an entry intended to act as the list pane in a list-detail scene SHALL pass `metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = …)` to the entry builder. The `detailPlaceholder` Composable SHALL render a non-empty visual surface that explains to the user what selecting a list item will reveal — at minimum, a textual prompt sourced from a string resource.

#### Scenario: Feed entry registers listPane metadata

- **WHEN** the `@MainShell`-qualified `EntryProviderInstaller` for the `Feed` `NavKey` is invoked at composition
- **THEN** the resulting entry SHALL carry `ListDetailSceneStrategy.listPane(...)` metadata, and the `detailPlaceholder` lambda SHALL render a non-empty Composable

#### Scenario: Compact width does not compose the detail placeholder

- **WHEN** the device window's `windowWidthSizeClass` is `Compact` and the back stack is `[Feed]`
- **THEN** the rendered tree SHALL NOT contain the `FeedDetailPlaceholder` Composable's content (the strategy collapses to single-pane on compact)

#### Scenario: Medium and expanded widths compose the detail placeholder

- **WHEN** the device window's `windowWidthSizeClass` is `Medium` or `Expanded` and the back stack is `[Feed]`
- **THEN** the rendered tree SHALL contain a `FeedDetailPlaceholder` Composable adjacent to the Feed list pane

### Requirement: Full-screen sub-routes do not declare `detailPane` metadata

Entries pushed onto the inner back stack that are intended to fully obscure the list pane on every window-size class (e.g. `Settings`, an account-management screen, or any screen that is not semantically a "detail of the list above it") SHALL NOT pass `metadata = ListDetailSceneStrategy.detailPane()`. Such entries SHALL pass no metadata at all, allowing the scene strategy to fall through to the strategy's default single-pane behavior.

#### Scenario: Settings entry is rendered single-pane on expanded

- **WHEN** the back stack is `[Feed, Settings]` on a window with `windowWidthSizeClass == Expanded`
- **THEN** `Settings` SHALL be rendered single-pane covering the full content area, and the Feed list pane SHALL NOT be visible alongside it

### Requirement: `:feature:moderation:impl` registers the `Report` sub-route as a `@MainShell` entry

`:feature:moderation:impl` SHALL provide a `@Provides @IntoSet @MainShell EntryProviderInstaller` binding for the `Report` `NavKey` declared in `:feature:moderation:api`. The provider SHALL render a Material 3 `ModalBottomSheet` hosting the report dialog content (the dialog Composable + its `ReportDialogViewModel` via `hiltViewModel()`). The Report entry MUST NOT carry `ListDetailSceneStrategy.listPane { }` or `detailPane { }` metadata — the Report sub-route is a transient overlay-style entry that the scene strategy may place in either pane on Medium / Expanded widths. `:app`'s `MainShellPlaceholderModule` MUST NOT register any placeholder for the `Report` NavKey (it was never a placeholder destination — Report is a sub-route, not a top-level tab).

#### Scenario: Report sub-route renders on push from any tab

- **WHEN** a feature module on any of the four top-level tabs (Feed / Search / Chats / Profile) pushes `Report(subject = ...)` onto `LocalMainShellNavState.current`
- **THEN** `MainShell`'s inner `NavDisplay` resolves the entry through `:feature:moderation:impl`'s `@MainShell` provider; no `:app`-side placeholder is consulted; the dialog renders as a Modal Bottom Sheet on top of the active tab's content

#### Scenario: Report entry has no list-detail pane metadata

- **WHEN** the `MainShell` `NavDisplay`'s `ListDetailSceneStrategy` inspects the Report entry on a Medium-width device
- **THEN** the entry SHALL NOT be tagged `listPane{}` and SHALL NOT be tagged `detailPane{}`; the strategy is free to render the Modal Bottom Sheet over either pane or the full viewport per its internal rules

### Requirement: List-detail panes are computed from the active tab's stack segment

On Medium and Expanded widths, the inner `NavDisplay`'s list-detail pane assignment SHALL be derived **only from the active tab's back-stack segment** (`mainShellNavState.topLevelKey` and that tab's per-tab stack). Entries belonging to other tabs — including the start-tab (Feed) stack that `MainShellNavState` concatenates beneath the active tab for predictive/system-back — SHALL NOT contribute to the list or detail pane. When the active tab changes, both panes SHALL recompute from the new active tab's segment: the list pane SHALL render the new tab's list, and the detail pane SHALL render the new tab's current detail entry or, if that tab has no detail entry, its list-pane `detailPlaceholder`.

#### Scenario: Switching tabs resets the detail pane

- **GIVEN** an Expanded window, active tab Feed with stack `[Feed, PostDetail(p)]` rendered as Feed | PostDetail(p), where the Chats tab has no detail entry
- **WHEN** the user taps the Chats top-level destination
- **THEN** the left pane SHALL render the Chats list and the detail pane SHALL render the Chats `detailPlaceholder` (e.g. "select a conversation") — it SHALL NOT continue to show `PostDetail(p)` from the Feed tab

#### Scenario: Returning to a tab restores that tab's detail entry

- **GIVEN** active tab Feed with stack `[Feed, PostDetail(p)]` rendered as Feed | PostDetail(p)
- **WHEN** the user switches to Chats (showing Chats | placeholder) and then switches back to Feed
- **THEN** the Feed tab SHALL again render Feed | PostDetail(p) (its preserved list anchor and detail entry)

### Requirement: Sub-routes opened from a detail pane stack within the detail region

On Medium and Expanded widths, within a list-detail surface, the inner `NavDisplay`'s scene assignment SHALL treat the **first `listPane()` entry of the active tab's segment** (per **List-detail panes are computed from the active tab's stack segment**) as the list anchor and SHALL treat **every pane-tagged entry after that anchor, within that tab's segment,** as belonging to the **detail region**, rendered in the detail (right) pane. A pane-tagged sub-route pushed onto the active tab's stack while a detail-region entry is already present SHALL stack within the detail region (becoming the visible detail content) and SHALL NOT re-anchor or replace the list pane — **even if that sub-route carries `ListDetailSceneStrategy.listPane()` metadata** (as `Profile` does, because `Profile` is also a top-level tab). The list pane SHALL remain visible and retain its scroll state. On Compact width, the same push SHALL stack full-screen as it does today (single-pane).

This requirement governs all `@MainShell` list-detail surfaces (Feed, Search, Chats) and the pane-tagged sub-routes reachable from a detail pane (e.g. `Profile` via an author tap, a nested or quoted `PostDetail`). Sub-routes that carry **no** pane metadata (e.g. `Settings`) are intentionally outside this rule — they continue to render full-screen / single-pane per **Full-screen sub-routes do not declare `detailPane` metadata**, which this requirement does not override.

#### Scenario: Author tap from PostDetail keeps the list pane

- **GIVEN** a Medium/Expanded window with the active tab's back stack `[Feed, PostDetail(p)]` rendered as Feed (list) | PostDetail (detail)
- **WHEN** the user taps the post author, pushing `Profile(handle = "…")` so the stack becomes `[Feed, PostDetail(p), Profile(…)]`
- **THEN** the **left pane SHALL still render Feed** (the list anchor), and the right (detail) pane SHALL render `Profile(…)` stacked over `PostDetail(p)` — the list pane SHALL NOT be replaced by `Profile`

#### Scenario: Back pops within the detail region

- **GIVEN** the stack `[Feed, PostDetail(p), Profile(…)]` rendered as Feed | Profile on Medium/Expanded
- **WHEN** the user presses Back
- **THEN** `Profile(…)` SHALL be popped, the detail pane SHALL return to `PostDetail(p)`, and the left pane SHALL still render Feed (i.e. the view returns to Feed | PostDetail)

#### Scenario: Nested detail from a detail pane does not re-anchor the list

- **GIVEN** the stack `[Search, PostDetail(a)]` rendered as Search (list) | PostDetail(a) on Expanded
- **WHEN** a sub-route opened from the detail pane pushes another detail-region entry (e.g. a quoted `PostDetail(b)`)
- **THEN** the left pane SHALL still render Search, and the detail pane SHALL render the newly pushed entry stacked over `PostDetail(a)`

#### Scenario: Compact width still stacks full-screen

- **GIVEN** a Compact window with the active stack `[Feed, PostDetail(p)]` rendered single-pane (PostDetail full-screen)
- **WHEN** the user taps the post author, pushing `Profile(…)`
- **THEN** `Profile(…)` SHALL render full-screen as the top of the single-pane stack, and Back SHALL return to `PostDetail(p)` — behavior unchanged from today
