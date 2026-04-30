## 1. Data layer — `FeedItemUi` sealed type

- [x] 1.1 Create `:data:models/src/main/kotlin/.../FeedItemUi.kt` with `sealed interface FeedItemUi`, `data class Single(val post: PostUi) : FeedItemUi`, and `data class ReplyCluster(val root, val parent, val leaf, val hasEllipsis) : FeedItemUi`. Annotate the interface with `@Stable`. Expose a `FeedItemUi.key: String` extension property that returns `post.id` for Single and `leaf.id` for ReplyCluster — used as the LazyColumn `key` lambda.
- [x] 1.2 Add 4 unit tests in `:data:models/src/test/.../FeedItemUiTest.kt` covering: `Single.key == post.id`; `ReplyCluster.key == leaf.id`; `Single` and `ReplyCluster` are distinct sealed variants (compile-time exhaustive `when`); `data class` equality on Single and ReplyCluster.

## 2. Mapper — `toFeedItemUiOrNull`

- [x] 2.1 In `:feature:feed:impl/data/FeedViewPostMapper.kt`, extract a `private fun PostView.toPostUiCore(repostedBy: String? = null): PostUi?` helper from the existing `FeedViewPost.toPostUiOrNull` body. The helper does the record-decode + createdAt-parse + PostUi construction; `repostedBy` is passed in by the caller (leaf reads from `reason`, root/parent pass null).
- [x] 2.2 Refactor `FeedViewPost.toPostUiOrNull` to call `post.toPostUiCore(repostedBy = ...)` — no behavioral change. Existing tests pass unmodified.
- [x] 2.3 Add new entry-point `internal fun FeedViewPost.toFeedItemUiOrNull(): FeedItemUi?`. Body: project the leaf via the existing `toPostUiOrNull` (early-return null if leaf can't project); detect `reply: ReplyRef?`; if reply is null, return `Single(leaf)`; if `reply.parent` is `BlockedPost` / `NotFoundPost`, return `Single(leaf)` and `Timber.w(...)`; else `when` on the `reply.parent` and `reply.root` PostView variants and project both via `toPostUiCore`; compute `hasEllipsis` per the heuristic; return `ReplyCluster(root, parent, leaf, hasEllipsis)`.
- [x] 2.4 Helper `private fun ProfileViewBasic?.distinctFromRoot(rootAuthorDid: String): Boolean` for the `hasEllipsis` heuristic — keeps the entry-point fn body readable.
- [x] 2.5 Add 6 unit tests in `:feature:feed:impl/src/test/.../data/FeedViewPostMapperTest.kt` (extend the existing test class) calling the **new** `toFeedItemUiOrNull` entry: `reply == null` → `Single`; `reply.parent` PostView + `grandparentAuthor == null` → `ReplyCluster(hasEllipsis = false)`; `reply.parent` PostView + `grandparentAuthor != null && grandparentAuthor.did == root.author.did` → `ReplyCluster(hasEllipsis = false)`; `reply.parent` PostView + `grandparentAuthor.did != root.author.did` → `ReplyCluster(hasEllipsis = true)`; `reply.parent` is `BlockedPost` → `Single`; `reply.parent` is `NotFoundPost` → `Single`. Add fixtures `timeline_with_reply.json`, `timeline_with_reply_grandparent.json`, `timeline_with_reply_blocked_parent.json`, `timeline_with_reply_notfound_parent.json` under `src/test/resources/fixtures/`.

## 3. `PostCard` connector integration

- [x] 3.1 Update `:designsystem/component/PostCard.kt` signature: add `connectAbove: Boolean = false, connectBelow: Boolean = false` params (in the right slot per the parameter-order convention — after required, before lambda). When either is true, apply `Modifier.threadConnector(connectAbove, connectBelow, color = MaterialTheme.colorScheme.outlineVariant)` to PostCard's root container. When both are false, no modifier applied.
- [x] 3.2 Add 2 new screenshot baselines for the connector-bearing variant in `:designsystem/src/screenshotTest/.../PostCardScreenshotTest.kt` (or its existing peer): `connectAbove + connectBelow` × {light, dark}. Existing PostCard baselines unchanged (defaults preserve old behavior).

## 4. `ThreadCluster` composable

- [x] 4.1 Create `:designsystem/src/main/kotlin/.../component/ThreadCluster.kt`. Signature per the spec: `ThreadCluster(root, parent, leaf, callbacks, modifier, hasEllipsis, leafVideoEmbedSlot, leafQuotedVideoEmbedSlot, onFoldTap)`. Body: a `Column` rendering root → optional ThreadFold → parent → leaf with the connector flags wired per the spec table.
- [x] 4.2 Add 4 screenshot baselines for ThreadCluster in `:designsystem/src/screenshotTest/.../ThreadClusterScreenshotTest.kt`: `{with-ellipsis, without-ellipsis} × {light, dark}`. Use a fixed-data fixture (similar shape to `PostCardQuotedPostScreenshotTest`'s FIXED_AUTHOR / FIXED_TEXT pattern) with three distinct authors so the cluster shape is visible.
- [x] 4.3 Add a `:designsystem/src/main/.../component/preview/ThreadClusterPreviews.kt` (or inline previews in `ThreadCluster.kt`) exposing 2 previews — with-ellipsis and without-ellipsis — for in-IDE inspection.

## 5. ViewState + ViewModel projection

- [x] 5.1 In `:feature:feed:impl/FeedScreenViewState.kt`, rename `Loaded.posts: ImmutableList<PostUi>` to `Loaded.feedItems: ImmutableList<FeedItemUi>`. Update the data-class generated equality / copy to match.
- [x] 5.2 In `:feature:feed:impl/FeedViewModel.kt`, find the projection from session state → `FeedScreenViewState.Loaded` and replace `feedViewPosts.mapNotNull { it.toPostUiOrNull() }` with `feedViewPosts.mapNotNull { it.toFeedItemUiOrNull() }`. Verify by running `./gradlew :feature:feed:impl:testDebugUnitTest` — existing fixtures should still pass.
- [x] 5.3 Update `:feature:feed:impl/src/test/.../FeedScreenViewStateTest.kt` to use `feedItems = ...` in fixtures. Mirror the existing `posts = ...` test helpers.

## 6. `FeedScreen` dispatch

- [x] 6.1 In `:feature:feed:impl/FeedScreen.kt`'s `LoadedFeedContent`: change the `LazyColumn { items(posts, key = { it.id }) }` to `items(feedItems, key = { it.key })` and dispatch on `when (item)`: `Single → PostCard(...)` (existing call shape, with `connectAbove = false, connectBelow = false`), `ReplyCluster → ThreadCluster(...)`. The existing `videoSlot` + `quotedVideoSlot` lambdas wire to the leaf's PostCard for clusters and to the post for singles.
- [x] 6.2 Update FeedScreen's previews (the `previewPosts(...)` + `FeedScreenLoadedPreview` etc. block) to use `previewFeedItems(...)` returning `ImmutableList<FeedItemUi>` with at least one `Single` and one `ReplyCluster` so the visual contrast renders in the IDE preview pane.
- [x] 6.3 Add a new screenshot baseline `FeedScreenLoadedWithClusterScreenshot` in `:feature:feed:impl/src/screenshotTest/.../FeedScreenScreenshotTest.kt` covering a Loaded viewState with a Single + a ReplyCluster (with ellipsis). Light + dark → 2 baselines.

## 7. Migration cleanup

- [x] 7.1 Verify no remaining references to `FeedScreenViewState.Loaded.posts` exist (`grep -rn "Loaded.posts" --include="*.kt"`). All callers should now use `feedItems`.
- [x] 7.2 Verify the only remaining production caller of `FeedViewPost.toPostUiOrNull` is the existing test suite. `DefaultFeedRepository` and `FeedViewModel` SHALL use `toFeedItemUiOrNull` exclusively. The 18+ existing mapper tests stay on `toPostUiOrNull` for now and will migrate in a future cleanup ticket — file as `discovered-from: nubecita-m28.3` after this PR merges.

## 8. Verification

- [x] 8.1 `./gradlew :data:models:testDebugUnitTest :feature:feed:impl:testDebugUnitTest :designsystem:testDebugUnitTest` — green.
- [x] 8.2 `./gradlew :designsystem:validateDebugScreenshotTest :feature:feed:impl:validateDebugScreenshotTest` — green.
- [x] 8.3 `./gradlew :app:assembleDebug spotlessCheck lint :app:checkSortDependencies` — green.
- [x] 8.4 `pre-commit run --all-files` — green.
- [x] 8.5 `openspec validate add-feed-cross-author-thread-cluster --strict` — green.
- [ ] 8.6 Manual smoke: `./gradlew :app:installDebug` on connected device, browse home feed, find a reply post, verify it renders as a cluster.
