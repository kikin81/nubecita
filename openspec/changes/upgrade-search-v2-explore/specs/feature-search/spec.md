# Spec Delta

## MODIFIED Requirements

### Requirement: Submitting collapses the bar and shows result tabs

Submitting a non-blank query (IME Search action, tapping a recent entry, or tapping a trend whose link is not a feed or a starter pack) SHALL persist it to recent searches, collapse the search bar, and render the Posts / People / Feeds / Packs result tabs in the body beneath the collapsed pill. Each tab SHALL page independently. The selected tab SHALL be kept when a new query is submitted while results are showing.

#### Scenario: Submit transitions to results

- **WHEN** the user submits a non-blank query
- **THEN** the query is recorded in recent searches
- **AND** the search bar collapses to a pill displaying the query
- **AND** the Posts / People / Feeds / Packs tabs render in the body with the submitted query

#### Scenario: Empty submission is ignored

- **WHEN** the user submits while the query is blank
- **THEN** no recent search is recorded
- **AND** the body remains in the resting (Discover) state

#### Scenario: Packs tab lists starter packs

- **WHEN** the user selects the Packs tab for a submitted query
- **THEN** starter packs matching the query are listed
- **AND** tapping one opens that starter pack

### Requirement: Resting body shows recent-search chips

When the search bar is collapsed and no query has been submitted (the resting Discover state), the body beneath the pill SHALL show the recent-search chip strip when recents exist, followed by the landing content defined in "The landing shows Trending and suggestions". The query lifecycle SHALL be modeled by a `SearchPhase` of exactly two states — `Discover` and `Results` — with no separate `Typeahead` body state (typeahead is presented only within the overlay).

#### Scenario: Recents present at rest

- **WHEN** the bar is collapsed, no query is submitted, and recent searches exist
- **THEN** the recent-search chip strip is shown at the top of the body

#### Scenario: No recents at rest

- **WHEN** the bar is collapsed, no query is submitted, and there are no recent searches
- **THEN** the body shows no recent-search content
- **AND** the landing content is still shown

### Requirement: Search is an adaptive list pane

The Search tab SHALL participate in `MainShell`'s list-detail scene strategy as a **list pane**. On Compact width the tab SHALL render single-pane; on Medium/Expanded width Search SHALL occupy the list pane, and the detail pane SHALL show Explore or the search Summary (see "The detail pane shows Explore, then a Summary, then the opened item") until a detail entry is pushed. The detail pane SHALL NOT show a generic "select a post" empty state for Search.

#### Scenario: Single-pane on compact

- **WHEN** the window is Compact width
- **THEN** the Search tab renders full-width (single-pane)

#### Scenario: Two-pane on medium/expanded

- **WHEN** the window is Medium or Expanded width and the Search tab is shown with no query submitted and no detail entry pushed
- **THEN** Search occupies the list pane
- **AND** the detail pane shows Explore

#### Scenario: Search content never appears on another tab

- **WHEN** the user switches from Search to another tab that has no detail entry pushed
- **THEN** that tab's own detail placeholder is shown
- **AND** neither Explore nor a search Summary is shown

## ADDED Requirements

### Requirement: Post search supports filters

The Posts tab SHALL let the user narrow results by sort, author relationship (anyone or people you follow), media, replies, one language, and date. With no date chosen, the search SHALL cover all time. Changing a filter SHALL re-run the search. Filters SHALL persist across successive queries while results are showing and SHALL reset when the search is cleared.

#### Scenario: Available choices

- **WHEN** the user opens the filters
- **THEN** the choices are: sort Top or Latest; from anyone or people you follow; media any, has media, or video only; replies include, hide, or only; any language or one language; date all time, past day, past week, past month, or a custom range

#### Scenario: A filter narrows the results

- **WHEN** the user turns on "Has media" for a submitted query
- **THEN** the Posts tab reloads and lists only posts with media

#### Scenario: Default date covers all time

- **WHEN** the user submits a query without choosing a date filter
- **THEN** posts of any age can appear in the results

#### Scenario: Filters survive a new query

- **WHEN** the user has "People you follow" on and submits a different query from the results
- **THEN** the new results are still limited to people the user follows

#### Scenario: Clearing the search resets filters

- **WHEN** the user clears the search and later submits a new query
- **THEN** no filters are active

#### Scenario: Custom range entered backwards

- **WHEN** the user picks a custom range whose end is before its start
- **THEN** the search covers the same span as if the dates had been picked in order

### Requirement: Filters are reachable from a chip row and a full sheet

The Posts tab SHALL show one horizontally scrolling chip row in a fixed order: a control that opens the filter sheet and shows the active-filter count, then sort, Following, Media, Video, Language and Date. Chips SHALL keep their position when toggled. A chip with a chosen value SHALL show it as its label and render as selected. The filter sheet SHALL offer every filter, a reset and a confirm action, and SHALL open from any results tab.

#### Scenario: One-tap filter

- **WHEN** the user taps the Following chip
- **THEN** it renders as selected in the same position
- **AND** the active-filter count increases by one

#### Scenario: Value shown in place

- **WHEN** the user chooses English in the language filter
- **THEN** the Language chip's label reads "English" and it renders as selected

#### Scenario: Reset

- **WHEN** the user taps reset in the filter sheet
- **THEN** every filter returns to its default and no chip is selected

### Requirement: An estimated result count is shown when available

When the server returns an estimated total for a post search, the Posts tab SHALL show it as an approximate, compactly formatted count. When the server returns none, the count SHALL be omitted with no placeholder.

#### Scenario: Count present

- **WHEN** a post search returns an estimated total of 1240
- **THEN** a line reading "About 1.2K posts" (localized) is shown above the results

#### Scenario: Count absent

- **WHEN** a post search returns no estimated total
- **THEN** no count line is shown

### Requirement: The landing shows Trending and suggestions

The resting Search body SHALL show a ranked Trending list. On Compact width it SHALL also show suggested feeds, people to follow and starter packs beneath Trending. On Medium/Expanded width those suggestions SHALL appear in the detail pane instead of the list pane. A section that fails to load or is empty SHALL be hidden.

#### Scenario: Trend row content

- **WHEN** a trend is listed
- **THEN** its row shows its rank and name
- **AND** its category, post count and a "hot" mark when the server provides them

#### Scenario: Phone landing

- **WHEN** the Search tab is opened on a Compact window with no query submitted
- **THEN** the body shows, in order: recent searches, Trending (five rows and a "Show more" control), suggested feeds, people to follow, starter packs

#### Scenario: Wide landing

- **WHEN** the Search tab is opened on a Medium or Expanded window with no query submitted
- **THEN** the list pane shows recent searches and the Trending list
- **AND** suggested feeds, people to follow and starter packs appear in the detail pane, not in the list pane

#### Scenario: A section fails

- **WHEN** the Trending request fails and the other sections load
- **THEN** the Trending section is not shown and the other sections are

### Requirement: Tapping a trend always leads somewhere

Tapping a trend SHALL open its feed when the trend links to a feed (identified by a DID or by a handle), open the starter pack when it links to one, submit the linked query when it links to a search, and otherwise submit the trend's name as a search. If a handle in a trend link cannot be resolved, the trend's name SHALL be submitted as a search.

#### Scenario: Feed trend

- **WHEN** the user taps a trend that links to a feed
- **THEN** that feed opens (in the detail pane on Medium/Expanded width)

#### Scenario: Unrecognised link

- **WHEN** the user taps a trend whose link has an unrecognised shape
- **THEN** the trend's name is submitted as a search

#### Scenario: Handle cannot be resolved

- **WHEN** the user taps a trend whose feed link names a handle that fails to resolve
- **THEN** the trend's name is submitted as a search

### Requirement: The detail pane shows Explore, then a Summary, then the opened item

On Medium/Expanded width, the Search tab's detail pane SHALL show Explore while no query is submitted, a Summary of the search once a query is submitted and nothing is opened, and the opened item once something is tapped. Submitting a search SHALL NOT open any result automatically. If every Explore section fails, the pane SHALL show a single retry state.

#### Scenario: Explore layout

- **WHEN** Explore is shown
- **THEN** suggested feeds, people to follow and starter packs each appear as a two-column grid sized to the pane
- **AND** no card is clipped at the pane's edge

#### Scenario: Summary replaces Explore on submit

- **WHEN** the user submits a query on a wide window
- **THEN** the detail pane shows the Summary for that query
- **AND** no post detail is opened

#### Scenario: Back from an opened item

- **WHEN** an item opened from the results is closed with system back
- **THEN** the detail pane shows the Summary again and the search is unchanged

#### Scenario: Explore fails entirely

- **WHEN** every Explore section fails to load
- **THEN** the pane shows one retry state
- **AND** retrying successfully shows the sections

### Requirement: The Summary reflects the current search

The Summary SHALL show the query, the active filters, the top post, and the first matching people, feeds and starter packs for the current search. Its filter and "See all" actions SHALL act on the same search the list pane shows. The Summary and the result tabs SHALL share requests: one search causes at most one first-page request per result type, and a refresh in a tab updates the Summary.

#### Scenario: Summary content

- **WHEN** the Summary is shown for a query with results of every type
- **THEN** it shows the query, the estimated post count and sort when a count is available, the active filters as removable chips, and an action that opens the filter sheet
- **AND** the top post as a card, up to three people and up to three feeds as rows with a line of context and a Follow or Pin action, and up to two starter packs

#### Scenario: Empty sections

- **WHEN** a result type has no matches
- **THEN** its section is hidden
- **AND** when nothing matches at all, the Summary says there are no results for the query

#### Scenario: See all switches the tab

- **WHEN** the user taps "See all" under People in the Summary
- **THEN** the list pane shows the People tab for the same query

#### Scenario: Removing a filter from the Summary

- **WHEN** the People tab is selected and the user removes a filter chip in the Summary
- **THEN** the Summary's count and top post update for the new filters
- **AND** the People tab stays selected

#### Scenario: One request per result type

- **WHEN** a query is submitted on a wide window and the user then opens the Feeds tab
- **THEN** exactly one first-page feeds request was made for that query

#### Scenario: Stale results are not shown

- **WHEN** a second query is submitted before the first query's results arrive
- **THEN** neither the tabs nor the Summary show the first query's results under the second query

### Requirement: Detail-pane position survives leaving the pane

The scroll position of Explore and of the Summary SHALL be preserved when the pane is temporarily replaced or removed — by opening and closing an item, switching tabs and returning, or folding and unfolding the device. The Summary's position SHALL reset when the query or the filters change; Explore's position SHALL be kept across searches.

#### Scenario: Return from an opened post

- **WHEN** the user scrolls the Summary, opens a post, and returns with system back
- **THEN** the Summary is at the scroll position the user left

#### Scenario: New query resets the Summary

- **WHEN** the user submits a different query
- **THEN** the Summary starts at the top

### Requirement: System back from results clears the search

When results are showing, the search bar is collapsed, and no item opened from Search is on top, system back SHALL clear the search and return to the landing. It SHALL NOT clear the search while an opened item is showing. From the landing, system back SHALL behave as it does for any tab root. The behaviour SHALL be the same on Compact and wider windows, including after folding or unfolding mid-search.

#### Scenario: Back from results

- **WHEN** results are showing with nothing opened and the user presses system back
- **THEN** the search is cleared and the landing is shown

#### Scenario: Back from an opened thread keeps the search

- **WHEN** a post opened from the results is showing and the user presses system back
- **THEN** the post closes and the same results are still showing

### Requirement: The search bar keeps its state across configuration changes

A collapsed search bar SHALL remain collapsed, with no keyboard shown, after a theme change, rotation, fold or unfold. A bar the user had expanded SHALL remain expanded.

#### Scenario: Theme change with results showing

- **WHEN** results are showing with the bar collapsed on a Medium or Expanded window and the system theme changes
- **THEN** the bar is still collapsed, the results are visible, and the keyboard is not shown

### Requirement: The search bar fits its container

The collapsed search bar SHALL span the available width with equal horizontal margins at every window width. On Medium/Expanded width the expanded dropdown SHALL be as wide as the collapsed bar, so no content of the pane beneath is visible beside it.

#### Scenario: Phone margins

- **WHEN** the Search tab is shown on a Compact window
- **THEN** the collapsed bar's left and right margins are equal

### Requirement: Result rows carry context and actions

People result rows SHALL show a one-line bio when the account has one and a Follow action. Feed result rows SHALL show a compactly formatted like count and a Pin action. Follow and Pin SHALL update immediately and roll back with a message on failure. Typeahead match highlighting SHALL NOT highlight text inside the default `.bsky.social` handle suffix.

#### Scenario: Like count formatting

- **WHEN** a feed with 48902 likes is listed
- **THEN** its row reads "48.9K" (localized), not "48902"

#### Scenario: Suffix is not highlighted

- **WHEN** the user types "al" and a suggestion's handle is `soci.bsky.social`
- **THEN** no part of ".bsky.social" is highlighted

### Requirement: Discovery and search data never crosses accounts

Suggestions, trends and search results held in memory SHALL be discarded when the signed-in account changes or the user signs out.

#### Scenario: Account switch

- **WHEN** the user switches to another account and opens Search
- **THEN** no suggestion, trend or result fetched for the previous account is shown
