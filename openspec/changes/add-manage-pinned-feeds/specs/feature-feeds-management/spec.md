# feature-feeds-management

## ADDED Requirements

### Requirement: Manage-feeds screen is the Feeds destination
The system SHALL render a pinned-feeds management screen (`ManageFeedsScreen`, `:feature:feeds:impl`) as the `@MainShell` destination for the `Feeds` `NavKey`, replacing the placeholder. The screen SHALL be reachable from the existing `[＋]` action on the Feeds-home pill row without any additional navigation wiring.

#### Scenario: Opening the manage screen from the Feeds pill row
- **WHEN** the user taps the `[＋]` action at the end of the Feeds-home pill row
- **THEN** the manage-feeds screen opens as a `@MainShell` sub-route and lists the user's pinned feeds

#### Scenario: Placeholder is gone
- **WHEN** the `Feeds` route is resolved
- **THEN** the real `:feature:feeds:impl` screen is shown and no `Manage feeds — coming soon` placeholder is registered

### Requirement: Pinned feeds are listed in server order
The screen SHALL display the user's pinned feeds in the order returned by `observePinnedFeeds()` (server/`position` order). While loading it SHALL show a loading state; once loaded it SHALL show the populated list. No empty state is required because the Following timeline is non-removable and the repository falls back to a default pinned set.

#### Scenario: Loading then populated
- **WHEN** the screen is first opened and the pinned feeds are still being read
- **THEN** a loading state is shown, and it is replaced by the ordered list once the feeds are available

### Requirement: Drag-to-reorder pinned feeds
The screen SHALL let the user reorder pinned feeds by dragging a per-row drag handle. The drag interaction SHALL raise the lifted row's tonal elevation and emit haptic feedback on pickup and on drop. The reordered order is held as local UI state and feels instant. Swipe-to-dismiss SHALL NOT be used (it conflicts with the vertical drag gesture).

#### Scenario: Reordering a feed
- **WHEN** the user long-presses a row's drag handle and drags it to a new position
- **THEN** the row lifts with raised elevation and haptic feedback, and the list reflects the new order immediately

#### Scenario: The Following timeline can be reordered
- **WHEN** the user drags the Following timeline row to a different position
- **THEN** the reorder is allowed and reflected in the list

### Requirement: Non-destructive remove, Following locked
Each row except the Following timeline SHALL show a trailing remove action that unpins the feed non-destructively (the feed remains saved in AT Proto preferences). The Following timeline row (`kind == FeedKind.Following`) SHALL omit the remove action so the user can never orphan themselves from the main network view.

#### Scenario: Removing a custom feed
- **WHEN** the user taps the remove action on a custom pinned feed
- **THEN** the feed is unpinned (removed from the list) but remains saved in the user's AT Proto preferences

#### Scenario: Following cannot be removed
- **WHEN** the Following timeline row is displayed
- **THEN** it has no remove action and cannot be unpinned

### Requirement: Accessible reordering and removal
Each row SHALL expose custom accessibility actions so screen-reader (TalkBack) users can operate the screen without the visual drag gesture: `Move up`, `Move down`, and `Remove` (the last omitted for the Following timeline).

#### Scenario: TalkBack reorders without dragging
- **WHEN** a TalkBack user activates the `Move up` or `Move down` action on a row
- **THEN** the feed moves accordingly in the list

#### Scenario: TalkBack remove is unavailable for Following
- **WHEN** a TalkBack user focuses the Following timeline row
- **THEN** no `Remove` action is offered
