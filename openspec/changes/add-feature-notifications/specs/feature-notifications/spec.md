## ADDED Requirements

### Requirement: `:feature:notifications:api` exposes the `NotificationsTab` `NavKey`

A new module `:feature:notifications:api` SHALL expose a single `NotificationsTab` (or equivalently-named) `@Serializable data object NotificationsTab : NavKey`. It SHALL NOT depend on `:feature:notifications:impl`. Cross-feature modules that need to push the Notifications tab onto the back stack SHALL depend on `:feature:notifications:api` alone.

#### Scenario: Module isolation

- **WHEN** another `:feature:*` module references `NotificationsTab`
- **THEN** it MUST import from `:feature:notifications:api` only, never from `:feature:notifications:impl`

### Requirement: `:feature:notifications:impl` contributes a `@MainShell`-qualified `EntryProviderInstaller`

The `:feature:notifications:impl` Hilt module SHALL provide an `@IntoSet @MainShell EntryProviderInstaller` lambda that registers a `NavEntry<NotificationsTab>` rendering `NotificationsScreen()`. The provider SHALL be unqualified by `@OuterShell` (which is reserved for Login).

#### Scenario: Inner NavDisplay picks up the Notifications entry

- **WHEN** `MainShell` enters composition and the inner `NavDisplay`'s `entryProvider` lambda iterates over installers
- **THEN** the `NotificationsTab` entry SHALL be among the rendered entries

### Requirement: `NotificationsScreen` follows the project's MVI conventions

`NotificationsScreen` SHALL be split into a Root composable (hoists VM via `hiltViewModel()`, collects state, drains effects in a single outer `LaunchedEffect`) and a stateless Screen composable parameterized by `NotificationsState` and an `onEvent: (NotificationsEvent) -> Unit` callback. `NotificationsViewModel` SHALL extend `MviViewModel<NotificationsState, NotificationsEvent, NotificationsEffect>`.

#### Scenario: VM is hoisted at the Root composable

- **WHEN** `NotificationsScreen()` is rendered as a `NavEntry`'s content
- **THEN** the VM SHALL be obtained via `hiltViewModel()` at the Root composable only; the Screen composable SHALL accept state + event-callback parameters

### Requirement: `NotificationsState` is flat with a sealed `NotificationsLoadStatus` for lifecycle

`NotificationsState` SHALL expose, at minimum:

- `items: ImmutableList<NotificationItemUi>`
- `activeFilter: NotificationFilter` (default `All`)
- `loadStatus: NotificationsLoadStatus` (default `InitialLoading`)
- `cursor: String?`
- `hasMore: Boolean`

`NotificationsLoadStatus` SHALL be a `sealed interface` with mutually-exclusive variants `Idle`, `InitialLoading`, `Refreshing`, `Appending`, and `InitialError(error: NotificationsError)`. The state SHALL NOT wrap remote data in an `Async<T>` / `Result<T>` envelope.

#### Scenario: Refreshing and Appending are mutually exclusive

- **WHEN** the user pulls-to-refresh while a tail-append is in flight
- **THEN** the VM MUST resolve the conflict (cancel the append or queue the refresh) such that `loadStatus` only holds one of `Refreshing` or `Appending` at any instant

### Requirement: Pagination uses the SDK's `paginate` helper for `listNotifications`

The repository SHALL paginate `app.bsky.notification.listNotifications` using cursor + limit (default 50). On filter change, the cursor SHALL be reset to `null`. On tail append (`LoadMore` event), the existing cursor SHALL be passed. Pagination state SHALL be observable by the VM.

#### Scenario: Filter change resets pagination

- **WHEN** the user selects a filter chip other than the active one
- **THEN** the VM SHALL reset `cursor` to null, set `items` to empty, set `loadStatus` to `InitialLoading`, and request a fresh page with the new filter's `reasons[]` array

#### Scenario: Tail append preserves existing items

- **WHEN** the user scrolls to within N rows of the end of the list and `loadStatus == Idle && hasMore == true`
- **THEN** the VM SHALL fire `LoadMore`, set `loadStatus` to `Appending`, and on success append the new items to `items` while preserving order

### Requirement: Filter chips map to the lexicon's `reasons[]` parameter

A `NotificationFilter` enum SHALL define exactly the values `All`, `Mentions`, `Reposts`, `Follows`, `Likes`. Each filter SHALL map to a `List<String>?` of reason values:

- `All` → `null` (omit the `reasons` parameter)
- `Mentions` → `["mention", "reply", "quote"]`
- `Reposts` → `["repost", "repost-via-repost"]`
- `Follows` → `["follow"]`
- `Likes` → `["like", "like-via-repost"]`

#### Scenario: All filter omits the reasons parameter

- **WHEN** `activeFilter == All` and the VM requests a page
- **THEN** the `listNotifications` request MUST be constructed without the `reasons` parameter (or with `reasons = null`)

#### Scenario: Mentions filter sends three reason values

- **WHEN** `activeFilter == Mentions` and the VM requests a page
- **THEN** the `listNotifications` request MUST set `reasons = ["mention", "reply", "quote"]`

### Requirement: Notifications are aggregated client-side into single-actor and multi-actor rows

A `NotificationsMapper` SHALL group same-page `(reason, reasonSubject)` notifications into one `NotificationItemUi.Aggregated` row with `actors` populated by the union of contributors, ordered by `indexedAt` descending. Follows (where `reasonSubject` is null) SHALL aggregate by `(reason, sameCalendarDay)`. Single-event groups SHALL be rendered as `NotificationItemUi.Single`. Aggregation SHALL be page-scoped (no cross-page merging in this slice).

#### Scenario: Three likes of the same post collapse into one Aggregated row

- **WHEN** a page contains three `reason = "like"` notifications with the same `reasonSubject` AT-URI
- **THEN** the mapper SHALL emit one `NotificationItemUi.Aggregated` row with `actors.size == 3` and a single shared `subjectPost`

#### Scenario: Single-actor row uses the Single variant

- **WHEN** only one reply notification exists for a given `uri`
- **THEN** the mapper SHALL emit a `NotificationItemUi.Single` (not `Aggregated`)

### Requirement: Subject-post hydration uses a batched `getPosts` per page

For each page returned by `listNotifications`, the repository SHALL collect the set of unique post URIs (the `reasonSubject` for like / repost / like-via-repost / repost-via-repost; the `uri` for reply / quote / mention / subscribed-post). It SHALL issue at most one `getPosts(uris = …)` call per page (split into batches of 25 if the set exceeds the lexicon's cap). The hydrated `PostView`s SHALL be mapped to `PostUi` and attached as `subjectPost` on each `NotificationItemUi`.

#### Scenario: Unresolvable URI renders without preview

- **WHEN** a notification's subject URI is included in the `getPosts` call but the response omits it (deleted post)
- **THEN** the corresponding `NotificationItemUi` SHALL have `subjectPost == null` and the row SHALL still render

### Requirement: Mark-read fires on tab exit, not tab open

The VM SHALL emit `NotificationsEvent.TabExited` (or equivalent) when the screen Composable observes that `LocalMainShellNavState.current.topLevelKey` transitions away from `NotificationsTab`. On `TabExited`, the VM SHALL call `app.bsky.notification.updateSeen(seenAt = Clock.now())` and optimistically zero the unread-count store. The VM SHALL NOT call `updateSeen` on initial composition.

#### Scenario: Opening the tab does not mark-read

- **WHEN** the user taps the Notifications tab and the screen enters composition
- **THEN** `updateSeen` SHALL NOT be called and unread items SHALL render with their unread visual treatment

#### Scenario: Leaving the tab marks-read

- **WHEN** the user taps a different bottom-nav destination while the Notifications tab is active
- **THEN** the VM SHALL call `updateSeen(seenAt = Clock.now())` and the unread-count badge SHALL clear optimistically

### Requirement: Row taps deep-link to the source surface

`NotificationsEvent.RowTapped(item)` SHALL resolve a `NavKey` target by `reason`:

- `like`, `repost`, `like-via-repost`, `repost-via-repost`, `subscribed-post` → PostDetail derived from `reasonSubject` (AT-URI → DID + rkey)
- `reply`, `quote`, `mention` → PostDetail derived from the notification's `uri`
- `follow`, `contact-match`, `starterpack-joined` → Profile of the actor (`actor.did`)
- `verified`, `unverified` → Profile of the recipient (the session DID)
- Unknown reason → no effect emitted (debug-log only)

The VM SHALL emit `NotificationsEffect.NavigateTo(target)`. The screen Composable SHALL collect effects and call `LocalMainShellNavState.current.add(target)`. The VM SHALL NOT inject `MainShellNavState` or any navigator.

#### Scenario: Like row navigates to PostDetail

- **WHEN** a `like` row is tapped and `reasonSubject = at://did:plc:alice/app.bsky.feed.post/abc123`
- **THEN** the VM SHALL emit `NavigateTo` with a PostDetail key derived from that AT-URI

#### Scenario: Follow row navigates to Profile

- **WHEN** a `follow` row is tapped and the actor is `did:plc:bob`
- **THEN** the VM SHALL emit `NavigateTo(Profile(handle = "did:plc:bob"))`

#### Scenario: Unknown reason is a safe no-op

- **WHEN** a row with `reason = "future-reason-value-not-in-known-list"` is tapped
- **THEN** the VM SHALL emit no `NavigateTo` effect and the app SHALL NOT crash

### Requirement: Tab re-tap scrolls the list to the top

`NotificationsScreen` SHALL read `LocalTabReTapSignal` and collect emissions in a `LaunchedEffect`, calling `lazyListState.animateScrollToItem(0)` on each `Unit` emission. The VM SHALL NOT observe this signal.

#### Scenario: Re-tapping the Notifications tab scrolls to top

- **WHEN** the Notifications tab is the active top-level destination and the user taps the same bottom-nav item
- **THEN** the LazyColumn SHALL animate-scroll to item 0

### Requirement: Filter chip row renders 5 single-select chips above the LazyColumn

The screen SHALL render a horizontal-scrolling `LazyRow` of M3 `FilterChip`s above the LazyColumn, in order: `All`, `Mentions`, `Reposts`, `Follows`, `Likes`. Exactly one chip SHALL be selected at any time. Selecting a chip SHALL fire `NotificationsEvent.FilterSelected(filter)`.

#### Scenario: Selecting a chip swaps the selected state

- **WHEN** the user taps the `Mentions` chip while `All` is active
- **THEN** the `Mentions` chip SHALL render selected, `All` SHALL render unselected, and the VM SHALL receive `FilterSelected(NotificationFilter.Mentions)`

### Requirement: Unread rows render with a tonal background tint

`NotificationItemUi` rows where `isRead == false` SHALL render with the row background set to `colorScheme.surfaceContainerLow`; read rows SHALL use `colorScheme.surface`. The transition between unread and read groups MAY be marked with a "New" divider chip per the design.

#### Scenario: Unread row tint

- **WHEN** a row's `isRead == false`
- **THEN** its background tint SHALL be the `surfaceContainerLow` token; otherwise SHALL be `surface`

### Requirement: Empty state and initial-error state are explicit render branches

When the list is empty and `loadStatus == Idle`, the screen SHALL render the `:designsystem` empty-state composable with a notifications-specific caption. When `loadStatus == InitialError` and `items.isEmpty()`, the screen SHALL render a full-screen retry composable with a button that fires `NotificationsEvent.Refresh`.

#### Scenario: Empty state when API returns zero notifications

- **WHEN** the first page response has `notifications = []` and `loadStatus` settles to `Idle`
- **THEN** the screen SHALL render the empty-state composable, NOT a blank LazyColumn

#### Scenario: Initial error retry

- **WHEN** the initial load fails with a network error and `items.isEmpty()`
- **THEN** the screen SHALL render a retry button; pressing it SHALL fire `Refresh` and the VM SHALL re-issue the listNotifications call

### Requirement: Polling of `getUnreadCount` is app-foregrounded and lifecycle-aware

A Hilt-singleton `NotificationsUnreadCountStore` SHALL expose a `StateFlow<Int>` of the current unread count. A `ProcessLifecycleOwner`-scoped observer registered in `NubecitaApplication.onCreate` SHALL run `lifecycle.repeatOnLifecycle(STARTED) { while (isActive) { fetch(); delay(60.seconds) } }`. While the app is backgrounded (lifecycle below `STARTED`), polling MUST stop. Polling MUST be single-flight (overlapping requests skipped) and MUST apply exponential backoff on failure (60s → 120s → 240s → cap 300s, reset on success). The store MUST clear (set count to 0) on logout.

#### Scenario: Backgrounding stops polling

- **WHEN** the user backgrounds the app (Activity moves below STARTED)
- **THEN** any in-flight or queued `getUnreadCount` request SHALL be cancelled and no further polls SHALL fire until the app returns to foreground

#### Scenario: Foregrounding restarts polling

- **WHEN** the app returns to foreground after being backgrounded
- **THEN** an immediate `getUnreadCount` poll SHALL fire, followed by the 60-second cadence

#### Scenario: Logout clears the unread count

- **WHEN** the user logs out
- **THEN** the store's `StateFlow<Int>` SHALL emit `0` and the badge SHALL no longer render

### Requirement: Tap on aggregated row's chevron opens an actor-list sheet

For `NotificationItemUi.Aggregated` rows, tapping the chevron control (or the avatar stack) SHALL fire `NotificationsEvent.AvatarStackTapped(item)`. The VM SHALL emit `NotificationsEffect.ShowActorList(actors)` and the screen SHALL render a modal bottom sheet listing all actors with tappable profile rows.

#### Scenario: Aggregated chevron tap surfaces actor list

- **WHEN** an `Aggregated` row's chevron is tapped and `actors.size == 8`
- **THEN** the VM SHALL emit `ShowActorList(actors)` and a bottom sheet listing all 8 actors SHALL appear

#### Scenario: Single rows do not render a chevron

- **WHEN** a row's `NotificationItemUi` is the `Single` variant
- **THEN** the chevron control SHALL NOT be rendered

### Requirement: VM emits errors as a one-shot `ShowError` effect

Transient errors (refresh failure, append failure, mark-seen failure not silently swallowed) SHALL be surfaced via `NotificationsEffect.ShowError(message: UiText)` and rendered as a Snackbar in the screen's host Scaffold. The state SHALL NOT carry a sticky `errorMessage: String?` field.

#### Scenario: Refresh failure surfaces snackbar

- **WHEN** a pull-to-refresh request fails with a network error and the list already has items
- **THEN** the VM SHALL emit `ShowError(...)` and the screen SHALL render a Snackbar; `items` SHALL remain unchanged
