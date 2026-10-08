## ADDED Requirements

### Requirement: A `Feeds` navigation stub exists for the deferred management screen

The system SHALL provide a `:feature:feeds:api` module exposing a single `Feeds` `NavKey` (`@Serializable data object Feeds : NavKey`), and `:app` SHALL register a `@MainShell` `EntryProviderInstaller` rendering a placeholder Composable for that key. The placeholder MUST follow the `:api`-first stub convention. The Feed chip row trailing button MUST navigate via `LocalMainShellNavState.current.add(Feeds)`.

#### Scenario: Trailing button opens the placeholder

- **WHEN** the user taps the trailing button at the end of the feed chip row
- **THEN** `Feeds` is pushed onto the `MainShell` back stack and the placeholder
  "coming soon" screen renders inside the inner `NavDisplay`

#### Scenario: Stub carries no screen logic

- **WHEN** `:feature:feeds:api` is inspected
- **THEN** it contains only the `Feeds` `NavKey` type (no screens, ViewModels, or Hilt
  modules), and the placeholder Composable lives in `:app` under a `@MainShell` provider
