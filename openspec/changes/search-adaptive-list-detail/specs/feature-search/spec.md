## ADDED Requirements

### Requirement: Search is an adaptive list pane

The Search tab SHALL participate in `MainShell`'s list-detail scene strategy as a **list pane**: its entry SHALL carry `ListDetailSceneStrategy.listPane(...)` metadata with a detail-pane placeholder. On Compact width the tab SHALL render single-pane exactly as before; on Medium/Expanded width Search SHALL occupy the list pane, with the placeholder filling the detail pane until a detail entry is pushed.

#### Scenario: Single-pane on compact

- **WHEN** the window is Compact width
- **THEN** the Search tab renders full-width (single-pane), identical to the pre-change behavior

#### Scenario: Two-pane on medium/expanded

- **WHEN** the window is Medium or Expanded width and the Search tab is shown with no detail entry pushed
- **THEN** Search occupies the list pane
- **AND** the detail pane shows the placeholder empty state

### Requirement: Tapping a post fills the detail pane (replaceTop)

When the user taps a post in Search results, the app SHALL push `PostDetailRoute` using **`replaceTop`**. On Medium/Expanded the tapped post SHALL render in the detail pane beside the results; tapping another result SHALL replace the detail pane's content rather than stacking; system Back from the detail SHALL return to the results list. On Compact the same push SHALL degrade to a normal full-screen navigation.

#### Scenario: Post fills detail pane on tablet

- **WHEN** the window is Medium/Expanded, Search shows results, and the user taps a post
- **THEN** the results remain in the list pane
- **AND** the tapped post renders in the detail pane

#### Scenario: Selecting another post swaps the detail

- **WHEN** a post is already shown in the detail pane and the user taps a different result
- **THEN** the detail pane shows the newly tapped post (the previous one is replaced, not stacked)

#### Scenario: Back returns to results

- **WHEN** a post is shown in the detail pane and the user invokes system back
- **THEN** the detail pane returns to the placeholder and focus is on the results list (not a previous post)

### Requirement: Expanded search is width-gated and pane-scoped on tablets

The expanded search surface SHALL be selected by window width class at a single call site: **Compact → `ExpandedFullScreenSearchBar`** (full-window); **Medium/Expanded → `ExpandedDockedSearchBar`** (a popup scoped to the list-pane region). On Medium/Expanded, expanding the search SHALL leave the detail pane and the navigation rail visible. The collapsed bar, the shared input field, the `SearchBarState`, and the overlay content (recents / typeahead) SHALL be identical across both widths; only the expanded container differs. The full-screen *contained* variant SHALL NOT be used (it covers the whole window and is not pane-scoped).

#### Scenario: Docked expansion on tablet keeps the detail pane visible

- **WHEN** the window is Medium/Expanded and the user expands the search bar
- **THEN** the expanded search renders as a docked surface within the list-pane region
- **AND** the detail pane and the navigation rail remain visible

#### Scenario: Full-screen expansion on compact

- **WHEN** the window is Compact and the user expands the search bar
- **THEN** the expanded search renders full-screen, as in the prior single-pane behavior

### Requirement: Actor taps navigate to the Profile list-detail

Tapping an actor in Search results or typeahead SHALL continue to navigate to the `Profile` destination (itself a list pane), which takes over the pane context — unchanged from current behavior. This change SHALL NOT make `Profile` render as Search's detail pane.

#### Scenario: Actor tap opens Profile

- **WHEN** the user taps an actor in Search results or typeahead
- **THEN** the app navigates to that actor's `Profile` destination
- **AND** Profile presents its own list-detail context
