## MODIFIED Requirements

### Requirement: List-pane entries supply a detail placeholder Composable

Any `EntryProviderInstaller @MainShell` that registers an entry intended to act as the list pane in a list-detail scene SHALL pass `metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = …)` to the entry builder. The `detailPlaceholder` Composable SHALL render a non-empty visual surface that explains to the user what selecting a list item will reveal — at minimum, a textual prompt sourced from a string resource.

#### Scenario: Feed entry registers listPane metadata

- **WHEN** the `@MainShell`-qualified `EntryProviderInstaller` for the `Feed` `NavKey` is invoked at composition
- **THEN** the resulting entry SHALL carry `ListDetailSceneStrategy.listPane(...)` metadata, and the `detailPlaceholder` lambda SHALL render a non-empty Composable

#### Scenario: NotificationsTab entry registers listPane metadata

- **WHEN** the `@MainShell`-qualified `EntryProviderInstaller` for the `NotificationsTab` `NavKey` is invoked at composition
- **THEN** the resulting entry SHALL carry `ListDetailSceneStrategy.listPane(...)` metadata, and the `detailPlaceholder` lambda SHALL render a non-empty Composable

#### Scenario: Compact width does not compose the detail placeholder

- **WHEN** the device window's `windowWidthSizeClass` is `Compact` and the back stack is `[NotificationsTab]`
- **THEN** the rendered tree SHALL NOT contain the detail placeholder Composable's content (the strategy collapses to single-pane on compact)

#### Scenario: Medium and expanded widths compose the detail placeholder

- **WHEN** the device window's `windowWidthSizeClass` is `Medium` or `Expanded` and the back stack is `[NotificationsTab]`
- **THEN** the rendered tree SHALL contain a `NotificationsDetailPlaceholder` Composable adjacent to the Notifications list pane
