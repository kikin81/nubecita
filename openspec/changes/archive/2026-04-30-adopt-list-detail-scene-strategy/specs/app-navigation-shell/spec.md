## ADDED Requirements

### Requirement: Inner `NavDisplay` applies a `ListDetailSceneStrategy`

`MainShell`'s inner `NavDisplay` SHALL be invoked with a non-empty `sceneStrategies` list whose first element is a `rememberListDetailSceneStrategy<NavKey>()` instance from `androidx.compose.material3.adaptive.navigation3`. The strategy SHALL be the only scene strategy supplied; additional strategies are out of scope for this change. The outer `NavDisplay` in `app/Navigation.kt` SHALL NOT be given any scene strategy.

#### Scenario: Inner NavDisplay receives ListDetailSceneStrategy

- **WHEN** `MainShell()` is composed
- **THEN** the inner `NavDisplay` SHALL be called with a `sceneStrategies` parameter containing exactly one `ListDetailSceneStrategy`-typed element

#### Scenario: Outer NavDisplay does not receive ListDetailSceneStrategy

- **WHEN** `MainNavigation()` (in `app/Navigation.kt`) is composed
- **THEN** the outer `NavDisplay` SHALL NOT pass `sceneStrategies`, or SHALL pass an empty list

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
