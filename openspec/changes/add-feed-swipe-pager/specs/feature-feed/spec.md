## ADDED Requirements

### Requirement: Feed switching uses an adaptive HorizontalPager

The system SHALL render pinned feeds inside `FeedHost` using a `HorizontalPager` with `beyondViewportPageCount = 0`. On compact screen widths (<600dp), horizontal swipe gestures SHALL be enabled (`userScrollEnabled = true`). On medium and expanded screen widths (≥600dp), horizontal swipe gestures SHALL be disabled (`userScrollEnabled = false`), requiring users to tap feed chips to switch feeds.

#### Scenario: Swiping between feeds on compact screens
- **WHEN** the app runs on a compact screen width (<600dp)
- **THEN** the user can horizontally drag or fling to transition between pinned feed pages

#### Scenario: Swiping disabled on tablet list-detail layout
- **WHEN** the app runs on a medium or expanded screen width (≥600dp)
- **THEN** horizontal drag gestures on the feed list pane do not trigger page scrolling, avoiding touch collisions with the pane expansion drag handle

### Requirement: Pager settlement synchronizes bidirectionally with FeedHost selection

The system SHALL synchronize `pagerState.settledPage` to `FeedHostViewModel.selectedFeedUri` using `snapshotFlow`. Tapping an external feed chip SHALL animate the pager to the selected feed's page index.

#### Scenario: User swipes to a new feed
- **WHEN** the user drags and releases the pager onto an adjacent feed page
- **THEN** `FeedHostViewModel` receives `SelectFeed` with the new feed's URI upon page settlement

#### Scenario: User taps a feed chip
- **WHEN** the user taps an inactive feed chip in the top chip row
- **THEN** the pager animates to the corresponding page index

### Requirement: Back navigation exits through home feed

The system SHALL intercept back navigation using `BackHandler` when the settled feed page index is greater than 0. Invoking the back gesture on any secondary feed SHALL animate the pager back to Page 0 (Following timeline).

#### Scenario: Back pressed on secondary feed
- **WHEN** the user is viewing Feed 1 (e.g. Discover) and triggers system back
- **THEN** the pager animates back to Page 0 (Following) and does not exit the app

#### Scenario: Back pressed on Page 0
- **WHEN** the user is viewing Page 0 (Following) and triggers system back
- **THEN** the feed `BackHandler` is inactive, allowing app-level navigation to handle the back event
