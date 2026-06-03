# feature-search Specification

## Purpose
TBD - created by archiving change search-m3-expressive-searchbar. Update Purpose after archive.
## Requirements
### Requirement: Search input uses the M3 Expressive SearchBar

The Search tab SHALL present its query input as a Material 3 Expressive `SearchBar` — a collapsed full-corner pill — instead of a bespoke `OutlinedTextField`. The collapsed pill SHALL sit at the top of the Search tab body. A single shared input field (`SearchBarDefaults.InputField` bound to the ViewModel's `TextFieldState` and the screen's `SearchBarState`) SHALL back both the collapsed pill and the expanded overlay. The migration SHALL NOT add a new dependency and MAY opt into `@ExperimentalMaterial3Api`.

#### Scenario: Collapsed pill is shown at rest

- **WHEN** the Search tab is displayed and the search bar is collapsed
- **THEN** a full-corner `SearchBar` pill is rendered at the top of the body
- **AND** its placeholder text is the search hint

#### Scenario: Leading and trailing icons reflect state

- **WHEN** the search bar is collapsed
- **THEN** the leading icon is a search icon
- **WHEN** the search bar is expanded
- **THEN** the leading icon is a back affordance that collapses the bar
- **WHEN** the query text is non-blank
- **THEN** a trailing clear (X) affordance is shown that clears the text field

### Requirement: Tapping the bar opens a full-screen typing overlay

Tapping the collapsed `SearchBar` SHALL expand it into an `ExpandedFullScreenSearchBar` overlay that hosts the active input field and the query-assist content. On Compact width the overlay SHALL be the full-screen variant. The expanded container SHALL be selected at a single width-class-keyed call site; the Medium/Expanded branch is reserved for a later change and for this change SHALL fall through to the full-screen variant.

#### Scenario: Expand on tap

- **WHEN** the user taps the collapsed search bar
- **THEN** the search bar animates to expanded and the full-screen overlay is shown
- **AND** the soft keyboard is requested

#### Scenario: Collapse via back affordance reverts unsubmitted text

- **WHEN** the search bar is expanded and the user activates the back affordance without submitting
- **THEN** the search bar animates to collapsed
- **AND** the text field is reverted to the last submitted query (or blank if none was submitted)
- **AND** the body content beneath the pill is unchanged

### Requirement: Overlay content is driven by live query text

While the search bar is expanded, the overlay content SHALL be determined by the live (debounced) query text, independent of the ViewModel's phase: a blank query SHALL show the recent-search list, and a non-blank query SHALL show the existing typeahead suggestions surface (including its "Search for {q}" call-to-action and result-row navigation).

#### Scenario: Blank query shows recents in the overlay

- **WHEN** the overlay is open and the query text is blank
- **THEN** the recent searches are listed in the overlay
- **AND** tapping a recent entry seeds the query, submits it, and collapses the bar

#### Scenario: Typing shows typeahead in the overlay

- **WHEN** the overlay is open and the query text is non-blank
- **THEN** the typeahead suggestions surface is shown for the current query
- **AND** selecting an actor suggestion navigates to that profile

### Requirement: Submitting collapses the bar and shows result tabs

Submitting a non-blank query (IME Search action, or tapping a recent entry) SHALL persist it to recent searches, collapse the search bar, and render the Posts / People / Feeds result tabs in the body beneath the collapsed pill. The result tabs, their per-tab ViewModels, paging, and result-row navigation SHALL be unchanged from the pre-migration behavior.

#### Scenario: Submit transitions to results

- **WHEN** the user submits a non-blank query
- **THEN** the query is recorded in recent searches
- **AND** the search bar collapses to a pill displaying the query
- **AND** the Posts / People / Feeds tabs render in the body with the submitted query

#### Scenario: Empty submission is ignored

- **WHEN** the user submits while the query is blank
- **THEN** no recent search is recorded
- **AND** the body remains in the resting (Discover) state

### Requirement: Resting body shows recent-search chips

When the search bar is collapsed and no query has been submitted (the resting Discover state), the body beneath the pill SHALL show the recent-search chip strip when recents exist, and nothing when there are none. The query lifecycle SHALL be modeled by a `SearchPhase` of exactly two states — `Discover` and `Results` — with no separate `Typeahead` body state (typeahead is presented only within the overlay).

#### Scenario: Recents present at rest

- **WHEN** the bar is collapsed, no query is submitted, and recent searches exist
- **THEN** the recent-search chip strip is shown in the body

#### Scenario: No recents at rest

- **WHEN** the bar is collapsed, no query is submitted, and there are no recent searches
- **THEN** the body shows no recent-search content

### Requirement: SearchBarState ownership and tab-retap behavior

The `SearchBarState` SHALL be owned by the Search screen Composable (via `rememberSearchBarState()`) and SHALL NOT live in the ViewModel; the ViewModel SHALL continue to own only the `TextFieldState` (the sanctioned editor exception) and its derived projections. Re-tapping the Search tab in the navigation bar SHALL expand the search bar and focus the input field.

#### Scenario: ViewModel does not hold SearchBarState

- **WHEN** the Search ViewModel is inspected
- **THEN** it exposes the `TextFieldState` and derived state but no `SearchBarState`

#### Scenario: Tab re-tap expands the bar

- **WHEN** the active Search tab is re-tapped via the navigation bar
- **THEN** the search bar animates to expanded and the input field is focused

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
