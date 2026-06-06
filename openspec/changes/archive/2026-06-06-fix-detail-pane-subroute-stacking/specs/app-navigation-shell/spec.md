## ADDED Requirements

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

On Medium and Expanded widths, within a list-detail surface, the inner `NavDisplay`'s scene assignment SHALL treat the **first `listPane()` entry of the active tab's segment** (per **List-detail panes are computed from the active tab's stack segment**) as the list anchor and SHALL treat **every entry after that anchor, within that tab's segment,** as belonging to the **detail region**, rendered in the detail (right) pane. A sub-route pushed onto the active tab's stack while a detail-region entry is already present SHALL stack within the detail region (becoming the visible detail content) and SHALL NOT re-anchor or replace the list pane — **even if that sub-route carries `ListDetailSceneStrategy.listPane()` metadata** (as `Profile` does, because `Profile` is also a top-level tab). The list pane SHALL remain visible and retain its scroll state. On Compact width, the same push SHALL stack full-screen as it does today (single-pane).

This requirement governs all `@MainShell` list-detail surfaces (Feed, Search, Chats) and all sub-routes reachable from a detail pane (e.g. `Profile` via an author tap, a nested or quoted `PostDetail`, `Settings`).

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

## MODIFIED Requirements

### Requirement: Cross-tab navigation links push onto the active tab's stack

Tapping an interactive element inside one tab whose target is a `NavKey` registered as a different tab's content (e.g. tapping an author handle inside a Feed post when the target screen is `Profile`) SHALL push that target onto the **currently active** tab's back stack. The active tab SHALL NOT change as a side effect of the link. On Medium/Expanded widths, when the push occurs while a detail-region entry is present, the pushed target SHALL stack within the detail region per **Sub-routes opened from a detail pane stack within the detail region** — it SHALL NOT re-anchor or replace the list pane, regardless of the target's own pane metadata.

#### Scenario: Profile link in Feed pushes onto Feed stack

- **WHEN** the active tab is Feed and a `FeedScreen` element with target `Profile(handle = "alice.bsky.social")` is tapped
- **THEN** `mainShellNavState.topLevelKey` SHALL remain `Feed` and the top of the Feed back stack SHALL become `Profile(handle = "alice.bsky.social")`

#### Scenario: Profile link tapped from the detail pane does not evict the list

- **WHEN** the active tab is Feed at Expanded width with stack `[Feed, PostDetail(p)]`, and an author element inside `PostDetail(p)` with target `Profile(handle = "alice.bsky.social")` is tapped
- **THEN** `mainShellNavState.topLevelKey` SHALL remain `Feed`, the top of the Feed back stack SHALL become `Profile(handle = "alice.bsky.social")`, and the **Feed list pane SHALL remain rendered** in the left pane while `Profile` renders in the detail pane

### Requirement: Inner `NavDisplay` applies a `ListDetailSceneStrategy`

`MainShell`'s inner `NavDisplay` SHALL be invoked with a non-empty `sceneStrategies` list that includes a list-detail scene strategy backed by `androidx.compose.material3.adaptive.navigation3`. Additional scene strategies (e.g. the `AdaptiveDialogSceneStrategy` overlay) MAY be supplied; when multiple strategies are present, overlay strategies SHALL precede the list-detail strategy in the list. The list-detail strategy SHALL assign panes per **Sub-routes opened from a detail pane stack within the detail region** — i.e. the first `listPane()` entry anchors the list pane and all later entries occupy the detail region; a later `listPane()`-tagged entry does NOT re-anchor the list. The outer `NavDisplay` in `app/Navigation.kt` SHALL NOT be given any scene strategy.

#### Scenario: Inner NavDisplay receives a list-detail scene strategy

- **WHEN** `MainShell()` is composed
- **THEN** the inner `NavDisplay` SHALL be called with a `sceneStrategies` parameter containing a list-detail scene strategy element (and, if present, any overlay strategy SHALL appear before it)

#### Scenario: Outer NavDisplay does not receive a scene strategy

- **WHEN** `MainNavigation()` (in `app/Navigation.kt`) is composed
- **THEN** the outer `NavDisplay` SHALL NOT pass `sceneStrategies`, or SHALL pass an empty list

#### Scenario: A later listPane-tagged entry does not re-anchor the list

- **WHEN** the inner `NavDisplay` renders an active stack `[Feed, PostDetail(p), Profile(…)]` on Expanded width, where both `Feed` and `Profile` carry `listPane()` metadata
- **THEN** the list pane SHALL render `Feed` (the first `listPane()` entry) and the detail pane SHALL render `Profile(…)`; `Profile(…)` SHALL NOT be promoted to the list pane
