# Profile feature — Bead D: own-profile screen + Posts tab body — design

**Bead ID:** `nubecita-s6p.4` (Bead D of the Profile epic `nubecita-s6p`)
**Branch:** `feat/nubecita-s6p.4-feature-profile-impl-own-profile-screen-posts-tab`
**Predecessor decisions:** `openspec/changes/add-profile-feature/design.md` (Decisions 1–8) — locked
**Predecessor requirements:** `openspec/changes/add-profile-feature/specs/feature-profile/spec.md` — locked

## Scope

This bead implements the **own-profile** variant of the Profile screen with the **Posts tab body** functional and the Replies / Media tabs rendering placeholders. After this bead lands:

- `Profile(handle = null)` resolves to a real `:feature:profile:impl` screen (no more `:app` placeholder for that route).
- Other-user (`Profile(handle = "alice…")`) navigation also reaches the same screen — the actions-row branch for `ownProfile = false` is wired in Bead F, but the screen itself does not crash if a non-null handle is pushed (it just renders with the Edit-variant actions row, which is harmless and gets corrected in Bead F).
- The Posts tab body is fully functional: loads on profile open, paginates via `LazyListState` end-prefetch, surfaces empty / loading / error states, and supports pull-to-refresh.
- The Replies and Media pill tabs are selectable; tapping either renders a themed "tab arrives soon" placeholder that ships its real body in Bead E.

### Out of scope (explicitly deferred)

| Excluded | Where it lands |
|---|---|
| Real Replies / Media tab bodies | Bead E (`nubecita-s6p.5`) |
| Other-user actions row (Follow + Message + overflow) | Bead F (`nubecita-s6p.6`) |
| `ListDetailSceneStrategy.listPane{}` metadata on the `@MainShell` provider | Bead F |
| Settings overflow entry from profile | Bead F |
| Settings stub Composable | Bead F |
| `:app` Settings placeholder removal | Bead F |
| Medium-width screenshot fixtures | Bead F |
| Scroll-collapsing hero / `TopAppBarScrollBehavior` | Follow-up epic |
| Real Follow / Edit / Message writes | Follow-up epics |

## Architecture

### File layout

```
feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/
├── ProfileScreen.kt                # stateful: hiltViewModel + effect collector + nav callbacks
├── ProfileScreenContent.kt         # stateless: ProfileScreenViewState in, Scaffold + LazyColumn out
├── ProfileTabBody.kt               # LazyListScope extension dispatch (Posts/Replies/Media)
└── ui/
    ├── ProfileHero.kt              # orchestrator: backdrop + identity + bio + stats + meta + actions
    ├── ProfileStatsRow.kt          # "412 Posts · 2.1k Followers · 342 Following"
    ├── ProfileMetaRow.kt           # 3 conditional rows: link / location / joined
    ├── ProfileActionsRow.kt        # own variant only in Bead D (Edit + overflow)
    ├── ProfilePostsTabBody.kt      # LazyListScope extension for the Posts items + appending row
    ├── ProfileEmptyState.kt        # tab-level empty (e.g., "No posts yet")
    ├── ProfileErrorState.kt        # tab-level error + Retry
    ├── ProfileLoadingState.kt      # tab-level shimmer skeletons (reuses PostCardShimmer)
    └── ProfileTabPlaceholder.kt    # "Replies tab arrives soon" / "Media tab arrives soon"

feature/profile/impl/src/main/res/values/strings.xml
    + ~10 new keys: action labels, snackbar messages, empty / error copy, content descriptions
```

**Rationale:** per-row file decomposition mirrors `:feature:feed:impl/ui/` (where `FeedAppendingIndicator`, `FeedEmptyState`, `FeedErrorState`, `PostCardVideoEmbed` are each their own file). Per-row files give the screenshot-test surface natural isolation — the spec mandates a per-fixture screenshot for the actions row, the empty states, and the error states, and the spec's per-tab empty-state copy means `ProfileEmptyState` accepts a tab parameter to pick copy.

### State flow

The screen consumes Bead C's `ProfileScreenViewState` **directly**. No `toViewState()` projection layer.

```
ProfileViewModel.uiState : StateFlow<ProfileScreenViewState>
       │
       ▼
ProfileScreen (stateful)
       │ state by viewModel.uiState.collectAsStateWithLifecycle()
       ▼
ProfileScreenContent(state, callbacks, …)
```

**Why no projection:** Bead C's `ProfileScreenViewState` was designed UI-first (flat fields + per-tab sealed `TabLoadStatus` sums; all list-typed fields are `ImmutableList`). The Feed module's `FeedScreenViewState.toViewState()` projection exists because Feed's MVI state carries reducer concerns (e.g., paginate-while-refreshing state machines) that the UI doesn't read. Profile's contract has no such concerns — every field on `ProfileScreenViewState` is consumed by the UI as-is. Adding a projection layer would be a pure no-op and would obscure that the contract was deliberately UI-shaped.

### Layout structure — one LazyColumn for the whole screen

```kotlin
Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
    PullToRefreshBox(isRefreshing = activeTabIsRefreshing, onRefresh = onRefresh) {
        LazyColumn(state = listState, contentPadding = padding) {
            item("hero") {
                ProfileHero(
                    header = state.header,
                    headerError = state.headerError,
                    ownProfile = state.ownProfile,
                    onRetryHeader = onRetryHeader,
                    onEditTap = { onEvent(ProfileEvent.EditTapped) },
                    onOverflowTap = { /* Bead F wires Settings */ },
                )
            }
            stickyHeader("tabs") {
                ProfilePillTabs(
                    tabs = ProfileTabs,
                    selectedTab = state.selectedTab.toPillTab(),
                    onTabSelect = { onEvent(ProfileEvent.TabSelected(it.toProfileTab())) },
                )
            }
            when (state.selectedTab) {
                ProfileTab.Posts -> profilePostsTabBody(
                    status = state.postsStatus,
                    callbacks = postCallbacks,
                    onLoadMore = { onEvent(ProfileEvent.LoadMore(ProfileTab.Posts)) },
                    onRetry = { /* retry posts initial load */ },
                )
                ProfileTab.Replies -> item("replies-placeholder") {
                    ProfileTabPlaceholder(tab = ProfileTab.Replies)
                }
                ProfileTab.Media -> item("media-placeholder") {
                    ProfileTabPlaceholder(tab = ProfileTab.Media)
                }
            }
        }
    }
}
```

**Key shape decision:** `profilePostsTabBody` is a `LazyListScope.(…) -> Unit` extension, **not a Composable that opens its own LazyColumn**. This is the only structure that makes "sticky tabs + hero scrolls away with the body" work without nesting scroll containers. Nested `LazyColumn`s break sticky-header behavior and produce two competing scroll states.

The same applies to the appending indicator (`item("appending") { ProfileAppendingIndicator() }`) and the per-tab states — empty/loading/error all render as `item { … }` entries inside the outer LazyColumn, not as full-screen Composables.

### Hero composition (`ProfileHero.kt`)

```
ProfileHero column:
├── BoldHeroGradient(banner = header?.bannerUrl, avatarHue = header?.avatarHue ?: 0) {
│      Column(centered) {
│          AvatarRing(avatarUrl = header?.avatarUrl)  // 88dp avatar, 4dp surface ring, elevation 2
│          Text(displayName, style = displayNameStyle)  // Fraunces 600 SOFT=70
│          Text("@$handle", style = handleStyle)        // JetBrains Mono 13sp
│      }
│   }
├── Spacer(8.dp)
├── Text(bio, textWrap = Pretty)                        // hidden when bio == null
├── ProfileStatsRow(postsCount, followersCount, followsCount)
├── ProfileMetaRow(website, location, joinedDisplay)    // each row conditional on non-null
└── ProfileActionsRow(ownProfile = state.ownProfile, onEdit = …, onOverflow = …)
```

**Three header lifecycle branches inside `ProfileHero`:**

1. `header != null` (any value of `headerError`) → render the hero as above. A non-null `headerError` triggers a snackbar via `ProfileEffect.ShowError` collected at the screen level — it does NOT change the hero's visual rendering. (Per the contract: `headerError` is sticky-once flat state; the snackbar is the user-visible signal.)
2. `header == null && headerError == null` → loading skeleton (greyed avatar circle + 2 greyed text bars + greyed stats row).
3. `header == null && headerError != null` → inline error placeholder with a Retry affordance that dispatches `ProfileEvent.Refresh` (which re-fetches both header + active tab — matches Bead C's `onRefresh` semantics).

### Pull-to-refresh wiring

`PullToRefreshBox` wraps the whole LazyColumn. The `isRefreshing` boolean is derived from the **active tab's** `TabLoadStatus.Loaded.isRefreshing` (or `false` if the active tab is in any other status). Pulling triggers `ProfileEvent.Refresh`, which the ViewModel already routes to `launchHeaderLoad` + `launchTabRefresh(activeTab)` — per Bead C's contract.

### Pagination wiring

```kotlin
LaunchedEffect(listState) {
    snapshotFlow {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        // Calculate threshold against the Posts items range (not the full LazyColumn —
        // hero + sticky tabs + items contribute to the total index, so use a
        // computed offset based on the items count we contributed).
        lastVisible > totalItemCount - PREFETCH_DISTANCE
    }
        .distinctUntilChanged()
        .collect { pastThreshold ->
            if (pastThreshold && state.selectedTab == ProfileTab.Posts) {
                onEvent(ProfileEvent.LoadMore(ProfileTab.Posts))
            }
        }
}

private const val PREFETCH_DISTANCE = 5
```

The name `PREFETCH_DISTANCE` is mandated by the spec ("the numeric value MAY differ across features but the name MUST match"). The value matches Feed's `5` — same scroll feel target.

### Effect collector (single `LaunchedEffect(Unit)` in `ProfileScreen`)

Mirrors Feed's pattern:

| Effect | Action |
|---|---|
| `ProfileEffect.ShowError(error)` | `snackbarHostState.currentSnackbarData?.dismiss()` + `showSnackbar(toMessage(error))` |
| `ProfileEffect.ShowComingSoon(action)` | Replace + show per-action snackbar copy (Edit / Follow / Message — Edit only used in Bead D) |
| `ProfileEffect.NavigateToPost(uri)` | `onNavigateToPost(uri)` host callback |
| `ProfileEffect.NavigateToProfile(handle)` | `onNavigateToProfile(handle)` host callback |
| `ProfileEffect.NavigateToSettings` | no-op in Bead D (Settings overflow doesn't ship until Bead F) |

Strings resolved via `stringResource(R.string.…)` at composition time so locale + theme participate in recomposition (per Feed pattern; avoids the `LocalContextGetResourceValueCall` lint).

### Nav module update (`ProfileNavigationModule.kt`)

Replace the Bead C inert `provideProfileEntries` body (empty lambda) with the real entry block:

```kotlin
@Provides
@IntoSet
@MainShell
fun provideProfileEntries(): EntryProviderInstaller = {
    entry<Profile> { route ->
        val navState = LocalMainShellNavState.current
        val vm = hiltViewModel<ProfileViewModel, ProfileViewModel.Factory>(
            creationCallback = { it.create(route) },
        )
        ProfileScreen(
            viewModel = vm,
            onNavigateToPost = { uri -> navState.add(PostDetailRoute(postUri = uri)) },
            onNavigateToProfile = { handle -> navState.add(Profile(handle = handle)) },
        )
    }
}
```

**`ListDetailSceneStrategy.listPane{}` metadata is NOT added in Bead D.** That metadata is what makes Medium-width post-taps land in the right pane — Bead F wires it. In Bead D, post-taps on Medium-width devices push `PostDetailRoute` onto the back stack and the post-detail screen replaces the profile screen full-screen (the existing `:feature:postdetail:impl` provider has its own listpane metadata, but without ours the strategy treats the profile as a full-pane entry).

The `provideSettingsEntries` provider stays inert in Bead D — Bead F wires the Settings stub.

### `:app` placeholder cleanup

`app/src/main/java/net/kikin/nubecita/shell/MainShellPlaceholderModule.kt`:

- **Delete** `provideProfilePlaceholderEntries`. The `Profile` and `Settings` entries currently live in the same provider block (one `EntryProviderInstaller` lambda registers both); split them so Settings can stay until Bead F. After the split, rename the function to reflect Settings-only ownership.

After Bead D:
```kotlin
@Module @InstallIn(SingletonComponent::class)
internal object MainShellPlaceholderModule {
    @Provides @IntoSet @MainShell fun provideSearchPlaceholderEntries(): EntryProviderInstaller = { entry<Search> { … } }
    @Provides @IntoSet @MainShell fun provideChatsPlaceholderEntries(): EntryProviderInstaller = { entry<Chats> { … } }
    @Provides @IntoSet @MainShell fun provideSettingsPlaceholderEntries(): EntryProviderInstaller = { entry<Settings> { … } }
}
```

The `provideProfilePlaceholderEntries` function and its `entry<Profile>` block are removed entirely.

### `:app` test fixture update

`app/src/androidTest/java/net/kikin/nubecita/shell/MainShellPersistenceTest.kt:80` references `Profile(handle = null)` as a top-level route — that line is unchanged (the route still exists; the provider just moved from `:app` to `:feature:profile:impl`).

## Sub-Composable contracts

### `ProfileStatsRow.kt`

```kotlin
@Composable
internal fun ProfileStatsRow(
    postsCount: Long,
    followersCount: Long,
    followsCount: Long,
    modifier: Modifier = Modifier,
)
```

Renders a single-line Row of three labels separated by `·`. Numbers formatted via `java.text.CompactDecimalFormat` (locale-aware short-scale; e.g. `412`, `2.1K`, `1.4M`). No chip variant — spec explicitly forbids it.

### `ProfileMetaRow.kt`

```kotlin
@Composable
internal fun ProfileMetaRow(
    website: String?,
    location: String?,
    joinedDisplay: String?,
    modifier: Modifier = Modifier,
)
```

Renders a Column of up to 3 rows. Each row is a `NubecitaIcon` (14dp) + `Text` pair. Rows whose data is null are not rendered (no placeholder, no spacing). If all three are null, the Column renders nothing (zero height).

Icons: `NubecitaIconName.Link` / `Location` / `Calendar` (or equivalent — names mapped during implementation if not present in the catalog yet).

### `ProfileActionsRow.kt`

Bead D only renders the **own variant**:

```kotlin
@Composable
internal fun ProfileActionsRow(
    ownProfile: Boolean,           // accepts both for type completeness; Bead D only exercises ownProfile = true
    onEdit: () -> Unit,
    onOverflow: () -> Unit,
    modifier: Modifier = Modifier,
)
```

When `ownProfile = true`: Row of [Edit button, overflow icon]. When `ownProfile = false`: Bead D renders the same Edit + overflow row as a defensive default — Bead F replaces this branch with the Follow + Message + overflow row. The screen does not crash on other-user profiles in Bead D; it just shows the wrong actions row (acceptable since other-user navigation isn't a primary entry point in Bead D's scope).

### `ProfilePostsTabBody.kt`

```kotlin
internal fun LazyListScope.profilePostsTabBody(
    status: TabLoadStatus,
    postCallbacks: PostCallbacks,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
)
```

Branches on `status`:
- `Idle` → `item { ProfileLoadingState() }` (shouldn't normally hit — VM kicks loads in init; defensive)
- `InitialLoading` → `item { ProfileLoadingState() }` (shimmer skeletons)
- `InitialError(err)` → `item { ProfileErrorState(err, onRetry) }`
- `Loaded(items, isAppending, _, hasMore, _)` →
  - if `items.isEmpty()`: `item { ProfileEmptyState(tab = Posts) }`
  - else: `items(items, key = { it.postUri }, contentType = { "post" }) { item -> PostCard(post = item.toPostUi(), callbacks = postCallbacks) }`
  - if `isAppending`: `item("appending") { ProfileAppendingIndicator() }`

`TabItemUi.Post` carries a `PostUi` directly — no projection needed. `MediaCell` is filtered out (Bead D never produces them for the Posts tab — `posts_no_replies` filter returns posts not media-cells; the type is unused here).

### `ProfileEmptyState.kt`

```kotlin
@Composable
internal fun ProfileEmptyState(
    tab: ProfileTab,
    modifier: Modifier = Modifier,
)
```

Per-tab copy via stringResource:
- Posts: "No posts yet" / "When @{handle} posts, you'll see it here."  *(Bead D)*
- Replies: "No replies yet" / "When @{handle} replies, you'll see it here."  *(Bead E — but the Composable accepts the tab now to avoid Bead E having to change the signature)*
- Media: "No media yet" / "When @{handle} posts media, you'll see it here."  *(Bead E)*

### `ProfileErrorState.kt`

```kotlin
@Composable
internal fun ProfileErrorState(
    error: ProfileError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
)
```

Per-error copy:
- `ProfileError.Network` → "Can't reach Bluesky" / Retry button
- `ProfileError.Unknown(_)` → "Something went wrong" / Retry button

Tap on Retry dispatches the appropriate retry event — for tab-level errors that's a new `ProfileEvent.RetryTab(tab: ProfileTab)` event (Bead C doesn't expose this; this bead adds it). For header errors that's `ProfileEvent.Refresh`.

**Spec note:** Bead C's contract has `Refresh` but not `RetryTab`. Adding `RetryTab(ProfileTab)` is a contract extension — purely additive, no removal. The ViewModel's handler launches `launchInitialTabLoad(actor, tab)` for the targeted tab.

### `ProfileLoadingState.kt`

`item { Column { repeat(5) { PostCardShimmer() } } }` — reuses the existing `:designsystem.PostCardShimmer`. No new shimmer Composable.

### `ProfileTabPlaceholder.kt`

```kotlin
@Composable
internal fun ProfileTabPlaceholder(
    tab: ProfileTab,  // Replies or Media; Posts shouldn't reach here
    modifier: Modifier = Modifier,
)
```

Themed empty-state shape: icon + headline + body text. Copy:
- Replies: "Replies tab arrives soon" / "Coming in the next release."
- Media: "Media tab arrives soon" / "Coming in the next release."

When Bead E lands, this Composable is deleted; its callers (`ProfileScreenContent`'s `when (selectedTab)` block) get the real `repliesTabBody` / `mediaTabBody` LazyListScope extensions wired in its place.

## Contract additions

Bead D adds **one** event to Bead C's `ProfileEvent`:

```kotlin
sealed interface ProfileEvent : UiEvent {
    // ... existing events from Bead C ...
    data class RetryTab(val tab: ProfileTab) : ProfileEvent
}
```

ViewModel handler:
```kotlin
private fun onRetryTab(tab: ProfileTab) {
    val actor = resolveActor() ?: return
    launchInitialTabLoad(actor, tab)
}
```

This is additive — no Bead C behavior changes. The motivation is the per-tab Retry button in `ProfileErrorState` (the screen needs a way to ask the VM to redo a single tab's initial load without refreshing the header).

## String resources (new in Bead D)

```xml
<!-- feature/profile/impl/src/main/res/values/strings.xml -->
<resources>
    <!-- Header / actions -->
    <string name="profile_action_edit">Edit profile</string>
    <string name="profile_action_overflow">More options</string>
    <string name="profile_action_coming_soon">%1$s — coming soon</string>

    <!-- Stats / meta -->
    <string name="profile_stats_posts_label">Posts</string>
    <string name="profile_stats_followers_label">Followers</string>
    <string name="profile_stats_follows_label">Following</string>
    <string name="profile_meta_link_content_description">Website</string>
    <string name="profile_meta_location_content_description">Location</string>
    <string name="profile_meta_joined_content_description">Joined date</string>

    <!-- Posts tab -->
    <string name="profile_posts_empty_title">No posts yet</string>
    <string name="profile_posts_empty_body">When this user posts, you\'ll see it here.</string>
    <string name="profile_replies_empty_title">No replies yet</string>
    <string name="profile_replies_empty_body">When this user replies, you\'ll see it here.</string>
    <string name="profile_media_empty_title">No media yet</string>
    <string name="profile_media_empty_body">When this user posts media, you\'ll see it here.</string>

    <!-- Errors / snackbars -->
    <string name="profile_error_network_title">Can\'t reach Bluesky</string>
    <string name="profile_error_unknown_title">Something went wrong</string>
    <string name="profile_error_retry">Try again</string>
    <string name="profile_snackbar_error_network">Network error. Pull to refresh.</string>
    <string name="profile_snackbar_error_unknown">Unable to load profile.</string>

    <!-- Tab placeholders -->
    <string name="profile_tab_placeholder_replies_title">Replies tab arrives soon</string>
    <string name="profile_tab_placeholder_media_title">Media tab arrives soon</string>
    <string name="profile_tab_placeholder_body">Coming in the next release.</string>

    <!-- Hero accessibility -->
    <string name="profile_avatar_content_description">Profile photo of %1$s</string>
</resources>
```

## Testing strategy

### Compose previews (drive screenshot fixtures)

Each fixture uses paired `@Preview` annotations (one default, one with `uiMode = Configuration.UI_MODE_NIGHT_YES`) — same convention as `:feature:feed:impl/FeedScreen.kt`. A fixed clock (`LocalClock provides PreviewClock`) keeps relative-time labels in PostCards deterministic. All previews invoke `ProfileScreenContent` with hand-crafted `ProfileScreenViewState` fixtures.

Fixture matrix (8 previews × 2 themes = 16 baselines):

1. `ProfileScreenHeroWithBannerPreview` — `header != null`, banner URL set, all tabs `Idle` (or first one loading skeleton)
2. `ProfileScreenHeroWithoutBannerPreview` — `header != null`, `bannerUrl = null` (avatarHue gradient)
3. `ProfileScreenHeroLoadingPreview` — `header == null`, no error
4. `ProfileScreenHeroErrorPreview` — `header == null`, `headerError = Network`
5. `ProfileScreenPostsTabLoadedPreview` — `postsStatus = Loaded(items = [3-5 fixture posts])`
6. `ProfileScreenPostsTabLoadingPreview` — `postsStatus = InitialLoading`
7. `ProfileScreenPostsTabEmptyPreview` — `postsStatus = Loaded(items = [])`
8. `ProfileScreenPostsTabErrorPreview` — `postsStatus = InitialError(Network)`

Plus a separate `ProfileActionsRowOwnPreview` and `ProfileActionsRowOtherPreview` (the other variant is rendered against Bead F's hypothetical state; Bead D ships the preview but doesn't gate on its visual fidelity — the screenshot baseline is regenerated in Bead F).

### Unit tests

`ProfileViewModelTest` additions (Bead C tests stay):
- `RetryTab event re-launches initial tab load for the named tab` — guards the new event.

### Instrumentation test

`feature/profile/impl/src/androidTest/.../ProfileScreenInstrumentationTest.kt`:

- Uses `createAndroidComposeRule<ComponentActivity>()` (same as PR #150's `clearAndSetSemantics` test).
- One scenario: render `ProfileScreen` with a fake `ProfileRepository` returning a successful header + empty posts. Tap the Edit button. Assert the snackbar with `R.string.profile_action_coming_soon` appears in the composition.
- PR gets the `run-instrumented` label per the `feedback_run_instrumented_label_on_androidtest_prs` memory.

## Risks

| Risk | Mitigation |
|---|---|
| `BoldHeroGradient` Palette extraction has a perceptible flicker on first paint (avatarHue fallback → palette swap). | The composable already caches Palette per banner URL (Bead B). First open accepts the flicker; subsequent opens hit the cache. Not Bead D's concern. |
| `LazyListState` end-prefetch fires `LoadMore` when the user lands on Replies / Media tabs because the placeholder is a single `item` and `lastVisible > totalItemCount - 5` is true. | Gate `onLoadMore` dispatch on `state.selectedTab == ProfileTab.Posts`. Captured in the pagination snippet above. |
| Replies / Media placeholder items don't have stable keys → recomposes when state changes. | Use string-literal keys (`"replies-placeholder"`, `"media-placeholder"`). Documented in the layout block. |
| Hero column inside `BoldHeroGradient` overflows on narrow screens (e.g. very long display names + long handles). | Trust the `BoldHeroGradient` parent constraints — it's a `Surface` that fills its width; the inner Column uses `Modifier.fillMaxWidth()` and `Text` defaults to ellipsis on overflow. Real-device verify per `feedback_compose_glyph_iteration_workflow.md`. |
| `MainShellPersistenceTest` breaks when the `:app` Profile placeholder is removed because it references `Profile(handle = null)` as a top-level route. | The route reference is independent of the provider — the NavKey type still exists in `:feature:profile:api`. The test continues to compile and pass after Bead D's `:app` cleanup. Verified during implementation. |

## Open questions (resolve at implementation time)

- **Hero spacing tokens** — exact `Spacer` heights between rows. Defer to design polish PR if needed; the spec doesn't lock specific dp values for inter-row spacing.
- **Avatar size** — spec says 80–96dp. Implementation will pick 88dp (midpoint) and screenshot-verify.
- **Snackbar duration for the Coming Soon copy** — `SnackbarDuration.Short` matches Feed's pattern; will go with that unless screenshot review surfaces a complaint.
- **Overflow menu in Bead D** — the actions row renders an overflow icon, but Bead D has no overflow entries (Settings push lands in Bead F). The icon is rendered for visual completeness but its `onClick` is a silent no-op (no snackbar, no `ProfileEffect`). Bead F replaces the no-op with a real `DropdownMenu` containing the Settings entry. The empty no-op is acceptable because the icon's `contentDescription` (`R.string.profile_action_overflow`) communicates the affordance for accessibility; users tapping a dead icon in Bead D is a transient compromise that disappears as soon as Bead F lands.

## Acceptance

This bead is done when:

- [ ] All files in the file-layout block exist with their documented contracts.
- [ ] `Profile(handle = null)` activates `:feature:profile:impl`'s screen — the `:app` Profile placeholder is gone.
- [ ] `Profile(handle = "alice…")` activates the same screen (other-user nav reaches it; actions row is the Edit variant — Bead F fixes that).
- [ ] All 16 screenshot baselines are committed.
- [ ] `ProfileScreenInstrumentationTest` passes on the configured Pixel 6 API 35 emulator.
- [ ] `./gradlew :feature:profile:impl:testDebugUnitTest :feature:profile:impl:validateDebugScreenshotTest :app:assembleDebug spotlessCheck lint` all green locally.
- [ ] PR carries the `run-instrumented` label.

## References

- Predecessor design: `openspec/changes/add-profile-feature/design.md`
- Predecessor requirements: `openspec/changes/add-profile-feature/specs/feature-profile/spec.md`
- File-layout precedent: `feature/feed/impl/src/main/kotlin/net/kikin/nubecita/feature/feed/impl/`
- Stateful/stateless screen pattern: `feature/feed/impl/.../FeedScreen.kt`
- Instrumentation test precedent: PR #150 (`clearAndSetSemantics` test using `createAndroidComposeRule<ComponentActivity>()`)
- Bead C ViewModel: `feature/profile/impl/src/main/kotlin/.../ProfileViewModel.kt`
- Bead B composables: `:designsystem.BoldHeroGradient`, `:designsystem.ProfilePillTabs`
- Bead A typography: `:designsystem.displayNameStyle`, `:designsystem.handleStyle`
