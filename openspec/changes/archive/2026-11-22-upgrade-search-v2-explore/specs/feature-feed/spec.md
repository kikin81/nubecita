# Spec Delta

## ADDED Requirements

### Requirement: A single-feed screen takes the detail-pane role on wide windows

The single-feed screen (a generator or list feed opened by its URI) SHALL take the detail-pane role in the main shell's list-detail layout. On Medium and Expanded widths it SHALL open beside the list that launched it; on Compact width it SHALL render full-screen as before. Posts opened from it SHALL stack above it in the same pane.

#### Scenario: Feed opened from a list on a wide window

- **WHEN** the window is Medium or Expanded width and the user opens a feed from the Search tab
- **THEN** the feed renders in the detail pane
- **AND** the Search list stays visible in the list pane

#### Scenario: Post opened from the feed

- **WHEN** the user taps a post inside a feed shown in the detail pane
- **THEN** the post detail replaces the feed in the same pane
- **AND** system back returns to the feed

#### Scenario: Compact width is unchanged

- **WHEN** the window is Compact width and the user opens a feed
- **THEN** the feed renders full-screen
