## ADDED Requirements

### Requirement: `:feature:profile:impl` resolves the `Profile` and `Settings` NavKeys

The new `:feature:profile:impl` module SHALL apply the `nubecita.android.feature` convention plugin and SHALL provide `@Provides @IntoSet @MainShell` `EntryProviderInstaller` bindings for both `Profile(handle: String? = null)` and `Settings` `NavKey`s declared in `:feature:profile:api`. The `Profile` provider MUST resolve both `handle == null` (own profile) and `handle != null` (other user) variants through a single screen Composable that branches on the handle internally. The `:app`-side placeholder providers for `Profile(handle = null)` and `Settings` previously registered by `:app`'s `MainShellPlaceholderModule` MUST be removed in lockstep with the `:impl` providers being added.

#### Scenario: You tab activation renders the real profile screen

- **WHEN** the user activates the `You` top-level tab in `MainShell`
- **THEN** `MainShell` renders the `:feature:profile:impl` profile screen with `Profile(handle = null)`, not a placeholder

#### Scenario: Feed handle tap pushes the real profile screen

- **WHEN** the user taps an author handle inside a feed post and the Feed screen emits `NavigateToAuthor("alice.bsky.social")`, which is collected as `LocalMainShellNavState.current.add(Profile(handle = "alice.bsky.social"))`
- **THEN** `MainShell`'s inner `NavDisplay` resolves the entry to `:feature:profile:impl`'s `Profile` provider and renders the screen with `handle = "alice.bsky.social"`

#### Scenario: Settings sub-route renders the stub screen

- **WHEN** any caller pushes the `Settings` `NavKey` onto `LocalMainShellNavState`
- **THEN** `MainShell`'s inner `NavDisplay` resolves the entry to `:feature:profile:impl`'s `Settings` provider, which renders a one-screen stub identifying Settings as not yet implemented and exposing a Sign Out affordance

### Requirement: `ProfileScreenViewState` uses flat fields for independent flags and per-tab sealed `TabLoadStatus`

The `ProfileViewModel` SHALL extend `net.kikin.nubecita.ui.mvi.MviViewModel<ProfileScreenViewState, ProfileEvent, ProfileEffect>` and expose `ProfileScreenViewState` with: a nullable `header: ProfileHeaderUi?` (null while loading), a `selectedTab: ProfileTab` flat field with values `Posts | Replies | Media`, an `ownProfile: Boolean` flat field, a `viewerRelationship: ViewerRelationship` flat field with values `None | Self | Following | NotFollowing`, and three independent `TabLoadStatus` fields: `postsStatus`, `repliesStatus`, `mediaStatus`. The `TabLoadStatus` MUST be a sealed sum with variants `Idle`, `InitialLoading`, `Loaded(items: ImmutableList<TabItemUi>, isAppending: Boolean, isRefreshing: Boolean, hasMore: Boolean, cursor: String?)`, and `InitialError(error: ProfileError)`. The state MUST NOT model the three tabs' loading states as flat booleans — invalid combinations (e.g. `isLoadingPosts && hasPostsError`) MUST be unrepresentable.

#### Scenario: Posts tab refreshing leaves Replies and Media states untouched

- **WHEN** the user pulls to refresh while the Posts tab is selected and the ViewModel transitions `postsStatus` from `Loaded(...)` to `Loaded(..., isRefreshing = true)`
- **THEN** `repliesStatus` and `mediaStatus` in the emitted state are identical to their pre-refresh values; the header is untouched

#### Scenario: Initial profile open emits four concurrent loads

- **WHEN** the screen is composed for the first time for a given `Profile(handle)` NavKey
- **THEN** the ViewModel launches four concurrent coroutines from `viewModelScope`: one calling `app.bsky.actor.getProfile(handle)` for the header, and three calling `app.bsky.feed.getAuthorFeed(handle, filter = posts_no_replies | posts_with_replies | posts_with_media)` for the three tab bodies. Each updates its own state field independently as it completes or fails.

#### Scenario: Per-tab independent failure

- **WHEN** the header `getProfile` call succeeds, the Posts `getAuthorFeed` call succeeds, the Replies `getAuthorFeed` call fails with a network error, and the Media `getAuthorFeed` call succeeds
- **THEN** the emitted state has `header != null`, `postsStatus is Loaded`, `repliesStatus is InitialError`, `mediaStatus is Loaded`. No `ProfileEffect.ShowMessage` is emitted for the Replies failure — the per-tab `InitialError` carries the error for in-tab rendering.

### Requirement: Hero card renders bold-derived gradient with banner palette extraction

The hero card SHALL render the user's profile header inside a `Surface` whose backdrop is the gradient produced by `BoldHeroGradient` (from `:designsystem`). When `profile.banner` is present, `BoldHeroGradient` MUST extract a palette from the banner via `androidx.palette.graphics.Palette` off the main thread, cache the result keyed on the banner blob `cid`, and derive the gradient from the palette swatches. When `profile.banner` is absent, the gradient MUST be derived from `avatarHue` (a deterministic hue computed from the user's `did` plus the first character of `handle`). The hero MUST NOT render the banner image literally as the backdrop — neither at full opacity nor under a scrim. The hero MUST render the avatar (80–96 dp), the user's display name in Fraunces 600 with the `SOFT` variable axis set to 70, the handle in JetBrains Mono 13 sp, and the bio at 14.5 sp / 21 sp line-height with text-wrap pretty.

#### Scenario: User with a banner shows a palette-derived gradient

- **WHEN** the profile is rendered for a user whose `profile.banner` is a non-null blob with `cid = abc123`
- **THEN** `BoldHeroGradient` decodes the banner image, runs `Palette.from(bitmap).generate()` on `Dispatchers.Default`, caches the result keyed on `cid = abc123`, and renders the hero's gradient using the extracted swatches. The banner image is NOT visible anywhere in the rendered hero.

#### Scenario: User without a banner shows an avatarHue-derived gradient

- **WHEN** the profile is rendered for a user whose `profile.banner` is null
- **THEN** the hero gradient is derived from `avatarHue` (computed from `did` + first char of `handle`). No call to `Palette` occurs. The fallback gradient is deterministic — the same user always renders the same gradient.

#### Scenario: Repeat profile open uses the Palette cache

- **WHEN** the user navigates away from a profile with banner `cid = abc123` and returns to the same profile
- **THEN** `BoldHeroGradient` retrieves the cached Palette result for `cid = abc123` without re-decoding the banner image; the gradient renders synchronously on first composition with no flicker through the avatarHue fallback

### Requirement: Inline stats and meta row replace the chip variant

The hero card SHALL render the user's stats as an inline single-line label: `<postsCount> Posts · <followersCount> Followers · <followingCount> Following`, with numbers formatted via locale-aware short-scale abbreviation (e.g. `2.1k`, `1.4M`). The chip variant of the stats (shown in earlier design iterations) MUST NOT be present. The meta row SHALL render up to three optional rows in vertical order, each preceded by a 14 dp `NubecitaIcon`: link (when `profile.link` is non-null), location (when `profile.location` is non-null), and joined date (always present, formatted as `Joined <Month YYYY>`).

#### Scenario: Inline stats are rendered as a single line

- **WHEN** the hero is rendered for a user with 412 posts, 2,142 followers, and 342 following
- **THEN** the stats line reads `412 Posts · 2.1k Followers · 342 Following` and is a single horizontally-arranged row; no chip-style backgrounds, borders, or per-stat surfaces are present

#### Scenario: Meta row hides absent optional fields

- **WHEN** the hero is rendered for a user with no `profile.link` and no `profile.location`
- **THEN** the meta row contains only the joined-date entry; the link and location rows are not rendered (no placeholder, no empty row)

### Requirement: Actions row ships as stubs that emit `ProfileEffect.ShowMessage`

The actions row SHALL render the design's three buttons at full visual fidelity. For `ownProfile = true`, the row MUST render an Edit button and an overflow button. For `ownProfile = false`, the row MUST render a Follow button, a Message button, and an overflow button. Tapping any of Edit, Follow, or Message MUST cause the ViewModel to emit `ProfileEffect.ShowMessage(UiText.StringResource(R.string.profile_action_coming_soon))`. The screen Composable's effect collector MUST surface the message via the screen's `SnackbarHostState`. No write API MAY be invoked from any of the three handlers in this change.

#### Scenario: Follow tap surfaces a Coming Soon snackbar

- **WHEN** the user views another user's profile and taps the Follow button
- **THEN** `ProfileViewModel` receives `ProfileEvent.FollowTapped` and emits `ProfileEffect.ShowMessage` with the Coming Soon string resource; the screen's `SnackbarHostState` displays the snackbar; no network call is made; `viewerRelationship` in state is unchanged

#### Scenario: Edit tap on own profile surfaces a Coming Soon snackbar

- **WHEN** the user views their own profile and taps the Edit button
- **THEN** `ProfileViewModel` receives `ProfileEvent.EditTapped` and emits `ProfileEffect.ShowMessage` with the Coming Soon string resource; no navigation occurs

#### Scenario: Message button is hidden on own profile

- **WHEN** the user views their own profile (`ownProfile = true`)
- **THEN** the actions row does not render a Message button; only Edit and the overflow render

### Requirement: Three pill tabs render via `ProfilePillTabs`

The hero region SHALL render three pill-style tabs (`Posts`, `Replies`, `Media`) using the `ProfilePillTabs` composable from `:designsystem`. The active tab MUST have a `primary` container fill and the active tab's icon MUST render with the `FILL` variable axis at 1. The tabs MUST be sticky during body scroll — they MUST remain visible at the top of the scrollable region as the user scrolls the tab body. The tab order MUST be `Posts`, `Replies`, `Media` in left-to-right LTR order.

#### Scenario: Tab activation switches the body without re-fetching

- **WHEN** the user taps the Replies tab while Posts is currently active and both `postsStatus` and `repliesStatus` are already `Loaded(...)`
- **THEN** `ProfileViewModel` receives `ProfileEvent.TabSelected(Replies)`, emits state with `selectedTab = Replies`; no `getAuthorFeed` call is issued; the Posts body unmounts and the Replies body renders from the cached `repliesStatus.items`

#### Scenario: Tabs remain visible during body scroll

- **WHEN** the user scrolls the tab body downward past the height of the hero card
- **THEN** the pill tabs remain pinned at the top of the body region (they do NOT scroll out with the hero); the hero card itself scrolls away (no collapse animation; the hero leaves the viewport at body-scroll position equal to its rendered height)

### Requirement: Posts, Replies, and Media bodies render per-tab

The Posts tab body SHALL render a `LazyColumn` of `:designsystem.PostCard`s derived from `app.bsky.feed.getAuthorFeed(filter = posts_no_replies)`. The Replies tab body SHALL render a `LazyColumn` of `PostCard`s derived from `getAuthorFeed(filter = posts_with_replies)`. The Media tab body SHALL render a `LazyVerticalGrid(GridCells.Fixed(3))` of media thumbs derived from `getAuthorFeed(filter = posts_with_media)`, using the first image from each post's embed as the grid cell content. Each tab MUST handle its own pagination — when the user scrolls within `LOAD_MORE_PREFETCH_DISTANCE` items of the end of its `items` list and `hasMore == true`, the ViewModel SHALL receive a `ProfileEvent.LoadMore(tab)` and issue a `getAuthorFeed` call with the tab's current cursor.

#### Scenario: Media grid renders three columns

- **WHEN** the Media tab body renders with `mediaStatus = Loaded(items = [post1, post2, post3, post4])`
- **THEN** the body uses a `LazyVerticalGrid(GridCells.Fixed(3))`; the first row contains post1, post2, post3; the second row contains post4 alone in the first column; each cell renders the first image from the post's embed

#### Scenario: Load-more is per-tab

- **WHEN** the user scrolls to the end of the Posts tab body and `postsStatus.hasMore == true`
- **THEN** the ViewModel issues a `getAuthorFeed(filter = posts_no_replies, cursor = postsStatus.cursor)` call; `repliesStatus.cursor` and `mediaStatus.cursor` are unchanged; the appended items are merged into `postsStatus.items`

#### Scenario: Empty tab renders empty-state Composable

- **WHEN** the Media tab body renders with `mediaStatus = Loaded(items = [])` (a user with no media posts)
- **THEN** the body renders an empty-state Composable (not a blank `LazyVerticalGrid`) identifying that the user has no media posts; the empty state is selected per tab (Media-specific copy, distinct from Posts and Replies)

### Requirement: Post tap inside the profile body emits `NavigateToPost` and integrates with `ListDetailSceneStrategy`

Tapping a `PostCard` or media grid cell inside any tab body SHALL cause the ViewModel to emit `ProfileEffect.NavigateToPost(postUri: String)`. The screen Composable's effect collector MUST call `LocalMainShellNavState.current.add(PostDetailRoute(postUri))`. The profile screen's `@MainShell` entry provider MUST be registered with `androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy.listPane { … }` metadata so that on Medium / Expanded widths the strategy renders the profile in the list pane and the pushed `PostDetailRoute` in the detail pane. On Compact widths the strategy passes through, rendering the `PostDetailRoute` full-screen via the existing `PostDetail` provider.

#### Scenario: Compact post tap navigates full-screen

- **WHEN** the user is on a phone in portrait (Compact width), is viewing the Posts tab of `Profile(handle = "alice")`, and taps a post
- **THEN** `PostDetailRoute` is pushed onto `mainShellNavState`; the screen renders full-screen via `:feature:postdetail:impl`'s `@MainShell` provider; the profile screen is no longer visible

#### Scenario: Medium post tap renders detail in the right pane

- **WHEN** the user is on a tablet in portrait (Medium width, two-pane), is viewing the Posts tab of `Profile(handle = "alice")`, and taps a post
- **THEN** `PostDetailRoute` is pushed onto `mainShellNavState`; `ListDetailSceneStrategy` resolves the profile (tagged with `listPane{}` metadata) into the left pane and the `PostDetailRoute` into the right pane; both panes are visible simultaneously

### Requirement: Handle tap inside the profile body navigates cross-profile, with self-tap no-op

Tapping an author handle inside a `PostCard` body within any profile tab SHALL cause the ViewModel to emit `ProfileEffect.NavigateToProfile(handle: String)`. The screen Composable's effect collector MUST call `LocalMainShellNavState.current.add(Profile(handle = ...))`. When the tapped handle equals the currently-rendered profile's handle, the ViewModel MUST emit nothing (no `NavigateToProfile`, no `ShowMessage`) — the tap is silently consumed to prevent recursive self-navigation.

#### Scenario: Cross-handle tap pushes another profile onto the active tab's stack

- **WHEN** the user is viewing `Profile(handle = "alice")` and taps a `@bob.bsky.social` handle inside one of Alice's reposted posts
- **THEN** `Profile(handle = "bob.bsky.social")` is pushed onto `mainShellNavState`; the active tab's stack now contains `[..., Profile("alice"), Profile("bob.bsky.social")]`; back navigation returns to Alice's profile

#### Scenario: Self-handle tap is a no-op

- **WHEN** the user is viewing `Profile(handle = "alice")` and taps the `@alice.bsky.social` handle inside one of Alice's own reposted ancestor posts
- **THEN** no `NavigateToProfile` effect is emitted; no entry is pushed onto `mainShellNavState`; the screen state is unchanged

### Requirement: Settings stub clears the OAuthSession on Sign Out

The `Settings` `NavKey` SHALL resolve to a one-screen Composable rendered by `:feature:profile:impl` that identifies Settings as not yet implemented and exposes exactly one interactive affordance: a Sign Out button. Tapping Sign Out MUST invoke the existing logout pathway via `:core:auth`, which clears the persisted `OAuthSession` and triggers the outer `Navigator` to `replaceTo(Login)`. No real settings (theme, notifications, account, etc.) MAY ship in this stub.

#### Scenario: Settings stub renders Sign Out

- **WHEN** the user pushes the `Settings` `NavKey` (e.g., via an overflow menu entry from the profile screen)
- **THEN** the stub renders identifying text (e.g. "Settings — coming soon") and a Sign Out button; no other interactive elements are present

#### Scenario: Sign Out clears the session and routes to Login

- **WHEN** the user taps Sign Out on the Settings stub
- **THEN** `:core:auth`'s logout pathway is invoked; the persisted `OAuthSession` is cleared; the outer `Navigator` performs `replaceTo(Login)`; the user lands on the Login screen with no MainShell state retained

### Requirement: Screenshot-test contract covers hero, tabs, actions row, and adaptive layouts

`:feature:profile:impl/src/screenshotTest/` SHALL contain Compose screenshot tests covering at minimum the following fixtures, each at light and dark themes:

- Own profile hero with a banner (Palette-derived gradient)
- Own profile hero without a banner (avatarHue-derived gradient)
- Other-user profile hero (with banner)
- Posts tab body — loaded with at least one PostCard
- Replies tab body — loaded with at least one PostCard
- Media tab body — loaded with a 3×N grid
- Each tab in `InitialLoading` state
- Each tab in `InitialError` state
- Each tab in `Loaded(items = [])` empty state
- Actions row variants — own (Edit + overflow), other (Follow + Message + overflow)
- Settings stub screen

#### Scenario: Screenshot suite renders deterministically

- **WHEN** the screenshot test suite is run via `./gradlew :feature:profile:impl:validateDebugScreenshotTest`
- **THEN** all fixtures match their committed reference PNGs; any deliberate visual change MUST regenerate the affected baselines via `:feature:profile:impl:updateDebugScreenshotTest` and commit them in the same PR
