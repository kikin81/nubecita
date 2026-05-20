## ADDED Requirements

### Requirement: `:feature:moderation:impl` registers the `Report` sub-route as a `@MainShell` entry

`:feature:moderation:impl` SHALL provide a `@Provides @IntoSet @MainShell EntryProviderInstaller` binding for the `Report` `NavKey` declared in `:feature:moderation:api`. The provider SHALL render a Material 3 `ModalBottomSheet` hosting the report dialog content (the dialog Composable + its `ReportDialogViewModel` via `hiltViewModel()`). The Report entry MUST NOT carry `ListDetailSceneStrategy.listPane { }` or `detailPane { }` metadata — the Report sub-route is a transient overlay-style entry that the scene strategy may place in either pane on Medium / Expanded widths. `:app`'s `MainShellPlaceholderModule` MUST NOT register any placeholder for the `Report` NavKey (it was never a placeholder destination — Report is a sub-route, not a top-level tab).

#### Scenario: Report sub-route renders on push from any tab

- **WHEN** a feature module on any of the four top-level tabs (Feed / Search / Chats / Profile) pushes `Report(subject = ...)` onto `LocalMainShellNavState.current`
- **THEN** `MainShell`'s inner `NavDisplay` resolves the entry through `:feature:moderation:impl`'s `@MainShell` provider; no `:app`-side placeholder is consulted; the dialog renders as a Modal Bottom Sheet on top of the active tab's content

#### Scenario: Report entry has no list-detail pane metadata

- **WHEN** the `MainShell` `NavDisplay`'s `ListDetailSceneStrategy` inspects the Report entry on a Medium-width device
- **THEN** the entry SHALL NOT be tagged `listPane{}` and SHALL NOT be tagged `detailPane{}`; the strategy is free to render the Modal Bottom Sheet over either pane or the full viewport per its internal rules
