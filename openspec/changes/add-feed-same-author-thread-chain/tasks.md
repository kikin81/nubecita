## 1. Data model: `FeedItemUi.SelfThreadChain`

- [x] 1.1 Add `data class SelfThreadChain(val posts: ImmutableList<PostUi>) : FeedItemUi` to `:data:models/.../FeedItemUi.kt` with `key: String get() = posts.last().id`. Update the file's class-level KDoc to describe the new variant alongside `Single` and `ReplyCluster` (replace the "future variant" forward-reference).
- [x] 1.2 No build.gradle changes (the variant uses only existing types: `PostUi`, `kotlinx.collections.immutable.ImmutableList`, `androidx.compose.runtime.Stable`).

## 2. Mapping: chain detection pass

- [x] 2.1 Add `internal fun List<FeedViewPost>.toFeedItemsUi(): ImmutableList<FeedItemUi>` to `:feature:feed:impl/data/FeedViewPostMapper.kt`. The function runs the existing per-entry pass first, then walks the result with index access to the original wire entries (so the strict `parent.uri == prev.uri` check works against `FeedViewPost.reply.parent`, not the projected `FeedItemUi`).
- [x] 2.2 Implement the strict link rule as a private helper `private fun linksTo(prev: FeedViewPost, next: FeedViewPost): Boolean` covering the 5-clause check from `feature-feed` spec (reply non-null, parent is `PostView`, same author DID, parent URI matches prev URI, neither has `ReasonRepost`).
- [x] 2.3 Build chains as maximal runs: when `linksTo(e[i-1], e[i])` is true AND both project to non-null `PostUi`, accumulate; otherwise flush the current chain (size 1 → emit as `Single`; size ≥ 2 → emit as `SelfThreadChain`) and continue.
- [ ] 2.4 Mapper unit tests: 3-post chain happy path, 2-post chain, broken chain (different author at link point), `ReasonRepost` rejection (on prev, on next, on both), parent URI mismatch (skip-ahead rejection), parent is `BlockedPost` rejection, parent is `NotFoundPost` rejection. Keep existing `FeedViewPostMapperTest` cases unchanged (the per-entry pass is untouched — only a new top-level pass is added).

## 3. Repository wiring

- [x] 3.1 Update `DefaultFeedRepository` to call the new `toFeedItemsUi()` pass instead of the per-entry projection where it builds `TimelinePage.feedItems`. The wire-level `posts: List<FeedViewPost>` must also be exposed on `TimelinePage` so the VM's page-boundary merge can read `reply.parent.uri` on the head entry.
- [x] 3.2 Add a `posts: ImmutableList<FeedViewPost>` field to `TimelinePage` (or a parallel structure that pairs each `FeedItemUi` with its source wire entry). Document in KDoc that this surface is for the VM merge step, not for general consumers.
- [ ] 3.3 Repository unit test: `getTimeline` projects a same-author 3-post run into one `SelfThreadChain` in `feedItems` AND populates the new wire-level field with the original `FeedViewPost` entries.

## 4. ViewModel: page-boundary chain merge

- [x] 4.1 Add a private extension `private fun ImmutableList<FeedItemUi>.mergeChainBoundary(newPage: TimelinePage): ImmutableList<FeedItemUi>` on `FeedViewModel`. Implements the fixed-point loop from design Decision 3: pop the current tail if it's a `Single` or `SelfThreadChain` whose last post links (via the strict rule) to the new page's head; reform the chain; recurse on the next entry until the link rule fails.
- [x] 4.2 Wire the merge into `applyInitialPage` (replaces `feedItems` on initial load + refresh). (Not needed — initial load + refresh REPLACE feedItems entirely; chain detection is already complete via the page-internal mapper. The merge only activates in `loadMore` where existing tail can extend across boundaries.)
- [x] 4.3 Wire the merge into the `LoadMore` reducer's `merged = ...` step. The de-dupe-by-key step still runs first (drops re-served leaves); merge runs after on the de-duped page.
- [x] 4.4 Add `is FeedItemUi.SelfThreadChain ->` branch to the `findPost` extension in `FeedViewModel` (resolve `id` against `chain.posts.firstOrNull { it.id == id }`). Also add the same branch to `replacePost` and `dedupeClusterContext`.
- [ ] 4.5 VM unit tests: page-boundary extension happy path (tail extends across the boundary), page-boundary no-extension (head is unrelated → page appended as-is), `ReplyCluster` tail does NOT extend (cross-author cluster + same-author reply ≠ chain), `findPost` resolves a URI inside a chain.

## 5. Render: `FeedScreen` chain branch

- [x] 5.1 Add `is FeedItemUi.SelfThreadChain ->` to `FeedScreen`'s render dispatch. Inside the branch, render a single `Column` containing one `PostCard` per chain post; per-index connector flags as in design Decision 5 (first: `connectBelow=true`; middle: both true; last: `connectAbove=true`).
- [x] 5.2 Plumb existing PostCallbacks (`onTap`, `onLike`, `onRepost`, `onShare`, `onAuthorTap`) through to each card in the chain — same-author chain posts are independently like-able / repost-able. Wire the `onImageClick` parameter as `null` for chain posts (same-author chain images stay un-interactive in the feed; tapping a chain post body opens post-detail per the existing PostCard body-tap contract).
- [x] 5.3 Add the `is FeedItemUi.SelfThreadChain ->` branches required to keep the render's existing `when` blocks exhaustive (the type-resolver dispatch for "single" / "cluster" content-type strings, the URI-to-post lookup for like/repost dispatch, etc.). These should be small additions — the render path's main changes are localized to the new branch.

## 6. Screenshot fixtures

- [ ] 6.1 Add a `feed-with-3-post-self-chain` fixture under `:feature:feed:impl/src/screenshotTest/` rendering a feed with one `SelfThreadChain(posts.size = 3)` between two `Single` entries, light theme. The fixture is the regression contract for the chain-renders-with-continuous-gutter-line scenario in the `feature-feed` spec.
- [ ] 6.2 Add a `feed-with-self-chain-quote-post-middle` fixture: 3-post chain whose middle post carries an `EmbedUi.Record` (quote-post). Locks the quote-post-vs-gutter non-collision contract from design Decision 6.
- [ ] 6.3 Run `./gradlew :feature:feed:impl:validateDebugScreenshotTest` and confirm only the new chain fixtures changed; every existing fixture stays byte-for-byte unchanged.

## 7. Verification gate

- [ ] 7.1 `./gradlew :feature:feed:impl:testDebugUnitTest` clean.
- [ ] 7.2 `./gradlew :feature:feed:impl:validateDebugScreenshotTest` clean (modulo the 4 pre-existing video-screenshot flakes documented in the m28.5.2 PR).
- [ ] 7.3 `./gradlew :data:models:assembleDebug` clean (no new deps, no compilation regressions).
- [ ] 7.4 `./gradlew :app:assembleDebug spotlessCheck lint` clean.
- [ ] 7.5 Manual: open a session with a known same-author thread author in the timeline (e.g. a creator with a known self-thread) → verify the chain renders with one continuous gutter line; force a refresh → chain still intact; scroll past the chain into a fresh page → chain doesn't visibly split if the wire response continues the chain across the cursor cut.
- [ ] 7.6 PR description references the openspec change name (`add-feed-same-author-thread-chain`) and the bd id (`Closes: nubecita-m28.4`).
