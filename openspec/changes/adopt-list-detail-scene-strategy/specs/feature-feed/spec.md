## ADDED Requirements

### Requirement: Feed entry registers `listPane{}` metadata in its `@MainShell` `EntryProviderInstaller`

The `@MainShell`-qualified `EntryProviderInstaller` provided by `:feature:feed:impl` for the `Feed` `NavKey` SHALL register the entry with `metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { FeedDetailPlaceholder() })`. `FeedDetailPlaceholder` SHALL be an `internal` Composable defined in `:feature:feed:impl`. The placeholder SHALL NOT be promoted to `:designsystem` until at least one additional list-pane host needs the same shape.

#### Scenario: Feed installer wraps entry with listPane metadata

- **WHEN** the `:feature:feed:impl` `@MainShell` installer is examined
- **THEN** the `entry<Feed>(…)` call SHALL include a `metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = …)` argument

#### Scenario: Placeholder lives in :feature:feed:impl, not :designsystem

- **WHEN** the source tree is searched for `FeedDetailPlaceholder`
- **THEN** the only definition site SHALL be inside `feature/feed/impl/src/main/`

### Requirement: `FeedDetailPlaceholder` displays a localized empty-state prompt

`FeedDetailPlaceholder` SHALL render a centered Composable consisting of, at minimum, a decorative icon and a textual prompt sourced from `R.string.feed_detail_placeholder_select` ("Select a post to read"). The prompt SHALL use a typography role no smaller than `MaterialTheme.typography.bodyLarge`. The icon SHALL declare `contentDescription = null` (decorative).

#### Scenario: Placeholder renders the localized prompt

- **WHEN** `FeedDetailPlaceholder()` is composed in a Compose-rule test
- **THEN** the rendered tree SHALL contain a node with text matching `Select a post to read` (or its locale-appropriate translation)

#### Scenario: Placeholder icon is decorative

- **WHEN** the source of `FeedDetailPlaceholder` is inspected
- **THEN** the contained `Icon` Composable SHALL pass `contentDescription = null`
