## ADDED Requirements

### Requirement: Detail navigation uses replaceTop semantics

The screen SHALL push detail targets from notification row taps and actor sheet selections using `mainShellNavState.replaceTop(...)`. On Medium/Expanded widths, tapping a notification SHALL replace the detail in place rather than stacking. On Compact width, `replaceTop` SHALL degrade to standard full-screen push.

#### Scenario: Notification tap replaces top of detail stack

- **WHEN** on Medium/Expanded width with `[Feed, NotificationsTab, PostDetail(p1)]` active, the user taps a notification targeting `PostDetail(p2)`
- **THEN** `mainShellNavState.backStack` becomes `[Feed, NotificationsTab, PostDetail(p2)]`, swapping the detail in place

#### Scenario: Back from detail restores placeholder

- **WHEN** on Medium/Expanded width with `[Feed, NotificationsTab, PostDetail(p)]` active, the user presses Back
- **THEN** `PostDetail(p)` is popped and the right pane restores `NotificationsDetailPlaceholder`

#### Scenario: Compact degrades to full-screen navigation

- **WHEN** on Compact width with `[Feed, NotificationsTab]` active, the user taps a notification
- **THEN** the target detail entry is pushed full-screen and system Back returns to the notifications list

### Requirement: Selection highlight is scoped to the NotificationsTab backstack segment

The notifications list SHALL determine the currently selected post URI or profile handle by inspecting only the backstack segment corresponding to the Notifications tab (`backStack.drop(backStack.lastIndexOf(NotificationsTab))`). Detail entries belonging to other tabs (such as the base `Feed` tab) SHALL NOT trigger selection highlighting on notification rows.

#### Scenario: Notification row highlights when matching top detail

- **WHEN** the top of the Notifications backstack segment is a `PostDetailRoute` matching a notification row's post URI
- **THEN** that notification row SHALL have `selected == true`

#### Scenario: Cross-tab stack entries do not trigger selection highlight

- **WHEN** the base backstack contains `PostDetailRoute` from the Feed tab, but no detail entry is pushed on `NotificationsTab`
- **THEN** no notification row SHALL have `selected == true`

## MODIFIED Requirements

### Requirement: `:feature:notifications:impl` contributes a `@MainShell`-qualified `EntryProviderInstaller`

The `:feature:notifications:impl` Hilt module SHALL provide an `@IntoSet @MainShell EntryProviderInstaller` lambda that registers a `NavEntry<NotificationsTab>` rendering `NotificationsScreen()` with `metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { NotificationsDetailPlaceholder() })`. The provider SHALL be unqualified by `@OuterShell` (which is reserved for Login).

#### Scenario: Inner NavDisplay picks up the Notifications entry

- **WHEN** `MainShell` enters composition and the inner `NavDisplay`'s `entryProvider` lambda iterates over installers
- **THEN** the `NotificationsTab` entry SHALL be among the rendered entries

#### Scenario: NotificationsTab registers listPane metadata with detail placeholder

- **WHEN** `NotificationsNavigationModule` installs `NotificationsTab` into the inner `NavDisplay`
- **THEN** the entry SHALL specify `ListDetailSceneStrategy.listPane` metadata supplying `NotificationsDetailPlaceholder`

### Requirement: Unread rows render with a tonal background tint

`NotificationItemUi` rows where `isRead == false` SHALL render with the row background set to `colorScheme.surfaceContainerLow`; read rows SHALL use `colorScheme.surface`. When a row corresponds to the currently active detail entry in list-detail mode (`selected == true`), the selected background SHALL override both read and unread backgrounds with `colorScheme.secondaryContainer`. The transition between unread and read groups MAY be marked with a "New" divider chip per the design.

#### Scenario: Unread row tint

- **WHEN** a row's `isRead == false` and `selected == false`
- **THEN** its background tint SHALL be the `surfaceContainerLow` token; otherwise SHALL be `surface`

#### Scenario: Selected row tint overrides unread tint

- **WHEN** a row's `selected == true`
- **THEN** its background tint SHALL be `colorScheme.secondaryContainer` regardless of `isRead`
