# Profile Bead E Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the Replies tab (LazyColumn of PostCards) and Media tab (3-col row-packed thumbnail grid) on top of Bead D's foundation. After this bead, all 3 pill tabs on the Profile screen are fully functional.

**Architecture:** Single LazyColumn for the whole screen (unchanged from Bead D). Replies reuses the Posts tab body — `profilePostsTabBody` is renamed to `profileFeedTabBody` and parameterized over `tab: ProfileTab`. Media uses a new `profileMediaTabBody` that chunks `TabItemUi.MediaCell` items into rows of 3, emitting each chunk as a single LazyColumn item containing a `Row` of weighted `MediaCellThumb` Boxes. Pagination gate generalizes from hardcoded-Posts to "fire `LoadMore(selectedTab)` for whichever tab is active and `Loaded && hasMore && !isAppending`". `ProfileTabPlaceholder.kt` and its 4 screenshot baselines are deleted.

**Tech Stack:** Kotlin · Jetpack Compose with Material 3 Expressive · `:designsystem.NubecitaAsyncImage` (Coil 3 wrapper) · `:designsystem.PostCard` (for Posts/Replies bodies) · JUnit 5 + Turbine + AssertK for VM tests · `com.android.tools.screenshot.PreviewTest` for screenshot baselines · all dependencies already in place from Bead D.

**Predecessor design:** `docs/superpowers/specs/2026-05-12-profile-bead-e-design.md`. Refer back to it when something is ambiguous.

---

## File Structure

```
feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/
├── ProfileScreenContent.kt                # MODIFY: tab dispatch + pagination gate
└── ui/
    ├── ProfilePostsTabBody.kt             # DELETE (renamed → ProfileFeedTabBody.kt)
    ├── ProfileFeedTabBody.kt              # CREATE: profileFeedTabBody(tab, status, callbacks, onRetry)
    ├── ProfileMediaTabBody.kt             # CREATE: profileMediaTabBody(status, onMediaTap, onRetry)
    ├── MediaCellThumb.kt                  # CREATE: single thumb cell
    └── ProfileTabPlaceholder.kt           # DELETE

feature/profile/impl/src/screenshotTest/kotlin/.../
├── ui/
│   ├── ProfileTabPlaceholderScreenshotTest.kt  # DELETE
│   └── ProfileMediaTabBodyScreenshotTest.kt    # CREATE: media-loaded/empty/error fixtures
├── ProfileScreenContentScreenshotTest.kt       # MODIFY: + 3 replies fixtures
└── reference baselines:
    ├── ProfileTabPlaceholderScreenshotTestKt/    # DELETE (4 PNGs)
    └── (12 new PNGs across Media + Replies)

feature/profile/impl/src/test/kotlin/.../
└── ProfileViewModelTest.kt                # MODIFY: + 2 new test cases
```

**No contract changes.** Bead C's `TabItemUi.MediaCell` + `LoadMore(tab)` / `PostTapped` / `RetryTab(tab)` event surface already covers everything. `:core:feed-mapping` and `AuthorFeedMapper` already use `thumbOrFullsize()` via the merged `nubecita-nwn` PR.

---

## Task 1: Add VM regression tests for Replies LoadMore isolation + Media PostTapped

These tests assert behaviors that already work in the Bead C ViewModel — they're regression guards before we touch the screen layer. Both should pass against `main` immediately.

**Files:**
- Modify: `feature/profile/impl/src/test/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileViewModelTest.kt`

- [ ] **Step 1: Append the Replies-isolation test**

After the existing `RetryTab re-launches initial tab load for the named tab` test in `ProfileViewModelTest.kt`, append:

```kotlin
    @Test
    fun `LoadMore on Replies does not touch Posts or Media cursors`() =
        runTest(mainDispatcher.dispatcher) {
            // All three tabs Loaded with a non-null cursor so LoadMore is legal on each.
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

            assertEquals(
                priorPostsCalls,
                repo.tabCalls[ProfileTab.Posts]!!.get(),
                "Replies LoadMore MUST NOT issue a Posts fetch",
            )
            assertEquals(
                priorMediaCalls,
                repo.tabCalls[ProfileTab.Media]!!.get(),
                "Replies LoadMore MUST NOT issue a Media fetch",
            )
            assertEquals(
                "next-cursor",
                repo.lastTabCursor[ProfileTab.Replies],
                "Replies LoadMore MUST pass the Replies cursor",
            )
            assertNull(
                repo.lastTabCursor[ProfileTab.Posts],
                "Replies LoadMore MUST NOT touch Posts cursor",
            )
            assertNull(
                repo.lastTabCursor[ProfileTab.Media],
                "Replies LoadMore MUST NOT touch Media cursor",
            )
        }
```

- [ ] **Step 2: Append the Media-PostTapped test**

Append after the previous test:

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

- [ ] **Step 3: Run the full VM test class**

```bash
./gradlew :feature:profile:impl:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL; all 10 ProfileViewModelTest cases pass (8 from Bead D + the 2 new ones). If either new test FAILS, the VM contract is broken — stop and investigate before continuing.

- [ ] **Step 4: Commit**

```bash
git add feature/profile/impl/src/test/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileViewModelTest.kt
git commit -m "test(feature/profile/impl): regression guards for per-tab LoadMore + Media PostTapped

Two new ProfileViewModelTest cases per tasks.md §5.6:

- LoadMore(Replies) doesn't issue Posts/Media fetches and doesn't
  touch their cursors.
- Media-tab PostTapped emits the same NavigateToPost effect shape as
  Posts-tab PostTapped (no Media-specific routing).

Both behaviors are already correct in Bead C's ViewModel — these tests
are regression guards before Bead E touches the screen layer.

Refs: nubecita-s6p.5"
```

---

## Task 2: Rename ProfilePostsTabBody → ProfileFeedTabBody + generalize for Posts/Replies

**Files:**
- Delete: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfilePostsTabBody.kt`
- Create: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileFeedTabBody.kt`
- Modify: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreenContent.kt`

- [ ] **Step 1: Create `ProfileFeedTabBody.kt` with the generalized function**

```kotlin
package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import net.kikin.nubecita.designsystem.component.PostCallbacks
import net.kikin.nubecita.designsystem.component.PostCard
import net.kikin.nubecita.feature.profile.impl.ProfileTab
import net.kikin.nubecita.feature.profile.impl.TabItemUi
import net.kikin.nubecita.feature.profile.impl.TabLoadStatus

/**
 * Contributes the Posts or Replies tab body items to the enclosing
 * LazyColumn — an extension on [LazyListScope] (rather than a Composable
 * that opens its own LazyColumn) so the hero, sticky pill tabs, and feed
 * items share one scroll surface. Nested LazyColumns break sticky headers,
 * so this shape is the only one that satisfies the design.
 *
 * Generalized from Bead D's `profilePostsTabBody`: the [tab] parameter
 * drives the per-item LazyColumn keys (`posts-loading` vs `replies-loading`)
 * and the empty-state copy via [ProfileEmptyState]. Posts and Replies
 * share this implementation; the Media tab has its own
 * [profileMediaTabBody] because it row-packs into a 3-col grid.
 *
 * Branches on [status]:
 * - [TabLoadStatus.Idle], [TabLoadStatus.InitialLoading] → loading skeletons
 * - [TabLoadStatus.InitialError] → error state + Retry
 * - [TabLoadStatus.Loaded] with empty items → empty state
 * - [TabLoadStatus.Loaded] with items → PostCards + optional appending row
 */
internal fun LazyListScope.profileFeedTabBody(
    tab: ProfileTab,
    status: TabLoadStatus,
    callbacks: PostCallbacks,
    onRetry: () -> Unit,
) {
    val keyPrefix =
        when (tab) {
            ProfileTab.Posts -> "posts"
            ProfileTab.Replies -> "replies"
            // Media has its own body in [profileMediaTabBody]; defensive prefix.
            ProfileTab.Media -> "posts"
        }
    when (status) {
        TabLoadStatus.Idle,
        TabLoadStatus.InitialLoading,
        -> {
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
                            is TabItemUi.MediaCell -> "media" // unreachable for Posts/Replies; defensive
                        }
                    },
                ) { item ->
                    when (item) {
                        is TabItemUi.Post -> PostCard(post = item.post, callbacks = callbacks)
                        is TabItemUi.MediaCell -> {
                            // Posts/Replies filter never yields a MediaCell.
                            // Branch exists for type completeness; renders nothing.
                        }
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

- [ ] **Step 2: Delete the old file**

```bash
rm feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfilePostsTabBody.kt
```

- [ ] **Step 3: Update the single call site in `ProfileScreenContent.kt`**

Replace the import:

```kotlin
import net.kikin.nubecita.feature.profile.impl.ui.profilePostsTabBody
```

with:

```kotlin
import net.kikin.nubecita.feature.profile.impl.ui.profileFeedTabBody
```

And update the dispatch (currently `when (state.selectedTab) { ProfileTab.Posts -> profilePostsTabBody(...) ; ...placeholder branches }`) — replace just the Posts arm for now (Replies/Media branches stay on the placeholder until Task 5):

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
                        item(key = "replies-placeholder", contentType = "placeholder") {
                            ProfileTabPlaceholder(tab = ProfileTab.Replies)
                        }
                    ProfileTab.Media ->
                        item(key = "media-placeholder", contentType = "placeholder") {
                            ProfileTabPlaceholder(tab = ProfileTab.Media)
                        }
                }
```

- [ ] **Step 4: Run tests + screenshots — Posts tab behavior must be unchanged**

```bash
./gradlew :feature:profile:impl:compileDebugKotlin :feature:profile:impl:testDebugUnitTest :feature:profile:impl:validateDebugScreenshotTest
```

Expected: BUILD SUCCESSFUL. All 10 VM tests pass; all 30 existing screenshot baselines validate unchanged (the renamed function produces identical output for the Posts case, so Bead D's `posts-loaded` / `posts-loading` / `posts-empty` / `posts-error` baselines are bit-identical).

- [ ] **Step 5: Commit**

```bash
git add feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileFeedTabBody.kt \
        feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreenContent.kt
git rm feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfilePostsTabBody.kt
git commit -m "refactor(feature/profile/impl): generalize PostsTabBody → FeedTabBody(tab, ...)

Renames profilePostsTabBody → profileFeedTabBody and adds a
\`tab: ProfileTab\` parameter so Bead E's Replies tab can reuse the
exact same implementation. The two bodies are literally identical
(LazyColumn of PostCards with the same loading/error/empty/loaded
branches) — generalizing now avoids a near-duplicate file when
Replies wires up in the next commit.

Item keys are prefixed by tab name (\"posts-loading\" vs
\"replies-loading\") so LazyColumn item identity stays stable across
tab switches. Empty-state composable already accepts the tab
parameter (Bead D).

No screenshot deltas; Bead D's Posts baselines render bit-identical
output through the renamed code path.

Refs: nubecita-s6p.5"
```

---

## Task 3: Create MediaCellThumb composable

**Files:**
- Create: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/MediaCellThumb.kt`

- [ ] **Step 1: Create the file**

```kotlin
package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.component.NubecitaAsyncImage
import net.kikin.nubecita.feature.profile.impl.TabItemUi

private val MEDIA_CELL_GUTTER = 2.dp
private val MEDIA_CELL_CORNER_RADIUS = 2.dp

/**
 * Single cell of the Profile Media grid. Renders the [cell]'s thumb URL
 * via Coil with a square (1:1) aspect ratio — the caller is expected to
 * apply `Modifier.weight(1f).aspectRatio(1f)` so the cell sizes itself
 * to one third of the row width.
 *
 * Tap dispatches via [onClick]; the screen-level effect collector
 * routes this through `ProfileEffect.NavigateToPost(cell.postUri)`.
 *
 * Image URL source: `cell.thumbUrl` is already a thumbnail-sized URL
 * via `nubecita-nwn`'s `thumbOrFullsize()` projection (falls back to
 * fullsize when the source lacks a thumb). `NubecitaAsyncImage`
 * renders a flat `surfaceContainerHighest` ColorPainter placeholder
 * while Coil fetches and on error/fallback.
 *
 * Accessibility: `contentDescription = null` per cell. A 3-col grid of
 * nearly-identical media is decorative as a cluster — per-cell
 * descriptions would create excessive TalkBack noise. The tap target
 * itself gets correct Compose semantics from `Modifier.clickable`
 * (focusable, has tap action, focusable via D-pad). Future a11y
 * polish (e.g., "Photo $i of $total" via `Modifier.semantics`) can
 * land in a separate bd if reviewers flag it.
 */
@Composable
internal fun MediaCellThumb(
    cell: TabItemUi.MediaCell,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .padding(MEDIA_CELL_GUTTER)
                .clip(RoundedCornerShape(MEDIA_CELL_CORNER_RADIUS))
                .clickable(onClick = onClick),
    ) {
        NubecitaAsyncImage(
            model = cell.thumbUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}
```

- [ ] **Step 2: Compile to verify**

```bash
./gradlew :feature:profile:impl:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/MediaCellThumb.kt
git commit -m "feat(feature/profile/impl): add MediaCellThumb composable

Square thumbnail cell for the Profile Media grid. NubecitaAsyncImage
wrapper handles placeholder/error/fallback; gutter and corner radius
are private file-level constants (2 dp each, Instagram-grid feel).

Caller is expected to apply Modifier.weight(1f).aspectRatio(1f) for
3-col equal-width sizing — keeps the grid layout math at the row
level rather than embedded in the cell.

contentDescription = null per cell (decorative within the grid;
clickable semantics provide the tap-target a11y).

Refs: nubecita-s6p.5"
```

---

## Task 4: Create profileMediaTabBody LazyListScope extension

**Files:**
- Create: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileMediaTabBody.kt`

- [ ] **Step 1: Create the file**

```kotlin
package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.kikin.nubecita.feature.profile.impl.ProfileTab
import net.kikin.nubecita.feature.profile.impl.TabItemUi
import net.kikin.nubecita.feature.profile.impl.TabLoadStatus

private const val MEDIA_GRID_COLUMNS = 3

/**
 * Contributes the Media tab body items to the enclosing LazyColumn.
 *
 * Row-packing approach — Bead D's design keeps the hero + sticky pill
 * tabs + active tab body in one LazyColumn so they share a single scroll
 * surface. A nested `LazyVerticalGrid` would break sticky headers AND
 * crash on the infinite-height constraint of a scrolling parent. The
 * trade-off: lose grid-native item-placement transitions; keep one
 * container shape and shared scroll state across all three tabs.
 *
 * Branches on [status]:
 * - [TabLoadStatus.Idle], [TabLoadStatus.InitialLoading] → shimmer skeletons
 * - [TabLoadStatus.InitialError] → error state + Retry
 * - [TabLoadStatus.Loaded] with empty items → empty state
 * - [TabLoadStatus.Loaded] with items → chunked rows of MediaCellThumb +
 *   optional appending row
 *
 * [onMediaTap] receives the tapped cell's `postUri` and is wired to
 * `ProfileEvent.PostTapped` at the screen level — identical chain to a
 * PostCard tap on Posts/Replies (proven by `ProfileViewModelTest`'s
 * Media-PostTapped regression test).
 */
internal fun LazyListScope.profileMediaTabBody(
    status: TabLoadStatus,
    onMediaTap: (postUri: String) -> Unit,
    onRetry: () -> Unit,
) {
    when (status) {
        TabLoadStatus.Idle,
        TabLoadStatus.InitialLoading,
        -> {
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
                // `filterIsInstance` is defensive — AuthorFeedMapper only
                // emits MediaCell for the Media tab. A future TabItemUi
                // variant would surface as a compile warning at the
                // exhaustiveness check in profileFeedTabBody.
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

/**
 * One row of the Media grid — up to [MEDIA_GRID_COLUMNS] cells. Short
 * trailing rows are left-padded with [Spacer]s so cells align with the
 * left edge of the grid.
 */
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
        repeat(MEDIA_GRID_COLUMNS - row.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
```

- [ ] **Step 2: Compile to verify**

```bash
./gradlew :feature:profile:impl:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileMediaTabBody.kt
git commit -m "feat(feature/profile/impl): add profileMediaTabBody row-packed grid extension

LazyListScope extension that contributes the Media tab body. Branches
on TabLoadStatus same as profileFeedTabBody, but the Loaded branch
chunks TabItemUi.MediaCell items into groups of 3 and emits each
chunk as one LazyColumn item containing a Row of weighted
MediaCellThumb cells.

Row-packing (rather than LazyVerticalGrid) keeps Bead D's
single-LazyColumn architecture intact — hero + sticky pill tabs +
all three tab bodies share one scroll surface. Trade-off documented
in the design doc.

Short trailing rows get Spacer(weight=1f) padding so cells stay
left-aligned. Defensive filterIsInstance handles the (impossible)
case where the Media filter yields a non-MediaCell — current
AuthorFeedMapper only emits MediaCell for the Media tab.

Refs: nubecita-s6p.5"
```

---

## Task 5: Wire Replies + Media in ProfileScreenContent; generalize pagination gate

**Files:**
- Modify: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreenContent.kt`

- [ ] **Step 1: Update imports**

In `ProfileScreenContent.kt`, drop the `ProfileTabPlaceholder` import and add `profileMediaTabBody`:

```kotlin
import net.kikin.nubecita.feature.profile.impl.ui.profileFeedTabBody
import net.kikin.nubecita.feature.profile.impl.ui.profileMediaTabBody
// REMOVE: import net.kikin.nubecita.feature.profile.impl.ui.ProfileTabPlaceholder
```

- [ ] **Step 2: Update tab dispatch — replace placeholder branches with real bodies**

Find the existing `when (state.selectedTab) { ... }` block inside the `LazyColumn` and replace it with:

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

- [ ] **Step 3: Generalize the pagination gate**

Find the private extension `ProfileScreenViewState.activeTabIsRefreshing()` at the bottom of `ProfileScreenContent.kt`. Below it, add a sibling extension that returns the active tab's status:

```kotlin
/**
 * Returns the [TabLoadStatus] of the currently-selected tab. Used by
 * the pagination gate to evaluate `Loaded && hasMore && !isAppending`
 * for whichever tab is active — Bead E generalized this from Bead D's
 * Posts-only hardcoded gate.
 */
private fun ProfileScreenViewState.activeTabStatus(): TabLoadStatus =
    when (selectedTab) {
        ProfileTab.Posts -> postsStatus
        ProfileTab.Replies -> repliesStatus
        ProfileTab.Media -> mediaStatus
    }
```

Optionally refactor the existing `activeTabIsRefreshing()` to reuse it (cleaner, no behavior change):

```kotlin
private fun ProfileScreenViewState.activeTabIsRefreshing(): Boolean {
    val status = activeTabStatus()
    return status is TabLoadStatus.Loaded && status.isRefreshing
}
```

- [ ] **Step 4: Update the LaunchedEffect pagination block**

Find the `LaunchedEffect(listState) { snapshotFlow { ... }.distinctUntilChanged().collect { ... } }` near the bottom of `ProfileScreenContent`. Replace the hardcoded Posts-only gate with the tab-agnostic version. The captures change from `currentPostsStatus` to `currentActiveTabStatus`:

Before (Bead D shape — find and replace this block):

```kotlin
    val currentSelectedTab by rememberUpdatedState(state.selectedTab)
    val currentPostsStatus by rememberUpdatedState(state.postsStatus)
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(listState) {
        snapshotFlow {
            val lastVisible =
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            lastVisible > total - PREFETCH_DISTANCE
        }
            .distinctUntilChanged()
            .collect { pastThreshold ->
                val status = currentPostsStatus
                if (
                    pastThreshold &&
                    currentSelectedTab == ProfileTab.Posts &&
                    status is TabLoadStatus.Loaded &&
                    status.hasMore &&
                    !status.isAppending
                ) {
                    currentOnEvent(ProfileEvent.LoadMore(ProfileTab.Posts))
                }
            }
    }
```

After:

```kotlin
    val currentSelectedTab by rememberUpdatedState(state.selectedTab)
    val currentActiveTabStatus by rememberUpdatedState(state.activeTabStatus())
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(listState) {
        snapshotFlow {
            val lastVisible =
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            lastVisible > total - PREFETCH_DISTANCE
        }
            .distinctUntilChanged()
            .collect { pastThreshold ->
                val status = currentActiveTabStatus
                if (
                    pastThreshold &&
                    status is TabLoadStatus.Loaded &&
                    status.hasMore &&
                    !status.isAppending
                ) {
                    currentOnEvent(ProfileEvent.LoadMore(currentSelectedTab))
                }
            }
    }
```

- [ ] **Step 5: Compile + run unit tests + assemble app**

```bash
./gradlew :feature:profile:impl:compileDebugKotlin :feature:profile:impl:testDebugUnitTest :app:assembleDebug
```

Expected: BUILD SUCCESSFUL; all 10 VM tests pass; `:app:assembleDebug` builds (verifies the new wiring doesn't break the DI graph or the Hilt aggregation).

- [ ] **Step 6: Commit**

```bash
git add feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreenContent.kt
git commit -m "feat(feature/profile/impl): wire Replies + Media tabs; generalize pagination gate

ProfileScreenContent's tab dispatch now routes:
- Posts → profileFeedTabBody(tab = Posts, ...)
- Replies → profileFeedTabBody(tab = Replies, ...)  // was placeholder
- Media → profileMediaTabBody(...)                  // was placeholder

Pagination gate generalized from Bead D's Posts-only hardcoded check
to fire LoadMore(selectedTab) whenever the active tab is in a
Loaded state with hasMore && !isAppending. New private extension
ProfileScreenViewState.activeTabStatus() mirrors the existing
activeTabIsRefreshing() pattern; the latter is refactored to share
the helper (no behavior change).

ProfileTabPlaceholder import dropped; the composable + its
screenshot baselines get deleted in the next commit.

Refs: nubecita-s6p.5"
```

---

## Task 6: Delete ProfileTabPlaceholder + its screenshot test + baselines

**Files:**
- Delete: `feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileTabPlaceholder.kt`
- Delete: `feature/profile/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileTabPlaceholderScreenshotTest.kt`
- Delete: `feature/profile/impl/src/screenshotTestDebug/reference/net/kikin/nubecita/feature/profile/impl/ui/ProfileTabPlaceholderScreenshotTestKt/` (directory of 4 PNGs)

- [ ] **Step 1: Delete the source file + its screenshot test + its baseline directory**

```bash
rm feature/profile/impl/src/main/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileTabPlaceholder.kt
rm feature/profile/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileTabPlaceholderScreenshotTest.kt
rm -r feature/profile/impl/src/screenshotTestDebug/reference/net/kikin/nubecita/feature/profile/impl/ui/ProfileTabPlaceholderScreenshotTestKt
```

- [ ] **Step 2: Compile + validate screenshots — no callers left**

```bash
./gradlew :feature:profile:impl:compileDebugKotlin :feature:profile:impl:validateDebugScreenshotTest
```

Expected: BUILD SUCCESSFUL. The placeholder composable had only two production call sites (Replies and Media branches in `ProfileScreenContent`); both were removed in Task 5. The 4 placeholder baselines and their test file are gone; validate confirms no orphaned references.

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "chore(feature/profile/impl): delete ProfileTabPlaceholder (Bead E retired)

The Replies/Media \"arrives soon\" placeholder shipped in Bead D as a
transitional Composable. Bead E's real tab bodies (profileFeedTabBody
for Replies + profileMediaTabBody for Media) replaced the call sites
in the previous commit, so the placeholder source file + screenshot
test + 4 reference baselines are now dead code.

Refs: nubecita-s6p.5"
```

---

## Task 7: Add ProfileMediaTabBodyScreenshotTest with 3 fixtures × 2 themes

**Files:**
- Create: `feature/profile/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileMediaTabBodyScreenshotTest.kt`

- [ ] **Step 1: Create the screenshot test file**

```kotlin
package net.kikin.nubecita.feature.profile.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.profile.impl.ProfileError
import net.kikin.nubecita.feature.profile.impl.TabItemUi
import net.kikin.nubecita.feature.profile.impl.TabLoadStatus

/**
 * Screenshot baselines for [profileMediaTabBody]. Renders the LazyListScope
 * extension inside a host LazyColumn so the layout reflects the production
 * call site (the extension only makes sense inside a parent LazyColumn).
 *
 * Per-fixture coverage: media-loaded (3×N grid + a short last row),
 * media-empty (Loaded(items=[])), media-error (InitialError(Network)).
 * Light + dark per fixture = 6 baselines.
 *
 * Coil is no-op in Layoutlib so MediaCellThumb baselines render the
 * NubecitaAsyncImage placeholder ColorPainter; visual delta with real
 * thumbs is verified on-device.
 */
private fun sampleCells(count: Int): List<TabItemUi> =
    (0 until count).map { idx ->
        TabItemUi.MediaCell(
            postUri = "at://did:plc:alice/post/media-$idx",
            thumbUrl = "https://cdn.bsky.app/img/feed_thumbnail/plain/cid$idx@jpeg",
        )
    }

@Composable
private fun MediaBodyHost(status: TabLoadStatus) {
    NubecitaTheme(dynamicColor = false) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            profileMediaTabBody(
                status = status,
                onMediaTap = {},
                onRetry = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "media-loaded-light", showBackground = true, heightDp = 800)
@Preview(name = "media-loaded-dark", showBackground = true, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileMediaTabBodyLoadedScreenshot() {
    // 7 cells → 2 full rows of 3 + 1 short row of 1 (exercises the
    // Spacer-padding code path for incomplete last rows).
    MediaBodyHost(
        status =
            TabLoadStatus.Loaded(
                items = sampleCells(count = 7).toImmutableList(),
                isAppending = false,
                isRefreshing = false,
                hasMore = false,
                cursor = null,
            ),
    )
}

@PreviewTest
@Preview(name = "media-empty-light", showBackground = true, heightDp = 400)
@Preview(name = "media-empty-dark", showBackground = true, heightDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileMediaTabBodyEmptyScreenshot() {
    MediaBodyHost(
        status =
            TabLoadStatus.Loaded(
                items = persistentListOf(),
                isAppending = false,
                isRefreshing = false,
                hasMore = false,
                cursor = null,
            ),
    )
}

@PreviewTest
@Preview(name = "media-error-light", showBackground = true, heightDp = 400)
@Preview(name = "media-error-dark", showBackground = true, heightDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileMediaTabBodyErrorScreenshot() {
    MediaBodyHost(status = TabLoadStatus.InitialError(ProfileError.Network))
}
```

- [ ] **Step 2: Generate the baselines**

```bash
./gradlew :feature:profile:impl:updateDebugScreenshotTest
```

Expected: BUILD SUCCESSFUL; 6 new PNGs written under `feature/profile/impl/src/screenshotTestDebug/reference/.../ProfileMediaTabBodyScreenshotTestKt/`.

- [ ] **Step 3: Validate**

```bash
./gradlew :feature:profile:impl:validateDebugScreenshotTest
```

Expected: PASS. All baselines (Bead D's existing + the 6 new Media ones) validate.

- [ ] **Step 4: Commit**

```bash
git add feature/profile/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/profile/impl/ui/ProfileMediaTabBodyScreenshotTest.kt \
        feature/profile/impl/src/screenshotTestDebug/reference/net/kikin/nubecita/feature/profile/impl/ui/ProfileMediaTabBodyScreenshotTestKt/
git commit -m "test(feature/profile/impl): screenshot baselines for profileMediaTabBody

3 fixtures × 2 themes = 6 new baselines covering the Media tab body:
- media-loaded: 7 cells = 2 full rows + 1 short row (exercises the
  Spacer-padding code path for incomplete last rows).
- media-empty: Loaded(items=[]) renders ProfileEmptyState(Media).
- media-error: InitialError(Network) renders ProfileErrorState.

Hosts the LazyListScope extension inside a LazyColumn so the layout
reflects the production call site. Coil is no-op in Layoutlib so
MediaCellThumb cells render NubecitaAsyncImage's placeholder
ColorPainter; visual delta with real thumbs is verified on-device.

Refs: nubecita-s6p.5"
```

---

## Task 8: Add 3 Replies fixtures to ProfileScreenContentScreenshotTest

**Files:**
- Modify: `feature/profile/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreenContentScreenshotTest.kt`

- [ ] **Step 1: Append the 3 new Replies fixtures**

At the bottom of `ProfileScreenContentScreenshotTest.kt` (after the existing `ProfileScreenPostsErrorScreenshot` function), append:

```kotlin
@PreviewTest
@Preview(name = "screen-replies-loaded-light", showBackground = true, heightDp = 1600)
@Preview(name = "screen-replies-loaded-dark", showBackground = true, heightDp = 1600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileScreenRepliesLoadedScreenshot() {
    // Replies tab selected; repliesStatus holds 3 reply PostCards.
    // postsStatus / mediaStatus stay Idle — irrelevant when selectedTab = Replies.
    ProfileScreenContentHost(
        sampleLoadedState().copy(
            selectedTab = ProfileTab.Replies,
            repliesStatus =
                TabLoadStatus.Loaded(
                    items =
                        persistentListOf(
                            TabItemUi.Post(samplePostUi("reply-a")),
                            TabItemUi.Post(samplePostUi("reply-b")),
                            TabItemUi.Post(samplePostUi("reply-c")),
                        ),
                    isAppending = false,
                    isRefreshing = false,
                    hasMore = false,
                    cursor = null,
                ),
        ),
    )
}

@PreviewTest
@Preview(name = "screen-replies-empty-light", showBackground = true, heightDp = 1200)
@Preview(name = "screen-replies-empty-dark", showBackground = true, heightDp = 1200, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileScreenRepliesEmptyScreenshot() {
    ProfileScreenContentHost(
        sampleLoadedState().copy(
            selectedTab = ProfileTab.Replies,
            repliesStatus =
                TabLoadStatus.Loaded(
                    items = EMPTY_LIST,
                    isAppending = false,
                    isRefreshing = false,
                    hasMore = false,
                    cursor = null,
                ),
        ),
    )
}

@PreviewTest
@Preview(name = "screen-replies-error-light", showBackground = true, heightDp = 1200)
@Preview(name = "screen-replies-error-dark", showBackground = true, heightDp = 1200, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileScreenRepliesErrorScreenshot() {
    ProfileScreenContentHost(
        sampleLoadedState().copy(
            selectedTab = ProfileTab.Replies,
            repliesStatus = TabLoadStatus.InitialError(ProfileError.Network),
        ),
    )
}
```

- [ ] **Step 2: Generate the baselines**

```bash
./gradlew :feature:profile:impl:updateDebugScreenshotTest
```

Expected: BUILD SUCCESSFUL. 6 new PNGs added under `feature/profile/impl/src/screenshotTestDebug/reference/.../ProfileScreenContentScreenshotTestKt/` (loaded + empty + error × 2 themes).

- [ ] **Step 3: Validate**

```bash
./gradlew :feature:profile:impl:validateDebugScreenshotTest
```

Expected: PASS across the whole module.

- [ ] **Step 4: Commit**

```bash
git add feature/profile/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/profile/impl/ProfileScreenContentScreenshotTest.kt \
        feature/profile/impl/src/screenshotTestDebug/reference/net/kikin/nubecita/feature/profile/impl/ProfileScreenContentScreenshotTestKt/
git commit -m "test(feature/profile/impl): screenshot baselines for Replies tab variants

3 fixtures × 2 themes = 6 new baselines covering the full
ProfileScreenContent with the Replies tab selected:
- screen-replies-loaded: 3 reply PostCards.
- screen-replies-empty: empty Loaded state → ProfileEmptyState(Replies).
- screen-replies-error: InitialError(Network) → ProfileErrorState.

Reuses sampleLoadedState() + samplePostUi() helpers from Bead D's
ProfileScreenContentScreenshotTest. FixtureClock keeps PostCard
relative-time labels deterministic.

Refs: nubecita-s6p.5"
```

---

## Task 9: Full local verification + open PR with run-instrumented label

**Files:** (no source changes — git/gh only)

- [ ] **Step 1: Run the full local verification**

```bash
./gradlew \
  :feature:profile:impl:assembleDebug \
  :feature:profile:impl:testDebugUnitTest \
  :feature:profile:impl:validateDebugScreenshotTest \
  :app:assembleDebug \
  spotlessCheck lint :app:checkSortDependencies
```

Expected: BUILD SUCCESSFUL across the board. 10 ProfileViewModelTest pass. The Bead D androidTest (`ProfileScreenInstrumentationTest.editTap_surfacesComingSoonSnackbar`) wasn't touched by Bead E; CI's instrumented job will re-run it under the existing label.

- [ ] **Step 2: Push the branch**

```bash
git push -u origin feat/nubecita-s6p.5-feature-profile-impl-replies-tab-media-tab-3-col-m
```

- [ ] **Step 3: Open the PR**

Write the PR body to a temp file first (avoids shell-escaping issues with backticks):

```bash
cat > /tmp/pr-s6p5-body.md <<'EOF'
## Summary

- Wires the Replies and Media tabs into `:feature:profile:impl` on top of Bead D's foundation. After this PR, all 3 pill tabs are fully functional — Replies renders a `LazyColumn` of `PostCard`s for reply-filtered posts, Media renders a 3-col row-packed thumbnail grid for media-filtered posts.
- Generalizes `profilePostsTabBody` → `profileFeedTabBody(tab, status, callbacks, onRetry)` so Posts and Replies share one implementation (the two are literally identical, not coincidentally similar). New `profileMediaTabBody` is a sibling LazyListScope extension that chunks `TabItemUi.MediaCell` items into rows of 3.
- Generalizes the pagination gate from Bead D's hardcoded-Posts version to fire `LoadMore(selectedTab)` for whichever tab is `Loaded && hasMore && !isAppending`. New private `activeTabStatus()` extension mirrors the existing `activeTabIsRefreshing()` pattern.
- Deletes `ProfileTabPlaceholder.kt` and its 4 screenshot baselines (Bead D's transitional composable). Adds 12 new screenshot baselines covering Replies-loaded/empty/error and Media-loaded/empty/error (light + dark each).
- Adds 2 new VM regression tests covering Replies LoadMore isolation and Media-tab PostTapped effect shape (both behaviors are correct in Bead C; tests are guards against future regressions).

Design: `docs/superpowers/specs/2026-05-12-profile-bead-e-design.md`
Plan: `docs/superpowers/plans/2026-05-12-profile-bead-e-plan.md`

## Test plan

- [ ] `./gradlew :feature:profile:impl:testDebugUnitTest` — 10 ProfileViewModelTest cases pass (8 from Bead D + 2 new).
- [ ] `./gradlew :feature:profile:impl:validateDebugScreenshotTest` — all baselines validate (Bead D's 30 + 12 new from this PR − 4 deleted placeholder baselines).
- [ ] `./gradlew :app:assembleDebug spotlessCheck lint :app:checkSortDependencies` — green locally.
- [ ] CI's instrumented job re-runs Bead D's `ProfileScreenInstrumentationTest` (no androidTest changes in this PR; existing `run-instrumented` label still applies).
- [ ] Manual on-device check: open the You tab → tap Replies → reply timeline loads + paginates; tap Media → 3-col grid renders + tap a cell → lands on PostDetail; pull-to-refresh on each tab.

## Out of scope

- Bead F (`nubecita-s6p.6`): other-user actions row, ListDetailSceneStrategy metadata, Settings stub, Medium-width screenshots.
- `nubecita-1tc`: scroll-collapsing hero + TopAppBar inset handling (the pill-tabs-drawing-into-status-bar issue from PR #158's on-device review still applies; not addressed here).

## Notable design decisions

- **Row-packing instead of `LazyVerticalGrid`** for the Media grid. The spec's literal wording said `LazyVerticalGrid(GridCells.Fixed(3))`, but a real LazyVerticalGrid can't live inside Bead D's scrolling LazyColumn (infinite-height constraint). Row-packing achieves identical visual outcome with a single shared scroll surface across all 3 tabs.
- **Generalize Posts/Replies into one body** (`profileFeedTabBody`) since the two bodies are literally identical. The Media tab gets its own body because the layout shape differs.

Closes: nubecita-s6p.5
EOF
```

```bash
gh pr create --base main --title "feat(feature/profile/impl): Replies tab + Media tab + 3-col media grid" --body-file /tmp/pr-s6p5-body.md
rm /tmp/pr-s6p5-body.md
```

Expected output: a single GitHub PR URL (something like `https://github.com/kikin81/nubecita/pull/NNN`).

- [ ] **Step 4: Verify the PR carries the `run-instrumented` label**

The PR doesn't touch `androidTest/` sources, but it does extend the screen behavior in ways that an instrumented test SHOULD validate over time. The Bead D instrumentation test (`ProfileScreenInstrumentationTest`) still applies — its scenario (Edit-tap → snackbar) is in the hero, not in the new tab bodies — so re-running it under the existing label is a regression guard.

```bash
gh pr edit --add-label run-instrumented
```

If the label is already on (unlikely on a brand-new PR), this is a no-op.

```bash
gh pr view --json number,url,labels,statusCheckRollup
```

Confirm: `run-instrumented` is among the labels. CI checks are running.

---

## Self-review checklist

Performed by the plan author before handoff:

**Spec coverage** — every section/requirement in `2026-05-12-profile-bead-e-design.md` traces to a task above:

| Spec section | Task |
|---|---|
| Generalize `profilePostsTabBody` → `profileFeedTabBody(tab, ...)` | Task 2 |
| `profileMediaTabBody` LazyListScope extension with row-packing | Task 4 |
| `MediaCellThumb` Composable | Task 3 |
| Tab dispatch in `ProfileScreenContent` (all 3 tabs functional) | Task 5 |
| Pagination gate generalization with `activeTabStatus()` | Task 5 |
| Delete `ProfileTabPlaceholder.kt` + screenshot test + baselines | Task 6 |
| 6 new Media screenshot baselines | Task 7 |
| 6 new Replies screenshot baselines | Task 8 |
| 2 new VM tests (Replies isolation + Media PostTapped) | Task 1 |
| Local verification + PR + `run-instrumented` label | Task 9 |

**Placeholder scan** — searched the plan for "TBD", "TODO", "Similar to Task N", "fill in details", "add appropriate error handling" — zero matches in step bodies. Every code step has complete Kotlin.

**Type consistency** —
- `profileFeedTabBody(tab: ProfileTab, status: TabLoadStatus, callbacks: PostCallbacks, onRetry: () -> Unit)` — signature consistent between Task 2 (definition) and Task 5 (call sites).
- `profileMediaTabBody(status: TabLoadStatus, onMediaTap: (String) -> Unit, onRetry: () -> Unit)` — consistent between Task 4 and Task 5.
- `MediaCellThumb(cell: TabItemUi.MediaCell, onClick: () -> Unit, modifier: Modifier)` — consistent between Task 3 (definition) and Task 4 (caller).
- `ProfileScreenViewState.activeTabStatus()` returns `TabLoadStatus` — consistent throughout Task 5.
- Screenshot fixture names (`screen-replies-loaded-light`, `media-loaded-light`, etc.) match the existing Bead D convention (`<scope>-<variant>-<theme>`).

**Scope check** — 9 tasks total, each producing a green commit on the `feat/nubecita-s6p.5-…` branch. Task 1 is regression tests against existing behavior (low risk, ships green). Tasks 2-6 are the screen-layer changes. Tasks 7-8 add screenshot baselines. Task 9 ships.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-05-12-profile-bead-e-plan.md`. Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
