# Profile feature — Bead E: Replies tab + Media tab + 3-col media grid — design

**Bead ID:** `nubecita-s6p.5` (Bead E of the Profile epic `nubecita-s6p`)
**Branch:** `feat/nubecita-s6p.5-feature-profile-impl-replies-tab-media-tab-3-col-m`
**Predecessor designs:** `openspec/changes/add-profile-feature/design.md` (Decisions 1–8) + `docs/superpowers/specs/2026-05-12-profile-bead-d-design.md` (file layout + LazyListScope-extension pattern)
**Predecessor requirements:** `openspec/changes/add-profile-feature/specs/feature-profile/spec.md`

## Scope

This bead adds the two remaining tab bodies on top of Bead D's foundation. After this bead lands:

- All 3 pill tabs are selectable AND functional. The Replies tab renders a `LazyColumn` of `PostCard`s for reply-filtered posts (`posts_with_replies` filter); the Media tab renders a 3-col grid of thumbnails (`posts_with_media` filter).
- Per-tab pagination fires for whichever tab is active (Bead D's gate hardcoded Posts; this bead generalizes).
- Tapping a Media cell pushes `PostDetailRoute` onto the back stack via the existing effect chain (same shape as a PostCard tap on Posts/Replies).
- `ProfileTabPlaceholder.kt` and its 4 screenshot baselines are deleted — no more "arrives soon" placeholders for Replies/Media.

### Out of scope (explicitly deferred)

| Excluded | Where it lands |
|---|---|
| Other-user actions row (Follow + Message + overflow) | Bead F (`nubecita-s6p.6`) |
| `ListDetailSceneStrategy.listPane{}` metadata on the `@MainShell` provider | Bead F |
| Settings overflow entry from profile | Bead F |
| Settings stub Composable | Bead F |
| `:app` Settings placeholder removal | Bead F |
| Medium-width screenshot fixtures | Bead F |
| Scroll-collapsing hero / TopAppBar inset handling | `nubecita-1tc` |
| Promotion of `MediaCellThumb` to `:designsystem` | Future bd (when a second caller appears) |
| Real Follow / Edit / Message writes | Follow-up epics |

## Architecture

### File layout

```
feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/
├── ProfileScreenContent.kt            # MODIFY: tab dispatch + generalized pagination gate
└── ui/
    ├── ProfilePostsTabBody.kt         # RENAME → ProfileFeedTabBody.kt (generalized)
    ├── ProfileFeedTabBody.kt          # NEW (renamed): handles Posts AND Replies
    ├── ProfileMediaTabBody.kt         # NEW: row-packed 3-col grid contributor
    ├── MediaCellThumb.kt              # NEW: single thumb cell (Coil + clickable + aspectRatio 1:1)
    └── ProfileTabPlaceholder.kt       # DELETE (Bead D transitional composable)

feature/profile/impl/src/screenshotTest/kotlin/.../
├── ui/
│   ├── ProfileTabPlaceholderScreenshotTest.kt   # DELETE (4 baselines removed)
│   ├── ProfileMediaTabBodyScreenshotTest.kt     # NEW: media-loaded/empty/error fixtures
│   └── (Replies coverage rolls into ProfileScreenContentScreenshotTest below)
└── ProfileScreenContentScreenshotTest.kt        # MODIFY: + replies-loaded / replies-empty / replies-error fixtures

feature/profile/impl/src/test/kotlin/.../
└── ProfileViewModelTest.kt            # MODIFY: + 2 new tests (Replies LoadMore isolation, Media PostTapped effect)
```

**No contract changes.** Bead C already defined `TabItemUi.MediaCell` + the `Refresh` / `LoadMore` / `RetryTab` event surface. The `AuthorFeedMapper` already emits `MediaCell` and uses `thumbOrFullsize()` (via the just-merged `nubecita-nwn`).

### Tab dispatch in `ProfileScreenContent`

The current dispatch routes Posts to a real body and Replies/Media to the placeholder:

```kotlin
when (state.selectedTab) {
    ProfileTab.Posts ->
        profilePostsTabBody(status = state.postsStatus, ...)
    ProfileTab.Replies ->
        item(key = "replies-placeholder") { ProfileTabPlaceholder(tab = ProfileTab.Replies) }
    ProfileTab.Media ->
        item(key = "media-placeholder") { ProfileTabPlaceholder(tab = ProfileTab.Media) }
}
```

Becomes:

```kotlin
when (state.selectedTab) {
    ProfileTab.Posts ->
        profileFeedTabBody(
            tab = ProfileTab.Posts,
            status = state.postsStatus,
            callbacks = postCallbacks,
            onRetry = { onEvent(ProfileEvent.RetryTab(ProfileTab.Posts)) },
        )
    ProfileTab.Replies ->
        profileFeedTabBody(
            tab = ProfileTab.Replies,
            status = state.repliesStatus,
            callbacks = postCallbacks,
            onRetry = { onEvent(ProfileEvent.RetryTab(ProfileTab.Replies)) },
        )
    ProfileTab.Media ->
        profileMediaTabBody(
            status = state.mediaStatus,
            onMediaTap = { uri -> onEvent(ProfileEvent.PostTapped(uri)) },
            onRetry = { onEvent(ProfileEvent.RetryTab(ProfileTab.Media)) },
        )
}
```

### `profileFeedTabBody` (rename + generalize `profilePostsTabBody`)

```kotlin
internal fun LazyListScope.profileFeedTabBody(
    tab: ProfileTab,           // <-- new: Posts or Replies (Media has its own body)
    status: TabLoadStatus,
    callbacks: PostCallbacks,
    onRetry: () -> Unit,
) {
    val keyPrefix = when (tab) {
        ProfileTab.Posts -> "posts"
        ProfileTab.Replies -> "replies"
        ProfileTab.Media -> "posts" // unreachable — Media routes to profileMediaTabBody
    }
    when (status) {
        TabLoadStatus.Idle,
        TabLoadStatus.InitialLoading -> {
            item(key = "$keyPrefix-loading", contentType = "loading") {
                ProfileLoadingState()
            }
        }
        is TabLoadStatus.InitialError -> {
            item(key = "$keyPrefix-error", contentType = "error") {
                ProfileErrorState(error = status.error, onRetry = onRetry)
            }
        }
        is TabLoadStatus.Loaded -> {
            if (status.items.isEmpty()) {
                item(key = "$keyPrefix-empty", contentType = "empty") {
                    ProfileEmptyState(tab = tab)
                }
            } else {
                items(
                    items = status.items,
                    key = { it.postUri },
                    contentType = { item ->
                        when (item) {
                            is TabItemUi.Post -> "post"
                            is TabItemUi.MediaCell -> "media" // unreachable for Posts/Replies
                        }
                    },
                ) { item ->
                    when (item) {
                        is TabItemUi.Post -> PostCard(post = item.post, callbacks = callbacks)
                        is TabItemUi.MediaCell -> { /* unreachable; defensive */ }
                    }
                }
                if (status.isAppending) {
                    item(key = "$keyPrefix-appending", contentType = "appending") {
                        ProfileAppendingIndicator()
                    }
                }
            }
        }
    }
}
```

Identical behavior to Bead D's `profilePostsTabBody` when `tab = ProfileTab.Posts`. The per-tab keys and the `tab` argument on `ProfileEmptyState` are the only diff.

### `profileMediaTabBody` (new)

Row-packing approach — preserves Bead D's single-LazyColumn-for-everything architecture by chunking media cells into rows of 3 and emitting each chunk as a single LazyColumn item. The visual outcome is identical to `LazyVerticalGrid(GridCells.Fixed(3))`; we trade grid-native features (item-placement transitions during scroll) for a single-container shape and shared scroll state across all 3 tabs.

```kotlin
internal fun LazyListScope.profileMediaTabBody(
    status: TabLoadStatus,
    onMediaTap: (postUri: String) -> Unit,
    onRetry: () -> Unit,
) {
    when (status) {
        TabLoadStatus.Idle,
        TabLoadStatus.InitialLoading -> {
            item(key = "media-loading", contentType = "loading") {
                ProfileLoadingState()
            }
        }
        is TabLoadStatus.InitialError -> {
            item(key = "media-error", contentType = "error") {
                ProfileErrorState(error = status.error, onRetry = onRetry)
            }
        }
        is TabLoadStatus.Loaded -> {
            if (status.items.isEmpty()) {
                item(key = "media-empty", contentType = "empty") {
                    ProfileEmptyState(tab = ProfileTab.Media)
                }
            } else {
                val cells = status.items.filterIsInstance<TabItemUi.MediaCell>()
                val rows = cells.chunked(MEDIA_GRID_COLUMNS)
                items(
                    items = rows,
                    key = { row -> row.joinToString(":") { it.postUri } },
                    contentType = { "media-row" },
                ) { row ->
                    MediaGridRow(row = row, onMediaTap = onMediaTap)
                }
                if (status.isAppending) {
                    item(key = "media-appending", contentType = "appending") {
                        ProfileAppendingIndicator()
                    }
                }
            }
        }
    }
}

private const val MEDIA_GRID_COLUMNS = 3

@Composable
private fun MediaGridRow(
    row: List<TabItemUi.MediaCell>,
    onMediaTap: (postUri: String) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        row.forEach { cell ->
            MediaCellThumb(
                cell = cell,
                onClick = { onMediaTap(cell.postUri) },
                modifier = Modifier.weight(1f).aspectRatio(1f),
            )
        }
        // Pad short last row so cells align with the left edge of the grid.
        repeat(MEDIA_GRID_COLUMNS - row.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
```

Notes:

- `filterIsInstance<TabItemUi.MediaCell>()` is defensive — Bead C's `AuthorFeedMapper.toMediaCellOrNull` only emits `MediaCell` for the Media tab, so the cast is total. The filter exists to make the type narrow without an `as` cast.
- The row key joins post URIs with `:` so partial reloads keep stable identity. Per-row allocation cost is negligible (≤100 cells per profile in practice).
- `aspectRatio(1f)` makes each cell square — Instagram-grid convention.
- Cell gutter (the visual gap between cells) is handled inside `MediaCellThumb` via a small `Modifier.padding`, NOT here, so the row math stays clean.

### `MediaCellThumb` (new)

```kotlin
@Composable
internal fun MediaCellThumb(
    cell: TabItemUi.MediaCell,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(MEDIA_CELL_GUTTER)
            .clip(RoundedCornerShape(MEDIA_CELL_CORNER_RADIUS))
            .clickable(onClick = onClick),
    ) {
        NubecitaAsyncImage(
            model = cell.thumbUrl,
            contentDescription = null,   // decorative — see accessibility note below
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

private val MEDIA_CELL_GUTTER = 2.dp
private val MEDIA_CELL_CORNER_RADIUS = 2.dp
```

**Accessibility:** `contentDescription = null` per cell. A 3-col grid of nearly-identical media is decorative as a cluster — per-cell descriptions create excessive TalkBack noise without semantic value. The tap target itself gets correct Compose semantics from `Modifier.clickable` (focusable, has tap action, focusable via D-pad). Future a11y polish (e.g., "Photo $i of $total" via `Modifier.semantics`) can land in a separate bd if reviewers flag it.

**Where it lives:** stays in `:feature:profile:impl/ui/` since the Profile Media tab is the only current caller. Per YAGNI, promote to `:designsystem` when a second caller appears (likely candidates: Search results media filter, DMs media gallery, hashtag galleries).

**Image URL source:** `cell.thumbUrl` is already a thumbnail-sized URL via `nubecita-nwn`'s `thumbOrFullsize()` (falls back to fullsize when the source lacks a thumb). No fullsize fallback logic in this composable.

### Pagination gate generalization

Bead D's `ProfileScreenContent.kt` hardcodes Posts in the gate:

```kotlin
if (
    pastThreshold &&
    currentSelectedTab == ProfileTab.Posts &&
    currentPostsStatus is TabLoadStatus.Loaded &&
    (currentPostsStatus as TabLoadStatus.Loaded).hasMore &&
    !(currentPostsStatus as TabLoadStatus.Loaded).isAppending
) {
    currentOnEvent(ProfileEvent.LoadMore(ProfileTab.Posts))
}
```

Becomes tab-agnostic:

```kotlin
val activeStatus = currentActiveTabStatus()
if (
    pastThreshold &&
    activeStatus is TabLoadStatus.Loaded &&
    activeStatus.hasMore &&
    !activeStatus.isAppending
) {
    currentOnEvent(ProfileEvent.LoadMore(currentSelectedTab))
}
```

Where `currentActiveTabStatus()` is a small file-private helper extension on `ProfileScreenViewState` (mirrors the existing `activeTabIsRefreshing()` extension already in `ProfileScreenContent.kt`):

```kotlin
private fun ProfileScreenViewState.activeTabStatus(): TabLoadStatus =
    when (selectedTab) {
        ProfileTab.Posts -> postsStatus
        ProfileTab.Replies -> repliesStatus
        ProfileTab.Media -> mediaStatus
    }
```

`activeTabIsRefreshing()` (already exists in Bead D) gets refactored to reuse this helper. The `rememberUpdatedState(state.postsStatus)` capture from Bead D becomes `rememberUpdatedState(state.activeTabStatus())`.

### State flow

No new state. The ViewModel from Bead C already handles per-tab `LoadMore` correctly — `onLoadMore(tab: ProfileTab)` reads the named tab's status, issues `fetchTab(actor, tab, cursor = current.cursor)`, and applies the result via the cursor-identity-guarded `setTabStatus(tab) { ... }`. Calling `LoadMore(Replies)` and `LoadMore(Media)` works out of the box.

### Effect routing for Media cell taps

The Media cell's `onClick` dispatches `ProfileEvent.PostTapped(postUri)`. The VM's existing handler routes this through `ProfileEffect.NavigateToPost(postUri)`. `ProfileScreen`'s effect collector calls `onNavigateToPost(postUri)`. `ProfileNavigationModule`'s entry wires this to `LocalMainShellNavState.current.add(PostDetailRoute(postUri))`. **Identical chain to a Posts/Replies tap on a PostCard.** No new code path.

## Tests

### `ProfileViewModelTest.kt` additions

Two new `@Test` methods (per `tasks.md §5.6`):

**Test 1: Replies LoadMore doesn't touch sibling cursors**

```kotlin
@Test
fun `LoadMore on Replies does not touch Posts or Media cursors`() =
    runTest(mainDispatcher.dispatcher) {
        // Setup: all three tabs Loaded with a non-null cursor.
        val pagedPage = ProfileTabPage(items = persistentListOf(), nextCursor = "next-cursor")
        val repo =
            FakeProfileRepository(
                headerResult = Result.success(SAMPLE_HEADER),
                tabResults = ProfileTab.entries.associateWith { Result.success(pagedPage) },
            )
        val vm = newVm(repo = repo)
        advanceUntilIdle()
        val priorPostsCalls = repo.tabCalls[ProfileTab.Posts]!!.get()
        val priorMediaCalls = repo.tabCalls[ProfileTab.Media]!!.get()

        vm.handleEvent(ProfileEvent.LoadMore(ProfileTab.Replies))
        advanceUntilIdle()

        assertEquals(priorPostsCalls, repo.tabCalls[ProfileTab.Posts]!!.get(),
            "Replies LoadMore MUST NOT issue a Posts fetch")
        assertEquals(priorMediaCalls, repo.tabCalls[ProfileTab.Media]!!.get(),
            "Replies LoadMore MUST NOT issue a Media fetch")
        assertEquals("next-cursor", repo.lastTabCursor[ProfileTab.Replies],
            "Replies LoadMore MUST pass the Replies cursor")
        assertNull(repo.lastTabCursor[ProfileTab.Posts],
            "Replies LoadMore MUST NOT touch Posts cursor")
        assertNull(repo.lastTabCursor[ProfileTab.Media],
            "Replies LoadMore MUST NOT touch Media cursor")
    }
```

**Test 2: Media tab PostTapped emits NavigateToPost (same shape as Posts)**

```kotlin
@Test
fun `Media tab PostTapped emits NavigateToPost effect with the tapped postUri`() =
    runTest(mainDispatcher.dispatcher) {
        val repo =
            FakeProfileRepository(
                headerResult = Result.success(SAMPLE_HEADER),
                tabResults = ProfileTab.entries.associateWith { Result.success(EMPTY_PAGE) },
            )
        val vm = newVm(repo = repo)
        advanceUntilIdle()

        vm.handleEvent(ProfileEvent.TabSelected(ProfileTab.Media))
        advanceUntilIdle()

        vm.effects.test {
            vm.handleEvent(ProfileEvent.PostTapped("at://did:plc:alice/post/abc"))
            val effect = awaitItem()
            assertEquals(
                ProfileEffect.NavigateToPost("at://did:plc:alice/post/abc"),
                effect,
                "Media-tab PostTapped MUST emit the same NavigateToPost effect shape as Posts-tab PostTapped",
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
```

### Screenshot fixtures

12 new baselines (6 fixtures × 2 themes), Compact width only:

**In `ProfileMediaTabBodyScreenshotTest.kt` (new file):**
- `media-loaded-light` / `media-loaded-dark` — 3×N grid with ~7 sample cells (so the last row has 1 padded slot)
- `media-empty-light` / `media-empty-dark` — `Loaded(items = [])` → renders `ProfileEmptyState(tab = Media)`
- `media-error-light` / `media-error-dark` — `InitialError(Network)` → renders `ProfileErrorState`

**In `ProfileScreenContentScreenshotTest.kt` (extension):**
- `screen-replies-loaded-light` / `screen-replies-loaded-dark` — full screen with Replies tab selected, 3 reply PostCards
- `screen-replies-empty-light` / `screen-replies-empty-dark` — Replies tab selected, empty
- `screen-replies-error-light` / `screen-replies-error-dark` — Replies tab selected, InitialError

**Removed:** `ProfileTabPlaceholderScreenshotTest.kt` and its 4 baselines (Bead D's transitional placeholder).

The reference `LocalClock` fixture from Bead D's polish commit (`FixtureClock` at `2026-04-26T12:00:00Z`) is reused so PostCard relative-time labels stay deterministic.

### Mapper coverage

`AuthorFeedMapperTest.kt` was added in Bead C. Re-verify it covers:

- `toMediaCellOrNull` for image posts → emits MediaCell with thumb URL.
- `toMediaCellOrNull` for video posts → emits MediaCell with `posterUrl`.
- `toMediaCellOrNull` for posts without media (text-only, link-only, record-only) → returns null (entry dropped from Media tab).

If any of those scenarios are missing, add them in this bead.

## Risks

| Risk | Mitigation |
|---|---|
| `chunked(3)` reallocates a list when the LazyColumn re-evaluates. | The `chunked` runs inside the `items()` call site, once per call to `profileMediaTabBody`. Compose treats the chunked list as the `items` parameter; structural equality on `ImmutableList<TabItemUi>` upstream means the recomposition path doesn't re-run `chunked` per row render. Empirically this pattern works in Feed's row-based composites. |
| Row key `joinToString(":") { it.postUri }` allocates a string per row per recomposition. | Acceptable — keys are computed at list-emission time, not per-cell-render. Profiles cap at ~30 cells/page; ≤10 rows × 1 string each. The alternative (a stable hash) is uglier without measurable benefit. |
| Switching tabs mid-scroll preserves scroll position (shared `listState`) but Posts↔Media unmount/mount items of different heights → visible "jump". | Acceptable. Bead D already exhibits this between Posts and the placeholder; Bead E doesn't worsen it. Per-tab scroll state is a future polish item (likely co-landed with `nubecita-1tc`'s TopAppBar work). |
| Layoutlib's Coil no-op means Media screenshot baselines render empty thumbs. | Same Layoutlib limitation Bead D shipped with. Visual delta with real thumbs verified on-device via manual QA. |
| `filterIsInstance<TabItemUi.MediaCell>()` is total today but a future contract addition (e.g., a `Pinned` variant) would silently drop new variants. | Bead C's `AuthorFeedMapper.toMediaCellOrNull` only emits MediaCell for Media filter, and the `TabItemUi` sealed sum is closed. Adding a new variant would surface a compile warning at the `when (item)` exhaustiveness checks in `profileFeedTabBody`. Acceptable. |

## Open questions (resolved at implementation time)

- **Cell gutter (2/1/4 dp)**: pick 2dp per the Instagram precedent; screenshot-validate.
- **Cell corner radius (0/2/4 dp)**: pick 2dp; matches the gutter; subtle softening without becoming card-like.
- **Default thumb placeholder** when Coil is mid-fetch: `NubecitaAsyncImage` already shimmer-placeholders via its wrapper.

## Acceptance

This bead is done when:

- [ ] `profileFeedTabBody(tab, status, callbacks, onRetry)` exists in `ProfileFeedTabBody.kt`; old `profilePostsTabBody` is renamed.
- [ ] `profileMediaTabBody(status, onMediaTap, onRetry)` exists in `ProfileMediaTabBody.kt`; row-packs cells.
- [ ] `MediaCellThumb` exists in `MediaCellThumb.kt`.
- [ ] `ProfileTabPlaceholder.kt` and `ProfileTabPlaceholderScreenshotTest.kt` are deleted (+ 4 baselines removed).
- [ ] `ProfileScreenContent.kt`'s tab dispatch routes all 3 tabs to the new LazyListScope extensions; pagination gate uses `currentSelectedTab` + `activeTabStatus()`.
- [ ] 12 new screenshot baselines committed.
- [ ] `ProfileViewModelTest` adds 2 new tests (Replies LoadMore isolation, Media PostTapped effect).
- [ ] `./gradlew :feature:profile:impl:assembleDebug :feature:profile:impl:testDebugUnitTest :feature:profile:impl:validateDebugScreenshotTest :app:assembleDebug spotlessCheck lint :app:checkSortDependencies` all green locally.
- [ ] On-device smoke check (Pixel 10 Pro XL): scroll through all 3 tabs; tap a Media cell → lands on PostDetail; pull-to-refresh on Media tab works; switching tabs from Media to Posts mid-scroll preserves scroll position.

## References

- Bead D design: `docs/superpowers/specs/2026-05-12-profile-bead-d-design.md`
- Bead D plan: `docs/superpowers/plans/2026-05-12-profile-bead-d-plan.md`
- Epic-level design: `openspec/changes/add-profile-feature/design.md`
- Capability spec: `openspec/changes/add-profile-feature/specs/feature-profile/spec.md` (Requirements: "Posts, Replies, and Media bodies render per-tab", "Post tap inside the profile body emits NavigateToPost…")
- Predecessor PR: #158 (Bead D), #159 (`nubecita-nwn` thumb plumbing)
- `nubecita-1tc`: scroll-collapsing hero — co-landing candidate after this bead and Bead F
