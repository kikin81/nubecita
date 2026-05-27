## MODIFIED Requirements

### Requirement: Top-level destination icons follow Material Symbols

The five top-level destinations SHALL use Material Symbols icons:

- Feed → `Home`
- Search → `Search`
- Notifications → `Notifications`
- Chats → `ChatBubbleOutline` (unselected) / `ChatBubble` (selected)
- You → `Person`

The selected state of each item SHALL be derived from `mainShellNavState.topLevelKey`. The Notifications icon SHALL be wrapped in a `BadgedBox` whose badge renders when the injected `NotificationsUnreadCountStore.unreadCount` `StateFlow<Int>` value is greater than zero.

#### Scenario: Selected tab icon reflects active top-level key

- **WHEN** `mainShellNavState.topLevelKey == Search`
- **THEN** the Search nav item SHALL be in selected state and the other four items SHALL be unselected

#### Scenario: Notifications icon shows badge when unread count is positive

- **WHEN** the injected unread-count StateFlow emits a value greater than zero
- **THEN** the Notifications nav item's icon SHALL be wrapped in a visible `BadgedBox` displaying the count

#### Scenario: Notifications icon hides badge when unread count is zero

- **WHEN** the injected unread-count StateFlow emits zero
- **THEN** the `BadgedBox` SHALL render without a visible badge

## ADDED Requirements

### Requirement: Notifications tab is registered as the 3rd top-level destination

`TopLevelDestinations` SHALL list exactly five entries in order: Feed (start), Search, Notifications, Chats, Profile. The Notifications entry's `NavKey` SHALL be `net.kikin.nubecita.feature.notifications.api.NotificationsTab`. The order is load-bearing: the inner `NavDisplay`'s flattened back stack uses Feed as the start route per "exit through home", and the navigation suite renders items in iteration order.

#### Scenario: Five-tab order

- **WHEN** `MainShellChrome` renders the navigation suite
- **THEN** the items SHALL appear in this exact order: Feed, Search, Notifications, Chats, You

### Requirement: `MainShell` injects `NotificationsUnreadCountStore` from Hilt for the badge

`MainShell` SHALL obtain the singleton `NotificationsUnreadCountStore` via the existing `NavigationEntryPoint` (or an additive `MainShellEntryPoint`) and read `unreadCount` as a `StateFlow<Int>`. The composable SHALL collect it via `collectAsStateWithLifecycle()` and pass the value to `MainShellChrome` so the chrome layer remains preview-friendly without standing up Hilt.

#### Scenario: Chrome is preview-friendly

- **WHEN** `MainShellChrome` is exercised in a preview or screenshot test
- **THEN** the unread count SHALL be supplied as a plain `Int` parameter — no Hilt entry point required to render the bar/rail
